package com.example.financeapplication.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helpers for amount input. Both "," and "." are accepted as a decimal separator,
 * spaces (thousands separators) are ignored, at most 2 decimals are allowed.
 */
object Money {

    /**
     * Filters raw user input for an amount field. Returns the new value to store in the field,
     * or [previous] if the new input is not acceptable.
     */
    fun filterInput(newValue: String, previous: String): String {
        if (newValue.isEmpty()) return ""
        val cleaned = newValue.filterNot { it.isWhitespace() || it == ' ' }
        if (cleaned.isEmpty()) return ""
        if (cleaned.any { !it.isDigit() && it != '.' && it != ',' }) return previous
        val separators = cleaned.count { it == '.' || it == ',' }
        if (separators > 1) return previous
        if (separators == 1) {
            val decimals = cleaned.substringAfterLast(if (cleaned.contains(',')) ',' else '.')
            if (decimals.length > 2) return previous
        }
        return cleaned
    }

    /** Parses an amount typed by the user. Accepts "12", "12.5", "12,50", ",5", "1 200,00". */
    fun parse(text: String): Float? {
        val normalized = text
            .filterNot { it.isWhitespace() || it == ' ' }
            .replace(',', '.')
        if (normalized.isEmpty() || normalized == ".") return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        return value.setScale(2, RoundingMode.HALF_UP).toFloat()
    }

    /** Converts a stored amount back to an editable string (no exponent, no trailing zeros). */
    fun toInput(amount: Float): String {
        if (amount == 0f) return ""
        return BigDecimal(amount.toString())
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    fun round(amount: Float): Float =
        BigDecimal(amount.toString()).setScale(2, RoundingMode.HALF_UP).toFloat()

    /** "$1 234.50" */
    fun format(amount: Float): String {
        val sign = if (amount < 0) "-" else ""
        return "$sign$${String.format(Locale.US, "%,.2f", kotlin.math.abs(amount)).replace(',', ' ')}"
    }

    fun formatSigned(amount: Float): String =
        if (amount >= 0) "+${format(amount)}" else format(amount)
}

object Dates {
    private val date get() = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dateTime get() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val time get() = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayHeader get() = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    fun date(ts: Long): String = date.format(Date(ts))
    fun dateTime(ts: Long): String = dateTime.format(Date(ts))
    fun time(ts: Long): String = time.format(Date(ts))
    fun dayHeader(ts: Long): String = dayHeader.format(Date(ts)).replaceFirstChar { it.uppercase() }
}
