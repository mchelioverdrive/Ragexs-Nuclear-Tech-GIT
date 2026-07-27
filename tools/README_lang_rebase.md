# RNT language rebase

This tool discards old locale contents and rebuilds each locale from `en_US.lang`.

## Run

Open Command Prompt in the repository root:

```cmd
python tools\lang_rebase_translate.py
```

That is the only normal command.

## What it does

- Uses `en_US.lang` as the full template.
- Keeps the same keys, comments, order, and blank lines.
- Translates active `key=value` lines.
- Translates commented guidebook entries such as `#book.rbmk.page1=...`.
- Protects `%s`, `%1$s`, `§` formatting codes, `$`, escape sequences, and URLs.
- Replaces old locale contents completely.
- Skips `test.lang`.
- Copies English into custom locales that have no real translation target.
- Saves old files under `build/lang-rebase-backup/`.
- Saves translation progress in `build/lang-translation-cache.json`.

An internet connection is required. The translation cache lets the command resume after a failure or rate limit.

## Files translated

The current RNT languages are mapped as follows:

- `de_DE` → German
- `fr_FR` → French
- `it_IT` → Italian
- `pl_PL` → Polish
- `ru_RU` → Russian
- `zh_CN` → Simplified Chinese

Custom locales such as `en_NT`, `ns_OC`, and `te_ST` are rebuilt with English text because they are not real machine-translation targets.

## After it finishes

Run:

```cmd
git diff -- src/main/resources/assets/hbm/lang
```

Do not commit files under `build/`.
