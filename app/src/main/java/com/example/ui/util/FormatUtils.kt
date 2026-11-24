package com.example.ui.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val swedishLocale: Locale = Locale.forLanguageTag("sv-SE")
private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val swedishShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", swedishLocale)

/**
 * Formats macro grams cleanly without floating-point artifacts (e.g. 12.300000000000001 -> "12.3").
 * Whole numbers are formatted without ".0" (e.g. "24").
 */
fun formatMacroDecimal(value: Double): String {
    val roundedTenths = (value * 10.0).roundToInt()
    return if (roundedTenths % 10 == 0) {
        (roundedTenths / 10).toString()
    } else {
        val whole = roundedTenths / 10
        val tenth = kotlin.math.abs(roundedTenths % 10)
        val sign = if (value < 0 && whole == 0) "-" else ""
        "$sign$whole,$tenth"
    }
}

/**
 * Formats a weight value in kg with 1 decimal place (e.g. "74,5").
 */
fun formatWeightKg(value: Double): String {
    return String.format(swedishLocale, "%.1f", value)
}

/**
 * Formats an ISO yyyy-MM-dd date string to readable Swedish (e.g. "Idag, 13 nov" or "10 nov").
 */
fun formatSwedishDateLabel(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate, isoDateFormatter)
        val today = LocalDate.now()
        val shortDate = date.format(swedishShortDateFormatter).replace(".", "")
        when (date) {
            today -> "Idag, $shortDate"
            today.minusDays(1) -> "Igår, $shortDate"
            today.plusDays(1) -> "Imorgon, $shortDate"
            else -> shortDate
        }
    } catch (_: Exception) {
        isoDate
    }
}
