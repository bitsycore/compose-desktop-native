#!/usr/bin/env python3
# Vendor pure, platform-independent leaf files from JetBrains/compose-multiplatform-core,
# BYTE-FOR-BYTE VERBATIM (no edits, no reformatting).
#
# This is the real implementation behind `sync.sh` (which is now a thin wrapper).
# It is a straight port of the old bash script, but does the ~1500-file copy loop
# IN-PROCESS instead of spawning ~7 subprocesses (dirname/mkdir/cp/grep/grep/sed/mv)
# per file. On Windows Git Bash a subprocess spawn costs ~100ms (antivirus scans
# every spawned .exe), so the bash version took 15-25 minutes and could wedge on a
# tmp-file rename; this runs in a couple of seconds and never touches temp files.
# Pure Python + a handful of `git` calls -> works the same on macOS / Linux / Windows.
#
# Each Gradle module that vendors upstream code carries its own `compose-fork.txt`
# alongside its `build.gradle.kts`, listing upstream files vendored into that module.
# `sync.py` walks every `*/compose-fork.txt` (or just the ones you ask for), copies
# each listed upstream file to the dest path shown next to it (relative to the
# manifest's own directory).
#
# Idempotent -- re-run after bumping compose-ref.txt to re-sync. The copy is verbatim,
# so `git diff` against a fresh upstream checkout shows exactly what upstream changed.
# Provenance = manifest + compose-ref.txt -- do NOT hand-edit vendored files; change a
# manifest or the ref and re-run instead.
#
# Usage (any platform):
#   python tools/compose-fork/sync.py                              # every module with a compose-fork.txt
#   python tools/compose-fork/sync.py :compose:animation-core      # gradle path
#   python tools/compose-fork/sync.py compose/animation-core       # module path
#   python tools/compose-fork/sync.py compose/ui/compose-fork.txt  # direct path to a manifest
# Every sync ALSO re-annotates each SET_ROOT manifest in place (idempotent): under each
# folder directive, commented `#     | src -> dest` lines for the files it copies (uncomment
# one to pin it as a per-file entry), plus a trailing `# >>> GAPS` block listing every
# upstream .kt under SET_ROOT not yet vendored (so new upstream files show up commented).
#   python tools/compose-fork/sync.py --gaps navigation3/navigation3-ui  # annotate ONLY (no copy)
# Env:
#   CMP_REF=<path>   reuse/create the clone here (default ../cmp-ref)

import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
REPO_URL = 'https://github.com/JetBrains/compose-multiplatform-core'
CMP_REF = os.environ.get('CMP_REF') or os.path.normpath(os.path.join(REPO_ROOT, '..', 'cmp-ref'))

# Dirs we never descend into when auto-discovering manifests.
PRUNE_DIRS = {'build', '.gradle', '.git', 'node_modules', 'tools'}

# `compose/<area>/<module>` prefix at the start of an upstream path -- one per
# sparse-checkout dir.
MODULE_RE = re.compile(r'compose/[a-z0-9-]+/[a-z0-9-]+')

# Kotlin 2.4's K2 metadata compile rejects two upstream-Compose idioms that were fine
# in 2.3 (OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE for JvmField/JvmName/JvmStatic
# imports in commonMain, and LESS_VISIBLE_TYPE_ACCESS_IN_INLINE for the Synchronization
# helpers). We suppress both file-level on every vendored .kt so the metadata publish
# succeeds without hand-editing files (the vendor tree is regenerated and must carry no
# local edits). The marker below is what tells us a file is already suppressed.
SUPPRESS_MARKER = b'OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE'
SUPPRESS_NAMES = b'"OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE", "LESS_VISIBLE_TYPE_ACCESS_IN_INLINE"'
FILE_SUPPRESS_RE = re.compile(rb'(?m)^@file:Suppress\(')


# ============
#  Resolve one CLI arg to a manifest path. Accepts:
#    :foo:bar          -> foo/bar/compose-fork.txt
#    foo:bar           -> foo/bar/compose-fork.txt
#    foo/bar           -> foo/bar/compose-fork.txt
#    foo               -> foo/compose-fork.txt
#    foo/compose-fork.txt (or any *.txt path, absolute or relative to repo root)
def resolve_manifest(arg):
	if arg.endswith('.txt'):
		return arg if os.path.isabs(arg) else os.path.join(REPO_ROOT, arg)
	rel = arg.lstrip(':').replace(':', '/')
	return os.path.join(REPO_ROOT, rel, 'compose-fork.txt')


# ============
#  Every `<module>/compose-fork.txt` under the repo, skipping build/tool outputs.
def find_all_manifests():
	found = []
	for dirpath, dirs, files in os.walk(REPO_ROOT):
		dirs[:] = [d for d in dirs if d not in PRUNE_DIRS]
		if 'compose-fork.txt' in files:
			found.append(os.path.join(dirpath, 'compose-fork.txt'))
	return sorted(found)


# ============
#  Normalize a SET_ROOT value: strip quotes, a leading `./`, and any trailing `/`.
def norm_root(r):
	r = r.strip().strip('"').strip("'").strip()
	if r.startswith('./'):
		r = r[2:]
	return r.rstrip('/')


# ============
#  Active (uncommented) `(upstream, dest)` pairs from a manifest. Three line forms:
#    SET_ROOT=<up-base>        set an upstream base prepended to subsequent `->` entries
#    <src> -> <dest>             arrow entry; <src> is relative to the current SET_ROOT.
#                                Trailing `/` on either side ⇒ a folder directive (whole tree).
#    <up> <dest>                 legacy: two full paths, whitespace-separated (root NOT applied)
#  SET_ROOT + `->` let a manifest name a whole module/source-set once instead of listing files.
#  A leading `|` on <src> (from an uncommented `--gaps` folder-expansion line) is stripped.
def active_entries(text):
	root = ''
	for line in text.splitlines():
		s = line.strip()
		if not s or s.startswith('#'):
			continue
		m = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		if '->' in s:
			src, dest = (p.strip() for p in s.split('->', 1))
			src = src.lstrip('|').strip()
			yield (root + '/' + src if root else src), dest
			continue
		parts = s.split()
		if len(parts) >= 2:
			yield parts[0], parts[1]


# ============
#  Upstream paths (relative to CMP_REF, resolved against SET_ROOT) that a `!<src>`
#  line excludes from folder copies. Use this for MANUAL VENDORING inside a folder
#  directive: a file we copied to src/{commonMain,...} and hand-edited (e.g. NavDisplay
#  with the K/N rememberLifecycleOwner workaround) must be skipped by the folder copy so
#  the edited project copy isn't shadowed by a pristine src/vendor duplicate.
def active_exclusions(text):
	root = ''
	out = set()
	for line in text.splitlines():
		s = line.strip()
		if not s or s.startswith('#'):
			continue
		m = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		if s.startswith('!'):
			src = s[1:].strip().lstrip('|').strip()
			out.add((root + '/' + src) if root else src)
	return out


# ============
#  Union of `compose/<area>/<module>` prefixes referenced by a manifest -- ACTIVE and
#  COMMENTED alike (a leading `#` is stripped first, matching the old bash behaviour).
#  Computed across EVERY manifest so a partial sync never shrinks the sparse clone.
def sparse_prefixes(text):
	prefixes = set()
	root = ''
	for line in text.splitlines():
		s = line.strip().lstrip('#').strip()
		if not s:
			continue
		m = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		# The upstream token, resolving `->` entries against SET_ROOT.
		if '->' in s:
			src = s.split('->', 1)[0].strip().lstrip('|').strip()
			tok = root + '/' + src if root else src
		else:
			tok = s.split(None, 1)[0]
		# Any `<area>/<module>/src/...` token (per-file OR folder directive) maps to
		# that module dir — generalises beyond compose/ (e.g. navigation3/navigation3-ui);
		# the sparse dir is everything before `/src/`.
		if '/src/' in tok:
			prefixes.add(tok.split('/src/', 1)[0])
			continue
		mm = MODULE_RE.match(tok)
		if mm:
			prefixes.add(mm.group(0))
	return prefixes


# ============
#  A manifest line whose upstream OR dest path ends with `/` is a FOLDER DIRECTIVE:
#  copy every .kt under the upstream dir into the dest dir, preserving the sub-tree.
#  One line then selects a whole module / source set instead of listing each file.
def is_folder_entry(up, dest):
	return up.endswith('/') or dest.endswith('/')


# ============
#  Write one vendored file: LF-normalize .kt, inject the K2 @file:Suppress into
#  src/vendor .kt, then write the raw bytes. Returns 1 (files written).
def write_vendor_file(src, dst, dest):
	os.makedirs(os.path.dirname(dst), exist_ok=True)
	with open(src, 'rb') as fh:
		data = fh.read()
	if dest.endswith('.kt'):
		data = data.replace(b'\r\n', b'\n')
	if dest.startswith('src/vendor/') and dest.endswith('.kt'):
		data = inject_suppress(data)
	with open(dst, 'wb') as fh:
		fh.write(data)
	return 1


# ============
#  Inject the K2 @file:Suppress into vendored .kt content, byte-for-byte identical to
#  what the old script produced. Returns the (possibly rewritten) bytes.
def inject_suppress(data):
	if SUPPRESS_MARKER in data:
		return data  # already suppressed -- leave untouched
	if FILE_SUPPRESS_RE.search(data):
		# Splice our names in right after `@file:Suppress(`, keeping the rest of the line.
		return FILE_SUPPRESS_RE.sub(b'@file:Suppress(' + SUPPRESS_NAMES + b', ', data)
	# No file-level suppress yet -- prepend a fresh one.
	return b'@file:Suppress(' + SUPPRESS_NAMES + b')\n\n' + data


# ============
#  git helpers (a handful of calls total -- not per file).
def git(args, quiet=False, check=True):
	kw = {}
	if quiet:
		kw['stdout'] = subprocess.DEVNULL
		kw['stderr'] = subprocess.DEVNULL
	return subprocess.run(['git', '-C', CMP_REF, *args], check=check, **kw)


def ensure_clone(sparse_dirs, ref):
	if not os.path.isdir(os.path.join(CMP_REF, '.git')):
		print('cloning %s -> %s (%d sparse dirs)' % (REPO_URL, CMP_REF, len(sparse_dirs)))
		# Pin autocrlf/eol off on the clone so the working tree holds upstream's exact
		# LF bytes -- otherwise a machine with core.autocrlf=true (Windows default)
		# checks files out as CRLF and we'd vendor mangled line endings. We also
		# normalize on write below as a belt-and-suspenders for pre-existing clones.
		subprocess.run(['git', 'clone', '--filter=blob:none', '--no-checkout',
			'--config', 'core.autocrlf=false', '--config', 'core.eol=lf', REPO_URL, CMP_REF], check=True)
		git(['sparse-checkout', 'set', *sparse_dirs])
	else:
		# Keep an existing clone's line-ending config sane too (helps future checkouts).
		git(['config', 'core.autocrlf', 'false'], quiet=True, check=False)
		git(['config', 'core.eol', 'lf'], quiet=True, check=False)
		# Extend the sparse set in case a new manifest reaches into a new area. Noop if covered.
		git(['sparse-checkout', 'set', *sparse_dirs], quiet=True)
	if git(['checkout', '-q', ref], quiet=True, check=False).returncode != 0:
		print('ref %s not present locally -- fetching' % ref)
		git(['fetch', 'origin', ref])
		git(['checkout', '-q', ref])
	desc = subprocess.run(['git', '-C', CMP_REF, 'describe', '--tags', '--always'],
		capture_output=True, text=True).stdout.strip()
	print('upstream @ %s' % desc)


def read_ref():
	with open(os.path.join(HERE, 'compose-ref.txt'), encoding='utf-8') as f:
		for line in f:
			s = line.strip()
			if s and not s.startswith('#'):
				return s
	sys.stderr.write('no ref in compose-ref.txt\n')
	sys.exit(1)


# ==================
# MARK: --gaps -- surface upstream files under SET_ROOT that the manifest doesn't select
# ==================

# Source-set (upstream dir name) -> vendor area, matching the project's
# src/vendor/{common,native,skikoRenderer,sdlRenderer} layout. Anything not listed
# falls back to the source-set name minus "Main" (e.g. desktopMain -> desktop) so a
# non-K/N set is clearly flagged as "you probably don't want this here" in the
# commented suggestion the user then edits.
GAP_AREA = {
	'commonMain': 'common',
	'nativeMain': 'native', 'macosMain': 'native', 'iosMain': 'native',
	'linuxMain': 'native', 'mingwMain': 'native', 'darwinMain': 'native',
	'appleMain': 'native', 'unixMain': 'native', 'tvosMain': 'native', 'watchosMain': 'native',
	'skikoMain': 'skikoRenderer', 'sdlMain': 'sdlRenderer',
}
GAP_START = '# >>> ---- DIAGNOSTIC GAPS ---- <<<<'
GAP_END = '# <<< ---- DIAGNOSTIC GAPS ---- >>>>'


# Every directive line -- ACTIVE or COMMENTED -- as (upstream, dest, is_folder),
# resolving `->` against the running (active) SET_ROOT. --gaps uses this to know
# which upstream paths are already listed (active or commented) so it never re-suggests.
def iter_directives(text):
	root = ''
	for line in text.splitlines():
		s = line.strip()
		if not s:
			continue
		if s.startswith('#'):
			s = s.lstrip('#').strip()
			if not s:
				continue
			commented = True
		else:
			commented = False
		m = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
		if m:
			if not commented:  # a commented SET_ROOT is inert
				root = norm_root(m.group(1))
			continue
		if '->' in s:
			src, dest = (p.strip() for p in s.split('->', 1))
			src = src.lstrip('|').strip()
			up = root + '/' + src if root else src
			yield up, dest, (src.endswith('/') or dest.endswith('/'))
			continue
		parts = s.split()
		if len(parts) >= 2:
			yield parts[0], parts[1], (parts[0].endswith('/') or parts[1].endswith('/'))


# The active SET_ROOT of a manifest (or '' if none).
def active_root(text):
	for line in text.splitlines():
		s = line.strip()
		if s.startswith('#'):
			continue
		m = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
		if m:
			return norm_root(m.group(1))
	return ''


# Guess a vendor dest for an unlisted upstream file. `rel` is the path relative to
# SET_ROOT (e.g. desktopMain/kotlin/androidx/.../Foo.kt). Maps the source set to a
# vendor area and drops the `<srcset>/(kotlin|java)/` prefix to get the package path.
def gap_dest(rel):
	srcset = rel.split('/', 1)[0]
	area = GAP_AREA.get(srcset) or (srcset[:-4] if srcset.endswith('Main') else srcset)
	pkg = rel
	for lang in ('/kotlin/', '/java/'):
		i = rel.find(lang)
		if i != -1:
			pkg = rel[i + len(lang):]
			break
	return 'src/vendor/%s/kotlin/%s' % (area, pkg)


# An auto folder-expansion line: `#     | <src> -> <dest>` written under a folder
# directive to show a file it copies. Uncommenting one (drop the `#`) turns it into a
# real per-file `->` entry (the leading `|` is stripped by the parsers).
EXP_RE = re.compile(r'#\s*\|')
EXP_PREFIX = '#     | '


# Strip ALL auto-generated content so --gaps regenerates rather than stacks: the gaps
# block (between the markers) AND every `#     | ...` folder-expansion line. Returns the
# clean manifest lines (no trailing blanks). Contract: to vendor an auto suggestion,
# uncomment it (folder-expansion) or move it above the `# >>> GAPS` marker (gap line).
def strip_auto(text):
	out, skip = [], False
	for ln in text.splitlines():
		st = ln.strip()
		if st.startswith(GAP_START):  # case-insensitive: catches old verbose header too
			skip = True
			continue
		if skip and st.startswith(GAP_END):
			skip = False
			continue
		if skip or EXP_RE.match(st):
			continue
		out.append(ln)
	while out and out[-1].strip() == '':
		out.pop()
	return out


# The .kt a folder directive copies, as (src, dest) both relative like the directive:
# src relative to SET_ROOT (the `->` left side), dest the mapped vendor path. Sorted.
def folder_files(up_folder, src_rel, dest_folder):
	fr = os.path.join(CMP_REF, *up_folder.rstrip('/').split('/'))
	out = []
	if os.path.isdir(fr):
		for dr, _, fs in os.walk(fr):
			for fn in fs:
				if not fn.endswith('.kt'):
					continue
				sub = os.path.relpath(os.path.join(dr, fn), fr).replace(os.sep, '/')
				out.append((src_rel.rstrip('/') + '/' + sub, dest_folder.rstrip('/') + '/' + sub))
	return sorted(out)


# --gaps / --fill: annotate each SET_ROOT manifest in place, idempotently:
#   1. under every ACTIVE folder directive, commented `#     | src -> dest` lines for the
#      files it copies -- shows what the folder expands to, and uncommenting one converts
#      it to a per-file entry (turn a folder into a file-by-file listing incrementally).
#   2. a trailing block of commented `src -> dest` for every upstream .kt under SET_ROOT
#      that NO directive lists -- surfaces source sets / files not yet vendored.
def report_gaps(manifests, quiet_skips=False):
	total = 0
	for m in manifests:
		with open(m, encoding='utf-8') as f:
			text = f.read()
		root = active_root(text)
		label = os.path.relpath(os.path.dirname(m), REPO_ROOT).replace(os.sep, '/')
		if not root:
			# Legacy (non-SET_ROOT) manifests are reorganized by format-manifest.py instead.
			if not quiet_skips:
				print('  %s: no SET_ROOT -- --gaps only applies to folder-style manifests' % label)
			continue
		root_abs = os.path.join(CMP_REF, *root.split('/'))
		if not os.path.isdir(root_abs):
			sys.stderr.write('  %s: SET_ROOT %r not in the clone (re-run a normal sync first)\n' % (label, root))
			continue

		# Rebuild the body from the clean lines, inserting expansions under each active
		# folder directive; track which upstream files end up covered (folder-expanded or
		# listed single files) so the trailing gaps block only shows the truly-unlisted.
		clean = strip_auto(text)
		body, known, cur_root, expansions = [], set(), '', 0
		for ln in clean:
			body.append(ln)
			s = ln.strip()
			if not s or s.startswith('#'):
				continue
			mm = re.match(r'SET_ROOT\s*=\s*(.+)$', s)
			if mm:
				cur_root = norm_root(mm.group(1))
				continue
			if '->' in s:
				src, dest = (p.strip() for p in s.split('->', 1))
				src = src.lstrip('|').strip()
				if src.endswith('/') or dest.endswith('/'):
					up_folder = (cur_root + '/' + src) if cur_root else src
					for fsrc, fdest in folder_files(up_folder, src, dest):
						body.append('%s%s -> %s' % (EXP_PREFIX, fsrc, fdest))
						known.add((cur_root + '/' + fsrc) if cur_root else fsrc)
						expansions += 1
				else:
					known.add((cur_root + '/' + src) if cur_root else src)
			else:
				parts = s.split()
				if len(parts) >= 2:
					known.add(parts[0])

		# Walk the whole module src; collect unlisted .kt, grouped by source set.
		by_set = {}
		for dr, _, fs in os.walk(root_abs):
			rel_dir = os.path.relpath(dr, CMP_REF).replace(os.sep, '/')
			after = rel_dir[len(root) + 1:] if rel_dir != root else ''
			srcset = after.split('/', 1)[0] if after else ''
			if 'test' in srcset.lower():  # androidDeviceTest, commonTest, jvmTest, ...
				continue
			for fn in sorted(fs):
				if not fn.endswith('.kt'):
					continue
				up = rel_dir + '/' + fn
				if up in known:
					continue
				rel = up[len(root) + 1:]  # relative to SET_ROOT -> the `src` half of a `->` line
				by_set.setdefault(srcset or '(root)', []).append(rel)

		# Gap lines share the folder-expansion format (`#     | src -> dest`) so uncommenting
		# one yields a per-file entry exactly like an expansion; grouped by source set.
		gap_body, count = [], 0
		for srcset in sorted(by_set):
			rels = sorted(by_set[srcset])
			gap_body.append('#   %s (%d)' % (srcset, len(rels)))
			for rel in rels:
				gap_body.append('%s%s -> %s' % (EXP_PREFIX, rel, gap_dest(rel)))
				count += 1
		if not count:
			gap_body.append('#   (none — every source set under SET_ROOT is vendored)')

		# The GAPS block is ALWAYS emitted (a stable trailing section), even when empty.
		new = '\n'.join(body).rstrip('\n') + '\n\n' + GAP_START + '\n' + '\n'.join(gap_body) + '\n' + GAP_END + '\n'
		if new != text:
			with open(m, 'w', encoding='utf-8', newline='\n') as f:
				f.write(new)
		print('  %s: %d folder-expansion line(s), %d unlisted .kt across %d source set(s)'
			% (label, expansions, count, len(by_set)))
		total += count
	if not quiet_skips:
		print('gaps: %d unlisted upstream .kt reported (commented, edit/uncomment to vendor)' % total)


def main():
	args = sys.argv[1:]
	ref = read_ref()

	# --gaps: don't sync -- instead append a regenerated block of commented `src -> dest`
	# lines for upstream .kt under each manifest's SET_ROOT that it doesn't yet list.
	gaps = '--gaps' in args or '--fill' in args
	args = [a for a in args if a not in ('--gaps', '--fill')]

	# ---- select manifests (all, or the ones named on the CLI)
	if args:
		manifests = []
		for arg in args:
			f = resolve_manifest(arg)
			if not os.path.isfile(f):
				sys.stderr.write('no such manifest: %s\n  (from arg %r)\n' % (f, arg))
				sys.exit(1)
			manifests.append(f)
	else:
		manifests = find_all_manifests()
		if not manifests:
			sys.stderr.write('no <module>/compose-fork.txt found under %s\n' % REPO_ROOT)
			sys.exit(1)

	# ---- sparse set = union across ALL manifests (partial sync must not shrink the clone)
	sparse = set()
	for m in find_all_manifests():
		with open(m, encoding='utf-8') as f:
			sparse |= sparse_prefixes(f.read())
	sparse_dirs = sorted(sparse)

	# ---- 1. sparse clone at the pinned ref
	ensure_clone(sparse_dirs, ref)

	# ---- --gaps mode: report unlisted upstream files into the manifest(s), then stop.
	if gaps:
		report_gaps(manifests)
		return

	# ---- 1.5 canonicalize + discover each selected manifest (non-fatal). Skipped for
	#      manifests written in the SET_ROOT / `->` folder style — format-manifest.py
	#      is per-file + compose/-specific and would drop those directives.
	fmt = os.path.join(HERE, 'format-manifest.py')
	if os.path.isfile(fmt):
		for m in manifests:
			with open(m, encoding='utf-8') as f:
				mtext = f.read()
			if 'SET_ROOT' in mtext or '->' in mtext:
				continue
			if subprocess.run([sys.executable, fmt, '--discover', CMP_REF, '--manifest', m]).returncode != 0:
				sys.stderr.write('warn: format-manifest.py failed on %s -- continuing\n' % m)

	# ---- 2. copy each manifest's entries verbatim (dest relative to the manifest's dir)
	total = 0
	for m in manifests:
		module_dir = os.path.dirname(m)
		label = os.path.relpath(module_dir, REPO_ROOT).replace(os.sep, '/')
		count = 0
		with open(m, encoding='utf-8') as f:
			text = f.read()
		excluded = active_exclusions(text)
		skipped = 0
		written = set()  # normalized abs dest paths THIS manifest produced
		for up, dest in active_entries(text):
			if is_folder_entry(up, dest):
				# Folder directive: copy every .kt under the upstream dir into dest/,
				# preserving the sub-tree. LF-normalize + K2-suppress happen per file.
				src_root = os.path.join(CMP_REF, *up.rstrip('/').split('/'))
				if not os.path.isdir(src_root):
					sys.stderr.write('MISSING upstream folder: %s\n' % up)
					sys.exit(1)
				for droot, _, files in os.walk(src_root):
					for fn in sorted(files):
						if not fn.endswith('.kt'):
							continue
						sp = os.path.join(droot, fn)
						up_path = os.path.relpath(sp, CMP_REF).replace(os.sep, '/')
						rel = os.path.relpath(sp, src_root).replace(os.sep, '/')
						rdest = dest.rstrip('/') + '/' + rel
						dst = os.path.join(module_dir, *rdest.split('/'))
						if up_path in excluded:  # manually vendored in src/{commonMain,...}; don't duplicate
							skipped += 1
							if os.path.isfile(dst):
								os.remove(dst)  # drop any stale copy from a pre-exclusion sync
							continue
						count += write_vendor_file(sp, dst, rdest)
						written.add(os.path.normcase(os.path.abspath(dst)))
				continue
			src = os.path.join(CMP_REF, *up.split('/'))
			if not os.path.isfile(src):
				sys.stderr.write('MISSING upstream file: %s\n' % up)
				sys.exit(1)
			dst = os.path.join(module_dir, *dest.split('/'))
			count += write_vendor_file(src, dst, dest)
			written.add(os.path.normcase(os.path.abspath(dst)))

		# ---- 2.5 clean stale vendor files: any .kt under src/vendor/ that THIS sync
		#      didn't write is a leftover from a renamed/dropped manifest entry (e.g.
		#      ArcSpline.native.kt lingering after the dest moved to ArcSpline.nonJvm.kt)
		#      and shadows/conflicts with the current tree. The vendor dir is fully
		#      regenerated output — nothing hand-made lives there — so deleting is safe.
		#      Non-.kt files (README etc.) are kept; emptied directories are pruned.
		removed = 0
		vendor_root = os.path.join(module_dir, 'src', 'vendor')
		if os.path.isdir(vendor_root):
			for droot, _, files in os.walk(vendor_root, topdown=False):
				for fn in files:
					if not fn.endswith('.kt'):
						continue
					p = os.path.join(droot, fn)
					if os.path.normcase(os.path.abspath(p)) not in written:
						os.remove(p)
						removed += 1
				if not os.listdir(droot):
					os.rmdir(droot)
		extras = ''
		if skipped:
			extras += ' (%d excluded -> manually vendored)' % skipped
		if removed:
			extras += ' (%d stale removed)' % removed
		print('  %s: %d files%s' % (label, count, extras))
		total += count

	# ---- 3. re-annotate SET_ROOT manifests in place so each always reflects the current
	#      upstream tree: commented `#     | src -> dest` lines for the files each folder
	#      directive copies, plus a `# >>> GAPS` block listing any new/unvendored .kt. Legacy
	#      (non-SET_ROOT) manifests are reorganized by step 1.5's format-manifest.py instead.
	report_gaps(manifests, quiet_skips=True)

	print('synced %d files verbatim at %s' % (total, ref))


if __name__ == '__main__':
	main()
