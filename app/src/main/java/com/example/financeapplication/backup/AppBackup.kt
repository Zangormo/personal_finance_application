package com.example.financeapplication.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.financeapplication.datastores.JsonListStore
import com.example.financeapplication.datastores.NotesDataStore
import com.example.financeapplication.datastores.allDataStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.example.financeapplication.utils.appVersionName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backups of the real app data (Android side of [BackupEngine]). */
object AppBackup {

    private fun engine(context: Context) = BackupEngine(
        stores = context.allDataStores(),
        filesRoot = NotesDataStore.filesDir(context),
        workDir = File(context.cacheDir, "backup_work").apply { mkdirs() },
    )

    private fun appVersion(context: Context): String = context.appVersionName()

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "PersonalCashier_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(now)) + "." + BACKUP_EXTENSION

    /** Writes a backup to a document the user picked ("Save to file"). */
    suspend fun exportTo(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        out.use { engine(context).export(it, appVersion(context)) }
    }

    /** Writes a backup to a private temporary file and returns a content Uri to share it. */
    suspend fun exportForSharing(context: Context): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply {
            deleteRecursively() // only the newest backup is kept around
            mkdirs()
        }
        val file = File(dir, suggestedFileName())
        file.outputStream().use { engine(context).export(it, appVersion(context)) }
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    /** Reads and checks a backup; nothing is changed yet. */
    suspend fun prepare(context: Context, uri: Uri): PreparedImport = withContext(Dispatchers.IO) {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e.message, e)
        } ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Cannot open $uri")
        input.use { engine(context).prepare(it) }
    }

    /** Replaces all app data with the backup. Screens update by themselves through their flows. */
    suspend fun apply(context: Context, prepared: PreparedImport) {
        engine(context).apply(prepared)
        JsonListStore.invalidateAll()
    }

    fun discard(context: Context, prepared: PreparedImport) = engine(context).discard(prepared)
}
