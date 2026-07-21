@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ExperimentalResourceApi

import com.compose.sdl.loadComposeResourceBytes

// ==================
// MARK: ResourceReader — data.kres actual
// ==================

/** The platform reader for this port: every app ships its composeResources
   content inside data.kres (a STORED zip next to the executable — see the
   apps' Zip tasks), and :ui's ResourceIO serves entries by exact path with an
   fseek+fread. The paths the generated Res accessors produce
   ("composeResources/<package>/drawable/x.png") are stored verbatim in
   data.kres, so no mapping is needed. */
private object KresResourceReader : ResourceReader {
	override suspend fun read(path: String): ByteArray =
		loadComposeResourceBytes(path) ?: throw MissingResourceException(path)

	override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
		val vAll = loadComposeResourceBytes(path) ?: throw MissingResourceException(path)
		val vFrom = offset.toInt().coerceIn(0, vAll.size)
		val vTo = (offset + size).toInt().coerceIn(vFrom, vAll.size)
		return vAll.copyOfRange(vFrom, vTo)
	}

	override fun getUri(path: String): String = "kres:///$path"
}

internal actual fun getPlatformResourceReader(): ResourceReader = KresResourceReader
