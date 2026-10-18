#!/usr/bin/env python3
"""Check Android string resources against the default locale and locale config."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
LANGUAGE_ALIASES = {"in": "id"}
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
UNESCAPED_APOSTROPHE_RE = re.compile(r"(?<!\\)(?:\\\\)*'")


def canonicalize_tag(tag: str) -> str:
    parts = tag.replace("_", "-").split("-")
    if not parts or not parts[0]:
        return tag
    language = parts[0].lower()
    normalized = [LANGUAGE_ALIASES.get(language, language)]
    for part in parts[1:]:
        if len(part) == 4 and part.isalpha():
            normalized.append(part.title())
        elif len(part) in (2, 3) and part.isalpha():
            normalized.append(part.upper())
        else:
            normalized.append(part.lower())
    return "-".join(normalized)


def locale_for_directory(name: str) -> str | None:
    if name == "values":
        return "en"
    bcp47 = re.fullmatch(r"values-b\+([A-Za-z]{2,8}(?:\+[A-Za-z0-9]{1,8})*)", name)
    if bcp47:
        return canonicalize_tag(bcp47.group(1).replace("+", "-"))
    # The app uses language-only qualifiers for base locales. Support legacy
    # language-region resource names too, while ignoring values-night, etc.
    legacy = re.fullmatch(r"values-([a-z]{2,3})(?:-r([A-Z]{2,3}))?", name)
    if legacy:
        language, region = legacy.groups()
        return canonicalize_tag(f"{language}-{region}" if region else language)
    return None


def read_strings(path: Path, errors: list[str]) -> dict[str, str]:
    try:
        raw = path.read_text(encoding="utf-8")
        root = ET.fromstring(raw)
    except (OSError, UnicodeError, ET.ParseError) as exc:
        errors.append(f"{path}: cannot read XML: {exc}")
        return {}

    if root.tag != "resources":
        errors.append(f"{path}: root element must be <resources>")

    strings: dict[str, str] = {}
    for node in root.findall("string"):
        name = node.get("name")
        if not name:
            errors.append(f"{path}: found a <string> without a name")
            continue
        if name in strings:
            errors.append(f"{path}: duplicate string key {name!r}")
        value = "".join(node.itertext())
        if not value.strip():
            errors.append(f"{path}: {name} has an empty value")
        if UNESCAPED_APOSTROPHE_RE.search(value):
            errors.append(f"{path}: {name} contains an unescaped apostrophe")
        strings[name] = value
    return strings


def main() -> int:
    project = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--res-dir",
        type=Path,
        default=project / "app" / "src" / "main" / "res",
        help="Android res directory (default: app/src/main/res)",
    )
    parser.add_argument(
        "--locale-config",
        type=Path,
        default=project / "app" / "src" / "main" / "res" / "xml" / "locales_config.xml",
        help="Android locale-config XML file",
    )
    args = parser.parse_args()

    errors: list[str] = []
    default_path = args.res_dir / "values" / "strings.xml"
    if not default_path.is_file():
        print(f"ERROR: default strings file not found: {default_path}", file=sys.stderr)
        return 1
    default = read_strings(default_path, errors)
    default_path_keys = set(default)

    localized: dict[str, tuple[Path, dict[str, str]]] = {"en": (default_path, default)}
    for path in sorted(args.res_dir.glob("values-*/strings.xml")):
        tag = locale_for_directory(path.parent.name)
        if tag is None:
            continue
        tag_key = tag.casefold()
        if tag_key in localized:
            errors.append(f"{path}: duplicate locale resource for {tag}")
            continue
        strings = read_strings(path, errors)
        localized[tag_key] = (path, strings)
        keys = set(strings)
        missing = sorted(default_path_keys - keys)
        extra = sorted(keys - default_path_keys)
        if missing:
            errors.append(f"{path}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{path}: extra keys: {', '.join(extra)}")
        for key in sorted(default_path_keys & keys):
            expected = Counter(PLACEHOLDER_RE.findall(default[key]))
            actual = Counter(PLACEHOLDER_RE.findall(strings[key]))
            if expected != actual:
                errors.append(
                    f"{path}: {key} placeholders differ from English "
                    f"(expected {dict(expected)}, found {dict(actual)})"
                )

    try:
        config_root = ET.parse(args.locale_config).getroot()
        configured = [node.get(ANDROID_NAME) for node in config_root.findall("locale")]
    except (OSError, ET.ParseError) as exc:
        errors.append(f"{args.locale_config}: cannot read locale config: {exc}")
        configured = []

    if not configured:
        errors.append(f"{args.locale_config}: no <locale android:name=...> entries found")
    if any(not tag for tag in configured):
        errors.append(f"{args.locale_config}: a <locale> is missing android:name")
    valid_configured = [canonicalize_tag(tag) for tag in configured if tag]
    configured_keys = [tag.casefold() for tag in valid_configured]
    if len(configured_keys) != len(set(configured_keys)):
        errors.append(f"{args.locale_config}: duplicate locale entries")
    resource_keys = set(localized)
    config_keys = set(configured_keys)
    missing_resources = sorted(config_keys - resource_keys)
    unlisted_resources = sorted(resource_keys - config_keys)
    if missing_resources:
        errors.append("Locale config entries without string resources: " + ", ".join(missing_resources))
    if unlisted_resources:
        errors.append("String resource locales missing from locale config: " + ", ".join(unlisted_resources))

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        f"Validated {len(localized)} locale string sets, "
        f"{len(default_path_keys)} shared keys, placeholders, and locale config."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
