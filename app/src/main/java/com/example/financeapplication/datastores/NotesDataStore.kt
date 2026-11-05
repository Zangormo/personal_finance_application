package com.example.financeapplication.datastores

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class AttachmentType { FILE, LINK }

/**
 * A file attached to a note is copied into the app's private storage ([path] is relative to
 * [NotesDataStore.filesDir]), so it stays available even if the original is moved or deleted.
 * A link keeps its address in [url].
 */
data class NoteAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val url: String? = null,
    val path: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val text: String = "",
    val attachments: List<NoteAttachment>? = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = title.isBlank() && text.isBlank() && attachments.orEmpty().isEmpty()
}

object NotesDataStore {
    private val store = JsonListStore("notes", Array<Note>::class.java)

    fun getNotes(context: Context): Flow<List<Note>> = store.flow(context)

    /** Inserts or replaces the note. */
    suspend fun save(context: Context, note: Note) = store.update(context) { list ->
        val i = list.indexOfFirst { it.id == note.id }
        if (i == -1) list.add(note) else list[i] = note
    }

    suspend fun remove(context: Context, note: Note) {
        store.update(context) { list -> list.removeAll { it.id == note.id } }
        note.attachments.orEmpty().forEach { deleteFile(context, it) }
    }

    fun filesDir(context: Context): File = File(context.filesDir, "note_files").apply { mkdirs() }

    fun fileOf(context: Context, attachment: NoteAttachment): File? =
        attachment.path?.let { File(filesDir(context), it) }

    /** Deletes the stored copy of a file attachment (each one lives in its own folder). */
    fun deleteFile(context: Context, attachment: NoteAttachment) {
        fileOf(context, attachment)?.parentFile?.takeIf { it != filesDir(context) }?.deleteRecursively()
    }

    /** Copies the picked document into private storage. Returns null if it cannot be read. */
    suspend fun importFile(context: Context, uri: Uri): NoteAttachment? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var name: String? = null
            var size: Long? = null
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n != -1) name = c.getString(n)
                    if (s != -1 && !c.isNull(s)) size = c.getLong(s)
                }
            }
            val displayName = name ?: uri.lastPathSegment ?: "file"
            val id = UUID.randomUUID().toString()
            val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val relative = "$id/$safeName"
            val target = File(filesDir(context), relative).apply { parentFile?.mkdirs() }
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            NoteAttachment(
                id = id,
                type = AttachmentType.FILE,
                name = displayName,
                path = relative,
                mimeType = resolver.getType(uri),
                size = size ?: target.length()
            )
        } catch (_: Exception) {
            null
        }
    }
}
