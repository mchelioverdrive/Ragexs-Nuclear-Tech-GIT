import tempfile
import unittest
from pathlib import Path

import lang_rebase_translate as tool


class FakeTranslator:
    def __init__(self, cache):
        self.cache = cache

    def translate_many(self, texts, target):
        return {text: f"{target}:{text}" for text in set(texts)}


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


if __name__ == "__main__":
    unittest.main()
