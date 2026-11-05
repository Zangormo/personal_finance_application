package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Single DataStore for all data introduced in v2 (planned items, subscriptions, debts, todos). */
val Context.appDataStore by preferencesDataStore("app_data_v2")

internal val appGson = Gson()

/**
 * Stores a list of objects as a JSON array under a single preferences key.
 */
class JsonListStore<T>(key: String, private val arrayClass: Class<Array<T>>) {
    private val prefKey = stringPreferencesKey(key)

    private fun decode(prefs: Preferences): List<T> {
        val json = prefs[prefKey] ?: return emptyList()
        return try {
            appGson.fromJson(json, arrayClass)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun flow(context: Context): Flow<List<T>> = context.appDataStore.data.map { decode(it) }

    suspend fun get(context: Context): List<T> = flow(context).first()

    suspend fun update(context: Context, transform: (MutableList<T>) -> Unit) {
        context.appDataStore.edit { prefs ->
            val list = decode(prefs).toMutableList()
            transform(list)
            prefs[prefKey] = appGson.toJson(list)
        }
    }
}

internal object AppFlags {
    val PLANNED_MIGRATED = booleanPreferencesKey("planned_migrated_v1")
}
