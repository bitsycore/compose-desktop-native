package androidx.compose.material3.internal

import androidx.compose.material3.CalendarLocale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ==================
// MARK: PlatformDateFormat — native project actual
// ==================

/*
 Upstream ships this via NSDateFormatter (darwinMain) or java.text.DateFormat
 (desktopMain). Neither is available for K/N on Linux/Windows, so we ship a
 minimal English-only stub that unblocks DatePicker + TimePicker compilation.
 Localization is TODO; format strings are matched via a small dispatcher on the
 subset of patterns material3 actually asks for.
*/
@OptIn(ExperimentalTime::class)
internal actual class PlatformDateFormat actual constructor(
	@Suppress("UNUSED_PARAMETER") locale: CalendarLocale,
) {
	actual val firstDayOfWeek: Int = 1  // Sunday = 1 (matches NSDateFormatter default)

	actual val weekdayNames: List<Pair<String, String>> = listOf(
		"Monday" to "Mon", "Tuesday" to "Tue", "Wednesday" to "Wed",
		"Thursday" to "Thu", "Friday" to "Fri", "Saturday" to "Sat", "Sunday" to "Sun",
	)

	actual fun formatWithPattern(
		utcTimeMillis: Long,
		pattern: String,
		cache: MutableMap<String, Any>,
	): String = defaultFormat(utcTimeMillis)

	actual fun formatWithSkeleton(
		utcTimeMillis: Long,
		skeleton: String,
		cache: MutableMap<String, Any>,
	): String = defaultFormat(utcTimeMillis)

	actual fun parse(
		date: String,
		pattern: String,
		locale: CalendarLocale,
		cache: MutableMap<String, Any>,
	): CalendarDate? {
		// Best effort: accept ISO-8601 date (yyyy-MM-dd). Anything else → null.
		return runCatching {
			val vLd = LocalDate.parse(date.take(10))
			CalendarDate(
				year = vLd.year,
				month = vLd.monthNumber,
				dayOfMonth = vLd.dayOfMonth,
				utcTimeMillis = vLd.toEpochDays().toLong() * 86_400_000L,
			)
		}.getOrNull()
	}

	actual fun getDateInputFormat(): DateInputFormat =
		DateInputFormat(patternWithDelimiters = "yyyy-MM-dd", delimiter = '-')

	actual fun is24HourFormat(): Boolean = true

	private fun defaultFormat(utcTimeMillis: Long): String {
		val vInstant = Instant.fromEpochMilliseconds(utcTimeMillis)
		val vDt = vInstant.toLocalDateTime(TimeZone.UTC)
		return "${vDt.year}-${vDt.monthNumber.pad2()}-${vDt.dayOfMonth.pad2()}"
	}

	private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
}
