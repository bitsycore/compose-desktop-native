import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sdl3backend.composeWindow

fun main() {
    composeWindow(title = "ComposeNativeSDL3 Showcase", width = 1000, height = 700) {
        MaterialTheme(colors = darkColors()) {
            App()
        }
    }
}

// ==================
// MARK: Screen registry
// ==================

private data class Screen(val name: String, val content: @Composable () -> Unit)

private val Screens: List<Screen> = listOf(
    Screen("Buttons")        { ButtonsScreen() },
    Screen("TextField")      { TextFieldScreen() },
    Screen("Text")           { TextScreen() },
    Screen("Layout")         { LayoutScreen() },
    Screen("Modifiers")      { ModifiersScreen() },
    Screen("Shapes")         { ShapesScreen() },
    Screen("State / Remember") { StateScreen() },
    Screen("Interaction")    { InteractionScreen() },
    Screen("Recomposition")  { RecompositionScreen() },
    Screen("Colors")         { ColorsScreen() },
    Screen("Scroll")         { ScrollScreen() },
    Screen("LazyColumn")     { LazyColumnScreen() },
    Screen("Counter")        { CounterScreen() },
)

// ==================
// MARK: App shell — sidebar + content
// ==================

@Composable
fun App() {
    RecompositionLogger("App")
    var current by remember { mutableStateOf(Screens[0]) }
    val vSidebarBg = MaterialTheme.colors.surface.blend(MaterialTheme.colors.onSurface, 0.02f)

    val vSidebarScroll = rememberScrollState()
    val vContentScroll = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
    ) {
        // Sidebar (vertically scrollable — try resizing the window short)
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(vSidebarBg)
                .verticalScroll(vSidebarScroll)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ComposeNativeSDL3",
                color = MaterialTheme.colors.primary,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (screen in Screens) {
                NavItem(
                    label = screen.name,
                    selected = screen.name == current.name,
                    onClick = { current = screen },
                )
            }
        }

        // Content (vertically scrollable — long screens fit a short window)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vContentScroll)
                .padding(24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            current.content()
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val vBg = when {
        selected -> MaterialTheme.colors.primary.copy(alpha = 0.20f)
        hovered  -> MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
        else     -> Color.Transparent
    }
    val vFg = if (selected) MaterialTheme.colors.primary
              else MaterialTheme.colors.onSurface.copy(alpha = 0.82f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(vBg, RoundedCornerShape(8.dp))
            .hoverable { hovered = it }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = vFg, fontSize = 14.sp)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null) {
    Column {
        Text(text = title, color = MaterialTheme.colors.onBackground, fontSize = 30.sp)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/* Card-wrapped section with a small caption above the demonstrated content.
   Every screen uses this for a consistent visual grid. */
@Composable
private fun Section(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = MaterialTheme.colors.onSurface, fontSize = 15.sp)
            if (description != null) {
                Text(
                    description,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }
            content()
        }
    }
}

// ==================
// MARK: Screens
// ==================

@Composable
private fun ButtonsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Buttons",
            "Filled / Outlined / Text variants with shape, content padding, and enabled states.",
        )

        Section("Filled Button", "Default: RoundedCornerShape(4.dp), Material primary container") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Action", color = MaterialTheme.colors.onPrimary) }
                Button(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colors.onPrimary)
                }
            }
        }

        Section("OutlinedButton", "Transparent fill, 1.dp border, primary content colour") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}) {
                    Text("Outlined", color = MaterialTheme.colors.primary)
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Disabled", color = MaterialTheme.colors.primary)
                }
            }
        }

        Section("TextButton", "No background, no border — text-only affordance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {}) {
                    Text("Text", color = MaterialTheme.colors.primary)
                }
            }
        }

        Section("Shape variants", "shape = RoundedCornerShape(0/12.dp) and CircleShape with contentPadding 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}, shape = RoundedCornerShape(0.dp)) {
                    Text("Rect", color = MaterialTheme.colors.onPrimary)
                }
                Button(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                    Text("12dp", color = MaterialTheme.colors.onPrimary)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colors.onPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextFieldScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "TextField",
            "BasicTextField + Material wrappers. Click to focus, type, drag to select, ⌘C/⌘V/⌘Z.",
        )

        var single by remember { mutableStateOf("") }
        var outlined by remember { mutableStateOf("") }
        var withError by remember { mutableStateOf("abc") }
        var multi by remember { mutableStateOf("Hello\nMulti-line text\nReturn to add a line\nUp / Down to navigate") }
        var raw by remember { mutableStateOf("BasicTextField (no chrome)") }

        Section("Material TextField (filled)", "label, placeholder, supportingText all wired") {
            TextField(
                value = single,
                onValueChange = { single = it },
                label = "Name",
                placeholder = "Type your name…",
                supportingText = "Click, drag-select, ⌘C / ⌘V / ⌘Z",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("OutlinedTextField") {
            OutlinedTextField(
                value = outlined,
                onValueChange = { outlined = it },
                label = "Email",
                placeholder = "user@example.com",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Error state", "isError = true turns border, label, cursor, supporting text red") {
            TextField(
                value = withError,
                onValueChange = { withError = it },
                label = "Password",
                isError = true,
                supportingText = "Too short",
                modifier = Modifier.width(320.dp),
            )
        }

        Section("Multi-line", "Return inserts \\n, Up/Down navigates rows, selection extends across") {
            OutlinedTextField(
                value = multi,
                onValueChange = { multi = it },
                label = "Bio",
                modifier = Modifier.width(420.dp).height(120.dp),
            )
        }

        Section("Raw BasicTextField", "No chrome — bare cursor + text") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.surface,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
                modifier = Modifier.width(320.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        color = MaterialTheme.colors.onSurface,
                        cursorColor = MaterialTheme.colors.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Text", "fontSize / color / textAlign / softWrap")

        Section("Font sizes", "All Sp-typed, scale with theme") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Default text", color = MaterialTheme.colors.onSurface)
                Text("fontSize 12.sp", fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 24.sp", fontSize = 24.sp, color = MaterialTheme.colors.onSurface)
                Text("fontSize 32.sp", fontSize = 32.sp, color = MaterialTheme.colors.onSurface)
            }
        }

        Section("Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Color.Red", color = Color.Red, fontSize = 16.sp)
                Text("MaterialTheme.colors.primary", color = MaterialTheme.colors.primary, fontSize = 16.sp)
                Text("MaterialTheme.colors.secondary", color = MaterialTheme.colors.secondary, fontSize = 16.sp)
            }
        }

        Section("TextAlign over fillMaxWidth()") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(400.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                        color = MaterialTheme.colors.onBackground)
                    Text("Center", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground)
                    Text("End", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        color = MaterialTheme.colors.onBackground)
                }
            }
        }

        Section("Soft-wrap", "Long text auto-wraps at word boundaries inside a constrained width") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump!",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Section("softWrap = false", "Overflows horizontally — Surface clips, content cropped at the right edge") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.width(280.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "This long sentence does not wrap and will get clipped.",
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 14.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Layout", "Row / Column / Box — Arrangement and Alignment")

        Section("Row with spacedBy(16.dp)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Swatch("A"); Swatch("B"); Swatch("C")
            }
        }

        Section("Row with Arrangement.SpaceBetween", "Children pinned to start and end, evenly spaced") {
            Row(
                modifier = Modifier.width(400.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Swatch("L"); Swatch("M"); Swatch("R")
            }
        }

        Section("Column with spacedBy(8.dp) + CenterHorizontally") {
            Column(
                modifier = Modifier.width(120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Swatch("1"); Swatch("2"); Swatch("3")
            }
        }

        Section("Box with contentAlignment.Center") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.background,
                modifier = Modifier.size(120.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Swatch("•") }
            }
        }
    }
}

@Composable
private fun ModifiersScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Modifiers", "background, border, clip, padding, size, offset, defaultMinSize")

        Section("background + border + padding") {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Text("Content", color = MaterialTheme.colors.onPrimary)
            }
        }

        Section("clip(RoundedCornerShape(12.dp))", "Clips children only — the background here already follows the shape") {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 60.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("clipped", color = MaterialTheme.colors.onPrimary)
            }
        }

        Section("offset(x = 20.dp, y = 10.dp)", "Visual nudge only — doesn't change measured size or sibling layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("A")
                Box(modifier = Modifier.offset(x = 20.dp, y = 10.dp)) { Swatch("B↘") }
                Swatch("C")
            }
        }

        Section("defaultMinSize vs size", "defaultMinSize only kicks in when the incoming min is 0") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 120.dp, minHeight = 40.dp)
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("min 120 × 40", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("40", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ShapesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Shapes", "RectangleShape, RoundedCornerShape(Dp / percent), CircleShape")

        Section("RectangleShape") {
            Box(modifier = Modifier.size(120.dp, 40.dp).background(MaterialTheme.colors.primary))
        }

        Section("RoundedCornerShape — radius 4 / 12 / 24 dp") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(24.dp)))
            }
        }

        Section("CircleShape") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colors.primary, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(MaterialTheme.colors.primary, CircleShape))
                Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colors.primary, CircleShape))
            }
        }

        Section("Borders on each shape", "Modifier.border respects the same Shape param as background") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colors.primary))
                Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, MaterialTheme.colors.primary, RoundedCornerShape(12.dp)))
                Box(modifier = Modifier.size(48.dp).border(2.dp, MaterialTheme.colors.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun CounterScreen() {
    var counter by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Counter", "The original +/- demo")
        Section("Click +/- to change the count") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Counter: $counter",
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 32.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { counter-- },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "-",
                            color = MaterialTheme.colors.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = { counter = 0 }) {
                        Text("Reset", color = MaterialTheme.colors.primary)
                    }
                    Button(
                        onClick = { counter++ },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "+",
                            color = MaterialTheme.colors.onPrimary,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ==================
// MARK: State / Remember screen
// ==================

@Composable
private fun StateScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("State / Remember", "mutableStateOf, derived state, remember(key) re-init")

        Section("Basic counter", "var n by remember { mutableStateOf(0) }") {
            var n by remember { mutableStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { n++ }) { Text("Increment", color = MaterialTheme.colors.onPrimary) }
                Text("n = $n", color = MaterialTheme.colors.onSurface, fontSize = 16.sp)
            }
        }

        Section("List state", "remembered List<String>; reassign to add / drop") {
            var items by remember { mutableStateOf(listOf("alpha", "beta", "gamma")) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { items = items + "item ${items.size}" }) {
                        Text("Add", color = MaterialTheme.colors.onPrimary)
                    }
                    OutlinedButton(onClick = { if (items.isNotEmpty()) items = items.dropLast(1) }) {
                        Text("Remove", color = MaterialTheme.colors.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in items) Text("• $i", color = MaterialTheme.colors.onSurface, fontSize = 14.sp)
                }
            }
        }

        Section("Derived state", "Total recomputes on every recomposition that reads aText / bText") {
            var aText by remember { mutableStateOf("3") }
            var bText by remember { mutableStateOf("4") }
            val total = (aText.toIntOrNull() ?: 0) + (bText.toIntOrNull() ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = aText, onValueChange = { aText = it }, modifier = Modifier.width(80.dp))
                Text("+", color = MaterialTheme.colors.onSurface, fontSize = 20.sp)
                OutlinedTextField(value = bText, onValueChange = { bText = it }, modifier = Modifier.width(80.dp))
                Text("=", color = MaterialTheme.colors.onSurface, fontSize = 20.sp)
                Text(total.toString(), color = MaterialTheme.colors.primary, fontSize = 20.sp)
            }
        }

        Section("remember(key)", "Toggling the key invalidates the memo so the lambda runs again") {
            var key by remember { mutableStateOf(0) }
            val rolledOnce = remember(key) { (0..99).random() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { key++ }) { Text("Re-roll", color = MaterialTheme.colors.onPrimary) }
                Text("rolled = $rolledOnce", color = MaterialTheme.colors.onSurface, fontSize = 16.sp)
            }
        }
    }
}

// ==================
// MARK: Interaction (hover / press / focus) screen
// ==================

@Composable
private fun InteractionScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Interaction", "Hover, press, focus state layers")

        Section("Hover overlay", "Move the cursor over each variant") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.onPrimary) }
                OutlinedButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
                TextButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
            }
        }

        Section("Press / drag-off", "Hold the mouse down; deeper overlay shows. Drag out and the press cancels.") {
            Button(onClick = {}) { Text("Press and hold", color = MaterialTheme.colors.onPrimary) }
        }

        Section("TextField focus", "Border color + width transition on focus / blur") {
            var a by remember { mutableStateOf("") }
            var b by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = a, onValueChange = { a = it }, label = "First", modifier = Modifier.width(160.dp))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = "Second", modifier = Modifier.width(160.dp))
            }
        }
    }
}

// ==================
// MARK: Recomposition diagnostics screen
// ==================

@Composable
private fun RecompositionScreen() {
    RecompositionLogger("Recomposition/outer")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Recomposition",
            "RecompositionLogger taps in nested scopes; stdout shows [tag] recomposed #N",
        )

        Section(
            "Scope-narrowing",
            "Clicking + only invalidates the inner block — App, outer screen, sibling logs stay at #1",
        ) {
            var counter by remember { mutableStateOf(0) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InnerCounterBlock(counter) { counter = it }
                SiblingBlock()
            }
        }
    }
}

@Composable
private fun InnerCounterBlock(counter: Int, onChange: (Int) -> Unit) {
    RecompositionLogger("Recomposition/inner")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onChange(counter + 1) }) { Text("+", color = MaterialTheme.colors.onPrimary) }
            Text("Counter: $counter", color = MaterialTheme.colors.onBackground, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SiblingBlock() {
    RecompositionLogger("Recomposition/sibling")
    Text(
        "This block doesn't read the counter — its log stays at #1.",
        color = MaterialTheme.colors.onBackground,
        fontSize = 12.sp,
    )
}

// ==================
// MARK: Colors screen — Material palette swatches
// ==================

@Composable
private fun ColorsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Colors", "MaterialTheme.colors palette swatches")
        Section("Theme palette") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = MaterialTheme.colors
                ColorRow("primary",         c.primary,         c.onPrimary)
                ColorRow("primaryVariant",  c.primaryVariant,  c.onPrimary)
                ColorRow("secondary",       c.secondary,       c.onSecondary)
                ColorRow("background",      c.background,      c.onBackground)
                ColorRow("surface",         c.surface,         c.onSurface)
                ColorRow("error",           c.error,           c.onError)
            }
        }
    }
}

@Composable
private fun ColorRow(name: String, fill: Color, content: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 36.dp)
                .background(fill, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, color = content, fontSize = 14.sp)
        }
    }
}

// ==================
// MARK: Scroll screen
// ==================

@Composable
private fun ScrollScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(
            "Scroll",
            "Modifier.verticalScroll(rememberScrollState()), wheel events, auto-clip",
        )

        Section(
            "Fixed-height scrollable Box",
            "Hover over the box and use mouse wheel / trackpad. Content is 40 rows tall.",
        ) {
            val vScroll = rememberScrollState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(400.dp).height(200.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..40) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colors.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$i",
                                        color = MaterialTheme.colors.onPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(
                                    "Row $i — scroll to see all 40 items",
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Section(
            "App-shell scrolling",
            "Shrink the window vertically — the sidebar AND the main content area both gain scrollbars.",
        ) {
            Text(
                "Both panes were wrapped in verticalScroll(rememberScrollState()) inside App().",
                color = MaterialTheme.colors.onSurface,
                fontSize = 13.sp,
            )
        }
    }
}

// ==================
// MARK: LazyColumn screen
// ==================

@Composable
private fun LazyColumnScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("LazyColumn", "DSL: items / item / itemsIndexed inside a verticalScroll viewport")

        Section(
            "items(count) — 100 rows",
            "Mouse-wheel over the list area to scroll. Header item pinned at the top of the list (it scrolls with content — sticky headers TBD).",
        ) {
            val state = rememberLazyListState()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(420.dp).height(260.dp),
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Text(
                            "LazyColumn — first item (a header)",
                            color = MaterialTheme.colors.primary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(100) { i ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colors.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$i",
                                    color = MaterialTheme.colors.onPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                "Item $i — generated via LazyColumn.items(100) { i -> … }",
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Section(
            "itemsIndexed(list)",
            "Use itemsIndexed when you need (index, element) — same API as upstream",
        ) {
            val names = remember { listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta") }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.background,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                modifier = Modifier.width(320.dp).height(160.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(names) { idx, name ->
                        Text(
                            "$idx. $name",
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ==================
// MARK: Helpers
// ==================

@Composable
private fun Swatch(label: String, color: Color = MaterialTheme.colors.primary) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colors.onPrimary, fontSize = 14.sp)
    }
}
