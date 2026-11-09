#!/usr/bin/env python3
"""Fail on unapproved Android permission changes between merged manifests."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSION_TAG_PREFIX = "uses-permission"
PermissionDeclaration = tuple[str, tuple[tuple[str, str], ...]]
APPROVED_ADDITIONS = {
    ("uses-permission", ((ANDROID + "name", "android.permission.FOREGROUND_SERVICE_DATA_SYNC"),)),
    ("uses-permission", ((ANDROID + "name", "android.permission.RUN_USER_INITIATED_JOBS"),)),
}


def permissions(path: Path) -> set[PermissionDeclaration]:
    root = ET.parse(path).getroot()
    found = set()
    for element in root.iter():
        raw_tag = element.tag
        local_tag = raw_tag.rsplit("}", 1)[-1]
        if not local_tag.startswith(PERMISSION_TAG_PREFIX):
            continue
        attributes = tuple(sorted(element.attrib.items()))
        found.add((raw_tag, attributes))
    return found


def describe(items: set[PermissionDeclaration]) -> list[str]:
    rows = []
    for tag, attributes in sorted(items):
        name = next((value for key, value in attributes if key == ANDROID + "name"), None)
        if name is None:
            declaration = f"{tag} <missing android:name>"
        elif not name:
            declaration = f"{tag} <empty android:name>"
        else:
            declaration = f"{tag} {name}"
        extras = [
            f"{key}={value}"
            for key, value in attributes
            if key != ANDROID + "name"
        ]
        suffix = f" ({', '.join(extras)})" if extras else ""
        rows.append(f"{declaration}{suffix}")
    return rows


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: compare-manifest-permissions.py BASELINE.xml CANDIDATE.xml", file=sys.stderr)
        return 2
    baseline_path, candidate_path = map(Path, sys.argv[1:])
    baseline = permissions(baseline_path)
    candidate = permissions(candidate_path)
    added, removed = candidate - baseline, baseline - candidate
    approved_added = added & APPROVED_ADDITIONS
    blocked_added = added - APPROVED_ADDITIONS
    if approved_added:
        print("Owner-approved merged permission additions:")
        print("\n".join(f"  + {row}" for row in describe(approved_added)))
    if blocked_added or removed:
        if blocked_added:
            print("New merged permissions (blocked):")
            print("\n".join(f"  + {row}" for row in describe(blocked_added)))
        if removed:
            print("Baseline merged permissions removed (review required):")
            print("\n".join(f"  - {row}" for row in describe(removed)))
        return 1
    if not approved_added:
        print(f"Merged permission declarations unchanged from main ({len(candidate)} entries).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
