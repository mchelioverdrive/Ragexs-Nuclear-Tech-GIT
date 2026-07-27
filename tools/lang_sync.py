#!/usr/bin/env python3
"""Safely audit and update RNT Minecraft .lang files.

Design goals:
- en_US.lang defines the current RNT key/value source of truth.
- Existing translations are preserved unless the English source value changed.
- Changed English values can replace stale translations with current English.
- Locale-only/vanilla/special keys are preserved.
- Source comments are never copied into other locale files.
- Files are not reordered.
- Dry-run is the default; --apply is required to write.
- Backups and reports live outside src/main/resources.

Python standard library only. No Gradle/build invocation.
"""

from __future__ import annotations

import argparse
import collections
import dataclasses
import datetime as dt
import difflib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple


DEFAULT_LANG_DIR = Path("src/main/resources/assets/hbm/lang")
DEFAULT_SOURCE_NAME = "en_US.lang"
DEFAULT_BACKUP_DIR = Path("build/lang-sync-backups")
DEFAULT_REPORT = Path("build/reports/lang-sync.json")
LOCALE_FILE_RE = re.compile(r"^[a-z]{2}_[A-Z]{2}\.lang$")
COMMENTED_PROPERTY_RE = re.compile(r"^\s*[#!]\s*([^=\s]+)\s*=(.*)$")

FORMAT_RE = re.compile(
    r"%(?:(?P<index>\d+)\$)?"
    r"(?P<flags>[-#+0,(<]*)"
    r"(?P<width>\d+)?"
    r"(?:\.(?P<precision>\d+))?"
    r"(?P<date>[tT])?"
    r"(?P<conversion>[bBhHsScCdoxXeEfgGaA%n])"
)
COLOR_RE = re.compile(r"(?:§|\\u00a7)([0-9A-FK-ORa-fk-or])")


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
    commented_properties: Dict[str, List[int]]
    newline: str
    ended_with_newline: bool

    @property
    def duplicate_keys(self) -> Dict[str, List[int]]:
        return {k: v for k, v in self.occurrences.items() if len(v) > 1}


@dataclasses.dataclass
class SourceDelta:
    added: List[str]
    changed: List[str]
    deleted: List[str]


@dataclasses.dataclass
class LocaleAudit:
    locale: str
    path: Path
    missing: List[str]
    extra: List[str]
    changed_source_present: List[str]
    changed_source_missing: List[str]
    deleted_source_present: List[str]
    duplicates: Dict[str, List[int]]
    malformed: List[Tuple[int, str]]
    format_mismatches: Dict[str, Dict[str, List[str]]]
    color_mismatches: Dict[str, Dict[str, List[str]]]
    same_as_english: List[str]
    planned_changes: int = 0
    rewritten: bool = False


class LangSyncError(RuntimeError):
    pass


def read_text(path: Path) -> Tuple[str, str, bool]:
    data = path.read_bytes()
    text = data.decode("utf-8-sig")
    newline = "\r\n" if b"\r\n" in data else "\n"
    ended = text.endswith("\n") or text.endswith("\r")
    return text, newline, ended


def parse_text(text: str, path: Path = Path("<memory>"), newline: str = "\n") -> ParsedLang:
    lines: List[Line] = []
    values: "collections.OrderedDict[str, str]" = collections.OrderedDict()
    occurrences: Dict[str, List[int]] = collections.defaultdict(list)
    malformed: List[Tuple[int, str]] = []
    commented: Dict[str, List[int]] = collections.defaultdict(list)

    for number, raw in enumerate(text.splitlines(), 1):
        stripped = raw.strip()
        if not stripped:
            lines.append(Line(number, "blank", raw))
            continue
        if raw.lstrip().startswith(("#", "!")):
            lines.append(Line(number, "comment", raw))
            match = COMMENTED_PROPERTY_RE.match(raw)
            if match:
                commented[match.group(1).strip()].append(number)
            continue
        if "=" not in raw:
            lines.append(Line(number, "malformed", raw))
            malformed.append((number, raw))
            continue
        key, value = raw.split("=", 1)
        key = key.strip()
        if not key:
            lines.append(Line(number, "malformed", raw))
            malformed.append((number, raw))
            continue
        lines.append(Line(number, "entry", raw, key, value))
        occurrences[key].append(number)
        # Java properties behavior is effectively last assignment wins.
        values[key] = value

    return ParsedLang(
        path=path,
        lines=lines,
        values=values,
        occurrences=dict(occurrences),
        malformed=malformed,
        commented_properties=dict(commented),
        newline=newline,
        ended_with_newline=text.endswith("\n") or text.endswith("\r"),
    )


def parse_file(path: Path) -> ParsedLang:
    text, newline, ended = read_text(path)
    parsed = parse_text(text, path, newline)
    parsed.ended_with_newline = ended
    return parsed


def format_tokens(value: str) -> List[str]:
    tokens: List[str] = []
    implicit_index = 0
    previous_index: Optional[int] = None
    for match in FORMAT_RE.finditer(value):
        conversion = match.group("conversion")
        if conversion in ("%", "n"):
            continue
        flags = match.group("flags") or ""
        explicit = match.group("index")
        if explicit is not None:
            argument_index = int(explicit)
        elif "<" in flags and previous_index is not None:
            argument_index = previous_index
        else:
            implicit_index += 1
            argument_index = implicit_index
        previous_index = argument_index
        date_prefix = (match.group("date") or "").lower()
        tokens.append(f"{argument_index}:{date_prefix}{conversion.lower()}")
    return tokens


def color_tokens(value: str) -> List[str]:
    return [m.group(1).lower() for m in COLOR_RE.finditer(value)]


def source_delta(previous: Mapping[str, str], current: Mapping[str, str]) -> SourceDelta:
    previous_keys = set(previous)
    current_keys = set(current)
    return SourceDelta(
        added=[k for k in current if k not in previous_keys],
        changed=[k for k in current if k in previous and current[k] != previous[k]],
        deleted=[k for k in previous if k not in current_keys],
    )


def audit_locale(source: ParsedLang, target: ParsedLang, delta: Optional[SourceDelta]) -> LocaleAudit:
    source_keys = set(source.values)
    target_keys = set(target.values)
    common = source_keys & target_keys
    fmt: Dict[str, Dict[str, List[str]]] = {}
    colors: Dict[str, Dict[str, List[str]]] = {}
    same: List[str] = []

    for key in source.values:
        if key not in common:
            continue
        src = source.values[key]
        dst = target.values[key]
        src_fmt = format_tokens(src)
        dst_fmt = format_tokens(dst)
        if collections.Counter(src_fmt) != collections.Counter(dst_fmt):
            fmt[key] = {"source": src_fmt, "target": dst_fmt}
        src_colors = color_tokens(src)
        dst_colors = color_tokens(dst)
        if src_colors != dst_colors:
            colors[key] = {"source": src_colors, "target": dst_colors}
        if src == dst and src.strip():
            same.append(key)

    changed = set(delta.changed) if delta else set()
    deleted = set(delta.deleted) if delta else set()

    return LocaleAudit(
        locale=target.path.stem,
        path=target.path,
        missing=[k for k in source.values if k not in target_keys],
        extra=[k for k in target.values if k not in source_keys],
        changed_source_present=[k for k in source.values if k in changed and k in target_keys],
        changed_source_missing=[k for k in source.values if k in changed and k not in target_keys],
        deleted_source_present=[k for k in target.values if k in deleted],
        duplicates=target.duplicate_keys,
        malformed=target.malformed,
        format_mismatches=fmt,
        color_mismatches=colors,
        same_as_english=same,
    )


def locate_repo_root(start: Path, lang_dir: Path, source_name: str) -> Path:
    candidates = [start.resolve(), Path(__file__).resolve().parent]
    checked: Set[Path] = set()
    for candidate in candidates:
        for root in (candidate, *candidate.parents):
            if root in checked:
                continue
            checked.add(root)
            if (root / lang_dir / source_name).is_file():
                return root
    raise LangSyncError(
        f"Could not locate repository root containing {lang_dir / source_name}. "
        "Run from the repository or pass --repo-root."
    )


def git_show_source(repo_root: Path, git_ref: str, source_rel_path: Path) -> ParsedLang:
    command = ["git", "show", f"{git_ref}:{source_rel_path.as_posix()}"]
    try:
        result = subprocess.run(
            command,
            cwd=str(repo_root),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise LangSyncError(f"Could not execute git: {exc}") from exc
    if result.returncode != 0:
        stderr = result.stderr.decode("utf-8", errors="replace").strip()
        raise LangSyncError(f"git show failed for {git_ref}: {stderr}")
    text = result.stdout.decode("utf-8-sig")
    return parse_text(text, Path(f"{git_ref}:{source_rel_path}"))


def load_baseline(path: Path) -> ParsedLang:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise LangSyncError(f"Cannot read baseline {path}: {exc}") from exc
    values = payload.get("values")
    if not isinstance(values, dict):
        raise LangSyncError(f"Baseline {path} must contain an object named 'values'.")
    text = "\n".join(f"{k}={v}" for k, v in values.items()) + "\n"
    return parse_text(text, path)


def write_baseline(path: Path, source: ParsedLang) -> None:
    payload = {
        "version": 2,
        "source": source.path.name,
        "updated_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "values": dict(source.values),
    }
    atomic_write(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def selected_locale_files(lang_dir: Path, source_name: str, filters: Sequence[str]) -> List[Path]:
    files = [
        path
        for path in sorted(lang_dir.iterdir())
        if path.is_file()
        and path.name != source_name
        and LOCALE_FILE_RE.fullmatch(path.name)
    ]
    if not filters:
        return files
    normalized = {
        value[:-5] if value.lower().endswith(".lang") else value
        for value in filters
    }
    selected = [p for p in files if p.stem in normalized]
    missing = normalized - {p.stem for p in selected}
    if missing:
        raise LangSyncError("Unknown locale(s): " + ", ".join(sorted(missing)))
    return selected


def build_updated_text(
    source: ParsedLang,
    target: ParsedLang,
    delta: Optional[SourceDelta],
    changed_policy: str,
    missing_policy: str,
    remove_deleted: bool,
) -> Tuple[str, int]:
    changed = set(delta.changed) if delta else set()
    deleted = set(delta.deleted) if delta else set()
    source_keys = set(source.values)
    output: List[str] = []
    modifications = 0

    # Preserve the target's structure and comments. Never copy source comments.
    for line in target.lines:
        if line.kind != "entry" or line.key is None or line.value is None:
            output.append(line.raw)
            continue

        key = line.key
        if key in changed:
            if changed_policy == "english":
                replacement = f"{key}={source.values[key]}"
                output.append(replacement)
                if replacement != line.raw:
                    modifications += 1
                continue
            if changed_policy == "omit":
                modifications += 1
                continue
            if changed_policy != "keep":
                raise LangSyncError(f"Unsupported changed policy: {changed_policy}")

        if remove_deleted and key in deleted:
            modifications += 1
            continue

        # Locale-only keys are intentionally retained.
        output.append(line.raw)

    target_keys = set(target.values)
    missing = [key for key in source.values if key not in target_keys]
    if missing_policy != "omit" and missing:
        if output and output[-1].strip():
            output.append("")
        output.append("# Added by lang_sync.py; translate these values when practical.")
        for key in missing:
            if missing_policy == "english":
                output.append(f"{key}={source.values[key]}")
                modifications += 1
            elif missing_policy == "marker":
                output.append("# UNTRANSLATED")
                output.append(f"{key}={source.values[key]}")
                modifications += 1
            else:
                raise LangSyncError(f"Unsupported missing policy: {missing_policy}")

    newline = target.newline
    text = newline.join(output)
    if target.ended_with_newline or output:
        text += newline
    return text, modifications


def atomic_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", newline="", dir=str(path.parent),
        prefix=path.name + ".", suffix=".tmp", delete=False
    ) as handle:
        temp = Path(handle.name)
        handle.write(text)
    try:
        os.replace(str(temp), str(path))
    except BaseException:
        temp.unlink(missing_ok=True)
        raise


def backup_file(repo_root: Path, backup_root: Path, target: Path) -> Path:
    timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    relative = target.resolve().relative_to(repo_root.resolve())
    destination = backup_root / timestamp / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target, destination)
    return destination


def unified_diff(old: str, new: str, path: Path, limit: int = 240) -> List[str]:
    lines = list(difflib.unified_diff(
        old.splitlines(), new.splitlines(),
        fromfile=str(path), tofile=str(path), lineterm=""
    ))
    if len(lines) > limit:
        return lines[:limit] + [f"... diff truncated ({len(lines) - limit} more lines)"]
    return lines


def report_dict(
    source: ParsedLang,
    previous: Optional[ParsedLang],
    delta: Optional[SourceDelta],
    audits: Sequence[LocaleAudit],
) -> Dict[str, object]:
    return {
        "generated_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "source": {
            "path": str(source.path),
            "keys": len(source.values),
            "duplicates": source.duplicate_keys,
            "malformed": [{"line": n, "text": t} for n, t in source.malformed],
            "commented_property_keys": source.commented_properties,
        },
        "previous_source": str(previous.path) if previous else None,
        "source_delta": dataclasses.asdict(delta) if delta else None,
        "locales": [
            {
                **dataclasses.asdict(audit),
                "path": str(audit.path),
                "malformed": [{"line": n, "text": t} for n, t in audit.malformed],
            }
            for audit in audits
        ],
    }


def print_examples(label: str, values: Sequence[str], limit: int) -> None:
    if not values:
        return
    shown = ", ".join(values[:limit])
    suffix = "" if len(values) <= limit else f" (+{len(values)-limit} more)"
    print(f"  {label}: {shown}{suffix}")


def print_audit(source: ParsedLang, delta: Optional[SourceDelta], audits: Sequence[LocaleAudit], examples: int) -> None:
    print(f"Source: {source.path} ({len(source.values)} active keys)")
    print(f"Source duplicates={len(source.duplicate_keys)} malformed={len(source.malformed)}")
    print(f"Commented property-like source lines={sum(len(v) for v in source.commented_properties.values())}")
    if delta:
        print(f"Compared source: added={len(delta.added)} changed={len(delta.changed)} deleted={len(delta.deleted)}")
    else:
        print("Compared source: none; semantic rename detection is disabled")
    print()

    for audit in audits:
        suffix = " [rewritten]" if audit.rewritten else ""
        print(f"[{audit.locale}]{suffix}")
        print(
            f"  missing={len(audit.missing)} extra-preserved={len(audit.extra)} "
            f"changed-present={len(audit.changed_source_present)} "
            f"deleted-present={len(audit.deleted_source_present)} "
            f"duplicates={len(audit.duplicates)} malformed={len(audit.malformed)}"
        )
        print(
            f"  format-mismatch={len(audit.format_mismatches)} "
            f"color-mismatch={len(audit.color_mismatches)} "
            f"same-as-English={len(audit.same_as_english)} "
            f"planned-changes={audit.planned_changes}"
        )
        print_examples("changed English keys", audit.changed_source_present, examples)
        print_examples("deleted English keys still present", audit.deleted_source_present, examples)
        print_examples("missing", audit.missing, examples)
        print_examples("extra keys retained", audit.extra, examples)
        print_examples("format mismatch", list(audit.format_mismatches), examples)
        if audit.duplicates:
            text = ", ".join(
                f"{k}@{','.join(map(str, v))}"
                for k, v in list(audit.duplicates.items())[:examples]
            )
            print(f"  duplicate keys: {text}")
        if audit.malformed:
            print("  malformed lines: " + ", ".join(str(n) for n, _ in audit.malformed[:examples]))
        print()


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Safely audit/update RNT language files.")
    p.add_argument("--repo-root", type=Path, help="Repository root; auto-detected by default.")
    p.add_argument("--lang-dir", type=Path, default=DEFAULT_LANG_DIR)
    p.add_argument("--source", default=DEFAULT_SOURCE_NAME)
    p.add_argument("--locale", action="append", default=[], help="Process one locale; repeatable.")
    compare = p.add_mutually_exclusive_group()
    compare.add_argument(
        "--git-base",
        help="Git ref from before English renames; used to detect changed/deleted English values.",
    )
    compare.add_argument("--baseline", type=Path, help="Baseline JSON created by --write-baseline.")
    p.add_argument("--write-baseline", type=Path, help="Write current English values and exit after audit.")
    p.add_argument("--apply", action="store_true", help="Write changes. Default is dry-run.")
    p.add_argument(
        "--allow-source-issues", action="store_true",
        help="Allow writes despite English duplicates/malformed lines; effective last duplicate value wins."
    )
    p.add_argument(
        "--changed-policy", choices=("keep", "english", "omit"), default="keep",
        help="Action for keys whose English value changed. Use 'english' to eliminate stale fictional names.",
    )
    p.add_argument(
        "--missing-policy", choices=("omit", "english", "marker"), default="omit",
        help="Action for current English keys missing from a locale.",
    )
    p.add_argument(
        "--remove-deleted", action="store_true",
        help="Remove locale entries only when comparison proves the English key was deleted.",
    )
    p.add_argument("--backup-dir", type=Path, default=DEFAULT_BACKUP_DIR)
    p.add_argument("--report", type=Path, help="Write JSON report. Suggested: build/reports/lang-sync.json")
    p.add_argument("--show-diff", action="store_true", help="Print dry-run unified diffs.")
    p.add_argument("--max-changes", type=int, default=500, help="Safety limit per locale; default 500.")
    p.add_argument("--allow-large", action="store_true", help="Override --max-changes.")
    p.add_argument("--examples", type=int, default=5)
    return p


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parser().parse_args(argv)
    try:
        starting = args.repo_root.resolve() if args.repo_root else Path.cwd()
        repo_root = (
            args.repo_root.resolve()
            if args.repo_root
            else locate_repo_root(starting, args.lang_dir, args.source)
        )
        lang_dir = (repo_root / args.lang_dir).resolve()
        source_path = lang_dir / args.source
        source = parse_file(source_path)

        if source.duplicate_keys or source.malformed:
            message = (
                f"English source has {len(source.duplicate_keys)} duplicate keys and "
                f"{len(source.malformed)} malformed lines. The parser uses the last duplicate value, "
                "matching effective Java-properties behavior."
            )
            if args.apply and not args.allow_source_issues:
                raise LangSyncError(message + " Fix them first or explicitly pass --allow-source-issues.")
            print("WARNING: " + message, file=sys.stderr)

        previous: Optional[ParsedLang] = None
        if args.git_base:
            source_rel = source_path.resolve().relative_to(repo_root.resolve())
            previous = git_show_source(repo_root, args.git_base, source_rel)
        elif args.baseline:
            baseline_path = args.baseline if args.baseline.is_absolute() else repo_root / args.baseline
            previous = load_baseline(baseline_path.resolve())

        delta = source_delta(previous.values, source.values) if previous else None
        if args.apply and args.changed_policy != "keep" and delta is None:
            raise LangSyncError(
                "--changed-policy requires --git-base or --baseline. Without historical English, "
                "the tool cannot distinguish a valid translation from a stale fictional name."
            )
        if args.apply and args.remove_deleted and delta is None:
            raise LangSyncError("--remove-deleted requires --git-base or --baseline.")

        locale_paths = selected_locale_files(lang_dir, args.source, args.locale)
        if not locale_paths:
            raise LangSyncError(f"No locale files matching xx_YY.lang in {lang_dir}")

        audits: List[LocaleAudit] = []
        plans: List[Tuple[Path, str, str, int]] = []
        for path in locale_paths:
            target = parse_file(path)
            audit = audit_locale(source, target, delta)
            old_text, _, _ = read_text(path)
            new_text, modifications = build_updated_text(
                source, target, delta, args.changed_policy,
                args.missing_policy, args.remove_deleted,
            )
            audit.planned_changes = modifications
            audits.append(audit)
            if new_text != old_text:
                if modifications > args.max_changes and not args.allow_large:
                    raise LangSyncError(
                        f"Refusing {modifications} changes in {path.name}; limit is {args.max_changes}. "
                        "Review the dry run, then use --allow-large if intentional."
                    )
                plans.append((path, old_text, new_text, modifications))

        print_audit(source, delta, audits, max(0, args.examples))

        if args.show_diff:
            for path, old, new, modifications in plans:
                print(f"--- Planned diff: {path.name} ({modifications} semantic changes) ---")
                print("\n".join(unified_diff(old, new, path)))
                print()

        if args.apply:
            backup_root = args.backup_dir if args.backup_dir.is_absolute() else repo_root / args.backup_dir
            for path, _old, new, _modifications in plans:
                backup = backup_file(repo_root, backup_root.resolve(), path)
                atomic_write(path, new)
                # Reparse and re-audit actual output rather than reporting stale pre-write state.
                reparsed = parse_file(path)
                new_audit = audit_locale(source, reparsed, delta)
                new_audit.rewritten = True
                new_audit.planned_changes = 0
                index = next(i for i, a in enumerate(audits) if a.path == path)
                audits[index] = new_audit
                print(f"Updated {path.name}; backup: {backup}")

        if args.report:
            report_path = args.report if args.report.is_absolute() else repo_root / args.report
            atomic_write(report_path.resolve(), json.dumps(
                report_dict(source, previous, delta, audits), ensure_ascii=False, indent=2
            ) + "\n")
            print(f"Report: {report_path.resolve()}")

        if args.write_baseline:
            baseline_path = args.write_baseline if args.write_baseline.is_absolute() else repo_root / args.write_baseline
            write_baseline(baseline_path.resolve(), source)
            print(f"Baseline: {baseline_path.resolve()}")

        if not args.apply:
            print(f"Dry run complete: {len(plans)} locale file(s) would change. Add --apply only after reviewing.")
        else:
            print(f"Applied changes to {len(plans)} locale file(s).")
        return 0

    except (OSError, ValueError, LangSyncError) as exc:
        print(f"lang_sync: error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
