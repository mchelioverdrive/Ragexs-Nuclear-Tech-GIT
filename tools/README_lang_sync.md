# RNT language audit and synchronization tool

`lang_sync.py` treats `en_US.lang` as the key schema for every RNT locale.
It never changes a language file unless `--fix` is present.
It uses only the Python standard library and does not run Gradle.

## Requirements

- Python 3.8 or newer
- Run the command from the RNT repository root

## Audit every locale

Windows:

```bat
py -3 tools\lang_sync.py
```

Linux or macOS:

```bash
python3 tools/lang_sync.py
```

The audit reports:

- Missing keys
- Obsolete keys
- Duplicate keys
- Malformed lines
- Java format argument mismatches such as `%s`, `%d`, and `%1$s`
- Minecraft formatting-code mismatches such as `§c`
- Values that are identical to current English
- English values that changed after a baseline was created
- Translations that still contain the previous English value

## Create the first English baseline

```bat
py -3 tools\lang_sync.py --update-baseline
```

Commit `tools/lang_baseline.json` after reviewing it.
Future audits will report keys whose English text changed.

## Write a JSON report

```bat
py -3 tools\lang_sync.py --json-report build\lang-audit.json
```

The `build` directory and report are local audit output and do not need to be committed.

## Synchronize every locale

```bat
py -3 tools\lang_sync.py --fix --backup
```

This action:

- Uses the key order and section comments from `en_US.lang`
- Keeps existing translated values
- Removes obsolete keys
- Collapses duplicate keys using the last value
- Drops malformed locale lines
- Preserves locale-specific header comments
- Leaves missing keys absent so Minecraft can use English fallback

Each changed file receives a `.bak` copy when `--backup` is present.
Review the diff before deleting the backup files.

## Synchronize one locale

```bat
py -3 tools\lang_sync.py --locale de_DE --fix --backup
```

Repeat `--locale` to select more than one locale.

## Missing translation modes

The default is `--missing omit`.
This keeps missing keys out of translated files and relies on English fallback.

Copy the current English value:

```bat
py -3 tools\lang_sync.py --fix --missing english
```

Copy English and add a marker comment:

```bat
py -3 tools\lang_sync.py --fix --missing marker
```

Do not use copied English values as finished translations.

## Strict validation

```bat
py -3 tools\lang_sync.py --strict
```

Normal mode returns exit code `1` for dangerous problems such as duplicate keys,
malformed lines, or format argument mismatches.
Strict mode also fails for missing, obsolete, color-code, unchanged-English, and
changed-English review items.

## Run the unit tests

Windows:

```bat
cd tools
py -3 -m unittest -v test_lang_sync.py
```

Linux or macOS:

```bash
cd tools
python3 -m unittest -v test_lang_sync.py
```
