import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

// ==================
// MARK: RecompositionLogger (demo-only debug helper)
// ==================

/* Debug helper. Drop into any composable to log when it (re)composes:

       @Composable fun App() {
           RecompositionLogger("App")
           // ...
       }

   remember { intArrayOf(0) } survives recompositions so the counter is
   stable; SideEffect runs once per successful composition. The IntArray
   sidesteps writing to a mutableStateOf from inside SideEffect (which would
   itself trigger another recomposition). */
@Composable
fun RecompositionLogger(tag: String) {
    val counter = remember { intArrayOf(0) }
    SideEffect {
        counter[0]++
        println("[$tag] recomposed #${counter[0]}")
    }
}
