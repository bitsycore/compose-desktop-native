# Parity harness

Renders every `:demo` screen on the **native** (SDL/Skia, Kotlin/Native) stack
and on the **JVM** upstream-Compose stack from the *same* commonMain
composables, then pixel-diffs them per screen. It's a regression net: a screen
whose difference jumps far above its usual level is a port bug (missing
content, wrong shape/colour, broken clip). Several of this project's renderer
regressions would have surfaced here.

```bash
python scripts/parity/parity.py                 # all screens (builds first)
python scripts/parity/parity.py Buttons Shapes  # a subset
python scripts/parity/parity.py --no-build      # reuse the last renders
python scripts/parity/parity.py --gpu=sdl3      # native renderer (default sdl3)
```

Output → `build/parity/` (gitignored):

- `<pct>_<Name>_compare.png` — native ∣ jvm ∣ amplified-diff, side by side
- `<pct>_<Name>_diff.png` — the amplified difference heatmap alone
- `report.txt` — screens ranked by % differing

The `<pct>` prefix is zero-padded, so a plain file listing (or the report)
sorts worst-first.

## Reading the result

**Absolute % is not the metric — the ranking is.** The two stacks use
different default fonts, so text carries a steady baseline difference (in the
heatmap, every text line shows a faint *doubled* ghost from slightly different
line metrics). Buttons, cards, shapes, images should align (dark). So:

- **doubled/ghosted text, dark shapes** → normal font drift.
- **a solid bright block, or a shape present on one side only** → a real
  regression. Open the `_compare.png` to see which stack is wrong.

Compare a screen's % against its neighbours and its own history: Buttons ~16%,
Shapes ~14%, Colors ~4% are the healthy baseline (mostly text). A screen that
reads 60% when text-light is the bug.

## Requirements

- Windows for the native leg today (mingwX64 exe); the JVM leg is host-neutral.
- Pillow (`pip install pillow`).
- The JVM leg renders all screens headlessly via `ImageComposeScene` in one
  process (`:demo:run --args=--screenshot-all=…`); native takes one exe launch
  per screen.
