#!/usr/bin/env python3
"""Fail unless candidate and baseline merged Android manifests keep identical permission declarations."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSION_TAG_PREFIX = "uses-permission"


def permissions(path: Path) -> set[tuple[str, str, str | None, str | None, str | None]]:
    root = ET.parse(path).getroot()
    found = set()
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if not tag.startswith(PERMISSION_TAG_PREFIX):
            continue
        name = element.attrib.get(ANDROID + "name")
        if not name:
            continue
        found.add(
            (
                tag,
                name,
                element.attrib.get(ANDROID + "maxSdkVersion"),
                element.attrib.get(ANDROID + "minSdkVersion"),
                element.attrib.get(ANDROID + "usesPermissionFlags"),
            )
        )
    return found


def describe(items: set[tuple[str, str, str | None, str | None, str | None]]) -> list[str]:
    return [
        f"{tag} {name} (maxSdk={max_sdk}, minSdk={min_sdk}, flags={flags})"
        for tag, name, max_sdk, min_sdk, flags in sorted(items)
    ]


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: compare-manifest-permissions.py BASELINE.xml CANDIDATE.xml", file=sys.stderr)
        return 2
    baseline_path, candidate_path = map(Path, sys.argv[1:])
    baseline = permissions(baseline_path)
    candidate = permissions(candidate_path)
    added, removed = candidate - baseline, baseline - candidate
    if added or removed:
        if added:
            print("New merged permissions (blocked):")
            print("\n".join(f"  + {row}" for row in describe(added)))
        if removed:
            print("Baseline merged permissions removed (review required):")
            print("\n".join(f"  - {row}" for row in describe(removed)))
        return 1
    print(f"Merged permission declarations unchanged from main ({len(candidate)} entries).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
