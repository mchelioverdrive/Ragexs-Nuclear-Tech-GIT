# RNT language synchronization tool — safe version

This replacement is deliberately conservative. It does **not** reorder locale files, copy English comments, delete locale-only keys, process `test.lang`, or write `.bak` files into `src/main/resources`.

It also does not pretend to understand whether an arbitrary translated phrase is stale. To detect renamed fictional terms, compare current `en_US.lang` against a Git commit from before the English renames.

## 1. Confirm the bad generated commit is reverted

The repository already contains a revert of commit `df8c34b3265812dce017ca95b605969f57bdac0a`. Pull it locally:

```bat
git pull
```

Then confirm the working tree is clean:

```bat
git status
```

## 2. Replace the old script

Copy this package's `tools\lang_sync.py` and `tools\test_lang_sync.py` into the repository's `tools` directory.

## 3. Fix duplicate keys in English first

Audit only:

```bat
python tools\lang_sync.py
```

The script normally refuses to write while `en_US.lang` contains duplicate or malformed active entries. Resolve each duplicate intentionally. For an immediate controlled pass, `--allow-source-issues` uses the last duplicate value, matching the effective Java-properties behavior, without copying duplicate source lines into locales.

## 4. Find a Git commit from before the fake-to-real English renames

```bat
git log --oneline -- src/main/resources/assets/hbm/lang/en_US.lang
```

Choose the commit immediately before the rename work. Call it `<OLD_COMMIT>` below.

## 5. Preview stale-name replacement

This dry run replaces translations for English values changed since `<OLD_COMMIT>` with the current English value. That guarantees stale fictional names such as `Elite-RadAway` cannot override the current real-world terminology.

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --show-diff --report build\reports\lang-sync.json
```

Nothing is written during this command.

To preview only German:

```bat
python tools\lang_sync.py --locale de_DE --git-base <OLD_COMMIT> --changed-policy english --show-diff
```

## 6. Apply after reviewing the diff

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --apply
```

If the 25 current English duplicates have not been cleaned yet, use:

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --allow-source-issues --apply
```

Backups are stored under `build\lang-sync-backups\<timestamp>\...`, not beside resource files.

If more than 500 entries in one locale would change, the tool stops. Review the dry run, then add `--allow-large` only when the scope is intentional.

## Missing keys

Leave missing translations absent and use Minecraft's English fallback:

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --apply
```

Or explicitly insert current English values with markers:

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --missing-policy marker --apply
```

## Deleted English keys

The tool preserves extra locale keys by default because some are vanilla, Forge, compatibility, or special-locale entries. It removes only keys proven to have existed in the historical English source and later been deleted:

```bat
python tools\lang_sync.py --git-base <OLD_COMMIT> --changed-policy english --remove-deleted --apply
```

## RBMK guidebook issue

Lines such as:

```properties
#book.rbmk.page1=...
```

are comments, not active localization entries. This tool reports them as commented property-like source lines but never copies them into other locales.

To localize that guidebook:

1. Uncomment the intended `book.rbmk.*` entries in `en_US.lang` so they become active keys.
2. Add genuine translations to each supported locale.
3. Leave untranslated keys absent, or insert marked English placeholders with `--missing-policy marker`.

Synchronization cannot automatically produce trustworthy German, French, Italian, Polish, Russian, or Chinese prose translations.

## Create a baseline for future changes

After the English source is clean:

```bat
python tools\lang_sync.py --write-baseline tools\lang_baseline.json
```

For future English changes, use:

```bat
python tools\lang_sync.py --baseline tools\lang_baseline.json --changed-policy english --show-diff
```

Update the baseline only after translations have been reviewed.

## Tests

```bat
cd tools
python -m unittest -v test_lang_sync.py
```
