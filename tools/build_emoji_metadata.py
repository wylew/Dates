#!/usr/bin/env python3
"""
Builds the emoji metadata the picker uses to show Unicode sections, names and search keywords.

Outputs (all under app/src/main/assets):
  emoji_index.json            Every fully-qualified Unicode emoji (no skin-tone variants), in
                              Unicode order: {"e": emoji, "n": name, "g": group, "k": keywords}.
                              Font-based libraries (System, Mona) and OpenMoji look emojis up here.
  segoe_svgs/metadata.json    One entry per Segoe SVG: file, name, category, keywords, emoji.

Fluent and OpenMoji files are named by codepoint, so the app matches them against emoji_index.json
directly (see tools/import_fluent_emoji.py). The Segoe SVGs are named after Unicode character
names ("black-heart-suit.svg") and are matched back to their emoji here, with a few hand-mapped
exceptions below.

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

# Segoe files that aren't emoji names; group by hand (index sorts them to the end of the group)
SEGOE_MANUAL = {
    "black-left-pointing-index": "People & Body",
    "black-right-pointing-index": "People & Body",
    "white-down-pointing-index": "People & Body",
    "white-left-pointing-index": "People & Body",
    "white-right-pointing-index": "People & Body",
    "reversed-victory-hand": "People & Body",
    "lower-right-pencil": "Objects",
    "upper-right-pencil": "Objects",
    "rotated-heavy-black-heart-bullet": "Symbols",
    "e009": "Symbols",
    "white-club-suit": "Activities",
    "white-diamond-suit": "Activities",
    "white-heart-suit": "Activities",
    "white-spade-suit": "Activities",
}

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


def sort_key(entry_group, order, name):
    return (GROUP_ORDER.index(entry_group), order, name)


def build_segoe(entries):
    folder = os.path.join(ASSETS, "segoe_svgs")
    if not os.path.isdir(folder):
        return
    by_name = {}
    for e in entries:
        by_name.setdefault(slug(e["name"], "-"), e)
    # Segoe files often use formal Unicode character names; map those through the codepoint
    by_codepoint = {}
    for e in entries:
        stripped = tuple(c for c in e["codepoints"] if c != 0xFE0F)
        by_codepoint.setdefault(stripped, e)
    char_names = {}
    for cp in range(0x20, 0x110000):
        try:
            char_names.setdefault(slug(unicodedata.name(chr(cp)), "-"), cp)
        except ValueError:
            pass

    out = []
    for file in sorted(f for f in os.listdir(folder) if f.endswith(".svg")):
        key = file[:-4]
        e = by_name.get(key)
        if e is None and key in char_names:
            e = by_codepoint.get((char_names[key],))
        if e is not None:
            group, order, emoji, cldr = e["group"], e["order"], e["emoji"], e["name"]
        elif key in SEGOE_MANUAL or key.startswith("mahjong-tile"):
            group, order, emoji, cldr = SEGOE_MANUAL.get(key, "Activities"), 10**6, None, None
            if key in char_names:
                emoji = chr(char_names[key])
        else:
            sys.exit(f"Segoe file not matched to an emoji: {file} (add it to SEGOE_MANUAL)")
        file_name = key.replace("-", " ")
        item = {"file": file, "name": title(cldr or file_name), "category": group,
                "keywords": keywords(file_name, cldr or "")}
        if emoji:
            item["emoji"] = emoji
        out.append((sort_key(group, order, key), item))
    out.sort(key=lambda t: t[0])
    write_json(os.path.join(folder, "metadata.json"), [item for _, item in out])


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    entries = parse_emoji_test(sys.argv[1])
    write_json(os.path.join(ASSETS, "emoji_index.json"), build_index(entries))
    build_segoe(parse_emoji_test(sys.argv[1], include_components=True))


if __name__ == "__main__":
    main()
