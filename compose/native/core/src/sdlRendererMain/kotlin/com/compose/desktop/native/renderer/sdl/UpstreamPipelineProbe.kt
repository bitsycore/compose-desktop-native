package com.compose.desktop.native.renderer.sdl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import com.compose.desktop.native.modifier.pressable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.MeasurePolicy
import com.compose.desktop.native.node.impl.ComposeOwner
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.compose.desktop.native.GpuMode
import com.compose.desktop.native.SDL3Backend
import com.compose.desktop.native.createRenderBackend
import kotlinx.cinterop.*
import platform.posix.*
import sdl3.*

// ==================
// MARK: UpstreamPipelineProbe
// ==================

/*
 Phase 9 B4 — end-to-end proof of the upstream render pipeline WITHOUT the full
 composition/ComposeWindow pivot. Hand-builds a tiny upstream `LayoutNode` tree
 (a dark root background with a red child box), drives it through the real
 `ComposeOwner` (→ vendored `MeasureAndLayoutDelegate`), then paints it with
 `owner.root.draw(Sdl3Canvas)` — the exact path B4 will wire into ComposeWindow.
 Screenshots the result to prove pillars B1 (draw engine) + B3 (Owner) +
 B4-infra (Sdl3Canvas) actually produce pixels together.

 Invoked from the demo via `--pipetest=<path.bmp>`.
*/
@OptIn(ExperimentalForeignApi::class)
fun runUpstreamPipelineProbe(inScreenshotPath: String) {
	val vBackend = SDL3Backend("Upstream Pipeline Probe", 800, 600, gpuMode = GpuMode.Sdl3.Auto)
	if (!vBackend.init()) { println("probe: SDL init failed"); return }
	vBackend.updateWindowSize()

	val vRb = createRenderBackend(vBackend, GpuMode.Sdl3.Auto)
	if (vRb == null || !vRb.ensureSize(vBackend.pixelWidth, vBackend.pixelHeight)) {
		println("probe: render backend init failed"); vBackend.destroy(); return
	}

	val vW = vBackend.windowWidth
	val vH = vBackend.windowHeight

	// Build the upstream tree: root (dark bg) → box (red), placed at (60,60).
	val vBox = LayoutNode().apply {
		measurePolicy = MeasurePolicy { _, _ ->
			layout(240, 140) {}
		}
		modifier = Modifier.background(Color(0xFFE53935))
	}
	val vRoot = LayoutNode().apply {
		measurePolicy = MeasurePolicy { inMeasurables, inConstraints ->
			val vPlaceables = inMeasurables.map { it.measure(Constraints()) }
			layout(inConstraints.maxWidth, inConstraints.maxHeight) {
				vPlaceables.forEach { it.place(60, 60) }
			}
		}
		modifier = Modifier.background(Color(0xFF202028))
	}
	vRoot.insertAt(0, vBox)

	val vOwner = ComposeOwner(vRoot, density = Density(1f))
	vOwner.attach()
	vOwner.setRootConstraints(Constraints.fixed(vW, vH))
	vOwner.measureAndLayout()

	// Draw a few frames so the window is realised, screenshotting the last.
	var vFrame = 0
	while (vFrame < 4) {
		// Drain quit events so the window is responsive.
		val vEv = nativeHeap.alloc<SDL_Event>()
		while (SDL_PollEvent(vEv.ptr)) { /* discard */ }
		nativeHeap.free(vEv)

		vRb.beginFrame(vBackend.pixelDensity)
		val vCanvas = Sdl3Canvas(vBackend.renderer!!, Size(vW.toFloat(), vH.toFloat()))
		vRoot.draw(vCanvas, null)
		vCanvas.finish()

		if (vFrame == 3) {
			val vShot = vRb.snapshotBgra()
			if (vShot != null) {
				writeBmp(inScreenshotPath, vShot.first, vShot.second, vShot.third)
				println("probe: wrote ${vShot.first}x${vShot.second} screenshot to $inScreenshotPath")
			} else {
				println("probe: snapshot failed")
			}
		}
		vRb.endFrame()
		SDL_Delay(16u)
		vFrame++
	}

	vRb.destroy()
	vBackend.destroy()
}

// ==================
// MARK: Input probe (B6a)
// ==================

/*
 Headless verification of B6a click dispatch: builds a ComposeRootHost with a clickable
 100x50 box, simulates a press+release at its centre via ComposeRootHost.onPointer, and
 reports whether the click callback fired — proving hit-test + chain dispatch on the
 upstream tree. Invoked via `--inputtest`.
*/
@OptIn(ExperimentalForeignApi::class)
fun runInputProbe() {
	val vHost = com.compose.desktop.native.node.ComposeRootHost(1f)
	vHost.attach()
	var vClicked = false
	var vPressed = false
	val vBox = LayoutNode().apply {
		measurePolicy = MeasurePolicy { _, _ -> layout(100, 50) {} }
		modifier = Modifier.pressable { p -> vPressed = p }.clickable { vClicked = true }
	}
	vHost.rootNode.insertAt(0, vBox)
	vHost.setConstraints(800, 600)
	vHost.measureAndLayout()

	vHost.onPointer(50f, 25f, 1, 0) // press at box centre
	println("inputtest: pressable fired on press = $vPressed")
	vHost.onPointer(50f, 25f, 2, 0) // release at box centre
	println("inputtest: clickable fired on release = $vClicked")
	println(if (vClicked) "inputtest: PASS" else "inputtest: FAIL")

	// Drive the vendored PointerInputEventProcessor (upstream dispatch) — verify it
	// doesn't crash even with no PointerInputModifierNode consumers yet.
	vHost.onPointerRaw(50f, 25f, 0, 0, 100L) // move
	vHost.onPointerRaw(50f, 25f, 1, 0, 101L) // press
	vHost.onPointerRaw(50f, 25f, 2, 0, 102L) // release
	vHost.onPointerRaw(400f, 400f, 0, 0, 103L) // move away
	println("inputtest: pointer processor OK (no crash)")
}

// Minimal 32-bit BGRA BMP writer (top-down via negative height).
@OptIn(ExperimentalForeignApi::class)
private fun writeBmp(inPath: String, inW: Int, inH: Int, inBgra: ByteArray) {
	val vRowBytes = inW * 4
	val vImgSize = vRowBytes * inH
	val vFileSize = 54 + vImgSize
	val vHeader = ByteArray(54)
	fun putI32(inOff: Int, inV: Int) {
		vHeader[inOff] = (inV and 0xFF).toByte()
		vHeader[inOff + 1] = ((inV shr 8) and 0xFF).toByte()
		vHeader[inOff + 2] = ((inV shr 16) and 0xFF).toByte()
		vHeader[inOff + 3] = ((inV shr 24) and 0xFF).toByte()
	}
	fun putI16(inOff: Int, inV: Int) {
		vHeader[inOff] = (inV and 0xFF).toByte()
		vHeader[inOff + 1] = ((inV shr 8) and 0xFF).toByte()
	}
	vHeader[0] = 'B'.code.toByte(); vHeader[1] = 'M'.code.toByte()
	putI32(2, vFileSize)
	putI32(10, 54)          // pixel data offset
	putI32(14, 40)          // DIB header size
	putI32(18, inW)
	putI32(22, -inH)        // negative → top-down
	putI16(26, 1)           // planes
	putI16(28, 32)          // bpp
	putI32(34, vImgSize)

	val vFile = fopen(inPath, "wb") ?: run { println("probe: fopen failed for $inPath"); return }
	try {
		vHeader.usePinned { fwrite(it.addressOf(0), 1u, 54u, vFile) }
		inBgra.usePinned { fwrite(it.addressOf(0), 1u, vImgSize.toULong(), vFile) }
	} finally {
		fclose(vFile)
	}
}
