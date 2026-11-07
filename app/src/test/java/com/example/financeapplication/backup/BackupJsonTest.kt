package com.example.financeapplication.backup

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private fun roundTrip(data: BackupData): BackupData = BackupJson.decode(BackupJson.encode(data))

    private fun dataWith(values: Map<String, Any>, files: List<String> = emptyList()) =
        BackupData(createdAt = 1_762_500_000_123L, appVersion = "0.2.2", stores = mapOf("s" to values), files = files)

    // ---- values of every type -----------------------------------------------------------------

    @Test
    fun stringsRoundTrip() {
        val values = mapOf(
            "empty" to "",
            "plain" to "hello",
            "unicode" to "Привет, мир! 你好 🎉👨‍👩‍👧",
            "quotes" to "he said \"hi\" and 'bye'",
            "escapes" to "back\\slash\ttab\nnew line\r\u0000nul",
            "json" to """[{"id":"1","title":"a"},{"nested":{"x":[1,2,3]}}]""",
            "spaces" to "   ",
            "long" to "x".repeat(200_000),
        )
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun booleansRoundTrip() {
        val values = mapOf("t" to true, "f" to false)
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun intsAndLongsRoundTripExactly() {
        val values = mapOf(
            "i0" to 0, "iMin" to Int.MIN_VALUE, "iMax" to Int.MAX_VALUE, "iNeg" to -42,
            "l0" to 0L, "lMin" to Long.MIN_VALUE, "lMax" to Long.MAX_VALUE,
            // bigger than 2^53: would lose precision as a JSON number read into a double
            "lBig" to 9_007_199_254_740_993L,
            "timestamp" to 1_762_500_000_123L,
        )
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun floatsAndDoublesRoundTripBitExact() {
        val values = mapOf(
            "f" to 1234.56f, "fSmall" to 0.1f, "fNeg" to -99.99f, "fZero" to 0f, "fNegZero" to -0f,
            "fMin" to Float.MIN_VALUE, "fMax" to Float.MAX_VALUE,
            "fNaN" to Float.NaN, "fInf" to Float.POSITIVE_INFINITY, "fNegInf" to Float.NEGATIVE_INFINITY,
            "d" to 1234.5678901234, "dSmall" to 0.1, "dMin" to Double.MIN_VALUE, "dMax" to Double.MAX_VALUE,
            "dNaN" to Double.NaN, "dInf" to Double.NEGATIVE_INFINITY, "dNegZero" to -0.0,
        )
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun typesAreKeptApart() {
        // the same number as int, long, float and double must come back as that exact type
        val values = mapOf("i" to 1, "l" to 1L, "f" to 1f, "d" to 1.0, "s" to "1", "b" to true)
        val back = roundTrip(dataWith(values)).stores.getValue("s")
        assertEquals(Integer::class.java, back.getValue("i")::class.javaObjectType)
        assertEquals(java.lang.Long::class.java, back.getValue("l")::class.javaObjectType)
        assertEquals(java.lang.Float::class.java, back.getValue("f")::class.javaObjectType)
        assertEquals(java.lang.Double::class.java, back.getValue("d")::class.javaObjectType)
        assertEquals(String::class.java, back.getValue("s")::class.java)
        assertEquals(java.lang.Boolean::class.java, back.getValue("b")::class.javaObjectType)
    }

    @Test
    fun stringSetsRoundTrip() {
        val values = mapOf(
            "empty" to emptySet<String>(),
            "one" to setOf("Milk"),
            "many" to setOf("Bread", "Молоко", "", "a|12.5", "emoji 🍞"),
        )
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun byteArraysRoundTrip() {
        val values = mapOf(
            "empty" to ByteArray(0),
            "all" to ByteArray(256) { it.toByte() },
            "random" to kotlin.random.Random(7).nextBytes(10_000),
        )
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun keysWithUnusualCharactersRoundTrip() {
        val values = mapOf("" to "empty key", "with space" to 1, "кириллица" to 2L, "a.b/c\"d" to true)
        assertBackupDataEqual(dataWith(values), roundTrip(dataWith(values)))
    }

    @Test
    fun manyStoresAndEmptyStoresRoundTrip() {
        val data = BackupData(
            createdAt = 0L,
            appVersion = "",
            stores = mapOf(
                "app_data_v2" to mapOf("notes" to "[]", "home_order" to "BALANCE,NOTES"),
                "user_prefs" to mapOf("overall_balance" to 10.5f, "is_first_run" to false),
                "empty_store" to emptyMap(),
            ),
            files = listOf("a/b.txt", "c d/ё.png"),
        )
        assertBackupDataEqual(data, roundTrip(data))
    }

    @Test
    fun headerIsWritten() {
        val json = JsonParser.parseString(BackupJson.encode(dataWith(emptyMap()))).asJsonObject
        assertEquals(BACKUP_FORMAT, json.get("format").asString)
        assertEquals(BACKUP_VERSION, json.get("version").asInt)
        assertEquals("1762500000123", json.get("createdAt").asString)
        assertEquals("0.2.2", json.get("appVersion").asString)
    }

    @Test
    fun encodingIsDeterministic() {
        // same data in a different map / set order gives the same file content
        val a = BackupData(1, "v", linkedMapOf("b" to linkedMapOf("y" to setOf("2", "1"), "x" to 1), "a" to emptyMap()), emptyList())
        val b = BackupData(1, "v", linkedMapOf("a" to emptyMap(), "b" to linkedMapOf("x" to 1, "y" to setOf("1", "2"))), emptyList())
        assertEquals(BackupJson.encode(a), BackupJson.encode(b))
    }

    @Test
    fun unsupportedValueTypeIsRejectedOnEncode() {
        assertThrows<IllegalArgumentException> { BackupJson.encode(dataWith(mapOf("x" to listOf("a")))) }
        assertThrows<IllegalArgumentException> { BackupJson.encode(dataWith(mapOf("x" to setOf(1, 2)))) }
    }

    // ---- validation -----------------------------------------------------------------------------

    private fun manifest(vararg fields: Pair<String, String>): String {
        val base = linkedMapOf(
            "format" to "\"$BACKUP_FORMAT\"",
            "version" to "$BACKUP_VERSION",
            "createdAt" to "\"1\"",
            "appVersion" to "\"x\"",
            "stores" to "{}",
            "files" to "[]",
        )
        fields.forEach { (k, v) -> if (v == "<remove>") base.remove(k) else base[k] = v }
        return base.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    @Test
    fun minimalManifestIsAccepted() {
        val data = BackupJson.decode(manifest())
        assertEquals(1L, data.createdAt)
        assertTrue(data.stores.isEmpty())
        assertTrue(data.files.isEmpty())
    }

    @Test
    fun notJsonIsNotABackup() {
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode("this is just text") }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode("") }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode("[1,2,3]") }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode("{\"a\":") }
    }

    @Test
    fun otherJsonIsNotABackup() {
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode("{\"name\":\"something else\"}") }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode(manifest("format" to "\"other-app-backup\"")) }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode(manifest("format" to "<remove>")) }
        assertBackupError(BackupException.Reason.NOT_A_BACKUP) { BackupJson.decode(manifest("format" to "{}")) }
    }

    @Test
    fun newerVersionIsReported() {
        assertBackupError(BackupException.Reason.NEWER_VERSION) { BackupJson.decode(manifest("version" to "${BACKUP_VERSION + 1}")) }
        assertBackupError(BackupException.Reason.NEWER_VERSION) { BackupJson.decode(manifest("version" to "999")) }
    }

    @Test
    fun badVersionIsCorrupted() {
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupJson.decode(manifest("version" to "0")) }
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupJson.decode(manifest("version" to "-1")) }
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupJson.decode(manifest("version" to "\"abc\"")) }
        assertBackupError(BackupException.Reason.CORRUPTED) { BackupJson.decode(manifest("version" to "<remove>")) }
    }

    @Test
    fun missingOrBrokenPartsAreCorrupted() {
        listOf(
            "createdAt" to "<remove>",
            "createdAt" to "\"yesterday\"",
            "stores" to "<remove>",
            "stores" to "[]",
            "stores" to "{\"s\":[]}",
            "stores" to "{\"s\":{\"k\":\"plain\"}}",
            "stores" to "{\"s\":{\"k\":{\"v\":\"no type\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"string\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"color\",\"v\":\"red\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"int\",\"v\":\"1.5\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"int\",\"v\":\"99999999999\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"long\",\"v\":\"x\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"float\",\"v\":\"abc\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"boolean\",\"v\":\"maybe\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"string_set\",\"v\":\"not an array\"}}}",
            "stores" to "{\"s\":{\"k\":{\"t\":\"bytes\",\"v\":\"***not base64***\"}}}",
            "files" to "<remove>",
            "files" to "\"a.txt\"",
            "files" to "[\"a.txt\",\"a.txt\"]",
        ).forEach { field ->
            assertBackupError(BackupException.Reason.CORRUPTED) { BackupJson.decode(manifest(field)) }
        }
    }

    @Test
    fun appVersionIsOptional() {
        assertEquals("", BackupJson.decode(manifest("appVersion" to "<remove>")).appVersion)
        assertEquals("", BackupJson.decode(manifest("appVersion" to "null")).appVersion)
    }

    @Test
    fun unknownTopLevelFieldsAreIgnored() {
        BackupJson.decode(manifest("future" to "{\"something\":[1,2]}"))
    }

    @Test
    fun unsafeFilePathsAreRejected() {
        listOf("../evil", "a/../../evil", "/etc/passwd", "a//b", "./a", "a/./b", "C:\\x", "a\\b", "", "a/", "..").forEach { path ->
            assertBackupError(BackupException.Reason.UNSAFE_PATH) {
                BackupJson.decode(manifest("files" to "[\"${path.replace("\\", "\\\\")}\"]"))
            }
        }
    }

    @Test
    fun safePathCheck() {
        listOf("a", "a.txt", "dir/file.png", "uuid-1/фото 1.jpg", "a..b", "...x", ".hidden").forEach {
            assertTrue(it, isSafeRelativePath(it))
        }
        listOf("", "/a", "a/", "a//b", ".", "..", "a/..", "../a", "a/./b", "a\\b", "C:x", "a\u0000b").forEach {
            assertTrue(it, !isSafeRelativePath(it))
        }
    }
}
