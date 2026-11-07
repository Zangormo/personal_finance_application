package com.example.financeapplication.backup

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/*
 * Backup file (*.pcbackup) = ZIP archive:
 *   manifest.json         – header + every key of every preferences DataStore, with its type
 *   files/<relative path> – note attachment files (relative to the note files folder)
 *
 * manifest.json:
 * {
 *   "format": "personal-cashier-backup", "version": 1,
 *   "createdAt": 1762500000000, "appVersion": "0.2.2",
 *   "stores": { "<store name>": { "<key>": { "t": "<type>", "v": <value> }, ... }, ... },
 *   "files": [ "<relative path>", ... ]
 * }
 * Types: string, boolean, int, long, float, double (numbers are written as strings, so nothing is
 * lost to JSON number precision and NaN/Infinity survive), string_set (array of strings), bytes (base64).
 */

const val BACKUP_FORMAT = "personal-cashier-backup"
const val BACKUP_VERSION = 1
const val BACKUP_EXTENSION = "pcbackup"
const val MANIFEST_ENTRY = "manifest.json"
const val FILES_PREFIX = "files/"

class BackupException(val reason: Reason, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: reason.name, cause) {
    enum class Reason {
        /** The file is not a backup of this app at all. */
        NOT_A_BACKUP,
        /** Made by a newer version of the app that uses a format this version does not know. */
        NEWER_VERSION,
        /** Looks like a backup but is damaged or incomplete. */
        CORRUPTED,
        /** Contains a file path that would be written outside of the app's folder. */
        UNSAFE_PATH,
    }
}

/**
 * Everything a backup holds.
 * [stores]: store name -> key -> value. Values are of the types a preferences DataStore can hold:
 * String, Boolean, Int, Long, Float, Double, Set<String> or ByteArray.
 * [files]: paths of the attachment files, relative to the note files folder, '/' separated.
 */
data class BackupData(
    val createdAt: Long,
    val appVersion: String,
    val stores: Map<String, Map<String, Any>>,
    val files: List<String>,
)

/** A path inside the files folder: relative, '/' separated, without "." / ".." parts. */
internal fun isSafeRelativePath(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) return false
    if (path.length >= 2 && path[1] == ':') return false // "C:..."
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

object BackupJson {

    fun encode(data: BackupData): String {
        val root = JsonObject()
        root.addProperty("format", BACKUP_FORMAT)
        root.addProperty("version", BACKUP_VERSION)
        root.addProperty("createdAt", data.createdAt.toString())
        root.addProperty("appVersion", data.appVersion)
        val stores = JsonObject()
        data.stores.toSortedMap().forEach { (storeName, values) ->
            val store = JsonObject()
            values.toSortedMap().forEach { (key, value) -> store.add(key, encodeValue(key, value)) }
            stores.add(storeName, store)
        }
        root.add("stores", stores)
        root.add("files", JsonArray().apply { data.files.forEach { add(it) } })
        return root.toString()
    }

    private fun encodeValue(key: String, value: Any): JsonObject = JsonObject().apply {
        when (value) {
            is String -> { addProperty("t", "string"); addProperty("v", value) }
            is Boolean -> { addProperty("t", "boolean"); addProperty("v", value) }
            is Int -> { addProperty("t", "int"); addProperty("v", value.toString()) }
            is Long -> { addProperty("t", "long"); addProperty("v", value.toString()) }
            is Float -> { addProperty("t", "float"); addProperty("v", value.toString()) }
            is Double -> { addProperty("t", "double"); addProperty("v", value.toString()) }
            is ByteArray -> { addProperty("t", "bytes"); addProperty("v", Base64.getEncoder().encodeToString(value)) }
            is Set<*> -> {
                addProperty("t", "string_set")
                add("v", JsonArray().apply {
                    value.map { it as? String ?: throw IllegalArgumentException("Set \"$key\" contains a non-string value") }
                        .sorted()
                        .forEach { add(it) }
                })
            }
            else -> throw IllegalArgumentException("Unsupported value type for \"$key\": ${value::class.java.name}")
        }
    }

    /** Reads and validates a manifest. Throws [BackupException] when it cannot be used. */
    fun decode(json: String): BackupData {
        val root: JsonObject = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Not JSON", e)
        } ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Not a JSON object")

        val format = (root.get("format") as? JsonElement)?.takeIf { it.isJsonPrimitive }?.asString
        if (format != BACKUP_FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Unknown format: $format")

        return try {
            val version = root.get("version")?.asInt ?: throw corrupted("No version")
            if (version > BACKUP_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION, "Version $version")
            if (version < 1) throw corrupted("Bad version $version")

            val createdAt = root.get("createdAt")?.asString?.toLong() ?: throw corrupted("No createdAt")
            val appVersion = root.get("appVersion")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

            val storesJson = root.get("stores")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw corrupted("No stores")
            val stores = storesJson.entrySet().associate { (storeName, storeJson) ->
                if (!storeJson.isJsonObject) throw corrupted("Store $storeName is not an object")
                storeName to storeJson.asJsonObject.entrySet().associate { (key, valueJson) ->
                    key to decodeValue(key, valueJson)
                }
            }

            val filesJson = root.get("files")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw corrupted("No files list")
            val files = filesJson.map { it.asString }
            files.forEach { if (!isSafeRelativePath(it)) throw BackupException(BackupException.Reason.UNSAFE_PATH, it) }
            if (files.toSet().size != files.size) throw corrupted("Duplicate files")

            BackupData(createdAt, appVersion, stores, files)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPTED, e.message, e)
        }
    }

    private fun decodeValue(key: String, json: JsonElement): Any {
        if (!json.isJsonObject) throw corrupted("Value of $key is not an object")
        val obj = json.asJsonObject
        val type = obj.get("t")?.asString ?: throw corrupted("No type for $key")
        val v = obj.get("v") ?: throw corrupted("No value for $key")
        return when (type) {
            "string" -> v.asString
            "boolean" -> when (v.asString) {
                "true" -> true
                "false" -> false
                else -> throw corrupted("Bad boolean for $key")
            }
            "int" -> v.asString.toInt()
            "long" -> v.asString.toLong()
            "float" -> v.asString.toFloat()
            "double" -> v.asString.toDouble()
            "bytes" -> Base64.getDecoder().decode(v.asString)
            "string_set" -> {
                if (!v.isJsonArray) throw corrupted("Bad set for $key")
                v.asJsonArray.map { it.asString }.toSet()
            }
            else -> throw corrupted("Unknown type \"$type\" for $key")
        }
    }

    private fun corrupted(message: String) = BackupException(BackupException.Reason.CORRUPTED, message)
}
