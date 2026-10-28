#!/usr/bin/env python3
"""
Rebuilds app/src/main/assets/segoe_svgs/metadata.json from the SVG file names.

The Segoe (Windows 10 Anniversary) SVGs are named after their Unicode character
names, e.g. "smiling-face-with-open-mouth.svg", "aubergine.svg",
"clock-face-eight-oclock.svg". This script matches each file back to its emoji
and pulls the standard Unicode/CLDR grouping (Smileys & Emotion, People & Body,
Food & Drink ...), the subgroup (hand-fingers-open, food-fruit ...), the modern
CLDR name and the CLDR keyword annotations, so the picker can show real
categories and search can find "eggplant" or "thumbs up".

Usage (data comes from the emojibase-data npm package):
    npm i --prefix /tmp/eb emojibase-data
    python3 tools/build_segoe_metadata.py /tmp/eb/node_modules/emojibase-data/en
"""
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(ROOT, "app/src/main/assets/segoe_svgs")

# Display names and order for the standard Unicode emoji groups.
GROUP_NAMES = {
    "smileys-emotion": "Smileys & Emotion",
    "people-body": "People & Body",
    "animals-nature": "Animals & Nature",
    "food-drink": "Food & Drink",
    "travel-places": "Travel & Places",
    "activities": "Activities",
    "objects": "Objects",
    "symbols": "Symbols",
    "flags": "Flags",
    "component": "Symbols",  # skin-tone swatches; not worth their own tab
}

# Non-emoji Unicode symbols that ship in the Segoe set. Hand-assigned to the
# group/subgroup an emoji picker user would look in.
MANUAL = {
    "black-left-pointing-index": ("☚", "Pointing Left (Solid)", "people-body", "hand-single-finger", ["point", "left", "finger", "hand"]),
    "black-right-pointing-index": ("☛", "Pointing Right (Solid)", "people-body", "hand-single-finger", ["point", "right", "finger", "hand"]),
    "white-left-pointing-index": ("☜", "Pointing Left", "people-body", "hand-single-finger", ["point", "left", "finger", "hand"]),
    "white-right-pointing-index": ("☞", "Pointing Right", "people-body", "hand-single-finger", ["point", "right", "finger", "hand"]),
    "white-down-pointing-index": ("☟", "Pointing Down", "people-body", "hand-single-finger", ["point", "down", "finger", "hand"]),
    "reversed-victory-hand": ("\U0001F594", "Reversed Victory Hand", "people-body", "hand-fingers-partial", ["victory", "peace", "hand", "fingers"]),
    "lower-right-pencil": ("\U0001F58E", "Pencil (Lower Right)", "objects", "writing", ["pencil", "write", "draw", "school"]),
    "upper-right-pencil": ("✐", "Pencil (Upper Right)", "objects", "writing", ["pencil", "write", "draw", "school"]),
    "rotated-heavy-black-heart-bullet": ("❥", "Rotated Heart", "smileys-emotion", "heart", ["heart", "love", "valentine", "anniversary"]),
    "white-club-suit": ("♧", "Club Suit Outline", "activities", "game", ["cards", "club", "suit", "game"]),
    "white-diamond-suit": ("♢", "Diamond Suit Outline", "activities", "game", ["cards", "diamond", "suit", "game"]),
    "white-heart-suit": ("♡", "Heart Suit Outline", "activities", "game", ["cards", "heart", "suit", "game", "love"]),
    "white-spade-suit": ("♤", "Spade Suit Outline", "activities", "game", ["cards", "spade", "suit", "game"]),
    "e009": ("", "Starburst Badge", "symbols", "geometric", ["badge", "stamp", "seal", "starburst", "sticker", "sale"]),
}

# Extra search terms that matter for a countdown app but are missing from CLDR.
EXTRA_KEYWORDS = {
    "birthday-cake": ["bday", "anniversary"],
    "wrapped-present": ["birthday", "christmas", "present", "bday"],
    "balloon": ["birthday", "bday"],
    "party-popper": ["birthday", "new year", "bday", "congrats"],
    "ring": ["wedding", "engagement", "anniversary", "proposal"],
    "heavy-black-heart": ["love", "anniversary", "valentine"],
    "christmas-tree": ["xmas", "holiday", "december"],
    "father-christmas": ["xmas", "christmas", "holiday"],
    "jack-o-lantern": ["october", "spooky"],
    "airplane": ["trip", "vacation", "flight"],
    "graduation-cap": ["school", "college", "degree"],
    "spiral-calendar-pad": ["date", "schedule", "deadline"],
    "calendar": ["date", "schedule", "deadline"],
    "tear-off-calendar": ["date", "schedule", "deadline"],
    "baby-bottle": ["baby shower", "due date"],
    "baby": ["due date", "newborn"],
    "house-building": ["move", "moving", "rent", "mortgage"],
    "beach-with-umbrella": ["vacation", "holiday", "summer"],
}

STOP = {"with", "and", "of", "the", "a", "an", "for", "in", "on", "at", "sign", "symbol", "face"}


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def words(s):
    return [w for w in re.split(r"[^a-z0-9]+", s.lower()) if w]


def title(label):
    small = {"a", "an", "and", "of", "with", "the", "or", "in", "on", "for"}
    out = []
    for i, w in enumerate(label.split(" ")):
        if w.isupper() or any(c.isdigit() for c in w) or (i and w in small):
            out.append(w)
        else:
            out.append(w[:1].upper() + w[1:])
    return " ".join(out)


def main(data_dir):
    data = json.load(open(os.path.join(data_dir, "data.json"), encoding="utf-8"))
    messages = json.load(open(os.path.join(data_dir, "messages.json"), encoding="utf-8"))
    group_keys = {g["order"]: g["key"] for g in messages["groups"]}
    subgroup_keys = {g["order"]: g["key"] for g in messages["subgroups"]}
    group_order = list(dict.fromkeys(GROUP_NAMES.values()))

    index = {}

    def add(key, entry, prio):
        if key and (key not in index or index[key][1] > prio):
            index[key] = (entry, prio)

    for e in data:
        if "group" not in e:
            continue
        cps = [c for c in e["emoji"] if c not in "️︎"]
        if len(cps) == 1:
            try:
                add(slug(unicodedata.name(cps[0])), e, 0)  # Unicode name: what the files use
            except ValueError:
                pass
        add(slug(e["label"]), e, 1)  # modern CLDR short name
        for sc in e.get("shortcodes") or []:
            add(slug(sc), e, 3)

    files = sorted(f for f in os.listdir(ASSET_DIR) if f.lower().endswith(".svg"))
    out, unmatched = [], []
    for f in files:
        key = f[:-4]
        entry = index.get(key, (None,))[0]
        if entry is None:
            for alt in (key.replace("-sign", ""), key + "-sign", key.replace("-symbol", ""), key + "-face", key.replace("-face", "")):
                if alt in index:
                    entry = index[alt][0]
                    break

        if entry is not None:
            gkey = group_keys[entry["group"]]
            sgkey = subgroup_keys.get(entry.get("subgroup"), "")
            emoji = entry["emoji"]
            label = entry["label"]
            order = entry.get("order", 99999)
            kw = list(entry.get("tags") or [])
            kw += [s.replace("_", " ") for s in entry.get("shortcodes") or []]
        elif key in MANUAL:
            emoji, label, gkey, sgkey, kw = MANUAL[key]
            kw = list(kw)
            order = 99999
        elif key.startswith("mahjong-tile-"):
            emoji, label, gkey, sgkey = "", title(key.replace("-", " ")), "activities", "game"
            kw = ["mahjong", "tile", "game"] + words(key[len("mahjong-tile-"):])
            order = 99999
        else:
            unmatched.append(f)
            continue

        category = GROUP_NAMES.get(gkey, "Symbols")
        kw += words(label) + words(key) + words(sgkey) + EXTRA_KEYWORDS.get(key, [])
        seen, keywords = set(), []
        for k in kw:
            k = k.strip().lower()
            if k and k not in STOP and k not in seen:
                seen.add(k)
                keywords.append(k)

        out.append({
            "file": f,
            "name": title(label),
            "emoji": emoji,
            "category": category,
            "subcategory": sgkey,
            "order": order,
            "keywords": keywords,
        })

    # Standard picker order: by group, then Unicode's own order inside the group.
    out.sort(key=lambda x: (group_order.index(x["category"]), x["order"], x["name"]))
    for i, x in enumerate(out):
        x["order"] = i

    with open(os.path.join(ASSET_DIR, "metadata.json"), "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
        fh.write("\n")

    from collections import Counter
    print(f"{len(out)} entries written, {len(unmatched)} unmatched: {unmatched}")
    for cat, n in Counter(x["category"] for x in out).most_common():
        print(f"  {cat}: {n}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
