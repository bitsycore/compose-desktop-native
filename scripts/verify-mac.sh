#!/usr/bin/env bash
# MAC-VERIFY runbook (P0.2, RENDERER_TASKS.md) - the one-command gate for renderer work.
#
# Runs on macOS or Linux and exercises BOTH renderers:
#   0. DRIFT-CHECK: vendor-clean (sync.py + diff src/vendor, P0.7) + manual-vendor
#      provenance tripwire (P0.6)
#   1. Build :demo + :apidemo for the host target        (Skia leg, then -Prenderer=sdl3)
#   2. Run the interaction probes on each leg, gate on their PASS/FAIL output
#   3. Run scripts/parity/parity.py for each leg         (gates on baselines.json when seeded)
#   4. CDN_PROFILE draw-ms spot-check on LazyColumn/Tabs (fails on >20% regression vs the
#      last recorded run; build/verify-mac/perf-baseline.txt, seeded on first run)
#
# Usage:
#   scripts/verify-mac.sh                        # full run, non-zero exit on any failure
#   scripts/verify-mac.sh --update-perf-baseline # re-seed the perf baseline first
#
# Notes:
#   - Probes self-terminate on a frame counter; a hang means the leg is broken anyway.
#   - Toggling -Prenderer= keys a separate Gradle configuration-cache entry; if the SDL
#     leg fails with "couldn't find sdl3_ttf", rm -rf .gradle/configuration-cache (see
#     CLAUDE.md "Common pitfalls") and re-run.

set -u

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

# ============
#  Host target
case "$(uname -s)/$(uname -m)" in
	Darwin/arm64)          TARGET=MacosArm64; EXE_DIR=macosArm64 ;;
	Darwin/x86_64)         TARGET=MacosX64;   EXE_DIR=macosX64 ;;
	Linux/aarch64)         TARGET=LinuxArm64; EXE_DIR=linuxArm64 ;;
	Linux/x86_64)          TARGET=LinuxX64;   EXE_DIR=linuxX64 ;;
	*) echo "verify-mac: unsupported host $(uname -s)/$(uname -m)" >&2; exit 2 ;;
esac

DEMO_EXE="demo/build/bin/$EXE_DIR/debugExecutable/demo.kexe"
PROBES=(nav3test backtest clicktest scrolltest multiwintest)
PERF_DIR="build/verify-mac"
PERF_BASELINE="$PERF_DIR/perf-baseline.txt"
mkdir -p "$PERF_DIR"
[ "${1:-}" = "--update-perf-baseline" ] && rm -f "$PERF_BASELINE"

FAILURES=()
note() { echo; echo "==== verify-mac: $*"; }
fail() { FAILURES+=("$1"); echo "verify-mac: FAIL - $1" >&2; }

# ============
#  Perf spot-check: run one screen for ~8s under the frame profiler, parse the last
#  "draw=<avg>/<max>ms" line, compare avg against the recorded baseline (+20%, +0.2ms
#  absolute guard so sub-ms values don't false-positive on noise).
perf_check() { # $1=leg $2=screen
	local leg="$1" s="$2"
	local log="$PERF_DIR/profile_${leg}_${s}.log"
	rm -f "$log"
	note "[$leg] perf spot-check $s"
	CDN_PROFILE="$log" CDN_FORCERENDER=1 "$DEMO_EXE" --screen="$s" &
	local pid=$!
	sleep 8
	kill "$pid" 2>/dev/null
	wait "$pid" 2>/dev/null
	local avg
	avg=$(tail -1 "$log" 2>/dev/null | grep -o 'draw=[0-9.]*' | head -1 | cut -d= -f2)
	if [ -z "$avg" ]; then
		fail "[$leg] perf $s (no profiler output in $log)"
		return
	fi
	local base
	base=$(grep "^$leg $s " "$PERF_BASELINE" 2>/dev/null | awk '{print $3}')
	if [ -z "$base" ]; then
		echo "$leg $s $avg" >> "$PERF_BASELINE"
		echo "verify-mac: [$leg] $s draw=${avg}ms (baseline seeded)"
	elif awk -v a="$avg" -v b="$base" 'BEGIN { exit !(a > b * 1.2 && a > b + 0.2) }'; then
		fail "[$leg] perf $s regression: draw=${avg}ms vs baseline ${base}ms (+20% gate)"
	else
		echo "verify-mac: [$leg] $s draw=${avg}ms (baseline ${base}ms) ok"
	fi
}

# ============
#  One leg = build + probes + parity + perf.
run_leg() { # $1=skia|sdl3
	local leg="$1"
	local props=()
	[ "$leg" = "sdl3" ] && props=(-Prenderer=sdl3)

	note "[$leg] build :demo + :apidemo ($TARGET)"
	if ! ./gradlew ${props[@]+"${props[@]}"} ":demo:linkDebugExecutable$TARGET" \
			":apidemo:linkDebugExecutable$TARGET" --console=plain; then
		fail "[$leg] build"
		return
	fi

	local p out
	for p in "${PROBES[@]}"; do
		note "[$leg] probe --$p"
		out=$("$DEMO_EXE" "--$p" 2>&1 | tail -40)
		if ! echo "$out" | grep -q "PASS"; then
			echo "$out"
			fail "[$leg] probe $p"
		fi
	done

	note "[$leg] parity ($leg leg)"
	python3 scripts/parity/parity.py --renderer="$leg" || fail "[$leg] parity"

	# P2.2 memory soak: cycle every screen x6, assert current RSS doesn't ratchet up
	# (regression guard for the snapshot-observation / dispose leak fixed in c59caf72).
	note "[$leg] soak (memory)"
	out=$(CDN_SOAK_CYCLES=6 "$DEMO_EXE" --soaktest 2>&1 | tail -5)
	if ! echo "$out" | grep -q "soaktest: PASS"; then
		echo "$out"
		fail "[$leg] soak"
	fi

	perf_check "$leg" LazyColumn
	perf_check "$leg" Tabs
}

# ============
#  DRIFT-CHECK first (cheap, cross-platform): the legs must build from a vendor tree
#  that matches the pinned refs, with no manual vendor lagging the pin.
note "drift-check: vendor-clean (sync.py + diff src/vendor)"
python3 scripts/compose-fork/check-vendor-clean.py || fail "drift-check: src/vendor drifted from the pinned refs"
note "drift-check: manual-vendor provenance"
python3 scripts/compose-fork/check-vendor-drift.py || fail "drift-check: a manual vendor lags the pinned ref"

run_leg skia
run_leg sdl3

# ============
#  Summary
echo
if [ ${#FAILURES[@]} -gt 0 ]; then
	echo "verify-mac: FAILED (${#FAILURES[@]}):"
	printf '  - %s\n' "${FAILURES[@]}"
	exit 1
fi
echo "verify-mac: ALL GREEN (both legs: build + probes + parity + perf)"
exit 0
