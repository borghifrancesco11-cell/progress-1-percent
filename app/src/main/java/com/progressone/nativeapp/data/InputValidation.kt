package com.progressone.nativeapp.data

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Small, dependency-free validation helpers shared by forms and the data layer.
 *
 * The helpers deliberately accept only the formats used by the native app.  In
 * particular, a date is not considered valid merely because it has the right
 * number of characters: impossible dates (for example 2024-02-31) are rejected.
 */
object InputValidation {
    /** The oldest year accepted in user-created and imported records. */
    const val MIN_VALID_YEAR: Int = 1900

    /** The newest year accepted in user-created and imported records. */
    const val MAX_VALID_YEAR: Int = 2100

    /** Alias useful to callers that want to talk about dates rather than years. */
    const val MIN_DATE_YEAR: Int = MIN_VALID_YEAR
    const val MAX_DATE_YEAR: Int = MAX_VALID_YEAR

    /** Money is non-negative and capped well below Double's overflow range. */
    const val MIN_MONEY_AMOUNT: Double = 0.0
    const val MAX_MONEY_AMOUNT: Double = 1_000_000_000.0

    /** Plausible adult/child profile ranges used by the optional profile fields. */
    const val MIN_WEIGHT_KG: Double = 20.0
    const val MAX_WEIGHT_KG: Double = 300.0
    const val MIN_HEIGHT_CM: Double = 50.0
    const val MAX_HEIGHT_CM: Double = 250.0
    const val MIN_AGE_YEARS: Double = 1.0
    const val MAX_AGE_YEARS: Double = 120.0

    /** A date must be an ISO calendar date in this practical year window. */
    fun isValidDate(value: String): Boolean {
        val text = value.trim()
        if (!text.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        return try {
            val date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT))
            date.year in MIN_VALID_YEAR..MAX_VALID_YEAR
        } catch (_: DateTimeException) {
            false
        }
    }

    /**
     * Parses a non-negative monetary amount.  Both `12.50` and `12,50` are
     * accepted.  Common thousands separators are accepted as well, while
     * malformed numbers, negative values, NaN and infinities return null.
     */
    fun parseAmount(value: String): Double? = parseFiniteNumber(value)?.takeIf {
        it >= MIN_MONEY_AMOUNT && it <= MAX_MONEY_AMOUNT
    }

    /** Parses any finite, non-negative number without scientific-notation overflow. */
    fun parseNonNegativeNumber(value: String): Double? = parseFiniteNumber(value)?.takeIf {
        it >= 0.0
    }

    /** Parses a profile weight in kilograms, returning null when out of range. */
    fun parseWeightKg(value: String): Double? = parseFiniteNumber(value)?.takeIf {
        it >= MIN_WEIGHT_KG && it <= MAX_WEIGHT_KG
    }

    /** Parses a profile height in centimetres, returning null when out of range. */
    fun parseHeightCm(value: String): Double? = parseFiniteNumber(value)?.takeIf {
        it >= MIN_HEIGHT_CM && it <= MAX_HEIGHT_CM
    }

    /** Parses a profile age in years, returning null when out of range. */
    fun parseAgeYears(value: String): Double? = parseFiniteNumber(value)?.takeIf {
        it >= MIN_AGE_YEARS && it <= MAX_AGE_YEARS
    }

    /** Strict local wall-clock time in HH:mm form. */
    fun isValidTime(value: String): Boolean {
        val text = value.trim()
        if (!text.matches(Regex("\\d{2}:\\d{2}"))) return false
        return try {
            LocalTime.parse(text, DateTimeFormatter.ISO_LOCAL_TIME.withResolverStyle(ResolverStyle.STRICT))
            true
        } catch (_: DateTimeException) {
            false
        }
    }

    /**
     * Converts a number without going through Double.parseDouble for the
     * overflow/infinity cases.  The final conversion is still checked for
     * finiteness because a very long decimal can exceed Double's range.
     */
    private fun parseFiniteNumber(value: String): Double? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val normalized = normalizeNumber(text) ?: return null
        return try {
            val decimal = BigDecimal(normalized)
            if (decimal < BigDecimal.ZERO) return null
            val result = decimal.toDouble()
            result.takeIf { it.isFinite() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Normalises decimal comma and thousands separators to a plain decimal
     * string.  If both separators occur, the last one is the decimal mark.
     */
    private fun normalizeNumber(value: String): String? {
        var text = value.replace(" ", "").replace('\u00A0', ' ').trim()
        if (text.isEmpty()) return null
        if (text.any { it !in "0123456789+-,." }) return null
        if (text.count { it == '+' } > 1 || text.count { it == '-' } > 1) return null
        if (text.startsWith("+")) text = text.substring(1)
        if (text.startsWith("-") || text.endsWith(",") || text.endsWith(".")) return null

        val commaCount = text.count { it == ',' }
        val dotCount = text.count { it == '.' }
        if (commaCount + dotCount > 2) return null

        // A correctly grouped integer is unambiguous only when the entire
        // value has three-digit groups (1,234 / 1.234.567). Never collapse
        // malformed groups such as 1,23,456.
        if (commaCount > 0 && dotCount == 0 && Regex("^\\d{1,3}(,\\d{3})+$").matches(text)) {
            return text.replace(",", "")
        }
        if (dotCount > 0 && commaCount == 0 && Regex("^\\d{1,3}(\\.\\d{3})+$").matches(text)) {
            return text.replace(".", "")
        }

        val comma = text.lastIndexOf(',')
        val dot = text.lastIndexOf('.')
        val decimalAt = maxOf(comma, dot)
        if (decimalAt < 0) return text.takeIf { it.all(Char::isDigit) }

        val decimalChar = text[decimalAt]
        val integerRaw = text.substring(0, decimalAt)
        val fractionPart = text.substring(decimalAt + 1)
        if (fractionPart.isEmpty() || !fractionPart.all(Char::isDigit)) return null

        // With both separators, the last one is the decimal mark and the other
        // one must form valid thousands groups in the integer part.
        if (commaCount == 1 && dotCount == 1) {
            val groupingChar = if (decimalChar == ',') '.' else ','
            val groups = integerRaw.split(groupingChar)
            if (groups.size < 2 || groups.first().length !in 1..3 ||
                groups.drop(1).any { it.length != 3 }) return null
            return groups.joinToString("") + "." + fractionPart
        }

        if (!integerRaw.all(Char::isDigit)) return null
        // A single separator followed by exactly three digits is conventionally
        // a thousands separator when the integer part has at most three digits.
        // Otherwise it is a decimal mark (keeps 12,50 useful as a locale decimal).
        if (fractionPart.length == 3 && integerRaw.length in 1..3) {
            return integerRaw + fractionPart
        }
        return "$integerRaw.$fractionPart"
    }
}
