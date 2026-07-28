import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

import lang_rebase_translate as tool


class FakeTranslator:
    def __init__(self, cache):
        self.cache = cache

    def translate_many(self, texts, target):
        return {text: f"{target}:{text}" for text in set(texts)}


class RateLimitedTranslator:
    def __init__(self, cache):
        self.cache = cache

    def translate_many(self, texts, target):
        self.cache.put(target, "already done", "déjà fait")
        self.cache.save()
        raise tool.TranslationRateLimit("test rate limit")


class LangRebaseTests(unittest.TestCase):
    def test_parses_active_and_commented_properties(self):
        lines = tool.parse_template(
            "item.one.name=One\n"
            "#book.rbmk.title1=Introduction\n"
            "# plain comment\n"
        )
        self.assertTrue(lines[0].is_property)
        self.assertEqual(lines[0].key, "item.one.name")
        self.assertEqual(lines[0].value, "One")
        self.assertTrue(lines[1].is_property)
        self.assertEqual(lines[1].prefix, "#")
        self.assertEqual(lines[1].key, "book.rbmk.title1")
        self.assertFalse(lines[2].is_property)

    def test_masks_game_tokens(self):
        source = "Heat: §e%s$Next %1$s at https://example.com"
        masked, replacements = tool.mask_text(source)
        self.assertNotIn("§e", masked)
        self.assertNotIn("%s", masked)
        self.assertNotIn("$", masked)
        self.assertEqual(tool.unmask_text(masked, replacements), source)

    def test_render_preserves_key_and_comment_prefix(self):
        template = tool.parse_template(
            "item.one.name=One\n#book.rbmk.title1=Introduction"
        )
        rendered = tool.render_template(
            template,
            {"One": "Eins", "Introduction": "Einleitung"},
        )
        self.assertEqual(rendered[0], "item.one.name=Eins")
        self.assertEqual(rendered[1], "#book.rbmk.title1=Einleitung")

    def test_full_rebase_uses_en_us_template(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lang_dir = root / tool.LANG_DIR
            lang_dir.mkdir(parents=True)

            (lang_dir / "en_US.lang").write_text(
                "item.real.name=Real Name\n"
                "#book.rbmk.title1=Introduction\n",
                encoding="utf-8",
            )
            (lang_dir / "de_DE.lang").write_text(
                "item.real.name=Old Fake Name\nobsolete.key=Old\n",
                encoding="utf-8",
            )
            (lang_dir / "test.lang").write_text(
                "do not touch",
                encoding="utf-8",
            )

            result = tool.run(
                root,
                translator_factory=lambda cache: FakeTranslator(cache),
            )
            self.assertEqual(result, 0)

            german = (lang_dir / "de_DE.lang").read_text(encoding="utf-8")
            self.assertEqual(
                german,
                "item.real.name=de:Real Name\n"
                "#book.rbmk.title1=de:Introduction\n",
            )
            self.assertNotIn("obsolete.key", german)
            self.assertEqual(
                (lang_dir / "test.lang").read_text(encoding="utf-8"),
                "do not touch",
            )

    def test_custom_locale_copies_english(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lang_dir = root / tool.LANG_DIR
            lang_dir.mkdir(parents=True)

            (lang_dir / "en_US.lang").write_text(
                "item.real.name=Real Name\n",
                encoding="utf-8",
            )
            (lang_dir / "en_NT.lang").write_text(
                "item.real.name=Old Name\n",
                encoding="utf-8",
            )

            tool.run(
                root,
                translator_factory=lambda cache: FakeTranslator(cache),
            )

            self.assertEqual(
                (lang_dir / "en_NT.lang").read_text(encoding="utf-8"),
                "item.real.name=Real Name\n",
            )

    def test_locale_filter_only_writes_selected_locale(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lang_dir = root / tool.LANG_DIR
            lang_dir.mkdir(parents=True)

            (lang_dir / "en_US.lang").write_text(
                "item.real.name=Real Name\n",
                encoding="utf-8",
            )
            (lang_dir / "fr_FR.lang").write_text("old french\n", encoding="utf-8")
            (lang_dir / "it_IT.lang").write_text("old italian\n", encoding="utf-8")

            result = tool.run(
                root,
                translator_factory=lambda cache: FakeTranslator(cache),
                selected_locales={"fr_FR"},
            )

            self.assertEqual(result, 0)
            self.assertEqual(
                (lang_dir / "fr_FR.lang").read_text(encoding="utf-8"),
                "item.real.name=fr:Real Name\n",
            )
            self.assertEqual(
                (lang_dir / "it_IT.lang").read_text(encoding="utf-8"),
                "old italian\n",
            )

    def test_rate_limit_returns_two_and_preserves_cache(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lang_dir = root / tool.LANG_DIR
            lang_dir.mkdir(parents=True)

            (lang_dir / "en_US.lang").write_text(
                "item.real.name=Real Name\n",
                encoding="utf-8",
            )
            (lang_dir / "fr_FR.lang").write_text(
                "item.real.name=Ancien nom\n",
                encoding="utf-8",
            )

            result = tool.run(
                root,
                translator_factory=lambda cache: RateLimitedTranslator(cache),
                selected_locales={"fr_FR"},
            )

            self.assertEqual(result, 2)
            cache = json.loads(
                (root / tool.CACHE_PATH).read_text(encoding="utf-8")
            )
            self.assertEqual(cache["fr"]["already done"], "déjà fait")
            self.assertEqual(
                (lang_dir / "fr_FR.lang").read_text(encoding="utf-8"),
                "item.real.name=Ancien nom\n",
            )

            report = json.loads(
                (root / tool.REPORT_PATH).read_text(encoding="utf-8")
            )
            self.assertEqual(report["status"], "rate-limited")
            self.assertEqual(report["stopped_locale"], "fr_FR")

    def test_request_raises_special_error_after_repeated_429(self):
        with tempfile.TemporaryDirectory() as temp:
            cache = tool.TranslationCache(Path(temp) / "cache.json")
            translator = tool.GoogleTranslator(cache, pause=0, max_attempts=2)
            error = urllib.error.HTTPError(
                translator.ENDPOINT,
                429,
                "Too Many Requests",
                {"Retry-After": "0"},
                io.BytesIO(),
            )

            with mock.patch.object(
                tool.urllib.request,
                "urlopen",
                side_effect=error,
            ), mock.patch.object(tool.time, "sleep"), mock.patch.object(
                tool.random,
                "uniform",
                return_value=0,
            ):
                with self.assertRaises(tool.TranslationRateLimit):
                    translator._request("hello", "fr")

    def test_rate_limited_batch_does_not_fallback_to_individual_requests(self):
        with tempfile.TemporaryDirectory() as temp:
            cache = tool.TranslationCache(Path(temp) / "cache.json")
            translator = tool.GoogleTranslator(cache, pause=0, max_attempts=1)

            with mock.patch.object(
                translator,
                "_request",
                side_effect=tool.TranslationRateLimit("limited"),
            ), mock.patch.object(
                translator,
                "_translate_one",
            ) as translate_one:
                with self.assertRaises(tool.TranslationRateLimit):
                    translator.translate_many(["One", "Two"], "fr")

                translate_one.assert_not_called()


if __name__ == "__main__":
    unittest.main()
