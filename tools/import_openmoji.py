#!/usr/bin/env python3
"""
Imports OpenMoji (https://openmoji.org, CC BY-SA 4.0) color SVGs into app/src/main/assets/openmoji_emojis.

OpenMoji names its files by codepoint ("1F600.svg", "1F468-200D-1F469.svg"), so the app matches
standard emoji against emoji_index.json directly. OpenMoji's own extras (private-use codepoints
like E000 "goldfish") and non-emoji Unicode symbols aren't in that index, so their names and
keywords are written to extras.json from OpenMoji's data/openmoji.json.

Usage:
    curl -sfLo /tmp/openmoji-svg-color.zip https://github.com/hfg-gmuend/openmoji/releases/download/17.0.0/openmoji-svg-color.zip
    curl -sfLo /tmp/openmoji.json https://raw.githubusercontent.com/hfg-gmuend/openmoji/17.0.0/data/openmoji.json
    mkdir /tmp/openmoji-svg && cd /tmp/openmoji-svg && unzip /tmp/openmoji-svg-color.zip
    python3 tools/import_openmoji.py /tmp/openmoji-svg /tmp/openmoji.json
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/openmoji_emojis")


def keywords(*texts):
    words = []
    for text in texts:
        for word in text.replace(",", " ").lower().split():
            if len(word) > 1 and word not in words:
                words.append(word)
    return words


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    svg_dir, metadata_path = sys.argv[1], sys.argv[2]
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    svgs = [f for f in os.listdir(svg_dir) if f.lower().endswith(".svg")]
    for name in svgs:
        shutil.copyfile(os.path.join(svg_dir, name), os.path.join(OUT, name))

    extras = []
    for entry in json.load(open(metadata_path, encoding="utf-8")):
        if not entry.get("group", "").startswith("extras"):
            continue
        file = entry["hexcode"] + ".svg"
        if file not in svgs:
            continue
        name = entry.get("annotation") or entry["hexcode"]
        extras.append({
            "file": file,
            "name": " ".join(w[:1].upper() + w[1:] for w in name.split(" ")),
            "keywords": keywords(name, entry.get("tags", ""), entry.get("openmoji_tags", "")),
        })
    with open(os.path.join(OUT, "extras.json"), "w", encoding="utf-8") as f:
        json.dump(extras, f, ensure_ascii=False, indent=1)
        f.write("\n")

    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"copied {len(svgs)} SVGs and {len(extras)} extras names to {os.path.relpath(OUT, ROOT)} ({total / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
