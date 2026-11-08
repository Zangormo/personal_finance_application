package com.example.financeapplication.datastores

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.financeapplication.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class PlannedType { ESSENTIAL, WISH }

/** Sort modes of the essentials / wishlist lists (each list remembers its own). */
enum class PlannedSort(@StringRes val label: Int) {
    NAME_AZ(R.string.sort_name_az),
    NAME_ZA(R.string.sort_name_za),
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    PRICE_HIGH(R.string.sort_price_high),
    PRICE_LOW(R.string.sort_price_low),
    REUSABLE_FIRST(R.string.sort_reusable_first),
    REUSABLE_LAST(R.string.sort_reusable_last),
}

/**
 * Items in the given order. Ties are broken alphabetically; items without a price are placed
 * after the priced ones in both price orders.
 */
fun List<PlannedItem>.sortedFor(sort: PlannedSort): List<PlannedItem> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { it: PlannedItem -> it.name.trim() }
    return sortedWith(
        when (sort) {
            PlannedSort.NAME_AZ -> byName
            PlannedSort.NAME_ZA -> byName.reversed()
            PlannedSort.NEWEST -> compareByDescending<PlannedItem> { it.createdAt }.then(byName)
            PlannedSort.OLDEST -> compareBy<PlannedItem> { it.createdAt }.then(byName)
            PlannedSort.PRICE_HIGH -> compareBy<PlannedItem> { it.price <= 0f }.thenByDescending { it.price }.then(byName)
            PlannedSort.PRICE_LOW -> compareBy<PlannedItem> { it.price <= 0f }.thenBy { it.price }.then(byName)
            PlannedSort.REUSABLE_FIRST -> compareByDescending<PlannedItem> { it.reusable }.then(byName)
            PlannedSort.REUSABLE_LAST -> compareBy<PlannedItem> { it.reusable }.then(byName)
        }
    )
}

/**
 * A planned purchase: an essential or a wishlist item.
 * [reusable] items stay in the list after being used in a spending (e.g. "Car wash").
 */
data class PlannedItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Float = 0f,
    val type: PlannedType = PlannedType.ESSENTIAL,
    val reusable: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Replaces the old EssentialsDataStore + WishlistDatastore (their data is migrated once). */
object PlannedItemsDataStore {
    private val store = JsonListStore("planned_items", Array<PlannedItem>::class.java)

    fun getItems(context: Context): Flow<List<PlannedItem>> = store.flow(context)

    suspend fun add(context: Context, item: PlannedItem) = store.update(context) { it.add(item) }

    suspend fun update(context: Context, item: PlannedItem) = store.update(context) { list ->
        val i = list.indexOfFirst { it.id == item.id }
        if (i != -1) list[i] = item
    }

    suspend fun remove(context: Context, id: String) = store.update(context) { list -> list.removeAll { it.id == id } }

    private fun sortKey(type: PlannedType) =
        stringPreferencesKey(if (type == PlannedType.ESSENTIAL) "planned_sort_essentials" else "planned_sort_wishlist")

    /** Sort mode of one list; alphabetical by default (the order of older versions). */
    fun getSort(context: Context, type: PlannedType): Flow<PlannedSort> = context.appDataStore.data.map { prefs ->
        prefs[sortKey(type)]?.let { name -> PlannedSort.entries.firstOrNull { it.name == name } } ?: PlannedSort.NAME_AZ
    }

    suspend fun setSort(context: Context, type: PlannedType, sort: PlannedSort) {
        context.appDataStore.edit { it[sortKey(type)] = sort.name }
    }

    /** Removes the used items that are not reusable. */
    suspend fun consume(context: Context, ids: Collection<String>) = store.update(context) { list ->
        list.removeAll { it.id in ids && !it.reusable }
    }

    /** One-time import of data from the old essentials / wishlist stores. */
    suspend fun migrateIfNeeded(context: Context) {
        val migrated = context.appDataStore.data.first()[AppFlags.PLANNED_MIGRATED] ?: false
        if (migrated) return
        val essentials = EssentialsDataStore.getEssentials(context).first()
        val wishes = WishlistDatastore.getWishlistItems(context).first()
        store.update(context) { list ->
            essentials.forEach { name -> list.add(PlannedItem(name = name, type = PlannedType.ESSENTIAL)) }
            wishes.forEach { w -> list.add(PlannedItem(name = w.name, price = w.price, type = PlannedType.WISH)) }
        }
        context.appDataStore.edit { it[AppFlags.PLANNED_MIGRATED] = true }
    }
}
