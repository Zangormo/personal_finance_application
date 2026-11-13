package com.example.financeapplication

import com.example.financeapplication.ui.components.mondayFirstLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

class MondayFirstLocaleTest {
    private fun firstDay(l: Locale) = WeekFields.of(l).firstDayOfWeek

    @Test
    fun sundayFirstLocaleBecomesMondayFirstAndKeepsLanguage() {
        val result = mondayFirstLocale(Locale.US)
        assertEquals(DayOfWeek.MONDAY, firstDay(result))
        assertEquals("en", result.language)
    }

    @Test
    fun mondayFirstLocaleIsUnchanged() {
        val ru = Locale.forLanguageTag("ru-RU")
        assertEquals(ru, mondayFirstLocale(ru))
    }

    @Test
    fun languageOnlyLocaleIsMondayFirst() {
        assertEquals(DayOfWeek.MONDAY, firstDay(mondayFirstLocale(Locale.ENGLISH)))
    }
}
