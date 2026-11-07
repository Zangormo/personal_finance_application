package com.example.financeapplication.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Writes and reads the ZIP container of a backup. Files are streamed, so big attachments are fine. */
object BackupArchive {

    /** Paths of all files under [root], relative and '/' separated, sorted. */
    fun listFiles(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    /** Writes [data] and the files it lists (taken from [filesRoot]) to [out]. Does not close [out]. */
    fun write(out: OutputStream, data: BackupData, filesRoot: File) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(BackupJson.encode(data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        data.files.forEach { rel ->
            zip.putNextEntry(ZipEntry(FILES_PREFIX + rel))
            File(filesRoot, rel).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /**
     * Reads a backup from [input]; its files are extracted into [extractTo].
     * Throws [BackupException] when the input is not a usable backup. Entries the format does not
     * know are skipped.
     */
    fun read(input: InputStream, extractTo: File): BackupData {
        var data: BackupData? = null
        val extracted = mutableSetOf<String>()
        try {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == MANIFEST_ENTRY -> {
                        if (data != null) throw BackupException(BackupException.Reason.CORRUPTED, "Two manifests")
                        data = BackupJson.decode(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    name.startsWith(FILES_PREFIX) -> {
                        val rel = name.removePrefix(FILES_PREFIX)
                        val target = safeResolve(extractTo, rel)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        extracted += rel
                    }
                    else -> Unit
                }
                zip.closeEntry()
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            throw BackupException(BackupException.Reason.CORRUPTED, e.message, e)
        } catch (e: IllegalArgumentException) {
            // e.g. a malformed entry name
            throw BackupException(BackupException.Reason.CORRUPTED, e.message, e)
        }
        val result = data ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "No manifest")
        val missing = result.files.filter { it !in extracted }
        if (missing.isNotEmpty()) throw BackupException(BackupException.Reason.CORRUPTED, "Missing files: $missing")
        return result
    }

    /** [root]/[rel], refusing anything that would end up outside of [root] ("zip slip"). */
    internal fun safeResolve(root: File, rel: String): File {
        if (!isSafeRelativePath(rel)) throw BackupException(BackupException.Reason.UNSAFE_PATH, rel)
        val base = root.canonicalFile
        val target = File(base, rel).canonicalFile
        if (!target.path.startsWith(base.path + File.separator)) throw BackupException(BackupException.Reason.UNSAFE_PATH, rel)
        return target
    }
}
