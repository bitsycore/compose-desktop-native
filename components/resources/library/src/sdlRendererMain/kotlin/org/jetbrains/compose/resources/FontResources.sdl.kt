@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.compose.sdl.icons.IconFont
import com.compose.sdl.text.NamedFont

// ==================
// MARK: Font actuals — SDL renderer
// ==================

/* The SDL text stack resolves fonts by FAMILY NAME through the project font
   registry (IconFont.registerIcon — the general byte-font table, not just
   icons). A font resource therefore loads its bytes through the resource
   reader, registers them under a per-resource family name, and returns a
   [NamedFont] — `FontFamily(Font(Res.font.x))` then renders through the
   standard project pipeline. Weight/style are carried on the NamedFont;
   variation axes apply at draw time via the renderer's axis support. */

private val emptyFont: Font = NamedFont("res-font:<loading>")

@Deprecated(
	message = "Use the new Font function with variationSettings instead.",
	level = DeprecationLevel.HIDDEN,
)
@Composable
actual fun Font(resource: FontResource, weight: FontWeight, style: FontStyle): Font {
	val resourceReader = LocalResourceReader.currentOrPreview
	val font by rememberResourceState(resource, weight, style, { emptyFont }) { env ->
		val path = resource.getResourceItemByEnvironment(env).path
		val family = "res-font:$path"
		IconFont.registerIcon(family, resourceReader.read(path))
		NamedFont(family, weight, style)
	}
	return font
}

@Composable
actual fun Font(
	resource: FontResource,
	weight: FontWeight,
	style: FontStyle,
	variationSettings: FontVariation.Settings,
): Font {
	val resourceReader = LocalResourceReader.currentOrPreview
	val font by rememberResourceState(resource, weight, style, variationSettings, { emptyFont }) { env ->
		val path = resource.getResourceItemByEnvironment(env).path
		val family = "res-font:$path"
		IconFont.registerIcon(family, resourceReader.read(path))
		NamedFont(family, weight, style, variationSettings)
	}
	return font
}
