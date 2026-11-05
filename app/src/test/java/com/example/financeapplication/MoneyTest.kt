package com.example.financeapplication

import com.example.financeapplication.datastores.RepeatPeriod
import com.example.financeapplication.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class MoneyTest {

    @Test
    fun parse_acceptsCommaAndDot() {
        assertEquals(12.5f, Money.parse("12.5"))
        assertEquals(12.5f, Money.parse("12,5"))
        assertEquals(12.55f, Money.parse("12,55"))
        assertEquals(0.5f, Money.parse(",5"))
        assertEquals(12f, Money.parse("12."))
        assertEquals(12f, Money.parse("12,"))
        assertEquals(1200f, Money.parse("1 200"))
        assertEquals(100f, Money.parse("100"))
    }

    @Test
    fun parse_rejectsGarbage() {
        assertNull(Money.parse(""))
        assertNull(Money.parse(","))
        assertNull(Money.parse("1,2,3"))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun filterInput_keepsValidInput() {
        assertEquals("12,5", Money.filterInput("12,5", "12,"))
        assertEquals("12.55", Money.filterInput("12.55", "12.5"))
        // third decimal rejected
        assertEquals("12,55", Money.filterInput("12,555", "12,55"))
        // second separator rejected (comma after dot and vice versa)
        assertEquals("12.5", Money.filterInput("12.5,", "12.5"))
        assertEquals("12,5", Money.filterInput("12,5.", "12,5"))
        // letters rejected
        assertEquals("12", Money.filterInput("12a", "12"))
        assertEquals("", Money.filterInput("", "12"))
    }

    @Test
    fun toInput_roundTrips() {
        assertEquals("12.5", Money.toInput(12.5f))
        assertEquals("10000000", Money.toInput(1.0E7f))
        assertEquals("", Money.toInput(0f))
        assertEquals(19.99f, Money.parse(Money.toInput(19.99f)))
    }

    @Test
    fun monthlyShift_clampsWithoutDrift() {
        val jan31 = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 31) }.timeInMillis
        val feb = Calendar.getInstance().apply { timeInMillis = RepeatPeriod.MONTHLY.shift(jan31, 1) }
        val mar = Calendar.getInstance().apply { timeInMillis = RepeatPeriod.MONTHLY.shift(jan31, 2) }
        assertEquals(28, feb.get(Calendar.DAY_OF_MONTH))
        assertEquals(31, mar.get(Calendar.DAY_OF_MONTH))
    }
}
