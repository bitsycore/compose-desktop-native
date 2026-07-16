package com.compose.sdl.renderer.sdl

import androidx.compose.ui.text.font.FontVariation

// ==================
// MARK: SdlDisplayList — captured draw commands for retained replay
// ==================

/*
 Phase 4 (RENDERER_REFACTOR.md §3/§13). A display list is a layer's drawing captured
 ONCE in layer-local space and replayed under the layer transform — instead of
 re-tessellating every frame (DeferredRenderNode) or round-tripping through an
 offscreen texture (SdlRenderNode: fixed-resolution soft + timing-nondeterministic).

 It is an ORDERED command stream (z-order preserved) mixing three command kinds, each
 chosen to avoid the expensive recompute the way upstream skiko's Picture + Skia
 caches do, adapted to SDL:
   * GeometryBatch — tessellated untextured triangles (layer-local). SDL has no path/
     tessellation cache, so we cache the vertices (skiko relies on Skia's GPU path
     cache instead). Owned float copy ⇒ always safe.
   * TextRun — a "draw this run" command by PARAMETERS, mirroring skiko recording a
     glyph-run into a Picture. Replay re-looks-up the run texture via the text
     renderer's own per-run LRU (our analog of Skia's glyph atlas); if the cache
     evicted it, the lookup re-rasterises — so it NEVER holds a dangling texture
     pointer. Covers PLAIN text AND spanned text (one TextRun per styled run); only a
     run with a SpanStyle.background still defers (the background fill isn't captured).
   * IconRun — a Material Symbols glyph by PARAMETERS; replay re-draws via FreeTypeIcons'
     own glyph cache (eviction-safe), same lifetime story as TextRun.

 `unsupported` trips on any op not yet capturable (image blits, saveLayer, shadow, a
 rounded/generic LAYER clip, alpha/blend/colorFilter/renderEffect, span backgrounds)
 so the node falls back to a crisp block-replay — nothing un-captured ever leaks to
 the GPU.
*/

internal sealed class DisplayCommand

/* One captured untextured triangle batch in layer-local coords (8 floats/vertex:
   pos.xy, color.rgba, tex.xy — Sdl3DrawScope.fVertexData packing). */
internal class GeometryBatch(
	val vertexData: FloatArray,
	val vertexCount: Int,
) : DisplayCommand()

/* One captured plain text run — replayed by re-looking-up its cached texture
   (eviction-safe) and blitting at the transformed origin (glyph size stays logical,
   matching the immediate path's "layer scale reaches position, not glyph size"). */
internal class TextRun(
	val fontFamily: String?,
	val text: String,
	val fontSizePx: Int,
	val variations: List<FontVariation.Setting>?,
	val style: Int,
	val x: Float,
	val y: Float,
	val w: Float,
	val h: Float,
	val colorArgb: Int,
) : DisplayCommand()

/* One captured icon-font glyph (Material Symbols via FreeType). The box origin is
   layer-local (replay maps it through the layer affine); the box size stays logical
   (the glyph centres in it, like the immediate icon path). Replay re-draws through
   FreeTypeIcons' own glyph cache — eviction-safe (re-rasterises if dropped). */
internal class IconRun(
	val fontFamily: String,
	val text: String,
	val fontSizePx: Int,
	val variations: List<FontVariation.Setting>?,
	val boxX: Float,
	val boxY: Float,
	val boxW: Float,
	val boxH: Float,
	val colorArgb: Int,
) : DisplayCommand()

/* Sink the drawscope + text renderer capture into during a recording. */
internal interface GeometrySink {
	fun captureGeometry(vertexData: FloatArray, vertexCount: Int)
}
internal interface TextRunSink {
	fun captureTextRun(
		fontFamily: String?,
		text: String,
		fontSizePx: Int,
		variations: List<FontVariation.Setting>?,
		style: Int,
		x: Float,
		y: Float,
		w: Float,
		h: Float,
		colorArgb: Int,
	)
	fun markUnsupported()
}

internal class SdlDisplayList : GeometrySink, TextRunSink {
	val commands = ArrayList<DisplayCommand>()
	var unsupported = false

	override fun captureGeometry(vertexData: FloatArray, vertexCount: Int) {
		if (vertexCount <= 0) return
		commands.add(GeometryBatch(vertexData.copyOf(vertexCount * kFloatsPerVertex), vertexCount))
	}

	override fun captureTextRun(
		fontFamily: String?,
		text: String,
		fontSizePx: Int,
		variations: List<FontVariation.Setting>?,
		style: Int,
		x: Float,
		y: Float,
		w: Float,
		h: Float,
		colorArgb: Int,
	) {
		commands.add(TextRun(fontFamily, text, fontSizePx, variations, style, x, y, w, h, colorArgb))
	}

	override fun markUnsupported() {
		unsupported = true
	}

	/* Icon glyphs are captured directly by the canvas (not via a sink) since the
	   canvas owns the icon-draw path. Box origin is layer-local; size logical. */
	fun captureIconRun(
		fontFamily: String,
		text: String,
		fontSizePx: Int,
		variations: List<FontVariation.Setting>?,
		boxX: Float,
		boxY: Float,
		boxW: Float,
		boxH: Float,
		colorArgb: Int,
	) {
		commands.add(IconRun(fontFamily, text, fontSizePx, variations, boxX, boxY, boxW, boxH, colorArgb))
	}

	fun clear() {
		commands.clear()
		unsupported = false
	}
}
