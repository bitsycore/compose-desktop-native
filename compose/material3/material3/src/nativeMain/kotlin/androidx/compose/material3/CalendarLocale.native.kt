package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// ==================
// MARK: CalendarLocale — native project actuals
// ==================

/**
 Upstream's darwinMain / desktopMain provide these actuals from platform locale
 APIs (NSLocale on darwin, java.util.Locale on JVM). Our K/N native target has
 neither, so we ship a locale-agnostic stub — a single implicit "current" locale
 the DatePicker / TimePicker use for formatting. All weekdays / month names
 rendered by material3 use its own l10n tables (vendored under
 material3.internal.l10n) keyed by this stub's identity, which resolves to the
 English translation until we wire real platform locale lookup.
*/

actual class CalendarLocale internal constructor() {
	override fun equals(other: Any?): Boolean = other is CalendarLocale
	override fun hashCode(): Int = 0
	override fun toString(): String = "en"
}

private val kDefaultCalendarLocale = CalendarLocale()

@Composable
@ReadOnlyComposable
internal actual fun defaultLocale(): CalendarLocale = kDefaultCalendarLocale
