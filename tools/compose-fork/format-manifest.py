#!/usr/bin/env python3
# Canonicalize a per-module `<module>/compose-fork.txt` manifest in place.
# Each manifest lives alongside its Gradle module's `build.gradle.kts` and lists
# upstream files vendored into that specific module. Dest paths are relative to
# the manifest's own directory.
#
# The rewrite groups entries by androidx package (hierarchy / alphabetical) under
# a "# ── <package> ──" header, VENDORED (uncommented) entries first then
# NOT-YET-VENDORED (commented) below, dest columns aligned. Dedups by dest with
# last-active-wins.
#
# Idempotent: running on an already-canonical manifest changes nothing. Purely a
# re-layout — the set of active upstream→dest pairs (what sync.sh consumes) is
# preserved, so the vendored tree is byte-identical.
#
# Usage:
#   python3 format-manifest.py --manifest core/compose-fork.txt                 # rewrite
#   python3 format-manifest.py --manifest core/compose-fork.txt --check         # exit 1 if not canonical
#   python3 format-manifest.py --manifest core/compose-fork.txt --discover PATH # + discover new
#   python3 format-manifest.py                                                  # rewrite ALL <module>/compose-fork.txt
#
# --discover scans the upstream clone for every .kt in any upstream module already
# referenced by this manifest, plus every module the manifest could conceivably
# grow to cover (anything already listed under compose/<area>/<module>/). New
# .kt files that aren't yet in the manifest are appended as commented candidates
# with a best-guess dest. sync.sh runs `--discover $CMP_REF` per-manifest.

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))

# Manifest entry: optional `#` + upstream path + dest path (relative to module dir).
ENTRY = re.compile(r'^(#\s*)?(compose/\S+\.kt)\s+(\S*src/vendor/\S+\.kt)\s*$')
# Path→package: `androidx/x/y/File.kt` → `androidx.x.y`
PKG = re.compile(r'src/vendor/[^/]+/kotlin/(.+)/[^/]+\.kt$')
# Upstream module: `compose/<area>/<module>/...`
MODULE_RE = re.compile(r'^compose/([a-z0-9-]+)/([a-z0-9-]+)/')

# Upstream source sets we mirror (native world). JVM/Android/JS/test are skipped.
NATIVE_SETS = {'nonJvmMain', 'nativeMain', 'nonAndroidMain', 'skikoMain'}
ALLOWED_SETS = {'commonMain'} | NATIVE_SETS
SRC_RE = re.compile(r'/src/([A-Za-z0-9]+)/kotlin/(androidx/.+\.kt)$')
# Platform-suffix tags stripped from a native dest filename before adding `.native`.
TAGS = ('nonJvm', 'skiko', 'nonAndroid', 'native')

# Non-runtime packages we never mirror (test framework, preview tooling).
EXCLUDE_PKGS = ('androidx.compose.ui.test', 'androidx.compose.ui.tooling')


def header_for(module):
	return [
		'# Files vendored VERBATIM from JetBrains/compose-multiplatform-core into :{}.'.format(module),
		'# Re-sync with: tools/compose-fork/sync.sh :{}   (or `sync.sh` for every module).'.format(module),
		'# Upstream ref pinned in tools/compose-fork/compose-ref.txt.',
		'#',
		'# Format:  <upstream-path-under-clone>  <dest-relative-to-this-module>',
		'# Grouped by androidx package (alphabetical). Uncommented = vendored, commented =',
		'# candidate. Never hand-edit the vendored files — change this manifest / the ref',
		'# and re-run sync.sh.',
		'',
	]


def package_of(dest):
	m = PKG.search(dest)
	return m.group(1).replace('/', '.') if m else '(root)'


def section_header(pkg):
	pre = '# ── %s ' % pkg
	return pre + '─' * max(3, 78 - len(pre))


def is_excluded(dest):
	p = package_of(dest)
	return any(p == e or p.startswith(e + '.') for e in EXCLUDE_PKGS)


def upstream_to_dest(up):
	"""Best-guess dest for an upstream path, following the manifest convention:
	commonMain -> src/vendor/common (name unchanged); native-family -> src/vendor/native
	with the filename's platform tag normalized to .native.kt. Returns None if the
	source set isn't one we mirror."""
	m = SRC_RE.search(up)
	if not m:
		return None
	srcset, rel = m.group(1), m.group(2)
	if srcset not in ALLOWED_SETS:
		return None
	if srcset == 'commonMain':
		return 'src/vendor/common/kotlin/' + rel
	d, fn = rel.rsplit('/', 1) if '/' in rel else ('', rel)
	base = fn[:-3]  # drop .kt
	for t in TAGS:
		if base.endswith('.' + t):
			base = base[: -(len(t) + 1)]
			break
	dest_rel = (d + '/' if d else '') + base + '.native.kt'
	return 'src/vendor/native/kotlin/' + dest_rel


def upstream_modules_in(text):
	"""Set of `compose/<area>/<module>` prefixes referenced by any line (active or
	commented) in the manifest. Used to bound `discover()` — a manifest only
	auto-grows within its already-referenced upstream modules."""
	prefixes = set()
	for line in text.splitlines():
		s = line.lstrip().lstrip('#').lstrip()
		m = MODULE_RE.match(s)
		if m:
			prefixes.add('compose/%s/%s' % (m.group(1), m.group(2)))
	return prefixes


def discover(prefixes, existing_ups, cmp_ref):
	"""Every tracked .kt under any of `prefixes` (a set of `compose/<area>/<module>`
	dir paths inside cmp_ref) in a mirrored source set that isn't already listed.
	Returns sorted list of (up, dest)."""
	found = []
	for pref in prefixes:
		root = os.path.join(cmp_ref, pref)
		if not os.path.isdir(root):
			continue
		for dirpath, _dirs, files in os.walk(root):
			if 'Test' in dirpath or '/test/' in dirpath.replace('\\', '/'):
				continue
			for f in files:
				if not f.endswith('.kt'):
					continue
				full = os.path.join(dirpath, f).replace('\\', '/')
				i = full.find('/compose/')
				if i < 0:
					continue
				up = full[i + 1:]
				if up in existing_ups:
					continue
				dest = upstream_to_dest(up)
				if dest and not is_excluded(dest):
					found.append((up, dest))
	return sorted(set(found))


def canonical(text, module):
	"""Regroup entries by androidx package, vendored-first, dest-aligned."""
	entries = {}
	for line in text.splitlines():
		m = ENTRY.match(line)
		if not m:
			continue
		up, dest, active = m.group(2), m.group(3), m.group(1) is None
		if is_excluded(dest) and not active:
			continue
		e = entries.get(dest)
		if e is None:
			entries[dest] = {'up': up, 'active': active}
		elif active:
			e['up'] = up
			e['active'] = True
		if active and is_excluded(dest):
			sys.stderr.write('warn: active entry under excluded package: %s\n' % dest)

	groups = {}
	for dest, e in entries.items():
		groups.setdefault(package_of(dest), []).append((dest, e))

	out = list(header_for(module))
	for pkg in sorted(groups):
		rows = groups[pkg]
		act = sorted((d, e) for d, e in rows if e['active'])
		com = sorted((d, e) for d, e in rows if not e['active'])
		ordered = [(d, e, False) for d, e in act] + [(d, e, True) for d, e in com]
		lefts = [('# ' if c else '') + e['up'] for d, e, c in ordered]
		w = max(len(x) for x in lefts) if lefts else 0
		out.append(section_header(pkg))
		for (d, e, c), left in zip(ordered, lefts):
			out.append('%s  %s' % (left.ljust(w), d))
		out.append('')
	return '\n'.join(out).rstrip() + '\n'


def module_name(manifest_path):
	"""Manifest at `<repo>/<a>/<b>/compose-fork.txt` → module label `a:b`
	(what you'd type as `:a:b` in Gradle)."""
	rel = os.path.relpath(os.path.dirname(os.path.abspath(manifest_path)), REPO_ROOT)
	return rel.replace(os.sep, ':') if rel != '.' else '(root)'


def run_one(path, check, cmp_ref):
	module = module_name(path)
	with open(path, encoding='utf-8') as f:
		cur = f.read()

	added = 0
	text = cur
	if cmp_ref:
		prefixes = upstream_modules_in(cur)
		existing = {m.group(2) for m in (ENTRY.match(l) for l in cur.splitlines()) if m}
		new_entries = discover(prefixes, existing, cmp_ref)
		added = len(new_entries)
		if added:
			text = cur + '\n' + '\n'.join('# %s  %s' % (up, dest) for up, dest in new_entries) + '\n'

	new = canonical(text, module)
	if new == cur:
		print('%s: already canonical%s' % (module,
			' (0 new files)' if cmp_ref else ''))
		return 0
	if check:
		sys.stderr.write('%s is NOT canonical — run format-manifest.py\n' % path)
		return 1
	with open(path, 'w', encoding='utf-8', newline='\n') as f:
		f.write(new)
	n_act = sum(1 for l in new.splitlines() if ENTRY.match(l) and not l.startswith('#'))
	print('%s canonicalized (%d active%s)' % (module, n_act,
		', +%d new' % added if added else ''))
	return 0


def discover_manifests(repo_root):
	"""Every `<module>/compose-fork.txt` under the repo, skipping build outputs."""
	results = []
	for dirpath, dirs, files in os.walk(repo_root):
		# Prune build / gradle / tool dirs
		dirs[:] = [d for d in dirs if d not in {'build', '.gradle', '.git', 'node_modules'}]
		if 'compose-fork.txt' in files:
			# Avoid the legacy tools/compose-fork/manifest.txt (has a different name anyway).
			results.append(os.path.join(dirpath, 'compose-fork.txt'))
	return sorted(results)


def main():
	args = sys.argv[1:]
	check = '--check' in args
	if check: args.remove('--check')

	cmp_ref = None
	if '--discover' in args:
		i = args.index('--discover')
		cmp_ref = args[i + 1] if i + 1 < len(args) and not args[i + 1].startswith('-') else os.environ.get('CMP_REF')
		if i + 1 < len(args) and not args[i + 1].startswith('-'):
			del args[i:i + 2]
		else:
			del args[i]
		if not cmp_ref or not os.path.isdir(cmp_ref):
			sys.stderr.write('--discover needs a valid clone path (arg or $CMP_REF)\n')
			return 2

	manifests = []
	while '--manifest' in args:
		i = args.index('--manifest')
		manifests.append(args[i + 1])
		del args[i:i + 2]
	if not manifests:
		manifests = discover_manifests(REPO_ROOT)
		if not manifests:
			sys.stderr.write('no <module>/compose-fork.txt found under %s\n' % REPO_ROOT)
			return 1

	rc = 0
	for m in manifests:
		if run_one(m, check, cmp_ref) != 0:
			rc = 1
	return rc


if __name__ == '__main__':
	raise SystemExit(main())
