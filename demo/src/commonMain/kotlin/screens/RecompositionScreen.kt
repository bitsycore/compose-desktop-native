package screens

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*

// ==================
// MARK: Recomposition diagnostics screen
// ==================

@Composable
internal fun RecompositionScreen() {
    trackRecomposition("Recomposition/outer")

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

class RecompositionTracker(private val tag: String) : RememberObserver {
    var count: Int = 0
        private set

    fun record() {
        ++count
        println("$tag: Recomposed: #$count")
    }

    override fun onRemembered() {
        println("[$tag] entered composition")
    }

    override fun onForgotten() {
        println("[$tag] left composition (after $count recompositions)")
    }

    override fun onAbandoned() {
        println("[$tag] abandoned (composition rolled back before commit)")
    }
}


@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun trackRecomposition(tag: String) {
    val inst = remember(tag) { RecompositionTracker(tag) }
    SideEffect { inst.record() }
}

@Composable
private fun InnerCounterBlock(counter: Int, onChange: (Int) -> Unit) {
    trackRecomposition("Recomposition/inner")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onChange(counter + 1) }) { Text("+", color = MaterialTheme.colorScheme.onPrimary) }
            Text("Counter: $counter", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SiblingBlock() {
    trackRecomposition("Recomposition/sibling")
    Text(
        "This block doesn't read the counter — its log stays at #1.",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 12.sp,
    )
}
