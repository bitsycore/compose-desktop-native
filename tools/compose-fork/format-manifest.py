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
#   python3 tools/compose-fork/format-manifest.py           # rewrite in place
#   python3 tools/compose-fork/format-manifest.py --check   # exit 1 if not canonical (no write)
#
# sync.sh runs this automatically before copying; run it standalone after
# hand-editing the manifest (adding / uncommenting entries) to re-tidy.
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


def section_header(pkg):
    pre = '# ── %s ' % pkg
    return pre + '─' * max(3, 78 - len(pre))


def canonical(text):
    # dest -> {up, active}; last active line wins the mapping (== sync.sh cp order)
    entries = {}
    for line in text.splitlines():
        m = ENTRY.match(line)
        if not m:
            continue
        up, dest, active = m.group(2), m.group(3), m.group(1) is None
        e = entries.get(dest)
        if e is None:
            entries[dest] = {'up': up, 'active': active}
        elif active:
            e['up'] = up
            e['active'] = True

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
    check = '--check' in sys.argv[1:]
    with open(MAN, encoding='utf-8') as f:
        cur = f.read()
    new = canonical(cur)
    if cur == new:
        print('manifest.txt already canonical')
        return 0
    if check:
        sys.stderr.write('manifest.txt is NOT canonical — run format-manifest.py\n')
        return 1
    with open(MAN, 'w', encoding='utf-8', newline='\n') as f:
        f.write(new)
    n_act = new.count('\n') and sum(1 for l in new.splitlines() if ENTRY.match(l) and not l.startswith('#'))
    print('manifest.txt canonicalized (%d active entries)' % n_act)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
