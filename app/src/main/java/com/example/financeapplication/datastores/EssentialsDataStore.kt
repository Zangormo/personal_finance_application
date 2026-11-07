package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStoreEssentials by preferencesDataStore(StoreNames.ESSENTIALS)

object EssentialsDataStore {

    private val ESSENTIALS_KEY = stringSetPreferencesKey("essentials_list")

    fun getEssentials(context: Context): Flow<List<String>> {
        return context.dataStoreEssentials.data.map { prefs ->
            prefs[ESSENTIALS_KEY]?.toList() ?: emptyList()
        }
    }

    suspend fun addEssential(context: Context, item: String) {
        context.dataStoreEssentials.edit { prefs ->
            val current = prefs[ESSENTIALS_KEY]?.toMutableSet() ?: mutableSetOf()
            current.add(item)
            prefs[ESSENTIALS_KEY] = current
        }
    }

    suspend fun removeEssential(context: Context, item: String) {
        context.dataStoreEssentials.edit { prefs ->
            val current = prefs[ESSENTIALS_KEY]?.toMutableSet() ?: mutableSetOf()
            current.remove(item)
            prefs[ESSENTIALS_KEY] = current
        }
    }

    suspend fun updateEssential(context: Context, oldItem: String, newItem: String) {
        context.dataStoreEssentials.edit { prefs ->
            val current = prefs[ESSENTIALS_KEY]?.toMutableSet() ?: mutableSetOf()
            if (current.remove(oldItem)) {
                current.add(newItem)
            }
            prefs[ESSENTIALS_KEY] = current
        }
    }
}
