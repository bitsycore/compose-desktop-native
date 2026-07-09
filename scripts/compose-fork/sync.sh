#!/usr/bin/env bash
# Thin wrapper -- the real implementation now lives in sync.py (cross-platform, and
# ~1000x fewer subprocess spawns, so it doesn't crawl / wedge on Windows Git Bash).
# Kept so `sh scripts/compose-fork/sync.sh [...]` and every existing doc/command still
# work. You can also just run the Python directly on any platform:
#   python scripts/compose-fork/sync.py [args]
#
# See sync.py for full usage. CMP_REF is honoured (inherited by the child process).
set -euo pipefail

kHere="$(cd "$(dirname "$0")" && pwd)"
kPy="$(command -v python3 || command -v python || true)"
if [ -z "$kPy" ]; then
	echo "python3 (or python) not found on PATH" >&2
	exit 1
fi
exec "$kPy" "$kHere/sync.py" "$@"
