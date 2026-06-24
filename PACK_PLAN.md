# Pack tree + inheritance — implementation plan

Turning apidemo's flat **session → pack → request** into a **tree** (loose
requests, nested packs, linked packs) with multi-level inheritance for
variables, headers and client certs.

## Locked decisions

- **Variables**: the current "Global" env becomes the **Session** level. One
  ladder, innermost wins: `Session → Pack → Sub-pack → … (→ request uses them)`.
  No separate global anymore.
- **Linked Copy Pack**: a **read-only mirror** of a source pack's children
  (requests + sub-packs reflect the source live, not editable from the copy);
  has its **own** name, variables, headers and cert.
- **Header override**: the request's Headers panel lists inherited headers
  read-only; an **Override** button **copies** that header into the request's
  own (editable) headers, where a same-key value wins.
- **Container settings UI**: the existing per-pack "env" tab in the main panel
  becomes a **Pack settings** view with **Variables / Headers / Cert** sub-tabs.
  The Session gets the same view.

## Target model

Runtime (Main.kt):

```
sealed interface TreeNode { var parent: PackState? }       // parent = null → session root

class ReqState(...) : TreeNode                              // request leaf (existing)

class PackState(...) : TreeNode {
    name, color, expanded, dirty, path
    variables, headers : SnapshotStateList<KeyVal>
    cert : CertConfig?
    id : String                                            // stable, for linking
    ownChildren : SnapshotStateList<TreeNode>              // requests + sub-packs
    linkedSource : PackState?                              // non-null → linked copy
    val children get() = linkedSource?.children ?: ownChildren
    val isLinked get() = linkedSource != null
}
```

Session root lives on App: `vRoots: SnapshotStateList<TreeNode>` (top-level
requests + packs), `vSessionVars`, `vSessionHeaders`, `vSessionCert`.

Inheritance helpers:
- `scopeChain(node)` = session root → ancestors (outer→inner).
- `effectiveVars(node)` = merge(sessionVars, each ancestor pack's vars), innermost wins.
- `inheritedHeaders(req)` = merge(sessionHeaders, ancestor headers) by key, innermost wins; request's own headers override on send.
- `effectiveCert(req)` = request's own cert if set, else nearest ancestor's cert, else session cert.

Persistence (Persist.kt / Model.kt) — recursive, polymorphic:

```
@Serializable sealed class NodeData
@Serializable @SerialName("req")  class RequestData(request: ApiRequest) : NodeData()
@Serializable @SerialName("pack") class PackData(
    name, color, variables, headers, cert: CertConfig?, id, linkedTo: String?, children: List<NodeData>) : NodeData()

@Serializable class CertConfig(certPath, certFormat, keyPath, keyFormat, certPassword)
@Serializable class Session(roots: List<NodeData>, variables, headers, cert: CertConfig?, ...)
```

Migration: `loadAppState` maps any old flat `packs: List<SavedPack>` → `roots`
(each pack → PackData with its requests as RequestData children) and old
`globalEnv` → session `variables`, so existing saved sessions survive.

## Phases (each must build + be committed)

> **Reordering note:** the tree restructure (P1/P2/P8) is the one big atomic
> change and is highest-risk, so it's being landed LAST. The inheritance,
> settings and linked-pack work (P3–P7) ships first on the current flat
> session→pack→request model — the inheritance helpers already walk a chain, so
> they extend to sub-packs for free once the tree lands. Progress:
> - DONE: container-settings data (CertConfig, pack/session headers+cert+id).
> - DONE: Env→Var / Global→Session UI rename.
> - DONE: **P4** — Var/Header sub-tabs (session + pack settings).
> - DONE: **P5** — header inheritance (session+pack) + Override in the request.
> - DONE: **P6** — client-cert inheritance + Override (Cert sub-tab + effectiveCert).
> - DONE: **P7** — Linked Copy Pack (read-only mirror; own Var/Header/Cert).
> - DONE: **tree slice 1 — loose requests.** A hidden session-root `PackState`
>   (`vRoot`, active when `vActivePack < 0`) holds requests not in any pack; they
>   render headerless at the top of the sidebar and inherit session settings only.
>   Add via the `+` → "New request (loose)". Persisted as `Session.root` /
>   `AppState.root` + `rootOpenTabs` (vPacks indices untouched).
> - REMAINING: **tree slices 2/3** — nested packs (PackState.children) + cross-pack
>   drag. This is one atomic refactor of the core (~100 interconnected sites:
>   vPacks→vRoots, PackState.requests→children, recursive sidebar + tab strip +
>   recursive NodeData persistence + migration). No compilable sub-step — best
>   landed as a dedicated focused push. When doing it: introduce
>   `sealed interface TreeNode`, `PackState.children`, `vRoots`, migrate all refs,
>   THEN add cross-pack drop + the `+`→New request/sub-pack entries.

- [ ] **P1 — Tree model + persistence.** TreeNode/PackState.children/sub-packs;
      `vRoots` replaces `vPacks`; recursive serialized NodeData; migrate
      load/save/export/import; sidebar renders the tree recursively (indented
      sections); drag-reorder still works within a pack. Loose top-level
      requests render at root. No inheritance yet (keep current var behaviour).
- [ ] **P2 — `+` menu + creation.** The tree `+` adds: New request (here),
      New pack, New sub-pack, Import pack, Load default. Loose requests open in
      the main panel like pack requests.
- [ ] **P3 — Variable inheritance.** Global→Session rename; `effectiveVars`
      walks the chain; `resolveVars` uses it; unresolved-var check uses it.
- [ ] **P4 — Pack settings tab.** Extend the pack/session tab into
      Variables / Headers / Cert sub-tabs (model fields already exist from P1).
- [ ] **P5 — Header inheritance + override.** Request Headers panel shows
      inherited headers read-only + Override (copy-to-editable); send merges
      inherited + own (own wins).
- [ ] **P6 — Cert inheritance + override.** Request Cert tab shows inherited
      cert read-only + Override; send/inspect use `effectiveCert`.
- [ ] **P7 — Linked Copy Pack.** ⋮ → "Linked copy"; read-only mirror of source
      children; own name/vars/headers/cert; persisted via `linkedTo` id.
- [ ] **P8 — Cross-pack drag-and-drop.** Drag a request/pack into another pack
      or to root; updates `parent` + children lists; drop indicators across packs.

## Open questions (resolved)

All four design forks answered — see "Locked decisions".

## Notes / pitfalls

- `paddingLeft` is only honoured by Box/Column/Row (not leaf Text) — indent the
  tree via nested Box/Column padding, not Text padding.
- Polymorphic kotlinx serialization needs the sealed class + `@SerialName` on
  each subtype; default Json adds a `type` discriminator.
- Generate pack `id`s with `kotlin.random` at creation; persist them.
- Keep each phase compiling; commit per phase (no Claude attribution).
