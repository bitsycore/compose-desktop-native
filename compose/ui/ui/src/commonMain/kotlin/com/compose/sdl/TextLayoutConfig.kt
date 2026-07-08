package com.compose.sdl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ==================
// MARK: TextLayoutConfig
// ==================

/* Global text-layout knobs read by the renderers. `tabWidth` is how many spaces
   a literal '\t' expands to for both measurement and drawing — i.e. the editor's
   "tab size". Snapshot-backed so UI exposing it recomposes when it changes,
   while the per-frame layout/draw pass reads the live value (the renderers key
   tab-containing text by it in their width caches, so a change re-measures
   cleanly rather than returning a stale width). */
object TextLayoutConfig {
	var tabWidth: Int by mutableStateOf(4)
}
