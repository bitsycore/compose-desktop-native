package androidx.compose.ui.text.intl

// ==================
// MARK: PlatformLocaleDelegate native actual
// ==================

/**
 * Returns a `PlatformLocaleDelegate` whose `current` is the OS preferred-locale
 * list, ordered by preference, from SDL (see
 * com.compose.sdl.text.systemPreferredLocaleTags). Falls back to a single-entry
 * `LocaleList("en-US")` before SDL init or on a host that reports none.
 *
 * Skiko's actual upstream walks `org.jetbrains.skiko.Locale.current`; we go
 * through SDL so the same read serves every native leg (macOS/linux/windows).
 */
internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
	object : PlatformLocaleDelegate {
		override val current: LocaleList
			get() {
				val tags = com.compose.sdl.text.systemPreferredLocaleTags()
				return if (tags.isEmpty()) LocaleList(listOf(Locale("en-US")))
				else LocaleList(tags.map(::Locale))
			}
	}
