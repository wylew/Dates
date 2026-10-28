#!/usr/bin/env python3
"""
Imports Microsoft's Fluent 3D emoji (still images) into app/src/main/assets/fluent_emojis.

Each emoji's 256px 3D PNG (and every skin-tone variant) is resized and converted to WebP, and
named by its Unicode codepoints, e.g. 1f600.webp, 1f44b-1f3fb.webp. The app matches those names
against emoji_index.json for names and sections, so no separate metadata file is needed.

Requires cwebp (libwebp). Usage:
    git clone --filter=blob:none --no-checkout --depth 1 https://github.com/microsoft/fluentui-emoji.git /tmp/fluentui-emoji
    git -C /tmp/fluentui-emoji sparse-checkout set --no-cone '/assets/*/metadata.json' '/assets/**/3D/*'
    git -C /tmp/fluentui-emoji checkout main
    python3 tools/import_fluent_emoji.py /tmp/fluentui-emoji
"""
import glob
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/fluent_emojis")
SIZE = 192  # px; the largest in-app display (details hero) is ~78dp
TONE_FOLDERS = ["Default", "Light", "Medium-Light", "Medium", "Medium-Dark", "Dark"]


def codepoint_name(unicode_field):
    # "1f44b 1f3fb" -> "1f44b-1f3fb"; drop variation selector 16, which the app ignores
    return "-".join(cp for cp in unicode_field.lower().split() if cp != "fe0f")


def convert(src, dest):
    subprocess.run(["cwebp", "-quiet", "-q", "85", "-alpha_q", "90", "-resize", str(SIZE), str(SIZE), src, "-o", dest],
                   check=True)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    assets = os.path.join(sys.argv[1], "assets")
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    count = 0
    for meta_path in sorted(glob.glob(os.path.join(assets, "*", "metadata.json"))):
        folder = os.path.dirname(meta_path)
        meta = json.load(open(meta_path, encoding="utf-8"))
        tones = meta.get("unicodeSkintones")
        if tones:
            # Skin-tone emoji: one 3D image per tone folder, in the same order as unicodeSkintones
            for tone_folder, unicode_field in zip(TONE_FOLDERS, tones):
                pngs = glob.glob(os.path.join(folder, tone_folder, "3D", "*.png"))
                if pngs:
                    convert(pngs[0], os.path.join(OUT, codepoint_name(unicode_field) + ".webp"))
                    count += 1
        else:
            pngs = glob.glob(os.path.join(folder, "3D", "*.png"))
            if not pngs:
                print(f"skipping {os.path.basename(folder)}: no 3D image")
                continue
            convert(pngs[0], os.path.join(OUT, codepoint_name(meta["unicode"]) + ".webp"))
            count += 1

    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"wrote {count} images to {os.path.relpath(OUT, ROOT)} ({total / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
