# compose-fork — vendored Compose sources

This directory is the tooling that vendors **byte-for-byte verbatim** source
files from [`JetBrains/compose-multiplatform-core`](https://github.com/JetBrains/compose-multiplatform-core)
into the repo. See [`../../CLAUDE.md`](../../CLAUDE.md) → "Compose API Fidelity"
for the three fix strategies (pull-verbatim / surface-match / intentional-custom).

## The vendored files are NOT committed

`<module>/src/vendor/` is **gitignored** — it is a generated artifact, not
source. Only the tooling here and the per-module manifests are tracked:

| File                              | Purpose                                                                                                                                                |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sync.sh`                         | Idempotent script that canonicalizes the selected manifest(s) then copies each active entry verbatim from the pinned upstream ref.                     |
| `format-manifest.py`              | Canonicalizes a `compose-fork.txt` in place (see "Manifest layout"). Run by `sync.sh`; also runnable standalone.                                       |
| `compose.properties`              | `NAME=value` variable declarations — the pinned upstream refs, tagged in one place. Manifests reference them as `<NAME>` in `SET_REPO=<url>@<NAME>`.     |
| `README.md`                       | This file.                                                                                                                                             |
| `../../<module>/compose-fork.txt` | Per-module manifest — lives **alongside the module's `build.gradle.kts`**. Today only `:core` has one; a future `:material3` module would add its own. |

## Manifest layout — per module, co-located with `build.gradle.kts`

Each Gradle module that vendors upstream code carries its own `compose-fork.txt`
next to its `build.gradle.kts`. That file lists every upstream file the module
vendors:

```
compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/Modifier.kt   src/vendor/common/kotlin/androidx/compose/ui/Modifier.kt
```

The destination is **relative to the manifest's own directory** (so `core/`
paths don't have a `core/` prefix — the manifest already lives in `core/`).
Entries are grouped by androidx package in hierarchy (alphabetical) order under
`# ── androidx.compose.<pkg> ──` headers. Within each package the **vendored**
(uncommented) entries come first, then the **not-yet-vendored** (commented)
candidates below.

`format-manifest.py` regenerates exactly this layout — deduping by dest (last
active line wins, matching `sync.sh`'s copy order) and dropping stray comments.
It is idempotent and a pure re-layout: the set of active `upstream → dest`
pairs is preserved, so the vendored tree is byte-identical. `sync.sh` runs it
automatically before copying; run it yourself after hand-editing:

```bash
python3 scripts/compose-fork/format-manifest.py                                       # rewrite EVERY <module>/compose-fork.txt
python3 scripts/compose-fork/format-manifest.py --manifest core/compose-fork.txt      # single manifest
python3 scripts/compose-fork/format-manifest.py --check                               # exit 1 if any manifest is not canonical
python3 scripts/compose-fork/format-manifest.py --discover PATH                       # + surface new upstream files
```

### Discovering new upstream files

`--discover <clone>` (or `$CMP_REF`) scans, for each manifest, every
`compose/<area>/<module>` upstream module the manifest already references, and
adds any `.kt` **not already listed** as a commented candidate under its
package section, with a best-guess dest (refine when you vendor it; only the
upstream path is deduped, so curated entries keep their dests). `sync.sh` runs
`--discover $CMP_REF` automatically after cloning per manifest, so a
`compose.properties` bump surfaces newly-added upstream files as candidates.
Source sets mirrored: `commonMain`, `nonJvmMain`, `nativeMain`, `nonAndroidMain`,
`skikoMain`. JVM / Android / JS / test are intentionally skipped.

Because the copies are verbatim, provenance lives entirely in
`<module>/compose-fork.txt` + `compose.properties`. **Never hand-edit a file under
`<module>/src/vendor/`** — change the manifest or the ref and re-run `sync.sh`
instead. Hand-written glue (shims, `expect`/`actual` actuals, project-specific
code) lives outside the vendor tree and IS committed.

## Bootstrapping a fresh checkout

The build needs the vendored files present on disk. After cloning this repo
you must populate `<module>/src/vendor/` once before building:

```bash
# Clones/updates the sparse upstream checkout at $CMP_REF (default ../cmp-ref)
# to the ref in compose.properties, then copies every manifest entry verbatim.
CMP_REF=../cmp-ref bash scripts/compose-fork/sync.sh
```

## Syncing selectively — per-module

`sync.sh` accepts one or more arguments identifying which manifests to run:

```bash
bash scripts/compose-fork/sync.sh                              # every <module>/compose-fork.txt in the repo
bash scripts/compose-fork/sync.sh :core                        # Gradle path
bash scripts/compose-fork/sync.sh core                         # module name (same thing)
bash scripts/compose-fork/sync.sh :material-symbols:outlined   # nested Gradle path
bash scripts/compose-fork/sync.sh core/compose-fork.txt        # direct path to a manifest
bash scripts/compose-fork/sync.sh :core :material              # multiple
```

Argument resolution:

| Form               | Resolves to                |
|--------------------|----------------------------|
| `:foo:bar`         | `foo/bar/compose-fork.txt` |
| `foo/bar`          | `foo/bar/compose-fork.txt` |
| `foo`              | `foo/compose-fork.txt`     |
| `path/to/file.txt` | that path                  |

Re-run any time after editing a manifest or bumping `compose.properties`.
The script is idempotent.

## Adding an upstream module

If you want to vendor a Compose module we don't yet cover (e.g. `material3`):

1. Create the module in the repo if needed (`material3/build.gradle.kts` etc.),
   and add its `include(":material3")` to `settings.gradle.kts`.
2. Create `material3/compose-fork.txt` with the standard header (any comment;
   `format-manifest.py` will fix the layout). Populate it either by hand or by
   letting `--discover` seed it:
   ```bash
   # Add ONE commented candidate to seed the upstream module reference, then
   # let discover fill in the rest:
   printf '# compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Placeholder.kt  src/vendor/common/kotlin/androidx/compose/material3/Placeholder.kt\n' > material3/compose-fork.txt
   CMP_REF=../cmp-ref bash scripts/compose-fork/sync.sh :material3
   ```
3. Uncomment the entries you actually want to vendor, then re-run `sync.sh
   :material3`.

## Adding a file to an existing module's vendor set

1. Find the upstream path in the module's `compose-fork.txt` — it may already
   be listed, commented out.
2. Add / uncomment its `<upstream-path>  <dest>` line under the matching
   `# ── androidx.compose.<pkg> ──` section. Destinations are relative to the
   module dir: `commonMain` → `src/vendor/common/`, `nonJvmMain` /
   `nativeMain` / `skikoMain` → `src/vendor/native/`. Exact placement /
   ordering doesn't matter — `sync.sh` re-canonicalizes.
3. Run `sync.sh :<module>` (canonicalizes the manifest, then copies).
4. Build and add any hand-written glue the new file needs (shims / actuals)
   outside the vendor tree.

## Bumping the upstream ref

Edit the ref variable (e.g. `COMPOSE_CORE_REF`) in `compose.properties`, re-run
`sync.sh`, then rebuild. A
verbatim re-sync makes `git status` of a temporary tracked copy show exactly
what upstream changed between refs.
