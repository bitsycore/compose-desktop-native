import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.*
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

    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
    ) {
        // Sidebar
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(vSidebarBg)
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

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            current.content()
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val vBg = if (selected)
        MaterialTheme.colors.primary.copy(alpha = 0.22f)
    else Color.Transparent
    val vFg = if (selected) MaterialTheme.colors.primary
              else MaterialTheme.colors.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(vBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = vFg,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Column {
        Text(text = title, color = MaterialTheme.colors.onBackground, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ==================
// MARK: Screens
// ==================

@Composable
private fun ButtonsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Buttons")

        Text("Default Button (filled, RoundedCornerShape 4dp):", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Action", color = MaterialTheme.colors.onPrimary) }
            Button(onClick = {}, enabled = false) {
                Text("Disabled", color = MaterialTheme.colors.onPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("OutlinedButton:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}) {
                Text("Outlined", color = MaterialTheme.colors.primary)
            }
            OutlinedButton(onClick = {}, enabled = false) {
                Text("Disabled", color = MaterialTheme.colors.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("TextButton:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {}) {
                Text("Text", color = MaterialTheme.colors.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Shapes:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun TextFieldScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("TextField")

        var single by remember { mutableStateOf("") }
        var outlined by remember { mutableStateOf("") }
        var withError by remember { mutableStateOf("abc") }
        var multi by remember { mutableStateOf("Hello\nMulti-line text\nReturn to add a line\nUp / Down to navigate") }
        var raw by remember { mutableStateOf("BasicTextField (no chrome)") }

        Text("Material TextField (filled):", color = Color.Gray, fontSize = 12.sp)
        TextField(
            value = single,
            onValueChange = { single = it },
            label = "Name",
            placeholder = "Type your name…",
            supportingText = "Click, drag-select, ⌘C / ⌘V / ⌘Z",
            modifier = Modifier.width(320.dp),
        )

        Text("OutlinedTextField:", color = Color.Gray, fontSize = 12.sp)
        OutlinedTextField(
            value = outlined,
            onValueChange = { outlined = it },
            label = "Email",
            placeholder = "user@example.com",
            modifier = Modifier.width(320.dp),
        )

        Text("Error state:", color = Color.Gray, fontSize = 12.sp)
        TextField(
            value = withError,
            onValueChange = { withError = it },
            label = "Password",
            isError = true,
            supportingText = "Too short",
            modifier = Modifier.width(320.dp),
        )

        Text("Multi-line (Return = newline, Up/Down = row nav):", color = Color.Gray, fontSize = 12.sp)
        OutlinedTextField(
            value = multi,
            onValueChange = { multi = it },
            label = "Bio",
            modifier = Modifier.width(420.dp).height(120.dp),
        )

        Text("Raw BasicTextField (no chrome):", color = Color.Gray, fontSize = 12.sp)
        BasicTextField(
            value = raw,
            onValueChange = { raw = it },
            color = MaterialTheme.colors.onBackground,
            cursorColor = MaterialTheme.colors.primary,
            fontSize = 16.sp,
            modifier = Modifier
                .width(320.dp)
                .background(Color(0xFF222222L))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TextScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle("Text")

        Text("Default Text", color = MaterialTheme.colors.onBackground)
        Text("fontSize 12.sp", fontSize = 12.sp, color = MaterialTheme.colors.onBackground)
        Text("fontSize 24.sp", fontSize = 24.sp, color = MaterialTheme.colors.onBackground)
        Text("fontSize 32.sp", fontSize = 32.sp, color = MaterialTheme.colors.onBackground)
        Text("Red", color = Color.Red, fontSize = 16.sp)
        Text("Primary", color = MaterialTheme.colors.primary, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(12.dp))
        Text("TextAlign over fillMaxWidth():", color = Color.Gray, fontSize = 12.sp)
        Box(modifier = Modifier.width(400.dp).background(Color(0xFF222222L)).padding(8.dp)) {
            Column {
                Text("Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colors.onBackground)
                Text("Center", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground)
                Text("End", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    color = MaterialTheme.colors.onBackground)
            }
        }
    }
}

@Composable
private fun LayoutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Layout")

        Text("Row with spacedBy(16.dp):", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Swatch("A"); Swatch("B"); Swatch("C")
        }

        Text("Row with Arrangement.SpaceBetween:", color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.width(400.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Swatch("L"); Swatch("M"); Swatch("R")
        }

        Text("Column with spacedBy(8.dp) + CenterHorizontally:", color = Color.Gray, fontSize = 12.sp)
        Column(
            modifier = Modifier.width(120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Swatch("1"); Swatch("2"); Swatch("3")
        }

        Text("Box with contentAlignment.Center:", color = Color.Gray, fontSize = 12.sp)
        Box(
            modifier = Modifier.size(120.dp).background(Color(0xFF222222L)),
            contentAlignment = Alignment.Center,
        ) { Swatch("•") }
    }
}

@Composable
private fun ModifiersScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Modifiers")

        Text("background + border + padding:", color = Color.Gray, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .width(220.dp)
                .background(MaterialTheme.colors.primary)
                .border(2.dp, Color.White)
                .padding(16.dp),
        ) {
            Text("Content", color = MaterialTheme.colors.onPrimary)
        }

        Text("clip(RoundedCornerShape(12.dp)):", color = Color.Gray, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 60.dp)
                .background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("clipped", color = MaterialTheme.colors.onPrimary)
        }

        Text("offset(x=20.dp, y=10.dp) applied to one of three:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch("A")
            Box(modifier = Modifier.offset(x = 20.dp, y = 10.dp)) { Swatch("B↘") }
            Swatch("C")
        }

        Text("defaultMinSize(120.dp, 40.dp) vs size(40.dp):", color = Color.Gray, fontSize = 12.sp)
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

@Composable
private fun ShapesScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Shapes")

        Text("RectangleShape:", color = Color.Gray, fontSize = 12.sp)
        Box(modifier = Modifier.size(120.dp, 40.dp).background(MaterialTheme.colors.primary))

        Text("RoundedCornerShape(4.dp) / 12.dp / 24.dp:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(12.dp)))
            Box(modifier = Modifier.size(80.dp, 40.dp).background(MaterialTheme.colors.primary, RoundedCornerShape(24.dp)))
        }

        Text("CircleShape (square inputs):", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colors.primary, CircleShape))
            Box(modifier = Modifier.size(60.dp).background(MaterialTheme.colors.primary, CircleShape))
            Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colors.primary, CircleShape))
        }

        Text("Border on each shape:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, Color.White))
            Box(modifier = Modifier.size(80.dp, 40.dp).border(2.dp, Color.White, RoundedCornerShape(12.dp)))
            Box(modifier = Modifier.size(48.dp).border(2.dp, Color.White, CircleShape))
        }
    }
}

@Composable
private fun CounterScreen() {
    var counter by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Counter: $counter", color = MaterialTheme.colors.onBackground, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { counter-- },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(text = "-", color = MaterialTheme.colors.onPrimary, fontSize = 24.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
                Text(text = "+", color = MaterialTheme.colors.onPrimary, fontSize = 24.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
        ScreenTitle("State / Remember")

        Text("remember { mutableStateOf(...) } — basic counter:", color = Color.Gray, fontSize = 12.sp)
        var n by remember { mutableStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { n++ }) { Text("Increment", color = MaterialTheme.colors.onPrimary) }
            Text("n = $n", color = MaterialTheme.colors.onBackground, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("remember { mutableStateListOf-ish via remember+List }:", color = Color.Gray, fontSize = 12.sp)
        var items by remember { mutableStateOf(listOf("alpha", "beta", "gamma")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { items = items + "item ${items.size}" }) {
                Text("Add", color = MaterialTheme.colors.onPrimary)
            }
            OutlinedButton(onClick = { if (items.isNotEmpty()) items = items.dropLast(1) }) {
                Text("Remove", color = MaterialTheme.colors.primary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in items) Text("• $i", color = MaterialTheme.colors.onBackground, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("derived state — total = sum of two inputs:", color = Color.Gray, fontSize = 12.sp)
        var aText by remember { mutableStateOf("3") }
        var bText by remember { mutableStateOf("4") }
        val total = (aText.toIntOrNull() ?: 0) + (bText.toIntOrNull() ?: 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = aText, onValueChange = { aText = it }, modifier = Modifier.width(80.dp))
            Text("+", color = MaterialTheme.colors.onBackground, fontSize = 20.sp)
            OutlinedTextField(value = bText, onValueChange = { bText = it }, modifier = Modifier.width(80.dp))
            Text("=", color = MaterialTheme.colors.onBackground, fontSize = 20.sp)
            Text(total.toString(), color = MaterialTheme.colors.primary, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("remember(key) — toggle invalidates remembered value:", color = Color.Gray, fontSize = 12.sp)
        var key by remember { mutableStateOf(0) }
        val rolledOnce = remember(key) { (0..99).random() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { key++ }) { Text("Re-roll", color = MaterialTheme.colors.onPrimary) }
            Text("rolled = $rolledOnce", color = MaterialTheme.colors.onBackground, fontSize = 16.sp)
        }
    }
}

// ==================
// MARK: Interaction (hover / press / focus) screen
// ==================

@Composable
private fun InteractionScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Interaction")

        Text("Hover the button — state-layer overlay appears:", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.onPrimary) }
            OutlinedButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
            TextButton(onClick = {}) { Text("Hover me", color = MaterialTheme.colors.primary) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Press (hold) — deeper overlay; drag off cancels:", color = Color.Gray, fontSize = 12.sp)
        Button(onClick = {}) { Text("Press and hold", color = MaterialTheme.colors.onPrimary) }

        Spacer(modifier = Modifier.height(8.dp))
        Text("TextField focus — border changes color + width on focus:", color = Color.Gray, fontSize = 12.sp)
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = a, onValueChange = { a = it }, label = "First", modifier = Modifier.width(160.dp))
            OutlinedTextField(value = b, onValueChange = { b = it }, label = "Second", modifier = Modifier.width(160.dp))
        }
    }
}

// ==================
// MARK: Recomposition diagnostics screen
// ==================

@Composable
private fun RecompositionScreen() {
    RecompositionLogger("Recomposition/outer")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Recomposition")

        Text(
            "Stdout shows '[tag] recomposed #N' lines. Compose's scope-narrowing " +
            "means clicking + only invalidates the inner scope reading `counter`, " +
            "not the outer App() or the screen wrapper.",
            color = Color.Gray,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))
        var counter by remember { mutableStateOf(0) }

        // The outer logger captures the screen-level recompositions.
        InnerCounterBlock(counter) { counter = it }

        // This sibling doesn't read `counter`, so its logger stays at #1
        // no matter how many times you increment.
        SiblingBlock()
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Colors")
        val c = MaterialTheme.colors
        ColorRow("primary",         c.primary,         c.onPrimary)
        ColorRow("primaryVariant",  c.primaryVariant,  c.onPrimary)
        ColorRow("secondary",       c.secondary,       c.onSecondary)
        ColorRow("background",      c.background,      c.onBackground)
        ColorRow("surface",         c.surface,         c.onSurface)
        ColorRow("error",           c.error,           c.onError)
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
