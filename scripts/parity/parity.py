#!/usr/bin/env python3
"""
Parity harness - render every demo screen on the NATIVE (SDL/Skia) stack and on
the JVM upstream-Compose stack, then pixel-diff them per screen.

The two stacks share the exact same commonMain screen composables, so a screen
that suddenly diverges from its usual difference level is a PORT REGRESSION
(missing content, wrong shape/colour, broken clip). Absolute pixel-perfection
is NOT the goal - fonts differ between stacks, so text-heavy screens carry a
steady baseline difference. The signal is the RANKING: a screen that jumps from
~8% to ~60% different is the bug. Four of the renderer regressions this project
hit would have surfaced here.

Usage (from repo root):
    python scripts/parity/parity.py                 # all screens, host's native target
    python scripts/parity/parity.py Buttons Shapes  # a subset
    python scripts/parity/parity.py --no-build      # reuse existing screenshots
    python scripts/parity/parity.py --renderer=sdl3 # force the SDL leg (default on mingw;
                                                    # on macOS/Linux this -Prenderer=sdl3 build)
    python scripts/parity/parity.py --target=macosArm64   # cross/explicit native target

Outputs to build/parity/ (gitignored):
    <pct>_<Name>_compare.png   native | jvm | amplified-diff, side by side
    <pct>_<Name>_diff.png      the amplified pixel-difference heatmap alone
    report.txt                 ranked table
The <pct> prefix is zero-padded so a plain file listing sorts worst-first.

TARGET-AWARE (P0.1): the native leg runs on whatever host you invoke it from - Windows
(mingwX64, always the SDL renderer), or macOS/Linux (default the Skia renderer, or the SDL
renderer under --renderer=sdl3). macOS/Linux run BOTH renderers, so one Mac verifies both
legs. Needs Pillow.
"""
import subprocess, sys, os, shutil, platform, json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "build" / "parity"

# native target -> (gradle link-task suffix, K/N executable filename).
TARGETS = {
    "mingwX64":   ("MingwX64",   "demo.exe"),
    "macosArm64": ("MacosArm64", "demo.kexe"),
    "macosX64":   ("MacosX64",   "demo.kexe"),
    "linuxX64":   ("LinuxX64",   "demo.kexe"),
    "linuxArm64": ("LinuxArm64", "demo.kexe"),
}


def host_target() -> str:
    s, m = platform.system(), platform.machine().lower()
    arm = m in ("arm64", "aarch64")
    if s == "Windows": return "mingwX64"
    if s == "Darwin":  return "macosArm64" if arm else "macosX64"
    if s == "Linux":   return "linuxArm64" if arm else "linuxX64"
    raise SystemExit(f"parity: unsupported host {s}/{m}")


def native_exe(target: str) -> Path:
    _, exe = TARGETS[target]
    return REPO / "demo" / "build" / "bin" / target / "debugExecutable" / exe


WIDTH, HEIGHT = 1000, 700
# Per-channel tolerance: below this a pixel counts as "same" (JPEG-ish noise,
# sub-pixel AA, font hinting). Tuned so unrelated screens sit well under it.
TOL = 32

GRADLEW = str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def run(cmd, **kw):
    print("+ " + " ".join(str(c) for c in cmd))
    return subprocess.run(cmd, cwd=REPO, **kw)


def build(target: str, renderer: str):
    suffix, _ = TARGETS[target]
    cmd = [GRADLEW, f":demo:linkDebugExecutable{suffix}", "--console=plain"]
    # macOS/Linux pick the renderer at BUILD time via -Prenderer; mingw is always sdl3
    # (no property needed, and omitting it keeps the default Windows invocation stable).
    if renderer == "sdl3" and target != "mingwX64":
        cmd.append("-Prenderer=sdl3")
    run(cmd, check=True)


def jvm_shots(dst: Path):
    run([GRADLEW, ":demo:run", f"--args=--screenshot-all={dst}", "--console=plain"], check=True)


def native_shot(name: str, dst: Path, exe: Path, gpu: str):
    bmp = dst / f"{name}.bmp"
    cmd = [str(exe), f"--screen={name}", f"--screenshot={bmp}",
           f"--width={WIDTH}", f"--height={HEIGHT}"]
    if gpu:  # runtime driver within the renderer; omit to let the app pick its default
        cmd.append(f"--gpu={gpu}")
    run(cmd, check=False, timeout=60)
    if bmp.exists():
        Image.open(bmp).convert("RGB").save(dst / f"{name}.png")
        bmp.unlink()


def align_native(native: Image.Image, jvm: Image.Image) -> Image.Image:
    """Normalise the native render to the JVM reference's dimensions.

    The native leg draws at the machine's physical pixel density (2000x1400 on a
    Retina Mac, DPR 2.0), while the JVM ImageComposeScene reference is fixed at
    density 1.0 (1000x700). Both represent the SAME 1000x700-dp viewport, so a
    proportional comparison must resize native down to the reference size rather
    than crop it (a top-left crop would compare only a 2x-magnified quarter — the
    old behaviour, invisible on Windows where both legs were already 1000x700).
    A no-op when the sizes already match."""
    if native.size != jvm.size:
        return native.resize(jvm.size, Image.LANCZOS)
    return native


def diff_pair(native: Image.Image, jvm: Image.Image):
    """Return (percent_differing, amplified_diff_image)."""
    native = align_native(native, jvm)
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
    native = align_native(native, jvm)
    w = min(native.width, jvm.width)
    h = min(native.height, jvm.height)
    canvas = Image.new("RGB", (w * 3 + 20, h + 20), (16, 16, 16))
    canvas.paste(native.crop((0, 0, w, h)), (0, 10))
    canvas.paste(jvm.crop((0, 0, w, h)), (w + 10, 10))
    canvas.paste(diff, (w * 2 + 20, 10))
    canvas.save(path)


# P0.4 (RENDERER.md §8): per-(target/renderer) golden baselines so parity GATES
# (non-zero exit) instead of only ranking. A screen regresses if it exceeds its baseline by
# more than max(ABS_MARGIN pts, baseline*REL_MARGIN) - tolerant of run-to-run AA jitter,
# strict on a real jump. `--update-baselines` reseeds the current target/renderer.
BASELINES = REPO / "scripts" / "parity" / "baselines.json"
ABS_MARGIN = 3.0   # percentage points
REL_MARGIN = 0.25  # +25% of the baseline


def load_baselines() -> dict:
    if BASELINES.exists():
        try:
            return json.loads(BASELINES.read_text(encoding="utf-8"))
        except Exception:
            print("parity: baselines.json unreadable - treating as empty", file=sys.stderr)
    return {}


def threshold(base: float) -> float:
    return base + max(ABS_MARGIN, base * REL_MARGIN)


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    no_build = "--no-build" in flags
    target = next((f.split("=", 1)[1] for f in flags if f.startswith("--target=")), host_target())
    if target not in TARGETS:
        print(f"parity: unknown --target={target} (known: {', '.join(TARGETS)})", file=sys.stderr)
        return 2
    # Renderer: mingw is always SDL; macOS/Linux default to Skia unless --renderer=sdl3.
    renderer = next((f.split("=", 1)[1] for f in flags if f.startswith("--renderer=")),
                    "sdl3" if target == "mingwX64" else "skia")
    # Runtime GPU driver: default "sdl3" for the SDL renderer; omit for Skia (app default,
    # e.g. Metal on macOS). Explicit --gpu= always wins.
    gpu = next((f.split("=", 1)[1] for f in flags if f.startswith("--gpu=")),
               "sdl3" if renderer == "sdl3" else "")
    exe = native_exe(target)
    update_baselines = "--update-baselines" in flags
    baseline_key = f"{target}/{renderer}"
    baselines = load_baselines()
    base_for_key = baselines.get(baseline_key, {})
    print(f"parity: target={target} renderer={renderer} gpu={gpu or '(default)'} exe={exe}")
    print(f"parity: baseline key '{baseline_key}' - {len(base_for_key)} screen(s) baselined"
          f"{'  [--update-baselines: WILL RESEED]' if update_baselines else ''}")

    OUT.mkdir(parents=True, exist_ok=True)
    jvm_dir = OUT / "_jvm"
    # Clear last run's pct-prefixed visualizations so stale percentages don't
    # linger alongside the fresh ones.
    for old in list(OUT.glob("*_compare.png")) + list(OUT.glob("*_diff.png")):
        old.unlink()

    if not no_build:
        build(target, renderer)
        if jvm_dir.exists():
            shutil.rmtree(jvm_dir)
        jvm_dir.mkdir(parents=True)
        jvm_shots(jvm_dir)

    # Screen set: the JVM run enumerates the full registry -> use its PNGs as the
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
            native_shot(name, OUT, exe, gpu)
            src = OUT / f"{name}.png"
            if src.exists():
                src.replace(OUT / f"{name}.native.png")
        native_png = OUT / f"{name}.native.png"
        if not native_png.exists():
            results.append((name, None))
            continue
        native = Image.open(native_png).convert("RGB")
        jvm = Image.open(jvm_png).convert("RGB")
        pct, amp = diff_pair(native, jvm)
        # Zero-padded pct prefix -> worst-first in any file listing.
        prefix = f"{pct:06.2f}"
        amp.save(OUT / f"{prefix}_{name}_diff.png")
        side_by_side(native, jvm, amp, OUT / f"{prefix}_{name}_compare.png")
        results.append((name, pct))
        print(f"  {name:28s} {pct:6.2f}% differing")

    # --update-baselines: write the current run as the golden baseline for this key.
    if update_baselines:
        baselines[baseline_key] = {n: round(p, 2) for n, p in results if p is not None}
        BASELINES.write_text(json.dumps(baselines, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"\nparity: reseeded {len(baselines[baseline_key])} baseline(s) for "
              f"'{baseline_key}' -> {BASELINES}")
        return 0

    results.sort(key=lambda r: (-1 if r[1] is None else r[1]), reverse=True)
    regressions = []
    lines = [f"screen                        %differ   baseline   verdict  ({baseline_key})", "-" * 66]
    for name, pct in results:
        base = base_for_key.get(name)
        if pct is None:
            verdict = "NATIVE FAILED"
            regressions.append(name)
            lines.append(f"{name:28s}  {'-':>7}   {base if base is None else f'{base:6.2f}%':>8}   {verdict}")
            continue
        if base is None:
            verdict = "(no baseline)"
        elif pct > threshold(base):
            verdict = f"REGRESSION (+{pct - base:.2f})"
            regressions.append(name)
        else:
            verdict = "ok"
        base_s = "   -   " if base is None else f"{base:6.2f}%"
        lines.append(f"{name:28s}  {pct:6.2f}%   {base_s:>8}   {verdict}")
    report = "\n".join(lines)
    (OUT / "report.txt").write_text(report + "\n", encoding="utf-8")
    print("\n" + report)
    print(f"\nDiff images + report in {OUT}")

    if not base_for_key:
        print(f"\nparity: NO baselines for '{baseline_key}' - not gating. "
              f"Seed with:  python scripts/parity/parity.py --update-baselines")
        return 0
    if regressions:
        print(f"\nparity: FAIL - {len(regressions)} regression(s): {', '.join(regressions)}")
        return 1
    print(f"\nparity: PASS - no screen exceeds its baseline (+{ABS_MARGIN}pts / +{REL_MARGIN:.0%}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
