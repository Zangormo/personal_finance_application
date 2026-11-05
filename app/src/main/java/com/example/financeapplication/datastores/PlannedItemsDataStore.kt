package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

enum class PlannedType { ESSENTIAL, WISH }

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
