# PLAN.md

Roadmap for matching Compose Multiplatform Desktop's TextField feature set on
top of the SDL3 + Skia backend.

## Key decisions (locked in)

| Decision | Choice |
| --- | --- |
| Public API | **Both overloads**: `(value: String, onValueChange: (String) -> Unit)` *and* `(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit)` — same as Compose |
| Cursor model | character-index `TextRange` |
| Single vs multi-line | single-line first (Phase 1–5), multi-line in Phase 6 |
| Focus model | one focused `LayoutNode` per window initially; directional traversal later |
| IME | full IME support in Phase 7 |

## Blocker — text measurement

`Font.measureText` and `Font.measureTextWidth` both abort on macOS Skiko
0.144.6 inside `SkTypeface_Mac::onCharsToGlyphs`. Until this is fixed we have
no accurate "where is character N on the x axis?" — which breaks cursor
positioning and click-to-position.

**Phase -1** below is to investigate before committing to either path.

---

## Phase -1 — Probe a newer Skiko (~30 min)

Try `org.jetbrains.skiko:skiko:0.9.4` (newer release per maven) then 0.144.6
back if regression. If the macOS text crash is fixed: revert the heuristic
in `SkiaTextRenderer` and switch back to real `measureText`-based widths.

If not fixed: ship Phase 1 with the heuristic, document the tradeoff, plan
a deeper fix later (e.g. bundle a FreeType-backed `FontMgr_Custom`).

## Phase 0 — Foundation (1–2 days)

Things TextField needs that aren't TextField:

- **`Modifier.focusable()`** + a `FocusableModifier` element
- **`FocusRequester`** class with `requestFocus()`
- One "currently focused" `LayoutNode` tracked in `ComposeWindow`
- Click changes focus to the focusable ancestor of the hit-tested node
- **`Modifier.onKeyEvent { ev -> Boolean }`** + dispatch from `ComposeWindow`
- New `AppEvent.TextInput(text: String)` mapped from `SDL_EVENT_TEXT_INPUT`
- Route key + text-input events to the focused node's modifier chain (walk up, first to return `true` consumes)
- **`rememberCoroutineScope()`** — composable returning a `CoroutineScope` tied to the composition lifecycle (uses `RememberObserver`)
- **`TextFieldValue(text: String, selection: TextRange, composition: TextRange? = null)`** data class in `androidx.compose.ui.text.input`
- **`TextRange(start: Int, end: Int)`** with `collapsed`, `min`, `max`

## Phase 1 — BasicTextField MVP (2–3 days)

Both overloads:

```kotlin
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Color = Color.Black,
    enabled: Boolean = true,
    readOnly: Boolean = false,
)

@Composable
fun BasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Color = Color.Black,
    enabled: Boolean = true,
    readOnly: Boolean = false,
)
```

The `String` overload internally keeps a `TextFieldValue` in `remember`, only
exposes the text portion to the caller — same shape as Compose's source.

Behaviour:
- Renders text + blinking 1-px cursor at the end
- `SDL_EVENT_TEXT_INPUT` → append at cursor
- Backspace → remove char before cursor
- Click anywhere → focuses, cursor at end

## Phase 2 — Editing mid-text (2–3 days)

- Cursor at arbitrary index
- Left / Right arrows move cursor
- Cmd+Left / Cmd+Right jump to start / end of line
- Alt+Left / Alt+Right jump word boundaries
- Home / End → line start / end
- Delete (forward) key
- Typing inserts AT cursor
- Click → places cursor (this is the first need for accurate glyph-x mapping)

## Phase 3 — Selection (3–4 days)

- Selection range in `TextFieldValue.selection`
- Shift + Arrow / Shift + Cmd/Alt+Arrow extends selection
- Backspace / Delete / typing replaces selection
- Mouse drag selects (needs press → move → release tracking with selection anchor)
- Double-click selects word (word-boundary code: `Char.isLetterOrDigit` runs)
- Triple-click selects line
- Cmd+A select all
- Selection-highlight rect rendered behind glyphs (use background colour from theme)

## Phase 4 — Clipboard + history (2–3 days)

- Cinterop calls to `SDL_GetClipboardText()` (free with `SDL_free`!) and `SDL_SetClipboardText(text)`
- Cmd+C copy, Cmd+X cut, Cmd+V paste — handle selection / cursor correctly
- Undo / Redo stack: keep snapshots of `TextFieldValue` per "edit run" (group consecutive char-typings into one undo step)
- Cmd+Z / Cmd+Shift+Z (or Cmd+Y)

## Phase 5 — Material chrome (2–3 days)

```kotlin
@Composable
fun TextField(
    value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    shape: Shape = TextFieldDefaults.shape,
)

// Same parameter set, TextFieldValue overload
@Composable
fun TextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, ...)

// And OutlinedTextField with both overloads
```

- Focused / unfocused colour transition (first real `animateColorAsState`)
- Border drawing per state (error / focused / unfocused)
- Layout: label floats above when focused or non-empty; placeholder shows when empty + unfocused
- `BasicTextField` under the hood

## Phase 6 — Multi-line + wrap + scroll (3–5 days)

- Soft line wrapping at the field's width
- `\n` handling for explicit line breaks
- Up / Down arrow row navigation (preserve preferred x-column)
- Vertical scroll inside the field, follow cursor
- `Modifier.verticalScroll` reused
- Mouse wheel
- `maxLines` / `minLines` constraints

## Phase 7 — IME (~1 week)

- `SDL_StartTextInput(window)` on focus, `SDL_StopTextInput(window)` on unfocus
- `SDL_EVENT_TEXT_EDITING` → in-progress composition; render with underline
- Replace composition range on `SDL_EVENT_TEXT_INPUT` commit
- `SDL_SetTextInputArea(window, rect, cursor)` per frame so OS IME candidates pop near the cursor
- Dead keys (´ + e = é) — `SDL_EVENT_TEXT_EDITING` then commit
- CJK composition + candidate windows
- AnnotatedString rendering for composition style

## Phase 8 — Behaviour polish (variable)

- `KeyboardOptions(imeAction, keyboardType, autoCorrect, capitalization)`
- `KeyboardActions(onDone, onSearch, onNext, ...)`
- `visualTransformation` (`PasswordVisualTransformation`)
- `decorationBox` slot for fully custom chrome
- `onTextLayout` callback exposing line metrics + cursor positions
- `interactionSource` integration for hover/press/focus
- `Modifier.imeAction` etc.

---

## Estimated total

- MVP (typing + cursor): ~1 week (Phase 0–2)
- Edit + select + clipboard: +1 week (Phase 3–4)
- Material chrome: +1 week (Phase 5)
- Multi-line: +1 week (Phase 6)
- IME: +1 week (Phase 7)
- Polish/options: +1 week (Phase 8)

~6 weeks. Each phase ships independently; the MVP after week 1 is enough to
validate the API before committing to the rest.
