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

/**
 Upstream ships this via NSDateFormatter (darwinMain) or java.text.DateFormat
 (desktopMain). Neither is available for K/N on Linux/Windows, so this actual is
 a cross-platform kotlinx-datetime formatter that HONOURS the requested
 CLDR-style pattern / skeleton (so DatePicker/TimePicker headlines read
 "Jul 29, 2026" / "July 2026" instead of a raw ISO date). Field VALUES are
 localized by the requested TimeZone.UTC calendar; field NAMES (month / weekday)
 stay English — full CLDR name localization needs ICU data we don't bundle.
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
	): String = formatPattern(utcTimeMillis, pattern)

	actual fun formatWithSkeleton(
		utcTimeMillis: Long,
		skeleton: String,
		cache: MutableMap<String, Any>,
	): String = formatPattern(utcTimeMillis, skeletonToPattern(skeleton))

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

	// ============
	//  Pattern / skeleton formatting

	/** Format [utcTimeMillis] against a CLDR-style [pattern] (y/M/d/E/H/h/m/s/a
	   field runs; `'...'` quotes literal text). */
	private fun formatPattern(utcTimeMillis: Long, pattern: String): String {
		val dt = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC)
		val out = StringBuilder()
		var i = 0
		while (i < pattern.length) {
			val c = pattern[i]
			when {
				c == '\'' -> {
					// Quoted literal; '' is an escaped single quote.
					var j = i + 1
					while (j < pattern.length && pattern[j] != '\'') { out.append(pattern[j]); j++ }
					i = j + 1
				}
				c.isLetter() -> {
					var j = i
					while (j < pattern.length && pattern[j] == c) j++
					out.append(formatField(c, j - i, dt))
					i = j
				}
				else -> { out.append(c); i++ }
			}
		}
		return out.toString()
	}

	private fun formatField(field: Char, count: Int, dt: kotlinx.datetime.LocalDateTime): String = when (field) {
		'y' -> if (count == 2) (dt.year % 100).pad2() else dt.year.toString()
		'M' -> when {
			count >= 4 -> MONTH_NAMES[dt.monthNumber - 1]
			count == 3 -> MONTH_ABBR[dt.monthNumber - 1]
			count == 2 -> dt.monthNumber.pad2()
			else -> dt.monthNumber.toString()
		}
		'd' -> if (count >= 2) dt.dayOfMonth.pad2() else dt.dayOfMonth.toString()
		'E' -> {
			val idx = dt.dayOfWeek.ordinal // kotlinx DayOfWeek: MONDAY=0 → matches weekdayNames order
			if (count >= 4) weekdayNames[idx].first else weekdayNames[idx].second
		}
		'H' -> if (count >= 2) dt.hour.pad2() else dt.hour.toString()
		'h' -> {
			val h12 = ((dt.hour + 11) % 12) + 1
			if (count >= 2) h12.pad2() else h12.toString()
		}
		'm' -> if (count >= 2) dt.minute.pad2() else dt.minute.toString()
		's' -> if (count >= 2) dt.second.pad2() else dt.second.toString()
		'a' -> if (dt.hour < 12) "AM" else "PM"
		else -> "" // unsupported field: drop rather than echo raw pattern letters
	}

	/** Map the CLDR skeletons material3 requests (unordered field sets) to concrete
	   English patterns. Unknown skeletons fall back to the skeleton itself, which
	   still renders the correct field VALUES (just without separators). */
	private fun skeletonToPattern(skeleton: String): String = when (skeleton) {
		"yMMMM" -> "MMMM yyyy"
		"yMMMMd", "yMMMMdd" -> "MMMM d, yyyy"
		"yMMMd" -> "MMM d, yyyy"
		"yMMM" -> "MMM yyyy"
		"yMMMMEEEEd" -> "EEEE, MMMM d, yyyy"
		"yMMMEd" -> "EEE, MMM d, yyyy"
		"MMMMEEEEd" -> "EEEE, MMMM d"
		"MMMMd" -> "MMMM d"
		"MMMd" -> "MMM d"
		"MMMEd" -> "EEE, MMM d"
		"y" -> "yyyy"
		"MMMM" -> "MMMM"
		"Hm" -> "HH:mm"
		"hm", "hma" -> "h:mm a"
		else -> skeleton
	}

	private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"

	private companion object {
		val MONTH_NAMES = listOf(
			"January", "February", "March", "April", "May", "June",
			"July", "August", "September", "October", "November", "December",
		)
		val MONTH_ABBR = listOf(
			"Jan", "Feb", "Mar", "Apr", "May", "Jun",
			"Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
		)
	}
}
