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
# Idempotent -- re-run after bumping compose.properties to re-sync. The copy is verbatim,
# so `git diff` against a fresh upstream checkout shows exactly what upstream changed.
# Provenance = manifest + compose.properties -- do NOT hand-edit vendored files; change a
# manifest or the ref and re-run instead.
#
# Usage (any platform):
#   python scripts/compose-fork/sync.py                              # every module with a compose-fork.txt
#   python scripts/compose-fork/sync.py :compose:animation-core      # gradle path
#   python scripts/compose-fork/sync.py compose/animation-core       # module path
#   python scripts/compose-fork/sync.py compose/ui/compose-fork.txt  # direct path to a manifest
# Every sync ALSO re-annotates each SET_FOLDER manifest in place (idempotent): under each
# folder directive, commented `#     | src -> dest` lines for the files it copies (uncomment
# one to pin it as a per-file entry), plus a trailing `# >>> GAPS` block listing every
# upstream .kt under SET_FOLDER not yet vendored (so new upstream files show up commented).
#   python scripts/compose-fork/sync.py --gaps navigation3/navigation3-ui  # annotate ONLY (no copy)
#
# REPO + PINNED REF: every manifest declares its upstream repo up top with
#   SET_REPO=<https-url>@<ref>
# before its SET_FOLDER. The <ref> is either a literal git ref OR a <VARNAME> looked
# up in compose.properties, which holds `NAME=value` variable declarations -- the
# pinned refs, version-tagged in ONE place. e.g. compose.properties:
#   COMPOSE_CORE_REF=1be9d64...     # JetBrains/compose-multiplatform-core (the hash)
#   COMPOSE_REF=v1.12.0-beta01      # JetBrains/compose-multiplatform (umbrella, a tag)
# and a manifest header:
#   SET_REPO=https://github.com/JetBrains/compose-multiplatform-core@<COMPOSE_CORE_REF>
# There is NO implicit default repo -- SET_REPO is REQUIRED before any entry (ssh
# URLs unsupported; use https). Each distinct repo gets its own sibling sparse
# clone: ../cmp-ref-<repo-name> (compose-multiplatform-core keeps ../cmp-ref).
# Env:
#   CMP_REF=<path>          reuse/create the compose-multiplatform-core clone (default ../cmp-ref)
#   CMP_REF_<REPO_NAME>=<path>  override another repo's clone dir (name upper-cased,
#                               non-alphanumerics -> '_', e.g. CMP_REF_COMPOSE_MULTIPLATFORM)

import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
REPO_URL = 'https://github.com/JetBrains/compose-multiplatform-core'
CMP_REF = os.environ.get('CMP_REF') or os.path.normpath(os.path.join(REPO_ROOT, '..', 'cmp-ref'))

# A `<NAME>` reference to a compose.properties variable.
VAR_RE = re.compile(r'<([A-Za-z_][A-Za-z0-9_]*)>')


# ============
#  compose.properties is a set of `NAME=value` variable declarations -- the pinned
#  upstream refs, tagged in one place. Manifests reference them as <NAME> inside
#  SET_REPO. Returns {NAME: value}. (Replaces the old "just a bare ref" format.)
def read_variables():
	path = os.path.join(HERE, 'compose.properties')
	out = {}
	with open(path, encoding='utf-8') as f:
		for n, line in enumerate(f, 1):
			s = line.strip()
			if not s or s.startswith('#'):
				continue
			if '=' not in s:
				sys.stderr.write('%s:%d: expected NAME=value (compose.properties is variable '
					'declarations, not a bare ref): %r\n' % (path, n, s))
				sys.exit(1)
			name, val = (p.strip() for p in s.split('=', 1))
			if not VAR_RE.fullmatch('<' + name + '>'):
				sys.stderr.write('%s:%d: invalid variable name %r\n' % (path, n, name))
				sys.exit(1)
			out[name] = val
	if not out:
		sys.stderr.write('no variables declared in compose.properties (need e.g. COMPOSE_CORE_REF=<hash>)\n')
		sys.exit(1)
	return out


# ============
#  Replace every `<NAME>` in `s` with variables[NAME]; hard-error on an undefined name.
def substitute(s, variables):
	def repl(m):
		name = m.group(1)
		if name not in variables:
			sys.stderr.write('undefined variable <%s> -- declare it in compose.properties\n' % name)
			sys.exit(1)
		return variables[name]
	return VAR_RE.sub(repl, s)


# ============
#  Clone dir for a repo url: compose-multiplatform-core keeps CMP_REF; every other
#  repo gets a sibling `../cmp-ref-<name>` (overridable via CMP_REF_<NAME>).
def repo_clone_dir(url):
	if url == REPO_URL:
		return CMP_REF
	name = re.sub(r'\.git$', '', url.rstrip('/').split('/')[-1])
	env = os.environ.get('CMP_REF_' + re.sub(r'[^A-Za-z0-9]+', '_', name).upper())
	return env or os.path.normpath(os.path.join(REPO_ROOT, '..', 'cmp-ref-' + name))


# ============
#  Parse a SET_REPO value: `<https-url>@<ref>` where <ref> may be a `<VARNAME>`
#  resolved from compose.properties. The ref is REQUIRED (ssh URLs unsupported).
def parse_set_repo(val, variables):
	v = substitute(val.strip().strip('"').strip("'").strip(), variables)
	url, sep, ref = v.rpartition('@')
	if not sep or not url or not ref:
		sys.stderr.write("SET_REPO must be '<https-url>@<ref-or-VARNAME>': %r\n" % v)
		sys.exit(1)
	return url.rstrip('/'), ref


#  Error out: a directive appeared before the manifest declared its repo.
def _need_repo(what):
	sys.stderr.write('%s before any SET_REPO -- every manifest must declare '
		'SET_REPO=<url>@<ref> first\n' % what)
	sys.exit(1)


# Dirs we never descend into when auto-discovering manifests.
PRUNE_DIRS = {'build', '.gradle', '.git', 'node_modules', 'scripts'}

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
#  Normalize a SET_FOLDER value: strip quotes, a leading `./`, and any trailing `/`.
def norm_root(r):
	r = r.strip().strip('"').strip("'").strip()
	if r.startswith('./'):
		r = r[2:]
	return r.rstrip('/')


# ============
#  Active (uncommented) `(repo, upstream, dest)` triples from a manifest. Line forms:
#    SET_REPO=<url>@<ref>      the upstream REPO for subsequent entries (REQUIRED first;
#                              <ref> may be a <VARNAME> from compose.properties)
#    SET_FOLDER=<up-base>        set an upstream base prepended to subsequent `->` entries
#    <src> -> <dest>             arrow entry; <src> is relative to the current SET_FOLDER.
#                                Trailing `/` on either side ⇒ a folder directive (whole tree).
#    <up> <dest>                 legacy: two full paths, whitespace-separated (root NOT applied)
#  SET_FOLDER + `->` let a manifest name a whole module/source-set once instead of listing files.
#  A leading `|` on <src> (from an uncommented `--gaps` folder-expansion line) is stripped.
#  `repo` is an (url, ref) tuple set by SET_REPO -- there is no implicit default.
def active_entries(text, variables):
	root = ''
	repo = None
	for line in text.splitlines():
		s = line.strip()
		if not s or s.startswith('#'):
			continue
		m = re.match(r'SET_REPO\s*=\s*(.+)$', s)
		if m:
			repo = parse_set_repo(m.group(1), variables)
			continue
		m = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		if repo is None:
			_need_repo('entry %r' % s)
		if '->' in s:
			src, dest = (p.strip() for p in s.split('->', 1))
			src = src.lstrip('|').strip()
			yield repo, (root + '/' + src if root else src), dest
			continue
		parts = s.split()
		if len(parts) >= 2:
			yield repo, parts[0], parts[1]


# ============
#  Upstream (repo, path) pairs (resolved against SET_REPO/SET_FOLDER) that a `!<src>`
#  line excludes from folder copies. Use this for MANUAL VENDORING inside a folder
#  directive: a file we copied to src/{commonMain,...} and hand-edited (e.g. NavDisplay
#  with the K/N rememberLifecycleOwner workaround) must be skipped by the folder copy so
#  the edited project copy isn't shadowed by a pristine src/vendor duplicate.
def active_exclusions(text, variables):
	root = ''
	repo = None
	out = set()
	for line in text.splitlines():
		s = line.strip()
		if not s or s.startswith('#'):
			continue
		m = re.match(r'SET_REPO\s*=\s*(.+)$', s)
		if m:
			repo = parse_set_repo(m.group(1), variables)
			continue
		m = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		if s.startswith('!'):
			if repo is None:
				_need_repo('exclusion %r' % s)
			src = s[1:].strip().lstrip('|').strip()
			out.add((repo, (root + '/' + src) if root else src))
	return out


# ============
#  Per-repo union of `compose/<area>/<module>` prefixes referenced by a manifest --
#  ACTIVE and COMMENTED alike (a leading `#` is stripped first, matching the old bash
#  behaviour). Computed across EVERY manifest so a partial sync never shrinks a clone.
#  Returns {repo: set(prefixes)}.
def sparse_prefixes(text, variables):
	prefixes = {}
	root = ''
	repo = None
	for line in text.splitlines():
		s = line.strip().lstrip('#').strip()
		if not s:
			continue
		m = re.match(r'SET_REPO\s*=\s*(.+)$', s)
		if m:
			repo = parse_set_repo(m.group(1), variables)
			continue
		m = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
		if m:
			root = norm_root(m.group(1))
			continue
		# The upstream token, resolving `->` entries against SET_FOLDER.
		if '->' in s:
			src = s.split('->', 1)[0].strip().lstrip('|').strip()
			tok = root + '/' + src if root else src
		else:
			tok = s.split(None, 1)[0]
		if repo is None:
			continue  # a commented preamble line before SET_REPO contributes nothing
		# Any `<area>/<module>/src/...` token (per-file OR folder directive) maps to
		# that module dir — generalises beyond compose/ (e.g. navigation3/navigation3-ui);
		# the sparse dir is everything before `/src/`.
		if '/src/' in tok:
			prefixes.setdefault(repo, set()).add(tok.split('/src/', 1)[0])
			continue
		mm = MODULE_RE.match(tok)
		if mm:
			prefixes.setdefault(repo, set()).add(mm.group(0))
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
def git(clone_dir, args, quiet=False, check=True):
	kw = {}
	if quiet:
		kw['stdout'] = subprocess.DEVNULL
		kw['stderr'] = subprocess.DEVNULL
	return subprocess.run(['git', '-C', clone_dir, *args], check=check, **kw)


def ensure_clone(url, ref, sparse_dirs):
	clone_dir = repo_clone_dir(url)
	if not os.path.isdir(os.path.join(clone_dir, '.git')):
		print('cloning %s -> %s (%d sparse dirs)' % (url, clone_dir, len(sparse_dirs)))
		# Pin autocrlf/eol off on the clone so the working tree holds upstream's exact
		# LF bytes -- otherwise a machine with core.autocrlf=true (Windows default)
		# checks files out as CRLF and we'd vendor mangled line endings. We also
		# normalize on write below as a belt-and-suspenders for pre-existing clones.
		subprocess.run(['git', 'clone', '--filter=blob:none', '--no-checkout',
			'--config', 'core.autocrlf=false', '--config', 'core.eol=lf', url, clone_dir], check=True)
		git(clone_dir, ['sparse-checkout', 'set', *sparse_dirs])
	else:
		# Keep an existing clone's line-ending config sane too (helps future checkouts).
		git(clone_dir, ['config', 'core.autocrlf', 'false'], quiet=True, check=False)
		git(clone_dir, ['config', 'core.eol', 'lf'], quiet=True, check=False)
		# Extend the sparse set in case a new manifest reaches into a new area. Noop if covered.
		git(clone_dir, ['sparse-checkout', 'set', *sparse_dirs], quiet=True)
	if git(clone_dir, ['checkout', '-q', ref], quiet=True, check=False).returncode != 0:
		print('ref %s not present locally -- fetching' % ref)
		# Try as a TAG first, mapping it to a local tag ref -- a plain `fetch origin <tag>`
		# only lands in FETCH_HEAD, so the checkout below would fail and every future sync
		# would need the network again. Branches / bare SHAs fall back to the plain fetch.
		if git(clone_dir, ['fetch', 'origin', 'refs/tags/%s:refs/tags/%s' % (ref, ref)],
				quiet=True, check=False).returncode != 0:
			git(clone_dir, ['fetch', 'origin', ref])
		if git(clone_dir, ['checkout', '-q', ref], quiet=True, check=False).returncode != 0:
			git(clone_dir, ['checkout', '-q', 'FETCH_HEAD'])
	desc = subprocess.run(['git', '-C', clone_dir, 'describe', '--tags', '--always'],
		capture_output=True, text=True).stdout.strip()
	print('upstream %s @ %s' % (url.rstrip('/').split('/')[-1], desc))


# ==================
# MARK: --gaps -- surface upstream files under SET_FOLDER that the manifest doesn't select
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
# resolving `->` against the running (active) SET_FOLDER. --gaps uses this to know
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
		if re.match(r'SET_REPO\s*=', s):
			continue  # repo switches don't contribute paths here
		m = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
		if m:
			if not commented:  # a commented SET_FOLDER is inert
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


# The manifest's active (repo, SET_FOLDER) — the repo in effect at the FIRST active
# SET_FOLDER (root '' if none). --gaps walks that root inside that repo's clone.
def active_repo_and_root(text, variables):
	repo = None
	for line in text.splitlines():
		s = line.strip()
		if s.startswith('#'):
			continue
		m = re.match(r'SET_REPO\s*=\s*(.+)$', s)
		if m:
			repo = parse_set_repo(m.group(1), variables)
			continue
		m = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
		if m:
			if repo is None:
				_need_repo('SET_FOLDER')
			return repo, norm_root(m.group(1))
	return repo, ''


# Guess a vendor dest for an unlisted upstream file. `rel` is the path relative to
# SET_FOLDER (e.g. desktopMain/kotlin/androidx/.../Foo.kt). Maps the source set to a
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
# src relative to SET_FOLDER (the `->` left side), dest the mapped vendor path. Sorted.
def folder_files(clone_dir, up_folder, src_rel, dest_folder):
	fr = os.path.join(clone_dir, *up_folder.rstrip('/').split('/'))
	out = []
	if os.path.isdir(fr):
		for dr, _, fs in os.walk(fr):
			for fn in fs:
				if not fn.endswith('.kt'):
					continue
				sub = os.path.relpath(os.path.join(dr, fn), fr).replace(os.sep, '/')
				out.append((src_rel.rstrip('/') + '/' + sub, dest_folder.rstrip('/') + '/' + sub))
	return sorted(out)


# --gaps / --fill: annotate each SET_FOLDER manifest in place, idempotently:
#   1. under every ACTIVE folder directive, commented `#     | src -> dest` lines for the
#      files it copies -- shows what the folder expands to, and uncommenting one converts
#      it to a per-file entry (turn a folder into a file-by-file listing incrementally).
#   2. a trailing block of commented `src -> dest` for every upstream .kt under SET_FOLDER
#      that NO directive lists -- surfaces source sets / files not yet vendored.
def report_gaps(manifests, variables, quiet_skips=False):
	total = 0
	for m in manifests:
		with open(m, encoding='utf-8') as f:
			text = f.read()
		repo, root = active_repo_and_root(text, variables)
		label = os.path.relpath(os.path.dirname(m), REPO_ROOT).replace(os.sep, '/')
		if not root:
			# Legacy (non-SET_FOLDER) manifests are reorganized by format-manifest.py instead.
			if not quiet_skips:
				print('  %s: no SET_FOLDER -- --gaps only applies to folder-style manifests' % label)
			continue
		clone_dir = repo_clone_dir(repo[0])
		root_abs = os.path.join(clone_dir, *root.split('/'))
		if not os.path.isdir(root_abs):
			sys.stderr.write('  %s: SET_FOLDER %r not in the clone (re-run a normal sync first)\n' % (label, root))
			continue

		# Rebuild the body from the clean lines, inserting expansions under each active
		# folder directive; track which upstream files end up covered (folder-expanded or
		# listed single files) so the trailing gaps block only shows the truly-unlisted.
		# cur_dir follows SET_REPO switches so folder expansions read the right clone.
		clean = strip_auto(text)
		body, known, cur_root, cur_dir, expansions = [], set(), '', None, 0
		for ln in clean:
			body.append(ln)
			s = ln.strip()
			if not s or s.startswith('#'):
				continue
			mm = re.match(r'SET_REPO\s*=\s*(.+)$', s)
			if mm:
				cur_dir = repo_clone_dir(parse_set_repo(mm.group(1), variables)[0])
				continue
			mm = re.match(r'SET_FOLDER\s*=\s*(.+)$', s)
			if mm:
				cur_root = norm_root(mm.group(1))
				continue
			if '->' in s:
				src, dest = (p.strip() for p in s.split('->', 1))
				src = src.lstrip('|').strip()
				if src.endswith('/') or dest.endswith('/'):
					up_folder = (cur_root + '/' + src) if cur_root else src
					for fsrc, fdest in folder_files(cur_dir, up_folder, src, dest):
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
			rel_dir = os.path.relpath(dr, clone_dir).replace(os.sep, '/')
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
				rel = up[len(root) + 1:]  # relative to SET_FOLDER -> the `src` half of a `->` line
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
			gap_body.append('#   (none — every source set under SET_FOLDER is vendored)')

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
	variables = read_variables()

	# --gaps: don't sync -- instead append a regenerated block of commented `src -> dest`
	# lines for upstream .kt under each manifest's SET_FOLDER that it doesn't yet list.
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

	# ---- per-repo sparse sets = union across ALL manifests (partial sync must not
	#      shrink a clone). Two manifests pinning the SAME url at DIFFERENT refs
	#      would fight over one clone dir -> hard error.
	repo_sparse = {}
	for m in find_all_manifests():
		with open(m, encoding='utf-8') as f:
			for repo, prefixes in sparse_prefixes(f.read(), variables).items():
				repo_sparse.setdefault(repo, set()).update(prefixes)
	by_url = {}
	for (url, ref) in repo_sparse:
		if url in by_url and by_url[url] != ref:
			sys.stderr.write('conflicting refs for %s: %s vs %s (one clone per url)\n'
				% (url, by_url[url], ref))
			sys.exit(1)
		by_url[url] = ref

	# ---- 1. sparse clone(s) at the pinned ref(s)
	for (url, ref), prefixes in sorted(repo_sparse.items()):
		ensure_clone(url, ref, sorted(prefixes))

	# ---- --gaps mode: report unlisted upstream files into the manifest(s), then stop.
	if gaps:
		report_gaps(manifests, variables)
		return

	# ---- 1.5 canonicalize + discover each selected manifest (non-fatal). Skipped for
	#      manifests written in the SET_FOLDER / `->` folder style — format-manifest.py
	#      is per-file + compose/-specific and would drop those directives.
	fmt = os.path.join(HERE, 'format-manifest.py')
	if os.path.isfile(fmt):
		for m in manifests:
			with open(m, encoding='utf-8') as f:
				mtext = f.read()
			if 'SET_FOLDER' in mtext or '->' in mtext:
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
		excluded = active_exclusions(text, variables)
		skipped = 0
		written = set()  # normalized abs dest paths THIS manifest produced
		for repo, up, dest in active_entries(text, variables):
			clone_dir = repo_clone_dir(repo[0])
			if is_folder_entry(up, dest):
				# Folder directive: copy every .kt under the upstream dir into dest/,
				# preserving the sub-tree. LF-normalize + K2-suppress happen per file.
				src_root = os.path.join(clone_dir, *up.rstrip('/').split('/'))
				if not os.path.isdir(src_root):
					sys.stderr.write('MISSING upstream folder: %s (repo %s)\n' % (up, repo[0]))
					sys.exit(1)
				for droot, _, files in os.walk(src_root):
					for fn in sorted(files):
						if not fn.endswith('.kt'):
							continue
						sp = os.path.join(droot, fn)
						up_path = os.path.relpath(sp, clone_dir).replace(os.sep, '/')
						rel = os.path.relpath(sp, src_root).replace(os.sep, '/')
						rdest = dest.rstrip('/') + '/' + rel
						dst = os.path.join(module_dir, *rdest.split('/'))
						if (repo, up_path) in excluded:  # manually vendored in src/{commonMain,...}; don't duplicate
							skipped += 1
							if os.path.isfile(dst):
								os.remove(dst)  # drop any stale copy from a pre-exclusion sync
							continue
						count += write_vendor_file(sp, dst, rdest)
						written.add(os.path.normcase(os.path.abspath(dst)))
				continue
			src = os.path.join(clone_dir, *up.split('/'))
			if not os.path.isfile(src):
				sys.stderr.write('MISSING upstream file: %s (repo %s)\n' % (up, repo[0]))
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

	# ---- 3. re-annotate SET_FOLDER manifests in place so each always reflects the current
	#      upstream tree: commented `#     | src -> dest` lines for the files each folder
	#      directive copies, plus a `# >>> GAPS` block listing any new/unvendored .kt. Legacy
	#      (non-SET_FOLDER) manifests are reorganized by step 1.5's format-manifest.py instead.
	report_gaps(manifests, variables, quiet_skips=True)

	print('synced %d files verbatim across %d repo(s)' % (total, len(by_url)))


if __name__ == '__main__':
	main()
