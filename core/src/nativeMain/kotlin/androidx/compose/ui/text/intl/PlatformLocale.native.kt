package androidx.compose.ui.text.intl

// ==================
// MARK: PlatformLocaleDelegate native actual
// ==================

/*
 * Returns a `PlatformLocaleDelegate` whose `current` is a single-entry
 * `LocaleList("en-US")`. We have no platform-specific locale source on
 * linux/windows, and macOS doesn't need NSLocale wiring until the
 * renderer cares about locale-aware text layout.
 *
 * Skiko's actual upstream walks `org.jetbrains.skiko.Locale.current` —
 * we stay platform-free here for portability and simplicity.
 */
internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
	object : PlatformLocaleDelegate {
		override val current: LocaleList = LocaleList(listOf(Locale("en-US")))
	}
