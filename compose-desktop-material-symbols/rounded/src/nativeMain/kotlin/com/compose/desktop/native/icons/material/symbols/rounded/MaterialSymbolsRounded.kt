package com.compose.desktop.native.icons.material.symbols.rounded

import androidx.compose.material.Icon
import androidx.compose.material.IconDefaults
import androidx.compose.material.MaterialIconAxes
import androidx.compose.material.MaterialIconAxisDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRendererCapabilities
import androidx.compose.ui.unit.Dp
import com.compose.desktop.native.icons.IconFont
import com.compose.desktop.native.loadComposeResourceBytes

// ==================
// MARK: MaterialSymbolsRounded
// ==================

/* Rounded style of Material Symbols. Drop the dependency and call
   `MaterialSymbolsRounded(MaterialSymbols.Home)` — the font auto-installs on
   first use. See :outlined for full notes. */
object MaterialSymbolsRounded {

	const val Family: String = "material-symbols-rounded"

	const val DefaultResourcePath: String = "font/MaterialSymbolsRounded.ttf"

	private var fInstalled = false

	fun install(inResourcePath: String = DefaultResourcePath): Boolean {
		if (fInstalled) return true
		val vBytes = loadComposeResourceBytes(inResourcePath)
		if (vBytes == null) {
			println(
				"MaterialSymbolsRounded: font missing at \"$inResourcePath\" in data.kres. " +
				"Add :compose-desktop-material-symbols:rounded to your app's dependencies."
			)
			return false
		}
		IconFont.register(Family, vBytes)
		fInstalled = true
		if (TextRendererCapabilities.supportsFontVariations == false) {
			println(
				"MaterialSymbolsRounded: the active text renderer does not support variable-font " +
				"axes (SDL3_ttf 3.2 has no axis-set API). Icons render at default position; " +
				"fill / weight / grade / opticalSize are ignored. Use Skia for axes."
			)
		}
		return true
	}

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
		Icon(
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
