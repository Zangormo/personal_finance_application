package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** Single DataStore for all data introduced in v2 (planned items, subscriptions, debts, todos). */
val Context.appDataStore by preferencesDataStore(StoreNames.APP)

internal val appGson = Gson()

/**
 * Stores a list of objects as a JSON array under a single preferences key.
 *
 * Changes are visible right away: [update] puts the new list into memory and the flow emits it
 * immediately, while writing to disk happens afterwards. Disk values that arrive while writes
 * are still in flight are ignored, so the UI never jumps back to an older state.
 */
class JsonListStore<T>(key: String, private val arrayClass: Class<Array<T>>) {
    private val prefKey = stringPreferencesKey(key)

    /** Newest known content, including changes that are not on disk yet. */
    private val memory = MutableStateFlow<List<T>?>(null)
    private val pendingWrites = AtomicInteger(0)
    private val lock = Mutex()

    init {
        synchronized(instances) { instances += this }
    }

    companion object {
        private val instances = mutableListOf<JsonListStore<*>>()

        /** Forgets the in-memory copies, e.g. after the stores were replaced by an import. */
        fun invalidateAll() {
            synchronized(instances) { instances.forEach { it.memory.value = null } }
        }
    }

    private fun decode(prefs: Preferences): List<T> {
        val json = prefs[prefKey] ?: return emptyList()
        return try {
            appGson.fromJson(json, arrayClass)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun disk(context: Context): Flow<List<T>> = context.appDataStore.data.map { decode(it) }

    fun flow(context: Context): Flow<List<T>> = merge(
        disk(context)
            .filter { pendingWrites.get() == 0 }
            .onEach { memory.value = it },
        memory.filterNotNull()
    ).distinctUntilChanged()

    suspend fun get(context: Context): List<T> = memory.value ?: disk(context).first()

    suspend fun update(context: Context, transform: (MutableList<T>) -> Unit) {
        lock.withLock {
            val base = memory.value ?: disk(context).first()
            val list = base.toMutableList()
            transform(list)
            pendingWrites.incrementAndGet()
            memory.value = list
        }
        try {
            // Always write the newest list, so concurrent writes cannot finish in the wrong order
            context.appDataStore.edit { prefs -> memory.value?.let { prefs[prefKey] = appGson.toJson(it) } }
        } finally {
            pendingWrites.decrementAndGet()
        }
    }
}

internal object AppFlags {
    val PLANNED_MIGRATED = booleanPreferencesKey("planned_migrated_v1")
}
