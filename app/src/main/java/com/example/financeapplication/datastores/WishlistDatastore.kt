package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStoreWishlist by preferencesDataStore(StoreNames.WISHLIST)

data class WishlistItem(
    val name: String,
    val price: Float = 0f
) {
    fun toStorageFormat(): String {
        return if (price > 0f) "$name|$price" else name
    }

    companion object {
        fun fromStorageFormat(str: String): WishlistItem {
            return if (str.contains("|")) {
                val parts = str.split("|")
                WishlistItem(parts[0], parts.getOrNull(1)?.toFloatOrNull() ?: 0f)
            } else {
                WishlistItem(str, 0f)
            }
        }
    }
}

object WishlistDatastore {

    private val WISHLIST_KEY = stringSetPreferencesKey("wishlist_list")


    fun getWishlistItems(context: Context): Flow<List<WishlistItem>> {
        return context.dataStoreWishlist.data.map { prefs ->
            prefs[WISHLIST_KEY]?.map { WishlistItem.fromStorageFormat(it) } ?: emptyList()
        }
    }

    suspend fun addWishlistItem(context: Context, item: String, price: Float = 0f) {
        context.dataStoreWishlist.edit { prefs ->
            val current = prefs[WISHLIST_KEY]?.toMutableSet() ?: mutableSetOf()
            current.add(WishlistItem(item, price).toStorageFormat())
            prefs[WISHLIST_KEY] = current
        }
    }

    suspend fun removeWishlistItem(context: Context, item: String) {
        context.dataStoreWishlist.edit { prefs ->
            val current = prefs[WISHLIST_KEY]?.toMutableSet() ?: mutableSetOf()
            val toRemove = current.find { 
                WishlistItem.fromStorageFormat(it).name == item 
            }
            if (toRemove != null) {
                current.remove(toRemove)
            } else {
                current.remove(item)
            }
            prefs[WISHLIST_KEY] = current
        }
    }

    suspend fun updateWishlistItem(context: Context, oldItem: String, newItem: String, price: Float = 0f) {
        context.dataStoreWishlist.edit { prefs ->
            val current = prefs[WISHLIST_KEY]?.toMutableSet() ?: mutableSetOf()
            val toRemove = current.find { 
                WishlistItem.fromStorageFormat(it).name == oldItem 
            }
            if (toRemove != null) {
                current.remove(toRemove)
            } else {
                current.remove(oldItem)
            }
            current.add(WishlistItem(newItem, price).toStorageFormat())
            prefs[WISHLIST_KEY] = current
        }
    }
}
