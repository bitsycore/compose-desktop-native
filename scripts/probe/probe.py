#!/usr/bin/env python3
"""
Drive a native compose-desktop-native app window for manual/automated visual
checks: launch it, send window-relative input (click / hover / hold / move),
and capture the client area via PrintWindow (works even when occluded).

Built from the ad-hoc rigs used to reproduce the square-on-click and TLS-chain
bugs - window-CLIENT-relative and process-addressed, so it doesn't care where
the window lands or whether it's focused.

Examples:
    # boot the demo's Images screen, screenshot after 4s
    python scripts/probe/probe.py demo --screen=Images --shot=images.png

    # boot bubble-wrap (example repo), press-and-hold a bubble mid-sheet, capture
    python scripts/probe/probe.py shared --exe <path-to>/shared.exe \\
        --hold 0.55,0.55 --shot press.png

    # hover then capture
    python scripts/probe/probe.py demo --screen=Shapes --hover 0.3,0.4 --shot hov.png

`proc` is the window's process name (demo / apidemo / shared). Native apps
built here use the mingwX64 debug exe by default; override with --exe. Actions
run in order; --shot captures after them. Windows-only. Needs Pillow only if
you pass --crop.
"""
import argparse, subprocess, sys, time, os, signal
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
DEFAULT_EXE = {
    "demo": REPO / "demo/build/bin/mingwX64/debugExecutable/demo.exe",
    "apidemo": REPO / "apidemo/build/bin/mingwX64/debugExecutable/apidemo.exe",
}


def ps(script: str, *args: str):
    return subprocess.run(
        ["powershell", "-ExecutionPolicy", "Bypass", "-File", str(HERE / script), *args],
        capture_output=True, text=True,
    )


def frac(s: str):
    x, y = s.split(",")
    return float(x), float(y)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("proc", help="window process name (demo / apidemo / shared)")
    ap.add_argument("--exe", help="path to the executable (default: mingwX64 debug for demo/apidemo)")
    ap.add_argument("--screen", help="pass --screen=<Name> to the app (demo)")
    ap.add_argument("--args", default="", help="extra args passed verbatim to the exe")
    ap.add_argument("--boot", type=float, default=4.0, help="seconds to wait after launch")
    ap.add_argument("--click", help="fractional X,Y to click (e.g. 0.5,0.5)")
    ap.add_argument("--hold", help="fractional X,Y to press-and-hold")
    ap.add_argument("--hover", help="fractional X,Y to hover")
    ap.add_argument("--hold-ms", type=int, default=900, help="hold duration")
    ap.add_argument("--settle", type=float, default=0.3, help="seconds between action and capture")
    ap.add_argument("--shot", help="capture the window to this PNG after the actions")
    args = ap.parse_args()

    exe = Path(args.exe) if args.exe else DEFAULT_EXE.get(args.proc)
    if not exe or not exe.exists():
        print(f"exe not found: {exe} (build it, or pass --exe)", file=sys.stderr)
        return 1

    cmd = [str(exe)]
    if args.screen:
        cmd.append(f"--screen={args.screen}")
    if args.args:
        cmd += args.args.split()

    proc = subprocess.Popen(cmd)
    try:
        time.sleep(args.boot)
        if args.hover:
            fx, fy = frac(args.hover)
            print(ps("_input.ps1", "-proc", args.proc, "-action", "hover", "-fx", str(fx), "-fy", str(fy)).stdout.strip())
        if args.click:
            fx, fy = frac(args.click)
            print(ps("_input.ps1", "-proc", args.proc, "-action", "click", "-fx", str(fx), "-fy", str(fy)).stdout.strip())
        if args.hold:
            fx, fy = frac(args.hold)
            # hold runs async so we can capture DURING the press
            p = subprocess.Popen(["powershell", "-ExecutionPolicy", "Bypass", "-File", str(HERE / "_input.ps1"),
                                   "-proc", args.proc, "-action", "hold", "-fx", str(fx), "-fy", str(fy), "-holdMs", str(args.hold_ms)])
        time.sleep(args.settle)
        if args.shot:
            out = str(Path(args.shot).resolve())
            r = ps("_capture.ps1", "-proc", args.proc, "-out", out)
            print(f"{r.stdout.strip()} -> {out}")
    finally:
        subprocess.run(["taskkill", "/F", "/IM", exe.name], capture_output=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
