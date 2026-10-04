package io.github.nmohith22.vellurix

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal data class ReadingBackupResult(val matchedBooks: Int, val unmatchedBooks: Int, val backupBooks: Int)

internal object ReadingBackup {
    private const val FORMAT = "vellurix-backup"
    private const val VERSION = 1
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val globalStores = setOf("reader_settings", "library_settings")
    private val bookStores = setOf("reading_progress", "reading_summary", "reader_bookmarks", "reader_book_appearance")

    private data class Book(val uri: String, val title: String, val format: String, val shelf: String, val id: String, val json: JSONObject)

    fun create(context: Context): String {
        val books = libraryBooks(context)
        val ids = books.associate { it.uri to identity(context, it.uri, it.title, it.format) }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created", System.currentTimeMillis())
            .put("global", JSONObject().apply {
                globalStores.forEach { store -> put(store, encodePreferences(context.getSharedPreferences(store, Context.MODE_PRIVATE).all)) }
            })
            .put("shelves", readShelves(context))
            .put("books", JSONArray().apply {
                books.forEach { book ->
                    val data = JSONObject()
                    bookStores.forEach { store ->
                        val values = bookValues(context.getSharedPreferences(store, Context.MODE_PRIVATE).all, store, book.uri)
                        if (values.length() > 0) data.put(store, values)
                    }
                    put(JSONObject()
                        .put("id", ids.getValue(book.uri))
                        .put("title", book.title)
                        .put("format", book.format)
                        .put("shelf", book.shelf)
                        .put("data", data))
                }
            })

        val lastUri = context.getSharedPreferences("reading_summary", Context.MODE_PRIVATE).getString("last_uri", null)
        ids[lastUri]?.let { root.put("lastBookId", it) }
        return root.toString()
    }

    fun restore(context: Context, input: InputStream): ReadingBackupResult {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_BYTES) break
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES) { "The backup is too large to import." }
        val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(root.optString("format") == FORMAT && root.optInt("version") == VERSION) { "This is not a supported Vellurix backup." }
        val savedBooks = root.optJSONArray("books") ?: JSONArray()
        require(savedBooks.length() <= 20_000) { "The backup contains too many book records." }

        val currentBooks = libraryBooks(context).map { it.copy(id = identity(context, it.uri, it.title, it.format)) }
        val currentById = currentBooks.groupBy(Book::id)
        val savedRecords = (0 until savedBooks.length()).map { savedBooks.getJSONObject(it) }
        val matchedIds = savedRecords.mapNotNull { record -> record.optString("id").takeIf { it.length in 1..256 && it in currentById } }.toSet()
        val backupBookCount = savedRecords.size
        if (backupBookCount > 0 && matchedIds.isEmpty()) return ReadingBackupResult(0, backupBookCount, backupBookCount)

        val updates = linkedMapOf<String, MutableMap<String, Any?>>()
        fun update(store: String, key: String, value: Any?) { updates.getOrPut(store, ::linkedMapOf)[key] = value }

        val global = root.optJSONObject("global") ?: JSONObject()
        globalStores.forEach { store -> decodePreferences(global.optJSONObject(store)).forEach { (key, value) -> update(store, key, value) } }

        val updatedLibraryBooks = currentBooks.associateBy(Book::uri).mapValues { (_, book) -> JSONObject(book.json.toString()) }.toMutableMap()
        val mappedBackupBooks = mutableListOf<Pair<String, Book>>()
        savedRecords.forEach { record ->
            val id = record.optString("id")
            val matches = currentById[id].orEmpty()
            if (matches.isEmpty()) return@forEach
            val title = record.optString("title").takeIf(String::isNotBlank) ?: matches.first().title
            val shelf = record.optString("shelf", "All books").ifBlank { "All books" }
            matches.forEach { book ->
                updatedLibraryBooks[book.uri]?.put("title", title)?.put("shelf", shelf)
                mappedBackupBooks += id to book.copy(title = title, shelf = shelf)
                val data = record.optJSONObject("data") ?: return@forEach
                bookStores.forEach { store ->
                    val entries = data.optJSONObject(store) ?: return@forEach
                    decodePreferences(entries).forEach { (suffix, value) ->
                        val key = when (store) {
                            "reading_progress" -> if (suffix.isEmpty()) book.uri else return@forEach
                            "reader_bookmarks" -> if (suffix == "bookmarks") "bookmarks:${book.uri}" else return@forEach
                            else -> if (suffix.isNotBlank() && !suffix.contains('|')) "${book.uri}|$suffix" else return@forEach
                        }
                        update(store, key, value)
                    }
                }
            }
        }

        val lastId = root.optString("lastBookId").takeIf(String::isNotBlank)
        val lastBook = lastId?.let { id -> mappedBackupBooks.firstOrNull { it.first == id }?.second }
        if (lastBook != null) {
            update("reading_summary", "last_uri", lastBook.uri)
            update("reading_summary", "last_title", lastBook.title)
            update("reading_summary", "last_format", lastBook.format)
        }

        val changedUris = mappedBackupBooks.mapTo(mutableSetOf()) { it.second.uri }
        val booksJson = JSONArray().apply { currentBooks.forEach { book -> put(updatedLibraryBooks.getValue(book.uri)) } }
        update("folio_library", "books", booksJson.toString())

        val savedShelves = root.optJSONArray("shelves") ?: JSONArray()
        val shelfNames = LinkedHashSet<String>()
        for (i in 0 until savedShelves.length()) savedShelves.optString(i).trim().takeIf(String::isNotEmpty)?.let(shelfNames::add)
        currentBooks.filterNot { it.uri in changedUris }.map(Book::shelf).filter { it != "All books" }.forEach(shelfNames::add)
        update("folio_library", "shelves", JSONArray(shelfNames.toList()).toString())

        commitUpdates(context, updates)
        val unmatched = (savedRecords.map { it.optString("id") }.toSet() - matchedIds).size
        return ReadingBackupResult(matchedIds.size, unmatched, backupBookCount)
    }

    internal fun contentIdentity(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun metadataIdentity(title: String, format: String): String =
        "metadata:${format.trim().lowercase(Locale.ROOT)}:${title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")}"

    private fun identity(context: Context, uriString: String, title: String, format: String): String = runCatching {
        val uri = Uri.parse(uriString)
        val stream = if (uri.scheme == "file") File(uri.path ?: error("Invalid file URI")).inputStream() else context.contentResolver.openInputStream(uri)
        stream?.use(::contentIdentity) ?: metadataIdentity(title, format)
    }.getOrElse { metadataIdentity(title, format) }

    private fun libraryBooks(context: Context): List<Book> = runCatching {
        val array = JSONArray(context.getSharedPreferences("folio_library", Context.MODE_PRIVATE).getString("books", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = json.optString("uri").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = json.optString("title", "Untitled")
            val format = json.optString("format", "")
            Book(uri, title, format, json.optString("shelf", "All books"), "", json)
        }
    }.getOrDefault(emptyList())

    private fun readShelves(context: Context): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences("folio_library", Context.MODE_PRIVATE).getString("shelves", "[]"))
    }.getOrDefault(JSONArray())

    private fun bookValues(all: Map<String, *>, store: String, uri: String): JSONObject = JSONObject().apply {
        all.forEach { (key, value) ->
            val suffix = when (store) {
                "reading_progress" -> if (key == uri) "" else null
                "reader_bookmarks" -> if (key == "bookmarks:$uri") "bookmarks" else null
                else -> key.removePrefix("$uri|").takeIf { key.startsWith("$uri|") }
            }
            if (suffix != null) encodeValue(value)?.let { put(suffix, it) }
        }
    }

    private fun encodePreferences(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) -> encodeValue(value)?.let { put(key, it) } }
    }

    private fun encodeValue(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("type", "string").put("value", value)
        is Boolean -> JSONObject().put("type", "boolean").put("value", value)
        is Int -> JSONObject().put("type", "int").put("value", value)
        is Long -> JSONObject().put("type", "long").put("value", value)
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is Set<*> -> JSONObject().put("type", "strings").put("value", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> null
    }

    private fun decodePreferences(json: JSONObject?): Map<String, Any> {
        if (json == null) return emptyMap()
        require(json.length() <= 20_000) { "The backup contains too many settings." }
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.length > 4096) continue
                val entry = json.optJSONObject(key) ?: continue
                when (entry.optString("type")) {
                    "string" -> put(key, entry.getString("value"))
                    "boolean" -> put(key, entry.getBoolean("value"))
                    "int" -> put(key, entry.getInt("value"))
                    "long" -> put(key, entry.getLong("value"))
                    "float" -> put(key, entry.getDouble("value").toFloat())
                    "strings" -> {
                        val values = entry.optJSONArray("value") ?: JSONArray()
                        put(key, buildSet { for (index in 0 until values.length()) values.optString(index).takeIf { it.length <= 4096 }?.let(::add) })
                    }
                }
            }
        }
    }

    private fun commitUpdates(context: Context, changes: Map<String, Map<String, Any?>>) {
        val applied = mutableListOf<Pair<SharedPreferences, Map<String, Any?>>>()
        changes.forEach { (store, values) ->
            val preferences = context.getSharedPreferences(store, Context.MODE_PRIVATE)
            val previous = values.keys.associateWith { preferences.all[it] }
            val editor = preferences.edit()
            values.forEach { (key, value) -> putValue(editor, key, value) }
            if (!editor.commit()) {
                applied.asReversed().forEach { (prefs, old) -> restoreValues(prefs, old) }
                restoreValues(preferences, previous)
                error("Could not save the restored data. Existing data was retained where possible.")
            }
            applied += preferences to previous
        }
    }

    private fun restoreValues(preferences: SharedPreferences, values: Map<String, Any?>) {
        val editor = preferences.edit()
        values.forEach { (key, value) -> putValue(editor, key, value) }
        editor.commit()
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
