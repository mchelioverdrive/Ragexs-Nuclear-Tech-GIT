import tempfile
import unittest
from pathlib import Path

import lang_sync


class LangSyncV2Tests(unittest.TestCase):
    def parsed(self, text: str, name: str = "x.lang"):
        return lang_sync.parse_text(text, Path(name))

    def test_split_first_equals(self):
        parsed = self.parsed("key==value\n")
        self.assertEqual(parsed.values["key"], "=value")

    def test_comments_are_not_entries(self):
        parsed = self.parsed("#book.rbmk.page1=English\nreal=Value\n")
        self.assertNotIn("book.rbmk.page1", parsed.values)
        self.assertEqual(parsed.commented_properties["book.rbmk.page1"], [1])

    def test_preserves_target_comments_extra_keys_and_order(self):
        source = self.parsed("a=New A\nb=English B\n", "en_US.lang")
        target = self.parsed("# German header\nvanilla.key=Keep\na=Alt A\n", "de_DE.lang")
        delta = lang_sync.source_delta({"a": "Old A", "b": "English B"}, source.values)
        text, changes = lang_sync.build_updated_text(
            source, target, delta, "english", "omit", False
        )
        self.assertIn("# German header", text)
        self.assertIn("vanilla.key=Keep", text)
        self.assertIn("a=New A", text)
        self.assertNotIn("b=English B", text)
        self.assertEqual(changes, 1)

    def test_changed_policy_omit_removes_stale_entry(self):
        source = self.parsed("item=Real Name\n", "en_US.lang")
        target = self.parsed("item=Fake Name\n", "de_DE.lang")
        delta = lang_sync.source_delta({"item": "Old Fake Name"}, source.values)
        text, changes = lang_sync.build_updated_text(
            source, target, delta, "omit", "omit", False
        )
        self.assertNotIn("item=", text)
        self.assertEqual(changes, 1)

    def test_only_proven_deleted_keys_removed(self):
        source = self.parsed("a=A\n", "en_US.lang")
        target = self.parsed("old=Old\nvanilla=Keep\n", "de_DE.lang")
        delta = lang_sync.source_delta({"a": "A", "old": "Old English"}, source.values)
        text, _ = lang_sync.build_updated_text(
            source, target, delta, "keep", "omit", True
        )
        self.assertNotIn("old=Old", text)
        self.assertIn("vanilla=Keep", text)

    def test_source_duplicate_reported(self):
        parsed = self.parsed("a=1\na=2\n", "en_US.lang")
        self.assertEqual(parsed.duplicate_keys, {"a": [1, 2]})

    def test_locale_filename_filter_excludes_test_lang(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in ["en_US.lang", "de_DE.lang", "test.lang", "ns_OC.lang"]:
                (root / name).write_text("a=A\n", encoding="utf-8")
            files = lang_sync.selected_locale_files(root, "en_US.lang", [])
            self.assertEqual([p.name for p in files], ["de_DE.lang", "ns_OC.lang"])


if __name__ == "__main__":
    unittest.main()
