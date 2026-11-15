"""Builds the Noto Serif SC subset for the 天光 theme (app/src/main/res/font).

Character set: every GB2312 character (incl. 6,763 hanzi and full-width forms), printable
ASCII, plus every non-ASCII character that appears in the app's Kotlin sources and XML
resources. Re-run after adding UI copy with characters outside GB2312.

Sources: NotoSerifSC-SemiBold.otf and NotoSerifSC-Bold.otf from
https://github.com/notofonts/noto-cjk/tree/main/Serif/SubsetOTF/SC (SIL OFL 1.1).
Requires fonttools. Usage:
    python make_serif_subset.py <font-src-dir> <repo-root> <out-dir>
then copy <out-dir>/noto_serif_sc_*.otf into app/src/main/res/font/.
"""
import os
import subprocess
import sys

src_dir, repo, out_dir = sys.argv[1:4]

chars = set(chr(c) for c in range(0x20, 0x7F))
for hi in range(0xA1, 0xF8):
    for lo in range(0xA1, 0xFF):
        try:
            chars.add(bytes([hi, lo]).decode("gb2312"))
        except UnicodeDecodeError:
            pass
gb_count = len(chars)

main = os.path.join(repo, "app", "src", "main")
for root, _, files in os.walk(main):
    for f in files:
        if f.endswith((".kt", ".xml")):
            with open(os.path.join(root, f), encoding="utf-8", errors="ignore") as fh:
                for ch in fh.read():
                    if ord(ch) > 0x7F and not ch.isspace():
                        chars.add(ch)
chars.update("，。、；：？！“”‘’（）《》【】…—·～％＋－×÷°")

text_file = os.path.join(out_dir, "chars.txt")
os.makedirs(out_dir, exist_ok=True)
with open(text_file, "w", encoding="utf-8") as fh:
    fh.write("".join(sorted(chars)))
print(f"characters: {len(chars)} (GB2312 + ASCII {gb_count}, from sources {len(chars) - gb_count})")

for weight in ("SemiBold", "Bold"):
    src = os.path.join(src_dir, f"NotoSerifSC-{weight}.otf")
    out = os.path.join(out_dir, f"noto_serif_sc_{weight.lower()}.otf")
    subprocess.run([
        sys.executable, "-m", "fontTools.subset", src,
        f"--text-file={text_file}",
        "--layout-features=*",
        "--name-IDs=*",
        "--name-languages=*",
        "--no-hinting",
        f"--output-file={out}",
    ], check=True)
    print(f"{os.path.basename(out)}: {os.path.getsize(src) / 1e6:.1f} MB -> {os.path.getsize(out) / 1e6:.2f} MB")
