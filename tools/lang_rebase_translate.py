#!/usr/bin/env python3
"""
Rebuild every locale from en_US.lang.

- en_US.lang is the only template.
- Real locales are machine translated.
- Custom locales are rebuilt with English text.
- Existing locale contents are discarded.
- Backups are written under build/, not resources.
- Translation progress is cached, so reruns resume.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable, Iterable

LANG_DIR = Path("src/main/resources/assets/hbm/lang")
SOURCE_NAME = "en_US.lang"
CACHE_PATH = Path("build/lang-translation-cache.json")
REPORT_PATH = Path("build/lang-rebase-report.json")

# Real languages supported by Google Translate.
LOCALE_TARGETS = {
    "de_DE": "de",
    "fr_FR": "fr",
    "it_IT": "it",
    "pl_PL": "pl",
    "ru_RU": "ru",
    "zh_CN": "zh-CN",
    "es_ES": "es",
    "pt_BR": "pt",
    "pt_PT": "pt",
    "ja_JP": "ja",
    "ko_KR": "ko",
    "nl_NL": "nl",
    "cs_CZ": "cs",
    "tr_TR": "tr",
    "uk_UA": "uk",
    "sv_SE": "sv",
    "fi_FI": "fi",
    "da_DK": "da",
    "nb_NO": "no",
}

# These are custom or joke locales, not real Google Translate targets.
# They are still fully rebased from en_US, but their values remain English.
COPY_ENGLISH = {"en_NT", "ns_OC", "te_ST"}

LOCALE_FILE_RE = re.compile(r"^[a-z]{2}_[A-Z]{2}\.lang$")
COMMENTED_PROPERTY_RE = re.compile(r"^(\s*#\s*)([^#=\s][^=]*?)=(.*)$")
ACTIVE_PROPERTY_RE = re.compile(r"^(\s*)([^#=\s][^=]*?)=(.*)$")

# Protect game formatting and variables from machine translation.
PROTECTED_RE = re.compile(
    r"("
    r"https?://[^\s$]+"
    r"|%\d+\$[A-Za-z]"
    r"|%%"
    r"|%[A-Za-z]"
    r"|§[0-9A-FK-ORa-fk-or]"
    r"|\\u00a7[0-9A-FK-ORa-fk-or]"
    r"|\\[nrt]"
    r"|\$"
    r")"
)

MARKER_RE = re.compile(r"\[\[RNT(\d{6})\]\]")


@dataclass
class TemplateLine:
    raw: str
    prefix: str | None = None
    key: str | None = None
    value: str | None = None

    @property
    def is_property(self) -> bool:
        return self.key is not None


def fail(message: str) -> None:
    print(f"lang_rebase: error: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_text_exact(path: Path) -> tuple[str, str, bool]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        text = handle.read()
    newline = "\r\n" if "\r\n" in text else "\n"
    has_final_newline = text.endswith(("\n", "\r"))
    return text, newline, has_final_newline


def parse_template(text: str) -> list[TemplateLine]:
    result: list[TemplateLine] = []
    for raw in text.splitlines():
        match = COMMENTED_PROPERTY_RE.match(raw)
        if match:
            result.append(
                TemplateLine(
                    raw=raw,
                    prefix=match.group(1),
                    key=match.group(2).strip(),
                    value=match.group(3),
                )
            )
            continue

        match = ACTIVE_PROPERTY_RE.match(raw)
        if match:
            result.append(
                TemplateLine(
                    raw=raw,
                    prefix=match.group(1),
                    key=match.group(2).strip(),
                    value=match.group(3),
                )
            )
            continue

        result.append(TemplateLine(raw=raw))

    return result


def mask_text(text: str) -> tuple[str, dict[str, str]]:
    replacements: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        token = f"__RNT_TOKEN_{len(replacements):04d}__"
        replacements[token] = match.group(0)
        return token

    return PROTECTED_RE.sub(replace, text), replacements


def unmask_text(text: str, replacements: dict[str, str]) -> str:
    for token, original in replacements.items():
        text = text.replace(token, original)
    return text


def should_translate(text: str) -> bool:
    if not text.strip():
        return False
    stripped = PROTECTED_RE.sub("", text)
    return bool(re.search(r"[A-Za-z]", stripped))


class TranslationCache:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.data: dict[str, dict[str, str]] = {}
        if path.exists():
            try:
                loaded = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(loaded, dict):
                    self.data = {
                        str(locale): {
                            str(source): str(translated)
                            for source, translated in values.items()
                        }
                        for locale, values in loaded.items()
                        if isinstance(values, dict)
                    }
            except (OSError, json.JSONDecodeError):
                print(f"Warning: ignoring invalid cache: {path}")

    def get(self, target: str, source: str) -> str | None:
        return self.data.get(target, {}).get(source)

    def put(self, target: str, source: str, translated: str) -> None:
        self.data.setdefault(target, {})[source] = translated

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.path.with_suffix(".tmp")
        temp.write_text(
            json.dumps(self.data, ensure_ascii=False, indent=2, sort_keys=True),
            encoding="utf-8",
        )
        temp.replace(self.path)


class GoogleTranslator:
    ENDPOINT = "https://translate.googleapis.com/translate_a/single"

    def __init__(self, cache: TranslationCache, pause: float = 0.15) -> None:
        self.cache = cache
        self.pause = pause

    def _request(self, payload: str, target: str) -> str:
        encoded = urllib.parse.urlencode(
            {
                "client": "gtx",
                "sl": "en",
                "tl": target,
                "dt": "t",
                "q": payload,
            }
        ).encode("utf-8")

        request = urllib.request.Request(
            self.ENDPOINT,
            data=encoded,
            headers={
                "User-Agent": "Mozilla/5.0",
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            },
            method="POST",
        )

        last_error: Exception | None = None
        for attempt in range(5):
            try:
                with urllib.request.urlopen(request, timeout=45) as response:
                    data = json.loads(response.read().decode("utf-8"))
                translated = "".join(
                    part[0] for part in data[0] if part and part[0] is not None
                )
                time.sleep(self.pause)
                return translated
            except (
                urllib.error.URLError,
                urllib.error.HTTPError,
                TimeoutError,
                json.JSONDecodeError,
                IndexError,
                TypeError,
            ) as exc:
                last_error = exc
                time.sleep(2 ** attempt)

        raise RuntimeError(f"translation request failed: {last_error}")

    def _translate_one(self, text: str, target: str) -> str:
        cached = self.cache.get(target, text)
        if cached is not None:
            return cached

        masked, replacements = mask_text(text)
        translated = self._request(masked, target)
        translated = unmask_text(translated, replacements)

        self.cache.put(target, text, translated)
        self.cache.save()
        return translated

    def translate_many(self, texts: Iterable[str], target: str) -> dict[str, str]:
        unique: list[str] = []
        seen: set[str] = set()

        for text in texts:
            if text in seen:
                continue
            seen.add(text)
            unique.append(text)

        result: dict[str, str] = {}
        pending: list[str] = []

        for text in unique:
            if not should_translate(text):
                result[text] = text
                continue

            cached = self.cache.get(target, text)
            if cached is not None:
                result[text] = cached
            else:
                pending.append(text)

        total = len(pending)
        completed = 0
        index = 0

        while index < len(pending):
            batch: list[tuple[str, str, dict[str, str]]] = []
            char_count = 0

            while index < len(pending):
                source = pending[index]
                masked, replacements = mask_text(source)
                marker = f"[[RNT{len(batch):06d}]]"
                addition = marker + masked + "\n"

                if batch and char_count + len(addition) > 3500:
                    break

                batch.append((source, masked, replacements))
                char_count += len(addition)
                index += 1

            payload = "".join(
                f"[[RNT{number:06d}]]{masked}\n"
                for number, (_, masked, _) in enumerate(batch)
            )

            try:
                translated_payload = self._request(payload, target)
                parts = MARKER_RE.split(translated_payload)

                recovered: dict[int, str] = {}
                part_index = 1
                while part_index + 1 < len(parts):
                    number = int(parts[part_index])
                    recovered[number] = parts[part_index + 1].rstrip("\r\n")
                    part_index += 2

                if len(recovered) != len(batch):
                    raise RuntimeError("batch markers were changed")

                for number, (source, _, replacements) in enumerate(batch):
                    translated = unmask_text(recovered[number], replacements)
                    result[source] = translated
                    self.cache.put(target, source, translated)

                self.cache.save()

            except Exception as exc:
                print(f"  Batch failed ({exc}). Retrying entries one at a time.")
                for source, _, _ in batch:
                    translated = self._translate_one(source, target)
                    result[source] = translated

            completed += len(batch)
            print(f"  translated {completed}/{total}", flush=True)

        return result


def render_template(
    template: list[TemplateLine],
    translated_values: dict[str, str],
) -> list[str]:
    output: list[str] = []

    for line in template:
        if not line.is_property:
            output.append(line.raw)
            continue

        assert line.prefix is not None
        assert line.key is not None
        assert line.value is not None

        output.append(f"{line.prefix}{line.key}={translated_values[line.value]}")

    return output


def make_backup(targets: list[Path]) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_dir = Path("build/lang-rebase-backup") / stamp

    for target in targets:
        if not target.exists():
            continue
        destination = backup_dir / target.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, destination)

    return backup_dir


def find_targets(lang_dir: Path) -> list[Path]:
    targets: list[Path] = []

    for path in sorted(lang_dir.glob("*.lang")):
        if path.name == SOURCE_NAME:
            continue
        if not LOCALE_FILE_RE.fullmatch(path.name):
            print(f"Skipping non-locale file: {path.name}")
            continue
        targets.append(path)

    return targets


def run(
    repo_root: Path,
    translator_factory: Callable[[TranslationCache], GoogleTranslator] = GoogleTranslator,
) -> int:
    lang_dir = repo_root / LANG_DIR
    source_path = lang_dir / SOURCE_NAME

    if not source_path.exists():
        fail(f"run this from the repository root; missing {source_path}")

    source_text, newline, has_final_newline = read_text_exact(source_path)
    template = parse_template(source_text)
    values = [line.value for line in template if line.value is not None]
    targets = find_targets(lang_dir)

    if not targets:
        fail(f"no locale files found in {lang_dir}")

    backup_dir = make_backup(targets)
    cache = TranslationCache(repo_root / CACHE_PATH)
    translator = translator_factory(cache)

    report: dict[str, object] = {
        "source": str(source_path),
        "source_property_lines": len(values),
        "backup": str(backup_dir),
        "locales": {},
    }

    for target_path in targets:
        locale = target_path.stem
        print(f"\n[{locale}]")

        target_language = LOCALE_TARGETS.get(locale)

        if target_language is None or locale in COPY_ENGLISH:
            print("  no real translation target; rebasing with English values")
            translated_values = {value: value for value in set(values)}
            mode = "english-copy"
        else:
            print(f"  translating en_US -> {target_language}")
            translated_values = translator.translate_many(values, target_language)
            mode = f"translated:{target_language}"

        output_lines = render_template(template, translated_values)
        output_text = newline.join(output_lines)
        if has_final_newline:
            output_text += newline

        target_path.write_text(output_text, encoding="utf-8", newline="")
        print(f"  wrote {target_path}")

        report["locales"][locale] = {
            "mode": mode,
            "lines": len(output_lines),
            "properties": len(values),
        }

    report_path = repo_root / REPORT_PATH
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"\nDone.")
    print(f"Backups: {backup_dir}")
    print(f"Report:  {report_path}")
    print("Review the Git diff before committing.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Rebuild and auto-translate all locale files from en_US.lang."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path.cwd(),
        help="Repository root. Default: current directory.",
    )
    args = parser.parse_args()
    return run(args.repo_root.resolve())


if __name__ == "__main__":
    raise SystemExit(main())
