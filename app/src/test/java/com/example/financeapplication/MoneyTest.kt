package com.example.financeapplication

import com.example.financeapplication.datastores.RepeatPeriod
import com.example.financeapplication.utils.Money
import com.example.financeapplication.datastores.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m, d, h, min) }.timeInMillis

    @Test
    fun weeklyPeriod_isMondayToSunday() {
        // 2026-11-06 is a Friday, 2026-11-02 is Monday, 2026-11-08 is Sunday
        val monday = at(2026, Calendar.NOVEMBER, 2)
        assertEquals(monday, RepeatPeriod.WEEKLY.periodStart(at(2026, Calendar.NOVEMBER, 6, 18)))
        assertEquals(monday, RepeatPeriod.WEEKLY.periodStart(at(2026, Calendar.NOVEMBER, 8, 23, 59)))
        assertEquals(monday, RepeatPeriod.WEEKLY.periodStart(monday))
        assertEquals(at(2026, Calendar.NOVEMBER, 9), RepeatPeriod.WEEKLY.nextPeriodStart(at(2026, Calendar.NOVEMBER, 6, 18)))
    }

    @Test
    fun dailyMonthlyYearlyPeriods() {
        val now = at(2026, Calendar.NOVEMBER, 6, 18, 30)
        assertEquals(at(2026, Calendar.NOVEMBER, 6), RepeatPeriod.DAILY.periodStart(now))
        assertEquals(at(2026, Calendar.NOVEMBER, 7), RepeatPeriod.DAILY.nextPeriodStart(now))
        assertEquals(at(2026, Calendar.NOVEMBER, 1), RepeatPeriod.MONTHLY.periodStart(now))
        assertEquals(at(2026, Calendar.DECEMBER, 1), RepeatPeriod.MONTHLY.nextPeriodStart(now))
        assertEquals(at(2026, Calendar.JANUARY, 1), RepeatPeriod.YEARLY.periodStart(now))
    }

    @Test
    fun recurringTask_doneUntilNextPeriod() {
        val friday = at(2026, Calendar.NOVEMBER, 6, 12)
        val task = TodoItem(title = "Gym", repeat = RepeatPeriod.WEEKLY, completedAt = friday)
        assertTrue(task.isDoneNow(at(2026, Calendar.NOVEMBER, 8, 23, 59))) // Sunday: still done
        assertFalse(task.isDoneNow(at(2026, Calendar.NOVEMBER, 9, 0, 0)))  // Monday 00:00: refreshed

        val daily = TodoItem(title = "Read", repeat = RepeatPeriod.DAILY, completedAt = at(2026, Calendar.NOVEMBER, 6, 23, 50))
        assertTrue(daily.isDoneNow(at(2026, Calendar.NOVEMBER, 6, 23, 59)))
        assertFalse(daily.isDoneNow(at(2026, Calendar.NOVEMBER, 7, 0, 1)))

        val monthly = TodoItem(title = "Rent", repeat = RepeatPeriod.MONTHLY, completedAt = at(2026, Calendar.NOVEMBER, 3))
        assertTrue(monthly.isDoneNow(at(2026, Calendar.NOVEMBER, 30, 23)))
        assertFalse(monthly.isDoneNow(at(2026, Calendar.DECEMBER, 1, 0, 0)))
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
