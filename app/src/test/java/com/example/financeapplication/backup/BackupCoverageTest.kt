package com.example.financeapplication.backup

import com.example.financeapplication.datastores.StoreNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards that backups stay complete when the app grows: every DataStore declared anywhere in the
 * code must be registered in allDataStores() (AppStores.kt) under a StoreNames constant.
 */
class BackupCoverageTest {

    private val sources: List<File> by lazy {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    private val storeNameConstants: Map<String, String> =
        StoreNames::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to it.get(null) as String }

    @Test
    fun everyDeclaredDataStoreUsesAStoreName() {
        val declarations = sources.flatMap { file ->
            Regex("""preferencesDataStore\(([^)]*)\)""").findAll(file.readText()).map { file.name to it.groupValues[1].trim() }.toList()
        }
        assertTrue("no DataStores found – is the source path right?", declarations.isNotEmpty())
        declarations.forEach { (file, arg) ->
            assertTrue("$file declares a DataStore with $arg; use a StoreNames constant so it is backed up", arg.startsWith("StoreNames."))
            assertTrue("$file: unknown constant $arg", arg.removePrefix("StoreNames.") in storeNameConstants)
        }
        assertEquals(
            "every StoreNames constant is used by exactly one DataStore",
            storeNameConstants.keys.sorted(),
            declarations.map { it.second.removePrefix("StoreNames.") }.sorted()
        )
    }

    @Test
    fun everyStoreNameIsRegisteredForBackups() {
        val appStores = sources.first { it.name == "AppStores.kt" }.readText()
        val registry = appStores.substringAfter("fun Context.allDataStores()")
        storeNameConstants.keys.forEach { name ->
            assertTrue("StoreNames.$name is missing in allDataStores()", registry.contains("StoreNames.$name to"))
        }
    }

    @Test
    fun storeNamesAreUnique() {
        assertEquals(storeNameConstants.size, storeNameConstants.values.toSet().size)
    }

    @Test
    fun noteFilesLiveInTheBackedUpFolder() {
        // attachments are copied into NotesDataStore.filesDir, which is the folder backups include
        val notesStore = sources.first { it.name == "NotesDataStore.kt" }.readText()
        assertTrue(notesStore.contains("File(filesDir(context), relative)"))
    }
}
