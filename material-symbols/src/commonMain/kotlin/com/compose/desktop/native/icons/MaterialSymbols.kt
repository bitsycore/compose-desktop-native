package com.compose.desktop.native.icons

// ==================
// MARK: MaterialSymbols (codepoints)
// ==================

/* Curated subset of Google's Material Symbols codepoints. Style-specific
   font modules (:material-symbols:{outlined,rounded,sharp}) ship the glyphs
   and register a font family with IconFont. The codepoint values are
   identical across the three styles, so they live in :core and any
   installed style renders them.

   Usage:

       import com.compose.desktop.native.icons.MaterialSymbols
       import com.compose.desktop.native.icons.material.symbols.outlined.MaterialSymbolsOutlined

       // once at startup:
       MaterialSymbolsOutlined.install()

       // anywhere:
       Icon(
           codepoint = MaterialSymbols.Home,
           fontFamily = MaterialSymbolsOutlined.Family,
           contentDescription = "Home",
       )

   The full Material Symbols set has ~3500 icons; this list is the common
   subset. To use any icon not listed, pass its hex codepoint directly:

       Icon(codepoint = 0xe88a, fontFamily = ...)
*/
object MaterialSymbols {

	// ============
	//  Navigation
	const val ArrowBack       = 0xe5c4
	const val ArrowForward    = 0xe5c8
	const val ArrowUpward     = 0xe5d8
	const val ArrowDownward   = 0xe5db
	const val ChevronLeft     = 0xe5cb
	const val ChevronRight    = 0xe5cc
	const val ExpandLess      = 0xe5ce
	const val ExpandMore      = 0xe5cf
	const val ArrowDropDown   = 0xe5c5
	const val ArrowDropUp     = 0xe5c7
	const val UnfoldMore      = 0xe5d7
	const val Menu            = 0xe5d2
	const val Close           = 0xe5cd

	// ============
	//  Actions
	const val Add             = 0xe145
	const val Remove          = 0xe15b
	const val Check           = 0xe5ca
	const val Clear           = 0xe14c
	const val Delete          = 0xe872
	const val Edit            = 0xe3c9
	const val Save            = 0xe161
	const val Send            = 0xe163
	const val Search          = 0xe8b6
	const val Refresh         = 0xe5d5
	const val Share           = 0xe80d
	const val Settings        = 0xe8b8
	const val MoreVert        = 0xe5d4
	const val MoreHoriz       = 0xe5d3
	const val Download        = 0xf090
	const val Upload          = 0xf09b
	const val Block           = 0xe14b
	const val ContentCopy     = 0xe14d
	const val ContentCut      = 0xe14e
	const val ContentPaste    = 0xe14f
	const val FileCopy        = 0xe173
	const val Undo            = 0xe166
	const val Redo            = 0xe15a
	const val Tune            = 0xe429
	const val Terminal        = 0xeb8e

	// ============
	//  Status / feedback
	const val Info            = 0xe88e
	const val Warning         = 0xe002
	const val Error           = 0xe000
	const val CheckCircle     = 0xe86c
	const val Cancel          = 0xe5c9
	const val Done            = 0xe876
	const val Lock            = 0xe897

	// ============
	//  Toggleables
	const val Favorite        = 0xe87d
	const val FavoriteBorder  = 0xe87e
	const val Star            = 0xe838
	const val StarBorder      = 0xe83a
	const val Visibility      = 0xe8f4
	const val VisibilityOff   = 0xe8f5
	const val CheckBox             = 0xe834
	const val CheckBoxOutlineBlank = 0xe835
	const val RadioButtonChecked   = 0xe837
	const val RadioButtonUnchecked = 0xe836

	// ============
	//  Content / common nouns
	const val Home            = 0xe88a
	const val Person          = 0xe7fd
	const val Email           = 0xe0be
	const val Folder          = 0xe2c7
	const val InsertDriveFile = 0xe24d
	const val Image           = 0xe3f4
	const val PhotoCamera     = 0xe412
	const val Mic             = 0xe029
	const val VolumeUp        = 0xe050
	const val VolumeOff       = 0xe04f
	const val PlayArrow       = 0xe037
	const val Pause           = 0xe034
	const val Stop            = 0xe047
	const val Notifications   = 0xe7f4
}
