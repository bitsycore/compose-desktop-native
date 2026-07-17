#!/usr/bin/env python3
# Vendor-clean check (P0.7, RENDERER_TASKS.md) - fails if src/vendor/ has drifted from
# what the pinned upstream refs produce.
#
# src/vendor/ is REGENERATED OUTPUT (gitignored): nothing hand-made may live there. A
# hand-edit that leaks in "works on my machine" but silently diverges from every clean
# checkout, and is overwritten by the next sync. This check makes that loud:
#
#   1. hash every file under each module's src/vendor/ (and snapshot each compose-fork.txt)
#   2. run sync.py (regenerates the trees byte-for-byte from the pinned clones,
#      deleting stale files and re-annotating manifests)
#   3. re-hash and diff: ANY change/add/remove = the local tree did not match the pin
#
# On failure the tree has already been RESTORED to the pinned state (that is sync.py's
# normal behaviour) - the report tells you what drifted. If the edit was intentional,
# manual-vendor it instead (CLAUDE.md vendoring rule 3: move the file out of src/vendor/,
# comment its manifest line out, re-sync). Manifest churn means the annotations were
# stale - commit the refreshed manifest.
#
# Usage (from anywhere; ASCII-only output for cp1252 consoles):
#   python scripts/compose-fork/check-vendor-clean.py
# Exit: 0 clean · 1 drift found (tree now restored) · 2 sync itself failed

import hashlib
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
PRUNE_DIRS = {'build', '.gradle', '.git', 'node_modules', 'scripts'}


# ============
#  Every module dir carrying a compose-fork.txt (same discovery rules as sync.py).
def module_dirs():
	found = []
	for dirpath, dirs, files in os.walk(REPO_ROOT):
		dirs[:] = [d for d in dirs if d not in PRUNE_DIRS]
		if 'compose-fork.txt' in files:
			found.append(dirpath)
	return sorted(found)


# ============
#  {repo-relative-path: sha1} over one directory tree (empty dict if absent).
def hash_tree(root):
	out = {}
	if not os.path.isdir(root):
		return out
	for dr, _, fs in os.walk(root):
		for fn in fs:
			p = os.path.join(dr, fn)
			with open(p, 'rb') as fh:
				digest = hashlib.sha1(fh.read()).hexdigest()
			out[os.path.relpath(p, REPO_ROOT).replace(os.sep, '/')] = digest
	return out


def read_text(path):
	with open(path, 'rb') as fh:
		return fh.read()


def main():
	modules = module_dirs()
	if not modules:
		sys.stderr.write('check-vendor-clean: no compose-fork.txt manifests found\n')
		return 2

	# ---- 1. pre-sync snapshot: vendor hashes + manifest bytes
	before = {}
	manifests_before = {}
	for mod in modules:
		before.update(hash_tree(os.path.join(mod, 'src', 'vendor')))
		mf = os.path.join(mod, 'compose-fork.txt')
		manifests_before[mf] = read_text(mf)
	print('check-vendor-clean: %d vendored files across %d modules - running sync.py'
		% (len(before), len(modules)))

	# ---- 2. regenerate from the pinned refs
	rc = subprocess.run([sys.executable, os.path.join(HERE, 'sync.py')], cwd=REPO_ROOT).returncode
	if rc != 0:
		sys.stderr.write('check-vendor-clean: sync.py failed (rc=%d) - cannot judge cleanliness\n' % rc)
		return 2

	# ---- 3. post-sync diff
	after = {}
	for mod in modules:
		after.update(hash_tree(os.path.join(mod, 'src', 'vendor')))
	changed = sorted(p for p in before.keys() & after.keys() if before[p] != after[p])
	removed = sorted(before.keys() - after.keys())
	added = sorted(after.keys() - before.keys())
	stale_manifests = sorted(
		os.path.relpath(mf, REPO_ROOT).replace(os.sep, '/')
		for mf, text in manifests_before.items() if read_text(mf) != text)

	ok = True
	if changed or removed or added:
		ok = False
		print('\ncheck-vendor-clean: FAIL - src/vendor/ did not match the pinned refs '
			'(now restored by the sync):')
		for p in changed:
			print('  changed : %s' % p)
		for p in removed:
			print('  stale   : %s (removed - no manifest entry produces it)' % p)
		for p in added:
			print('  missing : %s (recreated - was absent locally)' % p)
		print('src/vendor/ is regenerated output - never hand-edit it. Intentional edits '
			'must be MANUAL-VENDORED (move out of src/vendor/, comment the manifest line, '
			're-sync); see CLAUDE.md vendoring rule 3.')
	if stale_manifests:
		ok = False
		print('\ncheck-vendor-clean: FAIL - manifest annotations were stale (sync refreshed '
			'them; review + commit):')
		for p in stale_manifests:
			print('  manifest: %s' % p)

	if ok:
		print('check-vendor-clean: PASS - src/vendor/ matches the pinned refs '
			'(%d files) and all manifests are stable' % len(after))
		return 0
	return 1


if __name__ == '__main__':
	sys.exit(main())
