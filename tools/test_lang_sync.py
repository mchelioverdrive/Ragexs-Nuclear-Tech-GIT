import tempfile
import unittest
from pathlib import Path

import lang_sync


class LangSyncTests(unittest.TestCase):
    def parse(self, root: Path, name: str, text: str) -> lang_sync.ParsedLang:
        path = root / name
        path.write_text(text, encoding="utf-8", newline="")
        return lang_sync.parse_lang(path)

    def test_split_only_first_equals(self):
        with tempfile.TemporaryDirectory() as temp:
            parsed = self.parse(Path(temp), "en_US.lang", "key==value\n")
            self.assertEqual(parsed.values["key"], "=value")

    def test_duplicate_uses_last_value_and_reports_lines(self):
        with tempfile.TemporaryDirectory() as temp:
            parsed = self.parse(Path(temp), "de_DE.lang", "key=one\nkey=two\n")
            self.assertEqual(parsed.values["key"], "two")
            self.assertEqual(parsed.duplicate_keys, {"key": [1, 2]})

    def test_audit_detects_missing_obsolete_and_format_mismatch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = self.parse(
                root,
                "en_US.lang",
                "a=Value %1$s\nb=Other\n",
            )
            target = self.parse(
                root,
                "de_DE.lang",
                "a=Wert %1$d\nold=Alt\n",
            )
            audit = lang_sync.audit_locale(source, target, {})
            self.assertEqual(audit.missing_keys, ["b"])
            self.assertEqual(audit.obsolete_keys, ["old"])
            self.assertIn("a", audit.format_mismatches)

    def test_sync_keeps_translation_and_source_order(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = self.parse(
                root,
                "en_US.lang",
                "# Machines\n\nb=English B\na=English A\n",
            )
            target = self.parse(
                root,
                "de_DE.lang",
                "# German credits\na=Deutsch A\nold=Alt\nb=Deutsch B\n",
            )
            text = lang_sync.build_synced_text(source, target, "omit")
            self.assertIn("# German credits", text)
            self.assertLess(text.index("b=Deutsch B"), text.index("a=Deutsch A"))
            self.assertNotIn("old=Alt", text)

    def test_marker_mode_marks_missing_english(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = self.parse(root, "en_US.lang", "a=English\n")
            target = self.parse(root, "de_DE.lang", "# Deutsch\n")
            text = lang_sync.build_synced_text(source, target, "marker")
            self.assertIn("# UNTRANSLATED\na=English", text)

    def test_baseline_detects_changed_and_stale_english_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = self.parse(root, "en_US.lang", "a=New English\n")
            target = self.parse(root, "de_DE.lang", "a=Old English\n")
            audit = lang_sync.audit_locale(source, target, {"a": "Old English"})
            self.assertEqual(audit.changed_english_keys, ["a"])
            self.assertEqual(audit.stale_english_copies, ["a"])


if __name__ == "__main__":
    unittest.main()
