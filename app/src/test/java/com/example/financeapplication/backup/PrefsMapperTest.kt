package com.example.financeapplication.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsMapperTest {

    private val allTypes: Map<String, Any> = mapOf(
        "s" to "text", "b" to true, "i" to 7, "l" to 1_762_500_000_123L,
        "f" to 12.5f, "d" to 3.25, "set" to setOf("a", "b"), "bytes" to byteArrayOf(1, 2, 3),
    )

    @Test
    fun snapshotReadsEveryType() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("s") to "text",
            booleanPreferencesKey("b") to true,
            intPreferencesKey("i") to 7,
            longPreferencesKey("l") to 1_762_500_000_123L,
            floatPreferencesKey("f") to 12.5f,
            doublePreferencesKey("d") to 3.25,
            stringSetPreferencesKey("set") to setOf("a", "b"),
            byteArrayPreferencesKey("bytes") to byteArrayOf(1, 2, 3),
        )
        assertValuesEqual("prefs", allTypes, PrefsMapper.snapshot(prefs))
    }

    @Test
    fun replaceAllWritesEveryTypeWithTheRightKeyType() {
        val prefs = mutablePreferencesOf()
        PrefsMapper.replaceAll(prefs, allTypes)
        assertEquals("text", prefs[stringPreferencesKey("s")])
        assertEquals(true, prefs[booleanPreferencesKey("b")])
        assertEquals(7, prefs[intPreferencesKey("i")])
        assertEquals(1_762_500_000_123L, prefs[longPreferencesKey("l")])
        assertEquals(12.5f, prefs[floatPreferencesKey("f")])
        assertEquals(3.25, prefs[doublePreferencesKey("d")]!!, 0.0)
        assertEquals(setOf("a", "b"), prefs[stringSetPreferencesKey("set")])
        assertArrayEquals(byteArrayOf(1, 2, 3), prefs[byteArrayPreferencesKey("bytes")])
    }

    @Test
    fun replaceAllRemovesKeysThatAreNotInTheBackup() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("old") to "should go",
            stringPreferencesKey("s") to "will be replaced",
        )
        PrefsMapper.replaceAll(prefs, mapOf("s" to "new"))
        assertNull(prefs[stringPreferencesKey("old")])
        assertEquals("new", prefs[stringPreferencesKey("s")])
        assertEquals(1, prefs.asMap().size)
    }

    @Test
    fun replaceAllWithNothingEmptiesTheStore() {
        val prefs = mutablePreferencesOf(intPreferencesKey("a") to 1, stringPreferencesKey("b") to "x")
        PrefsMapper.replaceAll(prefs, emptyMap())
        assertTrue(prefs.asMap().isEmpty())
    }

    @Test
    fun snapshotThenReplaceGivesTheSamePreferences() {
        val original = mutablePreferencesOf()
        PrefsMapper.replaceAll(original, allTypes)
        val copy = mutablePreferencesOf(stringPreferencesKey("junk") to "x")
        PrefsMapper.replaceAll(copy, PrefsMapper.snapshot(original))
        assertValuesEqual("copy", PrefsMapper.snapshot(original), PrefsMapper.snapshot(copy))
    }

    @Test
    fun snapshotIsACopy() {
        val bytes = byteArrayOf(1, 2, 3)
        val prefs = mutablePreferencesOf(byteArrayPreferencesKey("bytes") to bytes)
        val snap = PrefsMapper.snapshot(prefs)
        (snap.getValue("bytes") as ByteArray)[0] = 99
        assertArrayEquals(byteArrayOf(1, 2, 3), prefs[byteArrayPreferencesKey("bytes")])
    }

    @Test
    fun unsupportedTypeIsRejected() {
        assertThrows<IllegalArgumentException> { PrefsMapper.replaceAll(mutablePreferencesOf(), mapOf("x" to listOf(1))) }
    }
}
