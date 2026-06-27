package apidemo

import androidx.compose.ui.graphics.Color

// ==================
// MARK: App-wide constants + pure color / asset mappers
// ==================

// Amber used to flag undefined {{variables}} and unsaved-changes warnings.
internal val kWarnColor = Color(0xFFFFAB00)

// SDL scancodes used for app keyboard shortcuts.
internal const val kScS = 22
internal const val kScN = 17
internal const val kScW = 26
internal const val kScEnter = 40
internal const val kScKpEnter = 88
internal const val kScEscape = 41

internal fun methodColor(inM: ReqMethod): Color = when (inM) {
    ReqMethod.GET -> Color(0xFF4C9AFF)
    ReqMethod.POST -> Color(0xFF36B37E)
    ReqMethod.PUT -> Color(0xFFFF991F)
    ReqMethod.PATCH -> Color(0xFF00B8D9)
    ReqMethod.DELETE -> Color(0xFFFF5630)
    ReqMethod.HEAD, ReqMethod.OPTIONS -> Color(0xFF8777FF)
}

// 20-colour palette for the per-pack box icon. Pack.color is a 1-based index
// into this list (0 = none → a neutral dot). Two rows of ten in the picker.
internal val kPackColors: List<Long> = listOf(
    0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2, 0xFF5C6BC0,
    0xFF42A5F5, 0xFF29B6F6, 0xFF26C6DA, 0xFF26A69A, 0xFF66BB6A,
    0xFF9CCC65, 0xFFD4E157, 0xFFFFEE58, 0xFFFFCA28, 0xFFFFA726,
    0xFFFF7043, 0xFF8D6E63, 0xFF78909C, 0xFFBDBDBD, 0xFF5C7CFA,
)

/* The palette colour for a 1-based pack-colour index, or null when unset. */
internal fun packColor(inIndex: Int): Color? =
    if (inIndex in 1..kPackColors.size) Color(kPackColors[inIndex - 1]) else null

/* Default save name for an image response, by content type. */
internal fun imageFileName(inContentType: String?): String = when {
    inContentType == null -> "image.bin"
    inContentType.contains("png", ignoreCase = true) -> "image.png"
    inContentType.contains("jpeg", ignoreCase = true) || inContentType.contains("jpg", ignoreCase = true) -> "image.jpg"
    inContentType.contains("gif", ignoreCase = true) -> "image.gif"
    inContentType.contains("webp", ignoreCase = true) -> "image.webp"
    inContentType.contains("svg", ignoreCase = true) -> "image.svg"
    else -> "image.bin"
}

internal fun statusColor(inStatus: Int): Color = when (inStatus) {
    in 200..299 -> Color(0xFF36B37E) // success — green
    in 300..399 -> Color(0xFF4C9AFF) // redirect — blue
    in 400..499 -> Color(0xFFFF991F) // client error — orange (warning)
    in 500..599 -> Color(0xFFFF5630) // server error — red
    else -> Color(0xFFFF991F)        // unknown / pending — orange
}
