package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.financeapplication.classes.NecessityLevel
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStoreSpending by preferencesDataStore(StoreNames.SPENDING)

data class SpendingRecord(
    val amount: Float,
    val items: List<String>,
    val timestamp: Long,
    val necessity: NecessityLevel = NecessityLevel.NECESSARY,
    /** null for manual spendings, [SOURCE_SUBSCRIPTION] for automatic subscription charges. */
    val source: String? = null
)

const val SOURCE_SUBSCRIPTION = "subscription"

object SpendingDataStore {

    private val SPENDINGS_KEY = stringPreferencesKey("spendings_list")
    private val gson = Gson()

    fun getSpendings(context: Context): Flow<List<SpendingRecord>> {
        return context.dataStoreSpending.data.map { prefs ->
            val json = prefs[SPENDINGS_KEY] ?: return@map emptyList()
            try {
                gson.fromJson(json, Array<SpendingRecord>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addSpending(
        context: Context,
        amount: Float,
        items: List<String>,
        necessity: NecessityLevel = NecessityLevel.NECESSARY,
        timestamp: Long = System.currentTimeMillis(),
        source: String? = null
    ) {
        context.dataStoreSpending.edit { prefs ->
            val json = prefs[SPENDINGS_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<SpendingRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            current.add(SpendingRecord(
                amount = amount,
                items = items,
                timestamp = timestamp,
                necessity = necessity,
                source = source
            ))
            
            prefs[SPENDINGS_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateSpending(context: Context, oldSpending: SpendingRecord, newAmount: Float, newItems: List<String>, newNecessity: NecessityLevel = NecessityLevel.NECESSARY) {
        context.dataStoreSpending.edit { prefs ->
            val json = prefs[SPENDINGS_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<SpendingRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            val index = current.indexOfFirst { 
                it.timestamp == oldSpending.timestamp && 
                it.amount == oldSpending.amount 
            }
            
            if (index != -1) {
                current[index] = current[index].copy(
                    amount = newAmount,
                    items = newItems,
                    necessity = newNecessity
                )
            }
            
            prefs[SPENDINGS_KEY] = gson.toJson(current)
        }
    }

    suspend fun deleteSpending(context: Context, spending: SpendingRecord) {
        context.dataStoreSpending.edit { prefs ->
            val json = prefs[SPENDINGS_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<SpendingRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            current.removeAll { 
                it.timestamp == spending.timestamp && 
                it.amount == spending.amount 
            }
            
            prefs[SPENDINGS_KEY] = gson.toJson(current)
        }
    }
}
