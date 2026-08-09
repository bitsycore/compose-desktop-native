# Probe driver

Launches a native compose-desktop-native app window, sends **window-relative**
synthetic input, and captures the client area - for reproducing visual bugs
deterministically (this is the packaged form of the rigs that caught the
square-on-click and TLS-chain regressions).

Why window-relative + PrintWindow: input coordinates are FRACTIONS of the
window client rect and addressed by process name, so a probe doesn't depend on
where the window landed or on it being foreground; capture uses `PrintWindow`,
which grabs the window even when occluded or unfocused (a plain screen grab
would catch whatever's on top).

```bash
# screenshot a demo screen
python scripts/probe/probe.py demo --screen=Images --shot images.png

# press-and-hold a point (fraction of the window) and capture DURING the press
python scripts/probe/probe.py demo --screen=Buttons --hold 0.1,0.32 --shot press.png

# hover then capture
python scripts/probe/probe.py demo --screen=Shapes --hover 0.3,0.45 --shot hov.png

# an app built elsewhere (e.g. the bridge example)
python scripts/probe/probe.py shared --exe /path/to/shared.exe --click 0.5,0.5 --shot x.png
```

- `proc` = the window's process name (`demo`, `apidemo`, `shared`).
- Actions run in order; `--hold` captures mid-press (async), `--click` /
  `--hover` complete first. `--shot` captures after `--settle` seconds.
- Windows-only (uses Win32 input + PrintWindow). Pillow only needed if you
  post-process the PNG.

Pairs with `scripts/parity/` (whole-screen diffing); this is for targeted
interaction repro.
