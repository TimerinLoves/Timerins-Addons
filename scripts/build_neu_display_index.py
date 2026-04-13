#!/usr/bin/env python3
"""
Build neu_display_to_internal.json from NotEnoughUpdates-REPO items/*.json
(https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO).

Each item file has "displayname" (with §) and "internalname". We map normalized
display text (no color codes, lowercased) -> internal SkyBlock id.

Run from repo root: python scripts/build_neu_display_index.py
"""
from __future__ import annotations

import io
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

ZIP_URL = "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master"

SECTION = re.compile(r"§.")


def strip_mc_codes(s: str) -> str:
    return SECTION.sub("", s or "").strip()


def normalize_key(s: str) -> str:
    t = " ".join(strip_mc_codes(s).split())
    return t.casefold()


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    out_path = root / "src/main/resources/assets/timerins_addons/neu_display_to_internal.json"
    icon_path = root / "src/main/resources/assets/timerins_addons/neu_internal_to_itemid.json"

    print(f"Downloading {ZIP_URL} …", file=sys.stderr)
    with urllib.request.urlopen(ZIP_URL, timeout=600) as resp:
        data = resp.read()

    display_to_internal: dict[str, str] = {}
    internal_to_itemid: dict[str, str] = {}
    collisions = 0

    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        names = [n for n in zf.namelist() if "/items/" in n and n.endswith(".json")]
        print(f"Found {len(names)} item JSON files", file=sys.stderr)
        for name in names:
            raw = zf.read(name).decode("utf-8", errors="replace")
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError:
                continue
            internal = obj.get("internalname") or Path(name).stem
            disp = obj.get("displayname")
            itemid = obj.get("itemid")
            if internal:
                iid = str(itemid).strip() if itemid else ""
                if iid and internal not in internal_to_itemid:
                    internal_to_itemid[str(internal)] = iid
            if not disp or not internal:
                continue
            key = normalize_key(str(disp))
            if not key:
                continue
            prev = display_to_internal.get(key)
            if prev is not None and prev != internal:
                collisions += 1
                # Prefer keeping first (stable); NEU rarely duplicates display for different ids
            else:
                display_to_internal[key] = str(internal)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as f:
        json.dump(display_to_internal, f, ensure_ascii=False, sort_keys=True, indent=0)

    with icon_path.open("w", encoding="utf-8") as f:
        json.dump(internal_to_itemid, f, ensure_ascii=False, sort_keys=True, indent=0)

    print(f"Wrote {len(display_to_internal)} entries to {out_path}", file=sys.stderr)
    print(f"Wrote {len(internal_to_itemid)} internal→itemid entries to {icon_path}", file=sys.stderr)
    print(f"Display collisions skipped/overwritten: {collisions}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
