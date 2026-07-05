package com.compose.desktop.native.icons.material.symbols.sharp

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
// MARK: MaterialSymbolsSharp
// ==================

/* Sharp style of Material Symbols. Drop the dependency and call
   `MaterialSymbolsSharp(MaterialSymbols.Home)` — the font auto-installs on
   first use. See :outlined for full notes. */
object MaterialSymbolsSharp {

	const val Family: String = "material-symbols-sharp"

	const val DefaultResourcePath: String = "font/MaterialSymbolsSharp.ttf"

	private var fInstalled = false

	fun install(inResourcePath: String = DefaultResourcePath): Boolean {
		if (fInstalled) return true
		val vBytes = loadComposeResourceBytes(inResourcePath)
		if (vBytes == null) {
			println(
				"MaterialSymbolsSharp: font missing at \"$inResourcePath\" in data.kres. " +
				"Add :material-symbols:sharp to your app's dependencies."
			)
			return false
		}
		IconFont.registerIcon(Family, vBytes)
		fInstalled = true
		if (TextRendererCapabilities.supportsFontVariations == false) {
			println(
				"MaterialSymbolsSharp: the active text renderer does not support variable-font " +
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
