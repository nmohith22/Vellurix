package io.github.nmohith22.vellurix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF292724)
private val Muted = Color(0xFF77716B)
private val Canvas = Color(0xFFF6F3EE)
private val Accent = Color(0xFF9A5B45)
private val CoverColors = listOf(
    Color(0xFF677767) to Color(0xFF38483B),
    Color(0xFFA96B54) to Color(0xFF694439),
    Color(0xFF6E7791) to Color(0xFF41465E),
    Color(0xFFC19B59) to Color(0xFF80643B),
    Color(0xFF9B767F) to Color(0xFF614750),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(246, 243, 238)
        window.navigationBarColor = android.graphics.Color.rgb(246, 243, 238)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { FolioApp() }
    }

    override fun onResume() {
        super.onResume()
        refreshHomeWidgets(this)
    }
}

private data class BookItem(val uri: String, val title: String, val format: String, val shelf: String = "All books")
private val supportedExtensions = setOf("epub", "pdf", "txt", "html", "htm", "fb2", "rtf")
internal fun fileFormat(name: String): String? = name.substringAfterLast('.', "").lowercase().takeIf(supportedExtensions::contains)

private class LibraryStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("folio_library", android.content.Context.MODE_PRIVATE)
    fun books(): List<BookItem> = runCatching {
        val data = JSONArray(prefs.getString("books", "[]"))
        (0 until data.length()).map { i -> data.getJSONObject(i).let { BookItem(it.getString("uri"), it.getString("title"), it.getString("format"), it.optString("shelf", "All books")) } }
    }.getOrDefault(emptyList())
    fun shelves(): List<String> = runCatching { JSONArray(prefs.getString("shelves", "[]")).let { data -> (0 until data.length()).map(data::getString) } }.getOrDefault(emptyList())
    fun exclusions(): Set<String> = runCatching { JSONArray(prefs.getString("excluded", "[]")).let { data -> (0 until data.length()).map(data::getString).toSet() } }.getOrDefault(emptySet())
    fun folder(): Uri? = prefs.getString("folder", null)?.let(Uri::parse)
    fun saveBooks(books: List<BookItem>) = prefs.edit().putString("books", JSONArray().apply { books.forEach { put(JSONObject().put("uri", it.uri).put("title", it.title).put("format", it.format).put("shelf", it.shelf)) } }.toString()).apply()
    fun saveShelves(shelves: List<String>) = prefs.edit().putString("shelves", JSONArray(shelves).toString()).apply()
    fun saveExclusions(values: Set<String>) = prefs.edit().putString("excluded", JSONArray(values.toList()).toString()).apply()
    fun saveFolder(uri: Uri) = prefs.edit().putString("folder", uri.toString()).apply()
}

@Composable
private fun FolioApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LibraryStore(context.applicationContext) }
    val books = remember { mutableStateListOf<BookItem>().apply { addAll(store.books()) } }
    val shelves = remember { mutableStateListOf("All books").apply { addAll(store.shelves().filterNot { it in this }) } }
    var selectedShelf by remember { mutableStateOf("All books") }
    var folderUri by remember { mutableStateOf(store.folder()) }
    var scanning by remember { mutableStateOf(false) }
    var shelfDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    var shelfName by remember { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<BookItem?>(null) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun saveBooks() {
        store.saveBooks(books.toList())
        refreshHomeWidgets(context.applicationContext)
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "Untitled"
            val format = fileFormat(name)
            if (format == null) Toast.makeText(context, "This file type is not supported.", Toast.LENGTH_SHORT).show()
            else {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                if (books.none { it.uri == uri.toString() }) {
                    books.add(BookItem(uri.toString(), name.substringBeforeLast('.'), format.uppercase()))
                    saveBooks()
                }
            }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            folderUri = uri
            store.saveFolder(uri)
        }
    }

    fun populate() {
        val rootUri = folderUri
        if (rootUri == null) { pickFolder.launch(null); return }
        scanning = true
        scope.launch {
            val found = scanFolder(context, rootUri)
            val excluded = store.exclusions()
            val existing = books.mapTo(mutableSetOf()) { it.uri }
            val added = found.filterNot { it.first.toString() in excluded || it.first.toString() in existing }
            added.forEach { (uri, name, format) -> books.add(BookItem(uri.toString(), name.substringBeforeLast('.'), format)) }
            saveBooks()
            scanning = false
            Toast.makeText(context, "Added ${added.size} supported files.", Toast.LENGTH_SHORT).show()
        }
    }

    val visibleBooks = books.filter { book ->
        (selectedShelf == "All books" || book.shelf == selectedShelf) && book.title.contains(search, true)
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink)) {
        Column(Modifier.fillMaxSize().background(Canvas)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("FOLIO", style = MaterialTheme.typography.labelLarge, color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Text("Your library", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.SemiBold)
                }
                Row {
                    IconButton(onClick = { shelfDialog = true }) { Icon(Icons.Rounded.Add, "Create a shelf", tint = Ink) }
                    IconButton(onClick = { importFile.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, "Import a book", tint = Ink) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search your library") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp))
                FilledTonalButton(onClick = ::populate, enabled = !scanning, contentPadding = PaddingValues(14.dp)) {
                    if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.CreateNewFolder, "Populate from folder")
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 18.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (folderUri == null) "Choose a folder, then tap Populate to scan it" else "Folder scans only run when you ask", style = MaterialTheme.typography.bodySmall, color = Muted)
                if (store.exclusions().isNotEmpty()) TextButton(onClick = { hiddenDialog = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Hidden ${store.exclusions().size}", style = MaterialTheme.typography.labelSmall) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shelves.size) { index ->
                    val shelf = shelves[index]
                    AssistChip(onClick = { selectedShelf = shelf }, label = { Text(shelf) }, leadingIcon = if (shelf == selectedShelf) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
                }
                item { AssistChip(onClick = { shelfDialog = true }, label = { Text("New shelf") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) }) }
            }
            if (visibleBooks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(42.dp), tint = Accent)
                        Text(if (books.isEmpty()) "A quieter place to read" else "No books in this shelf", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(if (books.isEmpty()) "Import a book or scan a folder you choose." else "Choose another shelf or add a book.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { importFile.launch(arrayOf("*/*")) }) { Text("Import book") }
                            FilledTonalButton(onClick = ::populate) { Text("Populate library") }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 154.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(visibleBooks, key = { it.uri }) { book ->
                        BookCard(book, onClick = { selectedBook = book }, onRemove = {
                            books.remove(book)
                            store.saveExclusions(store.exclusions() + book.uri)
                            saveBooks()
                        })
                    }
                }
            }
        }
    }

    if (shelfDialog) AlertDialog(onDismissRequest = { shelfDialog = false; shelfName = "" }, title = { Text("Create a shelf") }, text = { OutlinedTextField(shelfName, { shelfName = it }, singleLine = true, label = { Text("Shelf name") }) }, confirmButton = {
        TextButton(onClick = {
            val name = shelfName.trim()
            if (name.isNotEmpty() && shelves.none { it.equals(name, true) }) { shelves.add(name); store.saveShelves(shelves.drop(1)); refreshHomeWidgets(context.applicationContext); selectedShelf = name }
            shelfName = ""; shelfDialog = false
        }) { Text("Create") }
    }, dismissButton = { TextButton(onClick = { shelfDialog = false; shelfName = "" }) { Text("Cancel") } })

    if (hiddenDialog) AlertDialog(onDismissRequest = { hiddenDialog = false }, title = { Text("Hidden from folder scans") }, text = { Text("${store.exclusions().size} removed book(s) will stay out of later scans until you clear this list.") }, confirmButton = { TextButton(onClick = { store.saveExclusions(emptySet()); hiddenDialog = false }) { Text("Clear hidden list") } }, dismissButton = { TextButton(onClick = { hiddenDialog = false }) { Text("Done") } })

    selectedBook?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBook = null },
            icon = { Icon(Icons.Rounded.AutoStories, null, tint = Accent) },
            title = { Text(book.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${book.format} · Save to shelf")
                    shelves.forEach { shelf ->
                        TextButton(onClick = {
                            val index = books.indexOfFirst { it.uri == book.uri }
                            if (index >= 0) books[index] = book.copy(shelf = shelf)
                            saveBooks()
                            selectedBook = null
                        }) { Text(if (book.shelf == shelf) "✓  $shelf" else shelf) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_URI, book.uri)
                        putExtra(ReaderActivity.EXTRA_TITLE, book.title)
                        putExtra(ReaderActivity.EXTRA_FORMAT, book.format)
                    })
                    selectedBook = null
                }) { Text("Open book") }
            },
        )
    }
}

private suspend fun scanFolder(context: android.content.Context, uri: Uri): List<Triple<Uri, String, String>> = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
    val queue = ArrayDeque<DocumentFile>()
    val found = mutableListOf<Triple<Uri, String, String>>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        runCatching { current.listFiles() }.getOrDefault(emptyArray()).forEach { child ->
            if (child.isDirectory) queue.addLast(child)
            else if (child.isFile) child.name?.let { name -> fileFormat(name)?.let { ext -> found.add(Triple(child.uri, name, ext.uppercase())) } }
        }
    }
    found
}

@Composable
private fun BookCard(book: BookItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = CoverColors[(book.title.hashCode() and Int.MAX_VALUE) % CoverColors.size]
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.76f).clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(colors.first, colors.second))).padding(18.dp)) {
                Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(width = 28.dp, height = 2.dp).background(Color.White.copy(alpha = .65f)))
                    Text(book.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(book.format, color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
                }
                IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(Color.Black.copy(alpha = .14f), CircleShape)) { Icon(Icons.Rounded.DeleteOutline, "Remove from library", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Text(book.title, Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp), style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.format, Modifier.padding(start = 4.dp, top = 3.dp, bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

