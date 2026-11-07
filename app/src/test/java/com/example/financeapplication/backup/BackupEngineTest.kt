package com.example.financeapplication.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.financeapplication.classes.NecessityLevel
import com.example.financeapplication.datastores.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Whole-app round trips: a "device" full of realistic data (every section of the app, written in
 * the same keys and JSON format the app uses) is exported and imported on a second "device".
 */
class BackupEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeNames = listOf(
        StoreNames.APP, StoreNames.USER, StoreNames.SPENDING, StoreNames.INCOME, StoreNames.ESSENTIALS, StoreNames.WISHLIST,
    )

    /** One phone: its DataStores, note files folder and cache folder. */
    inner class Device(name: String) {
        val dir: File = tmp.newFolder(name)
        val testStores = TestStores(File(dir, "datastore").apply { mkdirs() }, storeNames)
        val stores: Map<String, DataStore<Preferences>> get() = testStores.stores
        val filesRoot = File(dir, "files/note_files").apply { mkdirs() }
        val engine = BackupEngine(stores, filesRoot, File(dir, "cache").apply { mkdirs() })

        fun store(name: String) = stores.getValue(name)
        suspend fun snapshot(): Map<String, Map<String, Any>> = stores.mapValues { PrefsMapper.snapshot(it.value.data.first()) }
        suspend fun string(store: String, key: String): String? = store(store).data.first()[stringPreferencesKey(key)]
        internal inline fun <reified T> list(json: String?): List<T> =
            if (json == null) emptyList()
            else appGson.fromJson(json, com.google.gson.reflect.TypeToken.getParameterized(List::class.java, T::class.java).type)

        suspend fun exportBytes(now: Long = NOW): ByteArray =
            ByteArrayOutputStream().also { engine.export(it, "0.2.2", now) }.toByteArray()

        suspend fun import(bytes: ByteArray) = engine.apply(engine.prepare(bytes.stream()))

        fun cacheIsClean() = File(dir, "cache").listFiles()!!.isEmpty()
    }

    private lateinit var phoneA: Device
    private lateinit var phoneB: Device

    @Before
    fun setUp() {
        phoneA = Device("phoneA")
        phoneB = Device("phoneB")
    }

    @After
    fun tearDown() {
        phoneA.testStores.close()
        phoneB.testStores.close()
    }

    // ---- realistic content ---------------------------------------------------------------------

    private val spendings = listOf(
        SpendingRecord(12.5f, listOf("Milk", "Bread"), 1_700_000_000_000L, NecessityLevel.NECESSARY),
        SpendingRecord(999.99f, listOf("Телевизор 📺"), 1_700_100_000_000L, NecessityLevel.NOT_NEEDED),
        SpendingRecord(9.99f, listOf("Netflix"), 1_700_200_000_000L, NecessityLevel.MEDIUM, source = SOURCE_SUBSCRIPTION),
        SpendingRecord(0.01f, emptyList(), 1_700_300_000_000L),
    )
    private val incomes = listOf(
        IncomeRecord(2500f, "Salary", 1_699_000_000_000L),
        IncomeRecord(150.75f, "Премия \"за проект\"", 1_699_500_000_000L),
        IncomeRecord(1f, "", 1_699_600_000_000L),
    )
    private val attachment = NoteAttachment(
        id = "att-1", type = AttachmentType.FILE, name = "photo.jpg",
        path = "att-1/photo.jpg", mimeType = "image/jpeg", size = 50_000, addedAt = 1_700_000_000_000L
    )
    private val notes = listOf(
        Note(
            id = "n1", title = "Shopping", text = "Bold and italic\n• item",
            spans = listOf(NoteSpan(NoteStyle.BOLD, 0, 4), NoteSpan(NoteStyle.ITALIC, 9, 15), NoteSpan(NoteStyle.HIGHLIGHT, 0, 15)),
            attachments = listOf(
                attachment,
                NoteAttachment(id = "l1", type = AttachmentType.LINK, name = "Site", url = "https://zangormo.github.io/"),
            ),
            createdAt = 1_690_000_000_000L, updatedAt = 1_700_000_000_000L, order = 2,
        ),
        Note(id = "n2", title = "Пароль от Wi-Fi", text = "secret", secret = true, order = 0,
            createdAt = 1_691_000_000_000L, updatedAt = 1_691_000_000_000L),
        Note(id = "n3", title = "", text = "untitled note", order = 1, createdAt = 1L, updatedAt = 2L),
    )
    private val categories = listOf(
        TodoCategory(id = "c-work", name = "Work", createdAt = 1L, order = 1),
        TodoCategory(id = "c-home", name = "Дом", createdAt = 2L, order = 0),
        TodoCategory(id = "c-long", name = "A category with a very long name that wraps", createdAt = 3L, order = 2),
    )
    private val tasks = listOf(
        TodoItem(id = "t1", categoryId = "c-work", title = "Report", comment = "until Friday",
            dueAt = 1_700_000_000_000L, hasTime = true, createdAt = 10L),
        TodoItem(id = "t2", categoryId = "c-home", title = "Water plants", repeat = RepeatPeriod.WEEKLY,
            completedAt = 1_700_000_000_000L, createdAt = 11L),
        TodoItem(id = "t3", categoryId = UNCATEGORIZED, title = "Done one", done = true, completedAt = 5L, createdAt = 12L),
        TodoItem(id = "t4", categoryId = "deleted-category", title = "Orphan", createdAt = 13L),
    )
    private val debts = listOf(
        Debt(id = "d1", person = "Alex", amount = 100f, direction = DebtDirection.I_OWE, note = "lunch",
            createdAt = 1L, dueAt = 2L, payments = listOf(DebtPayment(30f, 3L), DebtPayment(20.5f, 4L))),
        Debt(id = "d2", person = "Маша", amount = 50f, direction = DebtDirection.OWED_TO_ME, createdAt = 5L),
    )
    private val subscriptions = listOf(
        Subscription(id = "s1", name = "Netflix", amount = 9.99f, period = RepeatPeriod.MONTHLY, startAt = 1_690_000_000_000L,
            chargesMade = 3, necessity = NecessityLevel.MEDIUM),
        Subscription(id = "s2", name = "Gym", amount = 300f, period = RepeatPeriod.YEARLY, startAt = 1L, active = false),
    )
    private val planned = listOf(
        PlannedItem(id = "p1", name = "Car wash", price = 15f, type = PlannedType.ESSENTIAL, reusable = true, createdAt = 1L),
        PlannedItem(id = "p2", name = "Headphones", price = 199f, type = PlannedType.WISH, createdAt = 2L),
    )
    private val homeOrder = "NOTES,BALANCE,TODO,SPENDING,INCOME,HISTORY,PLANNED,SAVINGS,OVERVIEW,SUBSCRIPTIONS,DEBTS"
    private val photo = Random(42).nextBytes(50_000)
    private val pdf = Random(43).nextBytes(7_000)

    private suspend fun Device.fillWithEverything() {
        store(StoreNames.APP).edit {
            it[stringPreferencesKey("notes")] = appGson.toJson(notes)
            it[stringPreferencesKey("todo_categories")] = appGson.toJson(categories)
            it[stringPreferencesKey("todo_items")] = appGson.toJson(tasks)
            it[stringPreferencesKey("debts")] = appGson.toJson(debts)
            it[stringPreferencesKey("subscriptions")] = appGson.toJson(subscriptions)
            it[stringPreferencesKey("planned_items")] = appGson.toJson(planned)
            it[stringPreferencesKey("notes_sort")] = NoteSort.CUSTOM.name
            it[stringPreferencesKey("home_order")] = homeOrder
            it[stringPreferencesKey("home_balance_size")] = BalanceSize.SMALL.name
            it[booleanPreferencesKey("planned_migrated_v1")] = true
        }
        store(StoreNames.USER).edit {
            it[booleanPreferencesKey("is_first_run")] = false
            it[floatPreferencesKey("overall_balance")] = 1234.56f
            it[floatPreferencesKey("savings_balance")] = 5000.01f
        }
        store(StoreNames.SPENDING).edit { it[stringPreferencesKey("spendings_list")] = appGson.toJson(spendings) }
        store(StoreNames.INCOME).edit { it[stringPreferencesKey("incomes_list")] = appGson.toJson(incomes) }
        store(StoreNames.ESSENTIALS).edit { it[stringSetPreferencesKey("essentials_list")] = setOf("Soap", "Хлеб") }
        store(StoreNames.WISHLIST).edit { it[stringSetPreferencesKey("wishlist_list")] = setOf("Bike|450.0", "Book") }
        File(filesRoot, "att-1").mkdirs()
        File(filesRoot, "att-1/photo.jpg").writeBytes(photo)
        File(filesRoot, "att-2/док ументы").mkdirs()
        File(filesRoot, "att-2/док ументы/file.pdf").writeBytes(pdf)
    }

    // ---- full transfer ---------------------------------------------------------------------------

    @Test
    fun everythingIsTransferredToAnEmptyDevice() = runBlocking {
        phoneA.fillWithEverything()
        val before = phoneA.snapshot()

        phoneB.import(phoneA.exportBytes())

        // every key of every store, with the same type and value
        assertStoresEqual(before, phoneB.snapshot())
        // every attachment file, byte for byte
        assertEquals(filesUnder(phoneA.filesRoot), filesUnder(phoneB.filesRoot))
        assertEquals(listOf("att-1/photo.jpg", "att-2/док ументы/file.pdf"), BackupArchive.listFiles(phoneB.filesRoot))
        assertTrue(phoneB.cacheIsClean())
    }

    @Test
    fun importedDataReadsBackAsTheSameObjects() = runBlocking {
        phoneA.fillWithEverything()
        phoneB.import(phoneA.exportBytes())

        with(phoneB) {
            assertEquals(spendings, list<SpendingRecord>(string(StoreNames.SPENDING, "spendings_list")))
            assertEquals(incomes, list<IncomeRecord>(string(StoreNames.INCOME, "incomes_list")))
            assertEquals(notes, list<Note>(string(StoreNames.APP, "notes")))
            assertEquals(categories, list<TodoCategory>(string(StoreNames.APP, "todo_categories")))
            assertEquals(tasks, list<TodoItem>(string(StoreNames.APP, "todo_items")))
            assertEquals(debts, list<Debt>(string(StoreNames.APP, "debts")))
            assertEquals(subscriptions, list<Subscription>(string(StoreNames.APP, "subscriptions")))
            assertEquals(planned, list<PlannedItem>(string(StoreNames.APP, "planned_items")))
        }
    }

    @Test
    fun ordersAndLayoutAreKept() = runBlocking {
        phoneA.fillWithEverything()
        phoneB.import(phoneA.exportBytes())

        with(phoneB) {
            // home tiles and balance size
            assertEquals(homeOrder, string(StoreNames.APP, "home_order"))
            assertEquals(BalanceSize.SMALL.name, string(StoreNames.APP, "home_balance_size"))
            // to-do category tiles: same order values, so the same order on screen
            assertEquals(listOf("c-home", "c-work", "c-long"), list<TodoCategory>(string(StoreNames.APP, "todo_categories")).ordered().map { it.id })
            // notes: custom order and sort mode
            assertEquals(NoteSort.CUSTOM.name, string(StoreNames.APP, "notes_sort"))
            assertEquals(mapOf("n1" to 2, "n2" to 0, "n3" to 1), list<Note>(string(StoreNames.APP, "notes")).associate { it.id to it.order })
            // list order itself (history order, task order) is unchanged
            assertEquals(spendings.map { it.timestamp }, list<SpendingRecord>(string(StoreNames.SPENDING, "spendings_list")).map { it.timestamp })
            assertEquals(tasks.map { it.id }, list<TodoItem>(string(StoreNames.APP, "todo_items")).map { it.id })
        }
    }

    @Test
    fun secretNotesFormattingAndAttachmentsSurvive() = runBlocking {
        phoneA.fillWithEverything()
        phoneB.import(phoneA.exportBytes())

        val imported = phoneB.list<Note>(phoneB.string(StoreNames.APP, "notes")).associateBy { it.id }
        assertEquals(true, imported.getValue("n2").secret)
        assertEquals(notes[0].spans, imported.getValue("n1").spans)
        val file = imported.getValue("n1").attachments!!.first { it.type == AttachmentType.FILE }
        // the attachment path inside the note points to the transferred file
        assertEquals(photo.toList(), File(phoneB.filesRoot, file.path!!).readBytes().toList())
    }

    @Test
    fun balancesAndFlagsAreKept() = runBlocking {
        phoneA.fillWithEverything()
        phoneB.import(phoneA.exportBytes())
        val user = phoneB.store(StoreNames.USER).data.first()
        assertEquals(1234.56f, user[floatPreferencesKey("overall_balance")])
        assertEquals(5000.01f, user[floatPreferencesKey("savings_balance")])
        assertEquals(false, user[booleanPreferencesKey("is_first_run")])
        assertEquals(true, phoneB.store(StoreNames.APP).data.first()[booleanPreferencesKey("planned_migrated_v1")])
        assertEquals(setOf("Soap", "Хлеб"), phoneB.store(StoreNames.ESSENTIALS).data.first()[stringSetPreferencesKey("essentials_list")])
        assertEquals(setOf("Bike|450.0", "Book"), phoneB.store(StoreNames.WISHLIST).data.first()[stringSetPreferencesKey("wishlist_list")])
    }

    @Test
    fun keysTheAppDoesNotKnowYetAreTransferredToo() = runBlocking {
        // "absolutely everything": the backup is generic, it does not depend on a list of known keys
        phoneA.store(StoreNames.APP).edit {
            it[stringPreferencesKey("feature_from_the_future")] = "value"
            it[androidx.datastore.preferences.core.longPreferencesKey("some_counter")] = Long.MAX_VALUE
            it[androidx.datastore.preferences.core.doublePreferencesKey("ratio")] = 0.333
            it[androidx.datastore.preferences.core.intPreferencesKey("level")] = -5
            it[androidx.datastore.preferences.core.byteArrayPreferencesKey("blob")] = byteArrayOf(9, 8, 7)
        }
        val before = phoneA.snapshot()
        phoneB.import(phoneA.exportBytes())
        assertStoresEqual(before, phoneB.snapshot())
    }

    // ---- replacing existing data --------------------------------------------------------------

    @Test
    fun importReplacesEverythingOnTheTargetDevice() = runBlocking {
        phoneA.fillWithEverything()
        // phone B already has its own data and files
        phoneB.store(StoreNames.APP).edit {
            it[stringPreferencesKey("notes")] = appGson.toJson(listOf(Note(id = "old", title = "Old note")))
            it[stringPreferencesKey("only_on_b")] = "should disappear"
        }
        phoneB.store(StoreNames.USER).edit { it[floatPreferencesKey("overall_balance")] = -1f }
        File(phoneB.filesRoot, "old/old.txt").apply { parentFile!!.mkdirs(); writeText("old") }

        phoneB.import(phoneA.exportBytes())

        assertStoresEqual(phoneA.snapshot(), phoneB.snapshot())
        assertEquals(filesUnder(phoneA.filesRoot), filesUnder(phoneB.filesRoot))
        assertFalse(File(phoneB.filesRoot, "old").exists())
    }

    @Test
    fun importingAnEmptyBackupClearsEverything() = runBlocking {
        phoneB.fillWithEverything()
        phoneB.import(phoneA.exportBytes()) // phone A is empty
        assertTrue(phoneB.snapshot().values.all { it.isEmpty() })
        assertTrue(BackupArchive.listFiles(phoneB.filesRoot).isEmpty())
        assertTrue(phoneB.filesRoot.isDirectory) // the folder is still there for new attachments
    }

    @Test
    fun storeMissingFromTheBackupIsEmptied() = runBlocking {
        // e.g. a backup from an older version that did not have this store yet
        phoneB.store(StoreNames.WISHLIST).edit { it[stringSetPreferencesKey("wishlist_list")] = setOf("x") }
        val data = BackupData(NOW, "0.1", mapOf(StoreNames.USER to mapOf("is_first_run" to false)), emptyList())
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, data, phoneA.filesRoot) }.toByteArray()

        phoneB.import(bytes)

        assertTrue(phoneB.store(StoreNames.WISHLIST).data.first().asMap().isEmpty())
        assertEquals(false, phoneB.store(StoreNames.USER).data.first()[booleanPreferencesKey("is_first_run")])
    }

    @Test
    fun storeTheAppDoesNotKnowIsIgnored() = runBlocking {
        val data = BackupData(
            NOW, "9.9",
            mapOf(StoreNames.USER to mapOf("overall_balance" to 5f), "store_from_the_future" to mapOf("x" to 1)),
            emptyList()
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, data, phoneA.filesRoot) }.toByteArray()
        phoneB.import(bytes)
        assertEquals(setOf(*storeNames.toTypedArray()), phoneB.snapshot().keys)
        assertEquals(5f, phoneB.store(StoreNames.USER).data.first()[floatPreferencesKey("overall_balance")])
    }

    // ---- failures leave data untouched ----------------------------------------------------------

    @Test
    fun brokenFileDoesNotChangeAnything() = runBlocking {
        phoneB.fillWithEverything()
        val before = phoneB.snapshot()
        val filesBefore = filesUnder(phoneB.filesRoot)

        listOf(
            Random(1).nextBytes(1000),
            ByteArray(0),
            zipOf("something.txt" to byteArrayOf(1)),
            zipOf(MANIFEST_ENTRY to "{\"format\":\"$BACKUP_FORMAT\",\"version\":99}".toByteArray()),
            zipOf(MANIFEST_ENTRY to manifestBytes(BackupData(1, "", emptyMap(), listOf("missing.bin")))),
            zipOf(MANIFEST_ENTRY to manifestBytes(BackupData(1, "", emptyMap(), emptyList())), "files/../escape.txt" to byteArrayOf(1)),
        ).forEach { bad ->
            assertThrows<BackupException> { runBlocking { phoneB.engine.prepare(bad.stream()) } }
        }

        assertStoresEqual(before, phoneB.snapshot())
        assertEquals(filesBefore, filesUnder(phoneB.filesRoot))
        assertTrue("temporary files are removed", phoneB.cacheIsClean())
        assertFalse(File(phoneB.dir, "cache/escape.txt").exists())
    }

    @Test
    fun preparedButDiscardedImportChangesNothing() = runBlocking {
        phoneA.fillWithEverything()
        phoneB.store(StoreNames.USER).edit { it[floatPreferencesKey("overall_balance")] = 7f }
        val before = phoneB.snapshot()

        val prepared = phoneB.engine.prepare(phoneA.exportBytes().stream())
        phoneB.engine.discard(prepared)

        assertStoresEqual(before, phoneB.snapshot())
        assertTrue(BackupArchive.listFiles(phoneB.filesRoot).isEmpty())
        assertTrue(phoneB.cacheIsClean())
    }

    // ---- stability ------------------------------------------------------------------------------

    @Test
    fun exportImportExportGivesTheSameBackup() = runBlocking {
        phoneA.fillWithEverything()
        val first = phoneA.exportBytes(now = NOW)
        phoneB.import(first)
        val second = phoneB.exportBytes(now = NOW)
        val a = BackupArchive.read(first.stream(), tmp.newFolder("a"))
        val b = BackupArchive.read(second.stream(), tmp.newFolder("b"))
        assertEquals(BackupJson.encode(a), BackupJson.encode(b))
    }

    @Test
    fun repeatedImportsAreIdempotent() = runBlocking {
        phoneA.fillWithEverything()
        val bytes = phoneA.exportBytes()
        repeat(3) { phoneB.import(bytes) }
        assertStoresEqual(phoneA.snapshot(), phoneB.snapshot())
        assertEquals(filesUnder(phoneA.filesRoot), filesUnder(phoneB.filesRoot))
        assertTrue(phoneB.cacheIsClean())
    }

    @Test
    fun exportDoesNotChangeTheSourceDevice() = runBlocking {
        phoneA.fillWithEverything()
        val before = phoneA.snapshot()
        val filesBefore = filesUnder(phoneA.filesRoot)
        phoneA.exportBytes()
        assertStoresEqual(before, phoneA.snapshot())
        assertEquals(filesBefore, filesUnder(phoneA.filesRoot))
    }

    @Test
    fun exportRecordsTimeAndVersion() = runBlocking {
        val data = phoneA.engine.export(ByteArrayOutputStream(), "1.2.3", now = 42L)
        assertEquals(42L, data.createdAt)
        assertEquals("1.2.3", data.appVersion)
        assertEquals(setOf(*storeNames.toTypedArray()), data.stores.keys)
    }

    @Test
    fun deviceCanImportItsOwnBackup() = runBlocking {
        phoneA.fillWithEverything()
        val before = phoneA.snapshot()
        val files = filesUnder(phoneA.filesRoot)
        val bytes = phoneA.exportBytes()
        // the user changes things, then restores the backup
        phoneA.store(StoreNames.USER).edit { it[floatPreferencesKey("overall_balance")] = 0f }
        File(phoneA.filesRoot, "att-1/photo.jpg").delete()
        phoneA.import(bytes)
        assertStoresEqual(before, phoneA.snapshot())
        assertEquals(files, filesUnder(phoneA.filesRoot))
    }

    // ---- summary ---------------------------------------------------------------------------------

    @Test
    fun summaryCountsEverySection() = runBlocking {
        phoneA.fillWithEverything()
        val prepared = phoneB.engine.prepare(phoneA.exportBytes(now = 99L).stream())
        val s = prepared.summary
        assertEquals(99L, s.createdAt)
        assertEquals("0.2.2", s.appVersion)
        assertEquals(spendings.size, s.spendings)
        assertEquals(incomes.size, s.incomes)
        assertEquals(notes.size, s.notes)
        assertEquals(tasks.size, s.tasks)
        assertEquals(categories.size, s.taskCategories)
        assertEquals(debts.size, s.debts)
        assertEquals(subscriptions.size, s.subscriptions)
        assertEquals(planned.size, s.plannedItems)
        assertEquals(2, s.files)
        phoneB.engine.discard(prepared)
    }

    @Test
    fun summaryOfEmptyOrOddDataIsZero() {
        val s = BackupSummary.of(
            BackupData(
                1, "", mapOf(
                    StoreNames.APP to mapOf("notes" to "not json", "todo_items" to "{\"an\":\"object\"}", "debts" to 5),
                    StoreNames.SPENDING to mapOf("spendings_list" to "[]"),
                ), emptyList()
            )
        )
        assertEquals(BackupSummary(1, "", 0, 0, 0, 0, 0, 0, 0, 0, 0), s)
    }

    @Test
    fun backupFileIsAZipWithTheManifestFirst() = runBlocking {
        phoneA.fillWithEverything()
        val bytes = phoneA.exportBytes()
        // "PK" signature
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
        val names = java.util.zip.ZipInputStream(bytes.stream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(MANIFEST_ENTRY, names.first())
        assertEquals(listOf("files/att-1/photo.jpg", "files/att-2/док ументы/file.pdf"), names.drop(1))
    }

    companion object {
        private const val NOW = 1_762_500_000_000L
    }
}
