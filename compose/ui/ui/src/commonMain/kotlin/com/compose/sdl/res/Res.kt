package com.compose.sdl.res

// ==================
// MARK: Res
// ==================

/* Resource accessor root. The library provides the namespaces + readBytes;
   the per-module typed accessors are emitted by the generateComposeResAccessors
   Gradle task as extension properties on these objects, e.g.

       val Res.drawable.logo: Painter get() = painterResource("drawable/logo.png")
       val Res.files.data_bin: String get() = "files/data.bin"

   so consumers write Res.drawable.logo / Res.readBytes(Res.files.data_bin)
   with compile-time-checked names. */
object Res {
	object drawable
	object files

	/* Reads a bundled resource's raw bytes by path relative to
	   composeResources/ (e.g. Res.readBytes("files/data.bin")). Returns null if
	   the resource is missing or no loader is installed. */
	fun readBytes(inPath: String): ByteArray? = currentImageLoader.readBytes(inPath)
}
