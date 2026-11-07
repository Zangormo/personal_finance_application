package com.example.financeapplication.datastores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** File names of every preferences DataStore of the app. Each store's declaration uses these names. */
object StoreNames {
    const val APP = "app_data_v2"
    const val USER = "user_prefs"
    const val SPENDING = "spending_prefs"
    const val INCOME = "income_prefs"
    const val ESSENTIALS = "essentials_prefs"
    const val WISHLIST = "wishlist_prefs"
}

/**
 * All persistent data of the app except note attachment files (see [NotesDataStore.filesDir]).
 * A new DataStore must be added here, otherwise it is not included in backups.
 */
fun Context.allDataStores(): Map<String, DataStore<Preferences>> = mapOf(
    StoreNames.APP to appDataStore,
    StoreNames.USER to dataStoreUserPreferences,
    StoreNames.SPENDING to dataStoreSpending,
    StoreNames.INCOME to dataStoreIncome,
    StoreNames.ESSENTIALS to dataStoreEssentials,
    StoreNames.WISHLIST to dataStoreWishlist,
)
