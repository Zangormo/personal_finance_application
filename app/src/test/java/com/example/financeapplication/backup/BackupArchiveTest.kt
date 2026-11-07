package com.example.financeapplication.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

class BackupArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun data(files: List<String> = emptyList()) = BackupData(
        createdAt = 1_762_500_000_000L,
        appVersion = "0.2.2",
        stores = mapOf("app_data_v2" to mapOf("notes" to "[{\"id\":\"1\"}]", "n" to 5)),
        files = files,
    )

    private fun write(root: File, rel: String, bytes: ByteArray) = File(root, rel).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }

    private fun archive(data: BackupData, root: File): ByteArray =
        ByteArrayOutputStream().also { BackupArchive.write(it, data, root) }.toByteArray()

    @Test
    fun listFilesFindsNestedFilesSortedWithForwardSlashes() {
        val root = tmp.newFolder("files")
        write(root, "b/2.txt", byteArrayOf(1))
        write(root, "a.txt", byteArrayOf(2))
        write(root, "b/c/3.bin", byteArrayOf(3))
        File(root, "empty_dir").mkdirs()
        assertEquals(listOf("a.txt", "b/2.txt", "b/c/3.bin"), BackupArchive.listFiles(root))
    }

    @Test
    fun listFilesOfMissingFolderIsEmpty() {
        assertEquals(emptyList<String>(), BackupArchive.listFiles(File(tmp.root, "does-not-exist")))
    }

    @Test
    fun roundTripWithoutFiles() {
        val out = tmp.newFolder("out")
        val bytes = archive(data(), tmp.newFolder("files"))
        assertBackupDataEqual(data(), BackupArchive.read(bytes.stream(), out))
        assertTrue(out.listFiles()!!.isEmpty())
    }

    @Test
    fun roundTripKeepsEveryFileByteForByte() {
        val root = tmp.newFolder("files")
        val contents = mapOf(
            "id-1/photo.jpg" to Random(1).nextBytes(3 * 1024 * 1024), // big binary file
            "id-2/empty.txt" to ByteArray(0),
            "id-3/документ с пробелами.pdf" to Random(2).nextBytes(1000),
            "id-4/deep/er/file.bin" to ByteArray(256) { it.toByte() },
            "id-5/emoji 🎉.txt" to "текст".toByteArray(),
        )
        contents.forEach { (rel, bytes) -> write(root, rel, bytes) }
        val d = data(BackupArchive.listFiles(root))

        val out = tmp.newFolder("out")
        val back = BackupArchive.read(archive(d, root).stream(), out)

        assertBackupDataEqual(d, back)
        assertEquals(filesUnder(root), filesUnder(out))
    }

    @Test
    fun manifestDoesNotHaveToBeTheFirstEntry() {
        val out = tmp.newFolder("out")
        val d = data(listOf("a.txt"))
        val zip = zipOf("files/a.txt" to byteArrayOf(5), MANIFEST_ENTRY to manifestBytes(d))
        assertBackupDataEqual(d, BackupArchive.read(zip.stream(), out))
        assertEquals(listOf<Byte>(5), File(out, "a.txt").readBytes().toList())
    }

    @Test
    fun unknownEntriesAreIgnored() {
        val out = tmp.newFolder("out")
        val zip = zipOf(MANIFEST_ENTRY to manifestBytes(data()), "readme.txt" to byteArrayOf(1), "future/x.json" to byteArrayOf(2))
        assertBackupDataEqual(data(), BackupArchive.read(zip.stream(), out))
        assertTrue(out.listFiles()!!.isEmpty())
    }

    @Test
    fun randomBytesAreNotABackup() {
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) {
            BackupArchive.read(Random(3).nextBytes(5000).stream(), tmp.newFolder("out"))
        }
    }

    @Test
    fun emptyInputIsNotABackup() {
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupArchive.read(ByteArray(0).stream(), tmp.newFolder("out")) }
    }

    @Test
    fun plainJsonFileIsNotABackup() {
        // a manifest on its own, not inside a ZIP
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) {
            BackupArchive.read(manifestBytes(data()).stream(), tmp.newFolder("out"))
        }
    }

    @Test
    fun zipWithoutManifestIsNotABackup() {
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) {
            BackupArchive.read(zipOf("photo.jpg" to byteArrayOf(1, 2)).stream(), tmp.newFolder("out"))
        }
    }

    @Test
    fun zipOfAnotherAppIsNotABackup() {
        val zip = zipOf(MANIFEST_ENTRY to "{\"format\":\"something-else\"}".toByteArray())
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupArchive.read(zip.stream(), tmp.newFolder("out")) }
    }

    @Test
    fun newerBackupIsReported() {
        val manifest = BackupJson.encode(data()).replace("\"version\":$BACKUP_VERSION", "\"version\":${BACKUP_VERSION + 1}")
        val zip = zipOf(MANIFEST_ENTRY to manifest.toByteArray())
        assertBackupError(BackupException.Reason.NEWER_VERSION) { BackupArchive.read(zip.stream(), tmp.newFolder("out")) }
    }

    @Test
    fun listedFileMissingFromArchiveIsCorrupted() {
        val zip = zipOf(MANIFEST_ENTRY to manifestBytes(data(listOf("a.txt", "b.txt"))), "files/a.txt" to byteArrayOf(1))
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupArchive.read(zip.stream(), tmp.newFolder("out")) }
    }

    @Test
    fun twoManifestsAreCorrupted() {
        // ZipOutputStream refuses duplicate names, so write a same-length name and patch it afterwards
        val other = "manifesT.json"
        val zip = zipOf(MANIFEST_ENTRY to manifestBytes(data()), other to manifestBytes(data()))
        val patched = String(zip, Charsets.ISO_8859_1).replace(other, MANIFEST_ENTRY).toByteArray(Charsets.ISO_8859_1)
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupArchive.read(patched.stream(), tmp.newFolder("out")) }
    }

    @Test
    fun truncatedArchiveFails() {
        val root = tmp.newFolder("files")
        write(root, "big.bin", Random(4).nextBytes(200_000))
        val full = archive(data(BackupArchive.listFiles(root)), root)

        // cut inside the manifest and inside the file (a cut only in the trailing index still leaves
        // every entry complete, so such a file is rightly accepted)
        listOf(10, 100, full.size / 2).forEach { cut ->
            assertThrows<BackupException> { BackupArchive.read(full.copyOf(cut).stream(), tmp.newFolder("out$cut")) }
        }
    }

    @Test
    fun damagedManifestIsReported() {
        val zip = zipOf(MANIFEST_ENTRY to "{\"format\":\"$BACKUP_FORMAT\",\"version\":1,\"stores\":".toByteArray())
        assertThrows<BackupException> { BackupArchive.read(zip.stream(), tmp.newFolder("out")) }
    }

    @Test
    fun zipSlipEntriesAreRejectedAndNothingIsWrittenOutside() {
        val parent = tmp.newFolder("parent")
        val out = File(parent, "out").apply { mkdirs() }
        listOf("files/../evil.txt", "files/../../evil.txt", "files//abs.txt", "files/a/../../evil.txt").forEach { name ->
            val zip = zipOf(MANIFEST_ENTRY to manifestBytes(data()), name to "pwned".toByteArray())
            assertBackupError(BackupException.Reason.UNSAFE_PATH) { BackupArchive.read(zip.stream(), out) }
        }
        assertFalse(File(parent, "evil.txt").exists())
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun safeResolveStaysInsideTheRoot() {
        val root = tmp.newFolder("root")
        assertEquals(File(root, "a/b.txt").canonicalFile, BackupArchive.safeResolve(root, "a/b.txt"))
        listOf("../x", "a/../../x", "/x", "", "a\\..\\..\\x").forEach { rel ->
            assertBackupError(BackupException.Reason.UNSAFE_PATH) { BackupArchive.safeResolve(root, rel) }
        }
    }

    @Test
    fun writeDoesNotCloseTheStream() {
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() { closed = true }
        }
        BackupArchive.write(out, data(), tmp.newFolder("files"))
        assertFalse(closed)
        assertTrue(out.size() > 0)
    }
}
