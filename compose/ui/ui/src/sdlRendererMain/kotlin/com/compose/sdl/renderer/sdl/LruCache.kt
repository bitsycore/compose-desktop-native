package com.compose.sdl.renderer.sdl

// ==================
// MARK: LruCache
// ==================

/* Minimal insertion-order LRU for renderer-side caches (glyph textures, icon
   bitmaps). Backed by a LinkedHashMap; a hit re-inserts the entry so eviction
   always removes the least-recently-used one. onEvict runs for every value
   dropped (capacity eviction, overwrite, clear) — used to SDL_DestroyTexture
   GPU-side resources so a long session can't grow VRAM without bound. */
internal class LruCache<K, V>(
	private val fMaxSize: Int,
	private val fOnEvict: (V) -> Unit = {},
) {
	private val fMap = LinkedHashMap<K, V>()

	val size: Int get() = fMap.size
	val values: Collection<V> get() = fMap.values

	operator fun get(inKey: K): V? {
		val vValue = fMap.remove(inKey) ?: return null
		fMap[inKey] = vValue
		return vValue
	}

	fun containsKey(inKey: K): Boolean = fMap.containsKey(inKey)

	operator fun set(inKey: K, inValue: V) {
		val vOld = fMap.remove(inKey)
		if (vOld != null && vOld !== inValue) fOnEvict(vOld)
		fMap[inKey] = inValue
		while (fMap.size > fMaxSize) {
			val vEldest = fMap.keys.first()
			fMap.remove(vEldest)?.let(fOnEvict)
		}
	}

	fun clear() {
		for (vValue in fMap.values) fOnEvict(vValue)
		fMap.clear()
	}
}
