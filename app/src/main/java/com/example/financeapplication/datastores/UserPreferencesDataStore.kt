package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStoreUserPreferences by preferencesDataStore(StoreNames.USER)

object UserPreferencesDataStore {

    private val IS_FIRST_RUN_KEY = booleanPreferencesKey("is_first_run")
    private val OVERALL_BALANCE_KEY = floatPreferencesKey("overall_balance")
    private val SAVINGS_BALANCE_KEY = floatPreferencesKey("savings_balance")

    fun isFirstRun(context: Context): Flow<Boolean> {
        return context.dataStoreUserPreferences.data.map { prefs ->
            prefs[IS_FIRST_RUN_KEY] ?: true
        }
    }

    suspend fun setFirstRunCompleted(context: Context) {
        context.dataStoreUserPreferences.edit { prefs ->
            prefs[IS_FIRST_RUN_KEY] = false
        }
    }

    fun getOverallBalance(context: Context): Flow<Float> {
        return context.dataStoreUserPreferences.data.map { prefs ->
            prefs[OVERALL_BALANCE_KEY] ?: 0.0f
        }
    }

    suspend fun setOverallBalance(context: Context, balance: Float) {
        context.dataStoreUserPreferences.edit { prefs ->
            prefs[OVERALL_BALANCE_KEY] = balance
        }
    }

    suspend fun updateBalance(context: Context, amountChange: Float) {
        context.dataStoreUserPreferences.edit { prefs ->
            val current = prefs[OVERALL_BALANCE_KEY] ?: 0.0f
            prefs[OVERALL_BALANCE_KEY] = current + amountChange
        }
    }

    fun getSavingsBalance(context: Context): Flow<Float> {
        return context.dataStoreUserPreferences.data.map { prefs ->
            prefs[SAVINGS_BALANCE_KEY] ?: 0.0f
        }
    }

    suspend fun updateSavingsBalance(context: Context, amountChange: Float) {
        context.dataStoreUserPreferences.edit { prefs ->
            val current = prefs[SAVINGS_BALANCE_KEY] ?: 0.0f
            prefs[SAVINGS_BALANCE_KEY] = current + amountChange
        }
    }
}
