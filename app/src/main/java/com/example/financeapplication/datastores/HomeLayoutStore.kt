package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tiles of the home screen, in their default order. */
enum class HomeTileId { BALANCE, SPENDING, INCOME, HISTORY, PLANNED, SAVINGS, OVERVIEW, SUBSCRIPTIONS, DEBTS, TODO, NOTES }

/** LARGE / MEDIUM take the full width (MEDIUM without the stats row), SMALL is half width like the other tiles. */
enum class BalanceSize { LARGE, MEDIUM, SMALL }

data class HomeLayout(val order: List<HomeTileId>, val balanceSize: BalanceSize)

object HomeLayoutStore {
    private val ORDER_KEY = stringPreferencesKey("home_order")
    private val BALANCE_SIZE_KEY = stringPreferencesKey("home_balance_size")

    fun get(context: Context): Flow<HomeLayout> = context.appDataStore.data.map { prefs ->
        val saved = prefs[ORDER_KEY].orEmpty().split(',')
            .mapNotNull { name -> HomeTileId.entries.firstOrNull { it.name == name } }
            .distinct()
        // tiles added in a later version go to the end
        val order = saved + HomeTileId.entries.filter { it !in saved }
        val size = prefs[BALANCE_SIZE_KEY]?.let { name -> BalanceSize.entries.firstOrNull { it.name == name } } ?: BalanceSize.LARGE
        HomeLayout(order, size)
    }

    suspend fun setOrder(context: Context, order: List<HomeTileId>) {
        context.appDataStore.edit { it[ORDER_KEY] = order.joinToString(",") { t -> t.name } }
    }

    suspend fun setBalanceSize(context: Context, size: BalanceSize) {
        context.appDataStore.edit { it[BALANCE_SIZE_KEY] = size.name }
    }
}
