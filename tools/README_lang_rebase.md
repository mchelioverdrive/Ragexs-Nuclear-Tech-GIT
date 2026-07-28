# RNT language rebase translator

This tool rebuilds selected locale files from `en_US.lang` and translates their values while preserving keys, ordering, comments, blank lines, formatting codes, placeholders, URLs, and `$` page separators.

## Replace the files

Copy these files into the repository's `tools` directory:

- `lang_rebase_translate.py`
- `test_lang_rebase_translate.py`

Keep the existing translation cache:

```text
build/lang-translation-cache.json
```

Do not commit anything under `build/`.

## Resume the unfinished real languages

From the repository root:

```cmd
python tools\lang_rebase_translate.py --locale fr_FR --locale it_IT --locale pl_PL --locale ru_RU --locale zh_CN
```

The `--locale` option is repeatable. This avoids rewriting German and custom English locales while finishing the remaining translations.

## Rate limiting

The undocumented Google endpoint may return HTTP 429. The replacement:

- waits longer between requests;
- honors `Retry-After` when present;
- uses exponential backoff with jitter;
- never turns a rate-limited batch into hundreds of individual requests;
- saves cached progress and exits with code `2` if throttling persists;
- resumes from `build/lang-translation-cache.json` on the next run.

A locale file is only replaced after all its required translations are available. If throttling stops a locale halfway through, the old locale file remains intact while completed translations stay in the cache.

## Run tests

```cmd
cd tools
python -m unittest -v test_lang_rebase_translate.py
```

## Review before committing

```cmd
git status --short
git diff --check
git diff --stat
git diff -- src/main/resources/assets/hbm/lang
```
