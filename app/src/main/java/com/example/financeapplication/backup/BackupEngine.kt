package com.example.financeapplication.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.financeapplication.datastores.StoreNames
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** A backup that was read and checked, with its files extracted to a temporary folder. */
class PreparedImport internal constructor(val data: BackupData, internal val dir: File) {
    val summary: BackupSummary get() = BackupSummary.of(data)
}

/** What a backup contains, to show before it replaces the current data. */
data class BackupSummary(
    val createdAt: Long,
    val appVersion: String,
    val spendings: Int,
    val incomes: Int,
    val notes: Int,
    val tasks: Int,
    val taskCategories: Int,
    val debts: Int,
    val subscriptions: Int,
    val plannedItems: Int,
    val files: Int,
) {
    companion object {
        fun of(data: BackupData): BackupSummary {
            fun count(store: String, key: String): Int {
                val json = data.stores[store]?.get(key) as? String ?: return 0
                return try {
                    JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
                } catch (_: Exception) {
                    0
                }
            }
            return BackupSummary(
                createdAt = data.createdAt,
                appVersion = data.appVersion,
                spendings = count(StoreNames.SPENDING, "spendings_list"),
                incomes = count(StoreNames.INCOME, "incomes_list"),
                notes = count(StoreNames.APP, "notes"),
                tasks = count(StoreNames.APP, "todo_items"),
                taskCategories = count(StoreNames.APP, "todo_categories"),
                debts = count(StoreNames.APP, "debts"),
                subscriptions = count(StoreNames.APP, "subscriptions"),
                plannedItems = count(StoreNames.APP, "planned_items"),
                files = data.files.size,
            )
        }
    }
}

/**
 * Exports and imports all app data: every key of every given DataStore plus all files under
 * [filesRoot]. Has no Android dependencies, so it is fully testable on the JVM.
 *
 * @param stores all DataStores of the app, by name (the names are stored in the backup)
 * @param filesRoot folder with the note attachment files
 * @param workDir folder for temporary files while importing
 */
class BackupEngine(
    private val stores: Map<String, DataStore<Preferences>>,
    private val filesRoot: File,
    private val workDir: File,
) {

    suspend fun snapshot(appVersion: String, now: Long = System.currentTimeMillis()): BackupData =
        withContext(Dispatchers.IO) {
            BackupData(
                createdAt = now,
                appVersion = appVersion,
                stores = stores.mapValues { (_, store) -> PrefsMapper.snapshot(store.data.first()) },
                files = BackupArchive.listFiles(filesRoot),
            )
        }

    /** Writes a complete backup to [out] (not closed). */
    suspend fun export(out: OutputStream, appVersion: String, now: Long = System.currentTimeMillis()): BackupData {
        val data = snapshot(appVersion, now)
        withContext(Dispatchers.IO) { BackupArchive.write(out, data, filesRoot) }
        return data
    }

    /**
     * Reads and checks a backup without changing any data yet. Call [apply] to use it or
     * [discard] to throw it away. Throws [BackupException] for files that cannot be imported.
     */
    suspend fun prepare(input: InputStream): PreparedImport = withContext(Dispatchers.IO) {
        val dir = File(workDir, "backup_import_${System.nanoTime()}")
        dir.deleteRecursively()
        dir.mkdirs()
        try {
            PreparedImport(BackupArchive.read(input, dir), dir)
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
    }

    /**
     * Replaces ALL current data with the prepared backup: every store is overwritten (stores the
     * backup does not have are emptied, stores this app does not know are ignored), and the files
     * folder is replaced.
     */
    suspend fun apply(prepared: PreparedImport) = withContext(Dispatchers.IO) {
        try {
            stores.forEach { (name, store) ->
                val values = prepared.data.stores[name].orEmpty()
                store.edit { PrefsMapper.replaceAll(it, values) }
            }
            filesRoot.deleteRecursively()
            filesRoot.parentFile?.mkdirs()
            if (!prepared.dir.renameTo(filesRoot)) {
                prepared.dir.copyRecursively(filesRoot, overwrite = true)
            }
            filesRoot.mkdirs()
        } finally {
            prepared.dir.deleteRecursively()
        }
    }

    fun discard(prepared: PreparedImport) {
        prepared.dir.deleteRecursively()
    }
}
