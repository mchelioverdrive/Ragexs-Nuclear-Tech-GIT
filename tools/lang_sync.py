#!/usr/bin/env python3
"""Audit and synchronize Minecraft 1.7.10 .lang files.

The English file is the source schema. Other locale files keep their translated
values while their key order and section comments follow the English file.

This script uses only the Python standard library.
"""

from __future__ import annotations

import argparse
import collections
import dataclasses
import datetime as _datetime
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
from typing import Dict, Iterable, List, Mapping, MutableMapping, Optional, Sequence, Tuple


DEFAULT_LANG_DIR = Path("src/main/resources/assets/hbm/lang")
DEFAULT_SOURCE_NAME = "en_US.lang"
DEFAULT_BASELINE = Path("tools/lang_baseline.json")

# Java Formatter conversions used by Minecraft localization calls.
_FORMAT_RE = re.compile(
    r"%(?:(?P<index>\d+)\$)?"
    r"(?P<flags>[-#+0,(<]*)"
    r"(?P<width>\d+)?"
    r"(?:\.(?P<precision>\d+))?"
    r"(?P<date>[tT])?"
    r"(?P<conversion>[bBhHsScCdoxXeEfgGaA%n])"
)
_COLOR_RE = re.compile(r"(?:§|\\u00a7)([0-9A-FK-ORa-fk-or])")


@dataclasses.dataclass(frozen=True)
class Line:
    number: int
    kind: str
    raw: str
    key: Optional[str] = None
    value: Optional[str] = None


@dataclasses.dataclass
class ParsedLang:
    path: Path
    lines: List[Line]
    values: "collections.OrderedDict[str, str]"
    occurrences: Dict[str, List[int]]
    malformed: List[Tuple[int, str]]
    newline: str
    ended_with_newline: bool
    preamble: List[str]

    @property
    def duplicate_keys(self) -> Dict[str, List[int]]:
        return {
            key: line_numbers
            for key, line_numbers in self.occurrences.items()
            if len(line_numbers) > 1
        }


@dataclasses.dataclass
class LocaleAudit:
    path: Path
    locale: str
    translated_keys: int
    missing_keys: List[str]
    obsolete_keys: List[str]
    duplicate_keys: Dict[str, List[int]]
    malformed_lines: List[Tuple[int, str]]
    format_mismatches: Dict[str, Dict[str, List[str]]]
    color_mismatches: Dict[str, Dict[str, List[str]]]
    same_as_english: List[str]
    changed_english_keys: List[str]
    stale_english_copies: List[str]
    rewritten: bool = False

    @property
    def dangerous_issue_count(self) -> int:
        return (
            len(self.duplicate_keys)
            + len(self.malformed_lines)
            + len(self.format_mismatches)
        )

    @property
    def review_issue_count(self) -> int:
        return (
            len(self.missing_keys)
            + len(self.obsolete_keys)
            + len(self.color_mismatches)
            + len(self.same_as_english)
            + len(self.changed_english_keys)
        )


def _read_text(path: Path) -> Tuple[str, str, bool]:
    data = path.read_bytes()
    text = data.decode("utf-8-sig")
    newline = "\r\n" if b"\r\n" in data else "\n"
    ended_with_newline = text.endswith("\n") or text.endswith("\r")
    return text, newline, ended_with_newline


def parse_lang(path: Path) -> ParsedLang:
    text, newline, ended_with_newline = _read_text(path)
    raw_lines = text.splitlines()
    parsed_lines: List[Line] = []
    values: "collections.OrderedDict[str, str]" = collections.OrderedDict()
    occurrences: Dict[str, List[int]] = collections.defaultdict(list)
    malformed: List[Tuple[int, str]] = []
    preamble: List[str] = []
    found_entry = False

    for number, raw in enumerate(raw_lines, start=1):
        stripped = raw.strip()
        if not stripped:
            parsed_lines.append(Line(number, "blank", raw))
            if not found_entry:
                preamble.append(raw)
            continue
        if raw.lstrip().startswith("#"):
            parsed_lines.append(Line(number, "comment", raw))
            if not found_entry:
                preamble.append(raw)
            continue
        if "=" not in raw:
            parsed_lines.append(Line(number, "malformed", raw))
            malformed.append((number, raw))
            if not found_entry:
                preamble.append(raw)
            continue

        key, value = raw.split("=", 1)
        key = key.strip()
        if not key:
            parsed_lines.append(Line(number, "malformed", raw))
            malformed.append((number, raw))
            if not found_entry:
                preamble.append(raw)
            continue

        found_entry = True
        parsed_lines.append(Line(number, "entry", raw, key, value))
        occurrences[key].append(number)
        # Last assignment wins, matching Java Properties-style behavior.
        values[key] = value

    return ParsedLang(
        path=path,
        lines=parsed_lines,
        values=values,
        occurrences=dict(occurrences),
        malformed=malformed,
        newline=newline,
        ended_with_newline=ended_with_newline,
        preamble=preamble,
    )


def _format_tokens(value: str) -> List[str]:
    tokens: List[str] = []
    implicit_index = 0
    previous_index: Optional[int] = None

    for match in _FORMAT_RE.finditer(value):
        conversion = match.group("conversion")
        if conversion in ("%", "n"):
            continue

        flags = match.group("flags") or ""
        explicit_index = match.group("index")
        if explicit_index is not None:
            argument_index = int(explicit_index)
        elif "<" in flags and previous_index is not None:
            argument_index = previous_index
        else:
            implicit_index += 1
            argument_index = implicit_index

        previous_index = argument_index
        date_prefix = (match.group("date") or "").lower()
        conversion_name = (date_prefix + conversion).lower()
        tokens.append(f"{argument_index}:{conversion_name}")

    return tokens


def _color_tokens(value: str) -> List[str]:
    return [match.group(1).lower() for match in _COLOR_RE.finditer(value)]


def _load_baseline(path: Path) -> Dict[str, str]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Cannot read baseline {path}: {exc}")

    values = payload.get("values")
    if not isinstance(values, dict):
        raise ValueError(f"Baseline {path} does not contain an object named 'values'.")
    return {str(key): str(value) for key, value in values.items()}


def _write_baseline(path: Path, source_name: str, values: Mapping[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": 1,
        "source": source_name,
        "updated_utc": _datetime.datetime.now(_datetime.timezone.utc).isoformat(),
        "values": dict(values),
    }
    _atomic_write_text(path, json.dumps(payload, indent=2, ensure_ascii=False) + "\n", "\n")


def audit_locale(
    source: ParsedLang,
    target: ParsedLang,
    baseline: Mapping[str, str],
) -> LocaleAudit:
    source_keys = set(source.values)
    target_keys = set(target.values)
    common_keys = source_keys & target_keys

    format_mismatches: Dict[str, Dict[str, List[str]]] = {}
    color_mismatches: Dict[str, Dict[str, List[str]]] = {}
    same_as_english: List[str] = []
    changed_english_keys: List[str] = []
    stale_english_copies: List[str] = []

    for key in source.values:
        if key not in common_keys:
            continue
        source_value = source.values[key]
        target_value = target.values[key]

        source_formats = _format_tokens(source_value)
        target_formats = _format_tokens(target_value)
        if collections.Counter(source_formats) != collections.Counter(target_formats):
            format_mismatches[key] = {
                "source": source_formats,
                "target": target_formats,
            }

        source_colors = _color_tokens(source_value)
        target_colors = _color_tokens(target_value)
        if source_colors != target_colors:
            color_mismatches[key] = {
                "source": source_colors,
                "target": target_colors,
            }

        if target_value == source_value and source_value.strip():
            same_as_english.append(key)

        old_english = baseline.get(key)
        if old_english is not None and old_english != source_value:
            changed_english_keys.append(key)
            if target_value == old_english:
                stale_english_copies.append(key)

    return LocaleAudit(
        path=target.path,
        locale=target.path.stem,
        translated_keys=len(common_keys),
        missing_keys=[key for key in source.values if key not in target_keys],
        obsolete_keys=[key for key in target.values if key not in source_keys],
        duplicate_keys=target.duplicate_keys,
        malformed_lines=target.malformed,
        format_mismatches=format_mismatches,
        color_mismatches=color_mismatches,
        same_as_english=same_as_english,
        changed_english_keys=changed_english_keys,
        stale_english_copies=stale_english_copies,
    )


def _normalized_preamble(source: ParsedLang, target: ParsedLang) -> List[str]:
    source_comments = {line.strip() for line in source.preamble if line.strip()}
    result: List[str] = []
    for raw in target.preamble:
        stripped = raw.strip()
        if not stripped:
            if result and result[-1] != "":
                result.append("")
            continue
        if stripped.startswith("#") and stripped not in source_comments:
            result.append(raw)
    while result and result[-1] == "":
        result.pop()
    return result


def build_synced_text(
    source: ParsedLang,
    target: ParsedLang,
    missing_mode: str,
) -> str:
    output: List[str] = []
    locale_preamble = _normalized_preamble(source, target)
    if locale_preamble:
        output.extend(locale_preamble)
        output.append("")

    for line in source.lines:
        if line.kind in ("blank", "comment"):
            output.append(line.raw)
            continue
        if line.kind != "entry" or line.key is None or line.value is None:
            # Invalid source lines are reported but never copied into every locale.
            continue

        if line.key in target.values:
            output.append(f"{line.key}={target.values[line.key]}")
        elif missing_mode == "english":
            output.append(f"{line.key}={line.value}")
        elif missing_mode == "marker":
            output.append("# UNTRANSLATED")
            output.append(f"{line.key}={line.value}")
        elif missing_mode == "omit":
            continue
        else:
            raise ValueError(f"Unsupported missing mode: {missing_mode}")

    # Avoid excessive blank lines caused by omitted entries.
    compacted: List[str] = []
    blank_run = 0
    for raw in output:
        if raw.strip():
            blank_run = 0
            compacted.append(raw)
        else:
            blank_run += 1
            if blank_run <= 2:
                compacted.append("")
    while compacted and compacted[-1] == "":
        compacted.pop()

    return target.newline.join(compacted) + target.newline


def _atomic_write_text(path: Path, text: str, newline: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="",
        dir=str(path.parent),
        prefix=path.name + ".",
        suffix=".tmp",
        delete=False,
    ) as handle:
        temporary_path = Path(handle.name)
        handle.write(text.replace("\n", newline) if newline != "\n" else text)
    try:
        os.replace(str(temporary_path), str(path))
    except BaseException:
        temporary_path.unlink(missing_ok=True)
        raise


def rewrite_locale(
    source: ParsedLang,
    target: ParsedLang,
    missing_mode: str,
    backup: bool,
) -> bool:
    new_text = build_synced_text(source, target, missing_mode)
    old_text, _, _ = _read_text(target.path)
    if new_text == old_text:
        return False
    if backup:
        shutil.copy2(str(target.path), str(target.path) + ".bak")
    _atomic_write_text(target.path, new_text, "\n")
    return True


def _issue_examples(items: Sequence[str], limit: int) -> str:
    if not items:
        return ""
    shown = list(items[:limit])
    suffix = "" if len(items) <= limit else f" (+{len(items) - limit} more)"
    return ", ".join(shown) + suffix


def print_report(
    source: ParsedLang,
    audits: Sequence[LocaleAudit],
    baseline_exists: bool,
    examples: int,
) -> None:
    print(f"Source: {source.path} ({len(source.values)} keys)")
    print(
        "Source issues: "
        f"{len(source.duplicate_keys)} duplicate keys, "
        f"{len(source.malformed)} malformed lines"
    )
    if not baseline_exists:
        print("Baseline: not found; changed-English detection is disabled")
    print()

    for audit in audits:
        changed = " [rewritten]" if audit.rewritten else ""
        print(f"[{audit.locale}]{changed}")
        print(
            f"  translated={audit.translated_keys} "
            f"missing={len(audit.missing_keys)} "
            f"obsolete={len(audit.obsolete_keys)} "
            f"duplicates={len(audit.duplicate_keys)} "
            f"malformed={len(audit.malformed_lines)}"
        )
        print(
            f"  format_mismatch={len(audit.format_mismatches)} "
            f"color_mismatch={len(audit.color_mismatches)} "
            f"same_as_english={len(audit.same_as_english)} "
            f"english_changed={len(audit.changed_english_keys)} "
            f"stale_english_copy={len(audit.stale_english_copies)}"
        )

        details = [
            ("missing", audit.missing_keys),
            ("obsolete", audit.obsolete_keys),
            ("format mismatch", list(audit.format_mismatches)),
            ("color mismatch", list(audit.color_mismatches)),
            ("same as English", audit.same_as_english),
            ("English changed", audit.changed_english_keys),
            ("stale English copy", audit.stale_english_copies),
        ]
        for label, items in details:
            example_text = _issue_examples(items, examples)
            if example_text:
                print(f"  {label}: {example_text}")
        if audit.duplicate_keys:
            duplicate_text = ", ".join(
                f"{key}@{','.join(map(str, lines))}"
                for key, lines in list(audit.duplicate_keys.items())[:examples]
            )
            print(f"  duplicate keys: {duplicate_text}")
        if audit.malformed_lines:
            malformed_text = ", ".join(
                f"line {number}" for number, _ in audit.malformed_lines[:examples]
            )
            print(f"  malformed lines: {malformed_text}")
        print()


def _audit_to_dict(audit: LocaleAudit) -> Dict[str, object]:
    return {
        "path": str(audit.path),
        "locale": audit.locale,
        "translated_keys": audit.translated_keys,
        "missing_keys": audit.missing_keys,
        "obsolete_keys": audit.obsolete_keys,
        "duplicate_keys": audit.duplicate_keys,
        "malformed_lines": [
            {"line": number, "text": text} for number, text in audit.malformed_lines
        ],
        "format_mismatches": audit.format_mismatches,
        "color_mismatches": audit.color_mismatches,
        "same_as_english": audit.same_as_english,
        "changed_english_keys": audit.changed_english_keys,
        "stale_english_copies": audit.stale_english_copies,
        "rewritten": audit.rewritten,
    }


def write_json_report(
    report_path: Path,
    source: ParsedLang,
    audits: Sequence[LocaleAudit],
    baseline_path: Path,
) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_utc": _datetime.datetime.now(_datetime.timezone.utc).isoformat(),
        "source": {
            "path": str(source.path),
            "key_count": len(source.values),
            "duplicate_keys": source.duplicate_keys,
            "malformed_lines": [
                {"line": number, "text": text} for number, text in source.malformed
            ],
        },
        "baseline": str(baseline_path),
        "locales": [_audit_to_dict(audit) for audit in audits],
    }
    _atomic_write_text(
        report_path,
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        "\n",
    )


def _resolve_path(repo_root: Path, path: Path) -> Path:
    return path if path.is_absolute() else repo_root / path


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Audit Minecraft .lang files against en_US.lang and optionally "
            "rewrite them in source-key order."
        )
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path.cwd(),
        help="Repository root. Defaults to the current directory.",
    )
    parser.add_argument(
        "--lang-dir",
        type=Path,
        default=DEFAULT_LANG_DIR,
        help=f"Language directory relative to the repository root. Default: {DEFAULT_LANG_DIR}",
    )
    parser.add_argument(
        "--source",
        default=DEFAULT_SOURCE_NAME,
        help=f"Source language file name. Default: {DEFAULT_SOURCE_NAME}",
    )
    parser.add_argument(
        "--locale",
        action="append",
        default=[],
        help="Only process this locale stem or file name. Repeat to select more than one.",
    )
    parser.add_argument(
        "--fix",
        action="store_true",
        help="Rewrite locale files. Without this option, the command only audits.",
    )
    parser.add_argument(
        "--missing",
        choices=("omit", "english", "marker"),
        default="omit",
        help=(
            "How --fix handles missing translations. 'omit' keeps normal English fallback; "
            "'english' copies English; 'marker' adds '# UNTRANSLATED' before copied English."
        ),
    )
    parser.add_argument(
        "--backup",
        action="store_true",
        help="Create a .bak copy before each rewritten locale file.",
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help=f"English baseline JSON path. Default: {DEFAULT_BASELINE}",
    )
    parser.add_argument(
        "--update-baseline",
        action="store_true",
        help="Save the current English values after the audit.",
    )
    parser.add_argument(
        "--json-report",
        type=Path,
        help="Write the full audit as JSON.",
    )
    parser.add_argument(
        "--examples",
        type=int,
        default=5,
        help="Maximum example keys shown for each issue type. Default: 5.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Return exit code 1 for missing, obsolete, color, unchanged-English, or changed-English entries.",
    )
    return parser


def _selected_locale_files(
    lang_dir: Path,
    source_name: str,
    locale_filters: Sequence[str],
) -> List[Path]:
    files = sorted(
        path
        for path in lang_dir.glob("*.lang")
        if path.name.lower() != source_name.lower()
    )
    if not locale_filters:
        return files

    normalized = {
        item[:-5].lower() if item.lower().endswith(".lang") else item.lower()
        for item in locale_filters
    }
    selected = [path for path in files if path.stem.lower() in normalized]
    missing_filters = normalized - {path.stem.lower() for path in selected}
    if missing_filters:
        raise FileNotFoundError(
            "No locale file matched: " + ", ".join(sorted(missing_filters))
        )
    return selected


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_argument_parser()
    args = parser.parse_args(argv)

    try:
        repo_root = args.repo_root.resolve()
        lang_dir = _resolve_path(repo_root, args.lang_dir).resolve()
        source_path = lang_dir / args.source
        baseline_path = _resolve_path(repo_root, args.baseline).resolve()
        report_path = (
            _resolve_path(repo_root, args.json_report).resolve()
            if args.json_report
            else None
        )

        if not source_path.is_file():
            raise FileNotFoundError(f"Source language file not found: {source_path}")
        locale_paths = _selected_locale_files(lang_dir, args.source, args.locale)
        if not locale_paths:
            raise FileNotFoundError(f"No non-source .lang files found in {lang_dir}")

        source = parse_lang(source_path)
        baseline_exists = baseline_path.is_file()
        baseline = _load_baseline(baseline_path)
        audits: List[LocaleAudit] = []

        for locale_path in locale_paths:
            target = parse_lang(locale_path)
            audit = audit_locale(source, target, baseline)
            if args.fix:
                audit.rewritten = rewrite_locale(
                    source=source,
                    target=target,
                    missing_mode=args.missing,
                    backup=args.backup,
                )
            audits.append(audit)

        print_report(source, audits, baseline_exists, max(0, args.examples))

        if report_path is not None:
            write_json_report(report_path, source, audits, baseline_path)
            print(f"JSON report: {report_path}")

        if args.update_baseline:
            _write_baseline(baseline_path, args.source, source.values)
            print(f"Baseline updated: {baseline_path}")

        dangerous = len(source.duplicate_keys) + len(source.malformed)
        dangerous += sum(audit.dangerous_issue_count for audit in audits)
        review = sum(audit.review_issue_count for audit in audits)
        return 1 if dangerous or (args.strict and review) else 0

    except (OSError, ValueError) as exc:
        print(f"lang_sync: error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
