package com.compose.desktop.native.icons.material.symbols.outlined

import com.compose.desktop.native.icons.IconFontIcon
import com.compose.desktop.native.icons.IconDefaults
import com.compose.desktop.native.icons.MaterialIconAxes
import com.compose.desktop.native.icons.MaterialIconAxisDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.compose.desktop.native.text.TextRendererCapabilities
import androidx.compose.ui.unit.Dp
import com.compose.desktop.native.icons.IconFont
import com.compose.desktop.native.loadComposeResourceBytes

// ==================
// MARK: MaterialSymbolsOutlined
// ==================

/* The Outlined style of Material Symbols. The variable font ships in this
   module's own composeResources/font/ — the app's build merges it into
   data.kres alongside the app's own assets, so depending on this module is
   all that's needed. The first call into MaterialSymbolsOutlined(...)
   auto-registers the font with IconFont; an explicit install() is still
   available for callers that want to control the timing (e.g. preload at
   startup so the first render doesn't pay the registration cost).

   Usage:

       MaterialSymbolsOutlined(MaterialSymbols.Home)
       MaterialSymbolsOutlined(MaterialSymbols.Favorite, fill = 1f, weight = 700)

   The four variable-font axes (fill / weight / grade / opticalSize) are
   exposed as direct parameters so the API reads like a regular Icon. Skia
   honours them; SDL3_ttf 3.2 ignores them.

   Rounded and Sharp styles ship in sibling modules
   (:material-symbols:rounded / :material-symbols:sharp) and register under
   different family strings, so an app can install more than one if it
   needs to mix styles. */
object MaterialSymbolsOutlined {

	/* Family name used by IconFont and the fontFamily parameter on Icon / Text. */
	const val Family: String = "material-symbols-outlined"

	/* Default font path inside data.kres. Override if you renamed the file. */
	const val DefaultResourcePath: String = "font/MaterialSymbolsOutlined.ttf"

	private var fInstalled = false

	/* Loads the font bytes from the application's bundled resources and
	   registers them with IconFont under [Family]. Idempotent — subsequent
	   calls return immediately. Returns true on success, false (with a log
	   line) if the font file isn't bundled. Called automatically by the
	   `invoke` composable below the first time it runs; you only need to
	   call this directly if you want to control when the font is loaded. */
	fun install(inResourcePath: String = DefaultResourcePath): Boolean {
		if (fInstalled) return true
		val vBytes = loadComposeResourceBytes(inResourcePath)
		if (vBytes == null) {
			println(
				"MaterialSymbolsOutlined: font missing at \"$inResourcePath\" in data.kres. " +
				"Add :material-symbols:outlined to your app's dependencies."
			)
			return false
		}
		IconFont.registerIcon(Family, vBytes)
		fInstalled = true
		warnIfRendererSkipsAxes()
		return true
	}

	/* One-shot warning when the active text renderer can't apply the
	   variable-font axes that MaterialSymbolsOutlined(...) exposes. Silent
	   on Skia (axes work) and silent if no renderer has initialised yet
	   (install() called before composeWindow() — values become accurate
	   once the auto-install path runs from inside the composition). */
	private fun warnIfRendererSkipsAxes() {
		if (TextRendererCapabilities.supportsFontVariations == false) {
			println(
				"MaterialSymbolsOutlined: the active text renderer does not support variable-font " +
				"axes (SDL3_ttf 3.2 has no axis-set API). Icons will render at the font's default " +
				"position (wght=400, FILL=0, GRAD=0, opsz=24); fill / weight / grade / opticalSize " +
				"parameters are ignored. Use the Skia renderer (default on macOS/Linux) for axes."
			)
		}
	}

	/* Compose-style icon entry point. Resolves to an Icon backed by the
	   Material Symbols Outlined font; fill / weight / grade / opticalSize
	   feed the OpenType variable axes. Auto-installs the font on first use. */
	@Composable
	operator fun invoke(
		icon: Int,
		contentDescription: String? = null,
		modifier: Modifier = Modifier,
		tint: Color = IconDefaults.LocalContentColor,
		size: Dp = IconDefaults.DefaultIconSize,
		fill: Float = MaterialIconAxisDefaults.Fill,
		weight: Int = MaterialIconAxisDefaults.Weight,
		grade: Int = MaterialIconAxisDefaults.Grade,
		opticalSize: Int = MaterialIconAxisDefaults.OpticalSize,
	) {
		install()
		IconFontIcon(
			codepoint = icon,
			fontFamily = Family,
			contentDescription = contentDescription,
			modifier = modifier,
			tint = tint,
			size = size,
			fontVariationSettings = MaterialIconAxes(fill, weight, grade, opticalSize),
		)
	}
}
