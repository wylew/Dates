#!/usr/bin/env python3
"""
Builds assets/emoji_index.json: every fully-qualified Unicode emoji (no skin-tone variants), in
Unicode order, as {"e": emoji, "n": name, "g": group, "k": keywords}.

The picker uses it for names, sections and search in the font-based libraries (System, Mona) and
for packs whose files are named by codepoint (Fluent, OpenMoji). Segoe's metadata is built
separately by tools/build_segoe_metadata.py.

Usage:
    curl -sSfLo /tmp/emoji-test.txt https://unicode.org/Public/emoji/latest/emoji-test.txt
    python3 tools/build_emoji_metadata.py /tmp/emoji-test.txt
"""
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app/src/main/assets")

GROUP_ORDER = [
    "Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink",
    "Travel & Places", "Activities", "Objects", "Symbols", "Flags",
]

SKIN_TONES = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}

def slug(name, sep):
    name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", sep, name).strip(sep)


def title(name):
    return " ".join(w[:1].upper() + w[1:] for w in name.split(" "))


def keywords(*texts):
    words = []
    for text in texts:
        for w in re.findall(r"[a-z0-9]+", text.lower()):
            if len(w) > 1 and w not in words:
                words.append(w)
    return words


def parse_emoji_test(path, include_components=False):
    """Ordered fully-qualified emojis without skin tones: (emoji, name, group, subgroup, order).
    Components (skin-tone swatches, hair styles) are only included when asked, filed under People & Body."""
    entries, group, subgroup = [], None, None
    for line in open(path, encoding="utf-8"):
        if line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
            continue
        if line.startswith("# subgroup:"):
            subgroup = line.split(":", 1)[1].strip()
            continue
        m = re.match(r"^([0-9A-F ]+);\s*(fully-qualified|component)\s*#\s*(\S+)\s+E[\d.]+\s+(.*)$", line.strip())
        if not m:
            continue
        is_component = group == "Component"
        if is_component and not include_components:
            continue
        codepoints = [int(c, 16) for c in m.group(1).split()]
        if not is_component and SKIN_TONES & set(codepoints):
            continue
        entries.append({"emoji": m.group(3), "name": m.group(4),
                        "group": "People & Body" if is_component else group,
                        "subgroup": subgroup, "order": len(entries), "codepoints": codepoints})
    return entries


def build_index(entries):
    return [{"e": e["emoji"], "n": title(e["name"]), "g": e["group"],
             "k": keywords(e["name"], e["subgroup"].replace("-", " "))} for e in entries]


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1 if "metadata" in path else None,
                  separators=None if "metadata" in path else (",", ":"))
        f.write("\n")
    print(f"wrote {os.path.relpath(path, ROOT)} ({len(data)} entries)")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    entries = parse_emoji_test(sys.argv[1])
    write_json(os.path.join(ASSETS, "emoji_index.json"), build_index(entries))


if __name__ == "__main__":
    main()
