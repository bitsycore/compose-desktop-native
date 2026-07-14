#!/usr/bin/env python3
"""
Parity harness — render every demo screen on the NATIVE (SDL/Skia) stack and on
the JVM upstream-Compose stack, then pixel-diff them per screen.

The two stacks share the exact same commonMain screen composables, so a screen
that suddenly diverges from its usual difference level is a PORT REGRESSION
(missing content, wrong shape/colour, broken clip). Absolute pixel-perfection
is NOT the goal — fonts differ between stacks, so text-heavy screens carry a
steady baseline difference. The signal is the RANKING: a screen that jumps from
~8% to ~60% different is the bug. Four of the renderer regressions this project
hit would have surfaced here.

Usage (from repo root):
    python scripts/parity/parity.py                 # all screens
    python scripts/parity/parity.py Buttons Shapes  # a subset
    python scripts/parity/parity.py --no-build      # reuse existing screenshots

Outputs to build/parity/ (gitignored):
    <pct>_<Name>_compare.png   native | jvm | amplified-diff, side by side
    <pct>_<Name>_diff.png      the amplified pixel-difference heatmap alone
    report.txt                 ranked table
The <pct> prefix is zero-padded so a plain file listing sorts worst-first.

Windows-only for the native leg today (mingwX64 exe). Needs Pillow.
Native uses the SDL renderer; pass --gpu to change.
"""
import subprocess, sys, os, shutil
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "build" / "parity"
NATIVE_EXE = REPO / "demo" / "build" / "bin" / "mingwX64" / "debugExecutable" / "demo.exe"
WIDTH, HEIGHT = 1000, 700
# Per-channel tolerance: below this a pixel counts as "same" (JPEG-ish noise,
# sub-pixel AA, font hinting). Tuned so unrelated screens sit well under it.
TOL = 32

GRADLEW = str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def run(cmd, **kw):
    print("+ " + " ".join(str(c) for c in cmd))
    return subprocess.run(cmd, cwd=REPO, **kw)


def build():
    run([GRADLEW, ":demo:linkDebugExecutableMingwX64", "--console=plain"], check=True)


def jvm_shots(dst: Path):
    run([GRADLEW, ":demo:run", f"--args=--screenshot-all={dst}", "--console=plain"], check=True)


def native_shot(name: str, dst: Path, gpu: str):
    bmp = dst / f"{name}.bmp"
    run([str(NATIVE_EXE), f"--screen={name}", f"--screenshot={bmp}",
         f"--gpu={gpu}", f"--width={WIDTH}", f"--height={HEIGHT}"],
        check=False, timeout=60)
    if bmp.exists():
        Image.open(bmp).convert("RGB").save(dst / f"{name}.png")
        bmp.unlink()


def diff_pair(native: Image.Image, jvm: Image.Image):
    """Return (percent_differing, amplified_diff_image)."""
    w = min(native.width, jvm.width)
    h = min(native.height, jvm.height)
    a = native.crop((0, 0, w, h)).convert("RGB")
    b = jvm.crop((0, 0, w, h)).convert("RGB")
    diff = ImageChops.difference(a, b)
    # A pixel "differs" if any channel exceeds TOL.
    gray = diff.convert("L")
    mask = gray.point(lambda p: 255 if p > TOL else 0)
    differing = sum(mask.point(lambda p: 1 if p else 0).getdata())
    pct = 100.0 * differing / (w * h)
    amplified = diff.point(lambda p: min(255, p * 4))
    return pct, amplified


def side_by_side(native, jvm, diff, path):
    w = min(native.width, jvm.width)
    h = min(native.height, jvm.height)
    canvas = Image.new("RGB", (w * 3 + 20, h + 20), (16, 16, 16))
    canvas.paste(native.crop((0, 0, w, h)), (0, 10))
    canvas.paste(jvm.crop((0, 0, w, h)), (w + 10, 10))
    canvas.paste(diff, (w * 2 + 20, 10))
    canvas.save(path)


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    no_build = "--no-build" in flags
    gpu = next((f.split("=", 1)[1] for f in flags if f.startswith("--gpu=")), "sdl3")

    OUT.mkdir(parents=True, exist_ok=True)
    jvm_dir = OUT / "_jvm"
    # Clear last run's pct-prefixed visualizations so stale percentages don't
    # linger alongside the fresh ones.
    for old in list(OUT.glob("*_compare.png")) + list(OUT.glob("*_diff.png")):
        old.unlink()

    if not no_build:
        build()
        if jvm_dir.exists():
            shutil.rmtree(jvm_dir)
        jvm_dir.mkdir(parents=True)
        jvm_shots(jvm_dir)

    # Screen set: the JVM run enumerates the full registry → use its PNGs as the
    # source of truth for names (native takes one exe launch each).
    names = sorted(p.stem for p in jvm_dir.glob("*.png"))
    if argv:
        wanted = {n.lower() for n in argv}
        names = [n for n in names if n.lower() in wanted]
    if not names:
        print("No screens matched.", file=sys.stderr)
        return 1

    results = []
    for name in names:
        jvm_png = jvm_dir / f"{name}.png"
        if not no_build or not (OUT / f"{name}.native.png").exists():
            native_shot(name, OUT, gpu)
            src = OUT / f"{name}.png"
            if src.exists():
                src.rename(OUT / f"{name}.native.png")
        native_png = OUT / f"{name}.native.png"
        if not native_png.exists():
            results.append((name, None))
            continue
        native = Image.open(native_png).convert("RGB")
        jvm = Image.open(jvm_png).convert("RGB")
        pct, amp = diff_pair(native, jvm)
        # Zero-padded pct prefix → worst-first in any file listing.
        prefix = f"{pct:06.2f}"
        amp.save(OUT / f"{prefix}_{name}_diff.png")
        side_by_side(native, jvm, amp, OUT / f"{prefix}_{name}_compare.png")
        results.append((name, pct))
        print(f"  {name:28s} {pct:6.2f}% differing")

    results.sort(key=lambda r: (-1 if r[1] is None else r[1]), reverse=True)
    lines = ["screen                        %differ", "-" * 40]
    for name, pct in results:
        lines.append(f"{name:28s}  {'NATIVE FAILED' if pct is None else f'{pct:6.2f}%'}")
    report = "\n".join(lines)
    (OUT / "report.txt").write_text(report + "\n", encoding="utf-8")
    print("\n" + report)
    print(f"\nDiff images + report in {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
