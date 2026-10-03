package com.markazsayana.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val cairoZone: ZoneId = ZoneId.of("Africa/Cairo")
private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/** Converts any western 0-9 digits in [this] to Arabic-Indic digits, matching the design's numerals. */
fun String.toArabicDigits(): String = buildString {
    for (c in this@toArabicDigits) {
        append(if (c in '0'..'9') arabicDigits[c - '0'] else c)
    }
}

fun Int.arabic(): String = toString().toArabicDigits()

/** e.g. 364.8 -> "٣٦٤٫٨٠" using the Arabic decimal separator (U+066B) as specified. */
fun Double.arabicDecimal(decimals: Int = 2): String {
    val rounded = "%.${decimals}f".format(Locale.US, this)
    val (whole, frac) = rounded.split(".")
    return "${whole.toArabicDigits()}٫${frac.toArabicDigits()}"
}

/** e.g. 364.8 -> "٣٦٤٫٨٠ ج.م" */
fun Double.egp(): String = "${arabicDecimal()} ج.م"

/** e.g. 486500.0 -> "٤٨٦٫٥ ألف ج.م" for compact revenue display. */
fun Double.egpCompact(): String {
    if (this >= 1000) {
        val thousands = this / 1000.0
        val text = if (thousands == thousands.roundToInt().toDouble()) {
            thousands.roundToInt().toString().toArabicDigits()
        } else {
            "%.1f".format(Locale.US, thousands).replace(".", "٫").toArabicDigits()
        }
        return "$text ألف ج.م"
    }
    return egp()
}

private val arabicWeekdays = mapOf(
    java.time.DayOfWeek.SATURDAY to "السبت",
    java.time.DayOfWeek.SUNDAY to "الأحد",
    java.time.DayOfWeek.MONDAY to "الاثنين",
    java.time.DayOfWeek.TUESDAY to "الثلاثاء",
    java.time.DayOfWeek.WEDNESDAY to "الأربعاء",
    java.time.DayOfWeek.THURSDAY to "الخميس",
    java.time.DayOfWeek.FRIDAY to "الجمعة",
)

private val arabicMonths = mapOf(
    1 to "يناير", 2 to "فبراير", 3 to "مارس", 4 to "أبريل", 5 to "مايو", 6 to "يونيو",
    7 to "يوليو", 8 to "أغسطس", 9 to "سبتمبر", 10 to "أكتوبر", 11 to "نوفمبر", 12 to "ديسمبر",
)

fun Instant.toCairo(): ZonedDateTime = this.atZone(cairoZone)

fun ZonedDateTime.weekdayLabel(): String = arabicWeekdays[dayOfWeek] ?: dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))

fun ZonedDateTime.dateLabel(): String = "${weekdayLabel()} ${dayOfMonth.arabic()} ${arabicMonths[monthValue]}"

/** e.g. 13:00 -> "٠١:٠٠" (12h, zero-padded, no am/pm suffix — matches technician task list rows). */
fun ZonedDateTime.timeShort(): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "%02d:%02d".format(h12, minute).toArabicDigits()
}

/** e.g. 13:00 -> "٠١:٠٠ م" (12h with ص/م period suffix). */
fun ZonedDateTime.timeWithPeriod(): String {
    val period = if (hour < 12) "ص" else "م"
    return "${timeShort()} $period"
}

/** e.g. 10:30 -> "صباحاً" / 13:00 -> "مساءً" as its own line (reception dashboard appointment cards). */
fun ZonedDateTime.periodLabelLong(): String = if (hour < 12) "صباحاً" else "مساءً"

fun Instant.dateLabelCairo(): String = toCairo().dateLabel()

fun currentMonthYearLabelCairo(): String {
    val now = ZonedDateTime.now(cairoZone)
    return "${arabicMonths[now.monthValue]} ${now.year.arabic()}"
}
fun Instant.timeShortCairo(): String = toCairo().timeShort()
fun Instant.timeWithPeriodCairo(): String = toCairo().timeWithPeriod()
fun Instant.periodLabelLongCairo(): String = toCairo().periodLabelLong()

/** Elapsed HH:MM:SS since [start], matching the "٠٠:٣٤:١٢" timer in the work-order execution header. */
fun elapsedSince(start: Instant, now: Instant = Instant.now()): String {
    val totalSeconds = (now.epochSecond - start.epochSecond).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s).toArabicDigits()
}
