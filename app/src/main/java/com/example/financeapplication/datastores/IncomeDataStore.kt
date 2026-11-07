package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStoreIncome by preferencesDataStore(StoreNames.INCOME)

data class IncomeRecord(
    val amount: Float,
    val description: String,
    val timestamp: Long
)

object IncomeDataStore {

    private val INCOMES_KEY = stringPreferencesKey("incomes_list")
    private val gson = Gson()

    fun getIncomes(context: Context): Flow<List<IncomeRecord>> {
        return context.dataStoreIncome.data.map { prefs ->
            val json = prefs[INCOMES_KEY] ?: return@map emptyList()
            try {
                gson.fromJson(json, Array<IncomeRecord>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addIncome(context: Context, amount: Float, description: String) {
        context.dataStoreIncome.edit { prefs ->
            val json = prefs[INCOMES_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<IncomeRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            current.add(IncomeRecord(
                amount = amount,
                description = description,
                timestamp = System.currentTimeMillis()
            ))
            
            prefs[INCOMES_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateIncome(context: Context, oldIncome: IncomeRecord, newAmount: Float, newDescription: String) {
        context.dataStoreIncome.edit { prefs ->
            val json = prefs[INCOMES_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<IncomeRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            val index = current.indexOfFirst { 
                it.timestamp == oldIncome.timestamp && 
                it.amount == oldIncome.amount && 
                it.description == oldIncome.description 
            }
            
            if (index != -1) {
                current[index] = IncomeRecord(
                    amount = newAmount,
                    description = newDescription,
                    timestamp = oldIncome.timestamp
                )
            }
            
            prefs[INCOMES_KEY] = gson.toJson(current)
        }
    }

    suspend fun deleteIncome(context: Context, income: IncomeRecord) {
        context.dataStoreIncome.edit { prefs ->
            val json = prefs[INCOMES_KEY] ?: ""
            val current = try {
                if (json.isNotEmpty()) {
                    gson.fromJson(json, Array<IncomeRecord>::class.java).toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
            
            current.removeAll { 
                it.timestamp == income.timestamp && 
                it.amount == income.amount && 
                it.description == income.description 
            }
            
            prefs[INCOMES_KEY] = gson.toJson(current)
        }
    }
}
