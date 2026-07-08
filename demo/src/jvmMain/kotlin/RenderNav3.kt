import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import screens.Navigation3Screen
import java.io.File

// Headless render of the shared Navigation3Screen against UPSTREAM Compose Desktop —
// a diagnostic to eyeball the JVM output (the windowed app can't be screenshotted here).
// Run: ./gradlew :demojvm:renderNav3
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
	val scene = ImageComposeScene(width = 1000, height = 700, density = Density(1f)) {
		MaterialTheme(colorScheme = darkColorScheme()) {
			Surface(Modifier.fillMaxSize()) { Navigation3Screen() }
		}
	}
	val image = scene.render()
	File("nav3jvm.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
	scene.close()
	println("wrote nav3jvm.png")
}
