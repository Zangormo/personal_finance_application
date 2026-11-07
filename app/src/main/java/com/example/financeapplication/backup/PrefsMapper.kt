package com.example.financeapplication.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/** Converts between DataStore [Preferences] and the plain maps stored in a backup. */
object PrefsMapper {

    /** Every key with its value; ByteArrays and sets are copied, so later changes do not leak in. */
    fun snapshot(prefs: Preferences): Map<String, Any> = prefs.asMap().entries.associate { (key, value) ->
        key.name to when (value) {
            is ByteArray -> value.copyOf()
            is Set<*> -> value.toSet()
            else -> value
        }
    }

    /** Replaces the whole content of [prefs] with [values]. */
    fun replaceAll(prefs: MutablePreferences, values: Map<String, Any>) {
        prefs.clear()
        values.forEach { (key, value) -> put(prefs, key, value) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(prefs: MutablePreferences, key: String, value: Any) {
        when (value) {
            is String -> prefs[stringPreferencesKey(key)] = value
            is Boolean -> prefs[booleanPreferencesKey(key)] = value
            is Int -> prefs[intPreferencesKey(key)] = value
            is Long -> prefs[longPreferencesKey(key)] = value
            is Float -> prefs[floatPreferencesKey(key)] = value
            is Double -> prefs[doublePreferencesKey(key)] = value
            is ByteArray -> prefs[byteArrayPreferencesKey(key)] = value
            is Set<*> -> prefs[stringSetPreferencesKey(key)] = value as Set<String>
            else -> throw IllegalArgumentException("Unsupported value type for \"$key\": ${value::class.java.name}")
        }
    }
}
