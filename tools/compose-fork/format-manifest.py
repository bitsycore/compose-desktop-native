#!/usr/bin/env python3
# Canonicalize tools/compose-fork/manifest.txt in place: regroup every file
# entry by androidx package (hierarchy / alphabetical order) under a
# "# -- <package> --" header, VENDORED (uncommented) entries first then
# NOT-YET-VENDORED (commented) below, dest columns aligned. Dedups by dest with
# last-active-wins (matches sync.sh's copy order) and drops all freeform prose.
#
# Idempotent: running it on an already-canonical manifest changes nothing.
# Purely a re-layout — the set of active upstream->dest pairs (what sync.sh
# consumes) is preserved, so the vendored tree is byte-identical.
#
# Usage:
#   python3 tools/compose-fork/format-manifest.py                 # rewrite in place
#   python3 tools/compose-fork/format-manifest.py --check         # exit 1 if not canonical (no write)
#   python3 tools/compose-fork/format-manifest.py --discover PATH # + add new upstream files as commented candidates
#
# --discover scans the upstream clone (PATH, or $CMP_REF) for every .kt in the
# tracked modules / source sets and adds any not already listed as a COMMENTED
# candidate (best-guess dest; refine when you vendor it). Only the upstream path
# is deduped, so curated entries keep their dests. sync.sh runs
# `--discover $CMP_REF` automatically after cloning; run plain (or --check)
# standalone after hand-editing.
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MAN = os.path.join(HERE, 'manifest.txt')

ENTRY = re.compile(r'^(#\s*)?(compose/\S+\.kt)\s+(core/src/vendor/\S+\.kt)\s*$')
PKG = re.compile(r'core/src/vendor/[^/]+/kotlin/(.+)/[^/]+\.kt$')

HEADER = [
    '# Files vendored VERBATIM from JetBrains/compose-multiplatform-core into :core.',
    '# Re-sync with: tools/compose-fork/sync.sh  (upstream ref pinned in compose-ref.txt).',
    '#',
    '# Format:  <upstream-path-under-clone>  <dest-under-repo-root>',
    '# Grouped by androidx package, hierarchy (alphabetical) order. Within each package,',
    '# VENDORED (uncommented) entries come first, then NOT-YET-VENDORED (commented) below.',
    '# Never hand-edit vendored files — change the manifest / ref and re-run sync.sh.',
    '',
]


def package_of(dest):
    m = PKG.search(dest)
    return m.group(1).replace('/', '.') if m else '(root)'


# Non-runtime packages we never mirror (test framework, preview tooling).
# Commented candidates under these are dropped on canonicalize; discover skips
# them. An *active* (vendored) entry under one is kept + warned (unexpected).
EXCLUDE_PKGS = ('androidx.compose.ui.test', 'androidx.compose.ui.tooling')


def is_excluded(dest):
    p = package_of(dest)
    return any(p == e or p.startswith(e + '.') for e in EXCLUDE_PKGS)


def section_header(pkg):
    pre = '# ── %s ' % pkg
    return pre + '─' * max(3, 78 - len(pre))


# Upstream source sets we mirror (native world). JVM/Android/JS/test are skipped.
NATIVE_SETS = {'nonJvmMain', 'nativeMain', 'nonAndroidMain', 'skikoMain'}
ALLOWED_SETS = {'commonMain'} | NATIVE_SETS
SRC_RE = re.compile(r'/src/([A-Za-z0-9]+)/kotlin/(androidx/.+\.kt)$')
# platform-suffix tags stripped from a native dest filename before adding .native
TAGS = ('nonJvm', 'skiko', 'nonAndroid', 'native')


def upstream_to_dest(up):
    """Best-guess dest for an upstream path, following the manifest's convention:
    commonMain -> vendor/common (name unchanged); native-family -> vendor/native
    with the filename's platform tag normalized to .native.kt. Returns None if the
    source set isn't one we mirror."""
    m = SRC_RE.search(up)
    if not m:
        return None
    srcset, rel = m.group(1), m.group(2)
    if srcset not in ALLOWED_SETS:
        return None
    if srcset == 'commonMain':
        return 'core/src/vendor/common/kotlin/' + rel
    d, fn = rel.rsplit('/', 1) if '/' in rel else ('', rel)
    base = fn[:-3]  # drop .kt
    for t in TAGS:
        if base.endswith('.' + t):
            base = base[: -(len(t) + 1)]
            break
    dest_rel = (d + '/' if d else '') + base + '.native.kt'
    return 'core/src/vendor/native/kotlin/' + dest_rel


def discover(existing_ups, cmp_ref):
    """Every tracked-module .kt in a mirrored source set that isn't already listed
    (deduped by upstream path). Returns sorted list of (up, dest)."""
    found = []
    for area in ('ui', 'foundation', 'animation'):
        root = os.path.join(cmp_ref, 'compose', area)
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
                up = full[i + 1:]  # 'compose/...'
                if up in existing_ups:
                    continue
                dest = upstream_to_dest(up)
                if dest and not is_excluded(dest):
                    found.append((up, dest))
    return sorted(set(found))


def canonical(text):
    # dest -> {up, active}; last active line wins the mapping (== sync.sh cp order)
    entries = {}
    for line in text.splitlines():
        m = ENTRY.match(line)
        if not m:
            continue
        up, dest, active = m.group(2), m.group(3), m.group(1) is None
        if is_excluded(dest) and not active:
            continue  # drop non-runtime (test/tooling) candidates
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

    out = list(HEADER)
    for pkg in sorted(groups):
        rows = groups[pkg]
        act = sorted((d, e) for d, e in rows if e['active'])
        com = sorted((d, e) for d, e in rows if not e['active'])
        ordered = [(d, e, False) for d, e in act] + [(d, e, True) for d, e in com]
        lefts = [('# ' if c else '') + e['up'] for d, e, c in ordered]
        w = max(len(x) for x in lefts)
        out.append(section_header(pkg))
        for (d, e, c), left in zip(ordered, lefts):
            out.append('%s  %s' % (left.ljust(w), d))
        out.append('')
    return '\n'.join(out) + '\n'


def main():
    args = sys.argv[1:]
    check = '--check' in args
    cmp_ref = None
    if '--discover' in args:
        i = args.index('--discover')
        cmp_ref = args[i + 1] if i + 1 < len(args) and not args[i + 1].startswith('-') else os.environ.get('CMP_REF')
        if not cmp_ref or not os.path.isdir(cmp_ref):
            sys.stderr.write('--discover needs a valid clone path (arg or $CMP_REF)\n')
            return 2

    with open(MAN, encoding='utf-8') as f:
        cur = f.read()

    added = 0
    text = cur
    if cmp_ref:
        existing = {m.group(2) for m in (ENTRY.match(l) for l in cur.splitlines()) if m}
        new_entries = discover(existing, cmp_ref)
        added = len(new_entries)
        if added:
            text = cur + '\n' + '\n'.join('# %s  %s' % (up, dest) for up, dest in new_entries) + '\n'

    new = canonical(text)
    if new == cur:
        print('manifest.txt already canonical' + (' (0 new files discovered)' if cmp_ref else ''))
        return 0
    if check:
        sys.stderr.write('manifest.txt is NOT canonical — run format-manifest.py\n')
        return 1
    with open(MAN, 'w', encoding='utf-8', newline='\n') as f:
        f.write(new)
    n_act = sum(1 for l in new.splitlines() if ENTRY.match(l) and not l.startswith('#'))
    print('manifest.txt canonicalized (%d active entries%s)'
          % (n_act, ', +%d new candidates' % added if added else ''))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
