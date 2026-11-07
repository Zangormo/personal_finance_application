package com.example.financeapplication.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Compares store maps; ByteArrays are compared by content (data class equality would compare references). */
fun assertStoresEqual(expected: Map<String, Map<String, Any>>, actual: Map<String, Map<String, Any>>) {
    assertEquals("store names", expected.keys, actual.keys)
    expected.forEach { (store, values) -> assertValuesEqual(store, values, actual.getValue(store)) }
}

fun assertValuesEqual(store: String, expected: Map<String, Any>, actual: Map<String, Any>) {
    assertEquals("keys of $store", expected.keys, actual.keys)
    expected.forEach { (key, value) ->
        val other = actual.getValue(key)
        when (value) {
            is ByteArray -> assertArrayEquals("$store/$key", value, other as ByteArray)
            is Float -> {
                assertEquals("type of $store/$key", Float::class.javaObjectType, other::class.javaObjectType)
                // bit-exact: also distinguishes 0.0 / -0.0 and keeps NaN
                assertEquals("$store/$key", value.toRawBits(), (other as Float).toRawBits())
            }
            is Double -> {
                assertEquals("type of $store/$key", Double::class.javaObjectType, other::class.javaObjectType)
                assertEquals("$store/$key", value.toRawBits(), (other as Double).toRawBits())
            }
            else -> {
                assertEquals("type of $store/$key", value::class.java, other::class.java.let { c ->
                    // any Set implementation is fine
                    if (value is Set<*> && other is Set<*>) value::class.java else c
                })
                assertEquals("$store/$key", value, other)
            }
        }
    }
}

fun assertBackupDataEqual(expected: BackupData, actual: BackupData) {
    assertEquals(expected.createdAt, actual.createdAt)
    assertEquals(expected.appVersion, actual.appVersion)
    assertEquals(expected.files, actual.files)
    assertStoresEqual(expected.stores, actual.stores)
}

inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    try {
        block()
    } catch (t: Throwable) {
        if (t is T) return t
        throw AssertionError("Expected ${T::class.java.simpleName} but got ${t::class.java.simpleName}: ${t.message}", t)
    }
    fail("Expected ${T::class.java.simpleName}, nothing was thrown")
    throw IllegalStateException()
}

fun assertBackupError(reason: BackupException.Reason, block: () -> Unit) {
    val e = assertThrows<BackupException>(block)
    assertEquals(e.message, reason, e.reason)
}

/** Builds a ZIP by hand, for malformed / malicious archives. */
fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}

fun manifestBytes(data: BackupData) = BackupJson.encode(data).toByteArray(Charsets.UTF_8)

fun ByteArray.stream() = ByteArrayInputStream(this)

/** Real preferences DataStores in a folder, like the ones of the app. Close with [close]. */
class TestStores(dir: File, names: Collection<String>) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val stores: Map<String, DataStore<Preferences>> = names.associateWith { name ->
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "$name.preferences_pb") })
    }

    fun close() = scope.cancel()
}

/** All files under [root] with their content, by relative path. */
fun filesUnder(root: File): Map<String, List<Byte>> =
    BackupArchive.listFiles(root).associateWith { File(root, it).readBytes().toList() }
