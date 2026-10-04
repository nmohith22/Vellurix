package io.github.nmohith22.vellurix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

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
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(246, 243, 238)
        window.navigationBarColor = android.graphics.Color.rgb(246, 243, 238)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { FolioApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        refreshHomeWidgets(this)
        resumeTick++
    }
}

private data class BookItem(override val uri: String, override val title: String, override val format: String, val shelf: String = "All books", val sourceUri: String? = null) : CoverBookInfo
private val supportedExtensions = setOf("epub", "pdf", "txt", "html", "htm", "fb2", "rtf")
internal fun fileFormat(name: String): String? = name.substringAfterLast('.', "").lowercase().takeIf(supportedExtensions::contains)

private class LibraryStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("folio_library", android.content.Context.MODE_PRIVATE)
    fun books(): List<BookItem> = runCatching {
        val data = JSONArray(prefs.getString("books", "[]"))
        (0 until data.length()).map { i -> data.getJSONObject(i).let { BookItem(it.getString("uri"), it.getString("title"), it.getString("format"), it.optString("shelf", "All books"), it.optString("sourceUri").takeIf(String::isNotBlank)) } }
    }.getOrDefault(emptyList())
    fun shelves(): List<String> = runCatching { JSONArray(prefs.getString("shelves", "[]")).let { data -> (0 until data.length()).map(data::getString) } }.getOrDefault(emptyList())
    fun exclusions(): Set<String> = runCatching { JSONArray(prefs.getString("excluded", "[]")).let { data -> (0 until data.length()).map(data::getString).toSet() } }.getOrDefault(emptySet())
    fun folder(): Uri? = prefs.getString("folder", null)?.let(Uri::parse)
    fun saveBooks(books: List<BookItem>) = prefs.edit().putString("books", JSONArray().apply { books.forEach { put(JSONObject().put("uri", it.uri).put("title", it.title).put("format", it.format).put("shelf", it.shelf).put("sourceUri", it.sourceUri ?: it.uri)) } }.toString()).apply()
    fun saveShelves(shelves: List<String>) = prefs.edit().putString("shelves", JSONArray(shelves).toString()).apply()
    fun saveExclusions(values: Set<String>) = prefs.edit().putString("excluded", JSONArray(values.toList()).toString()).apply()
    fun saveFolder(uri: Uri) = prefs.edit().putString("folder", uri.toString()).apply()
}

@Composable
private fun FolioApp(resumeTick: Int) {
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
    var settingsOpen by remember { mutableStateOf(false) }
    val settings = remember { context.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE) }
    val readerSettings = remember { context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE) }
    var layoutMode by rememberSaveable { mutableStateOf(settings.getString("layout", "cards") ?: "cards") }
    var cardSize by rememberSaveable { mutableFloatStateOf(settings.getFloat("card_size", 1f).coerceIn(.75f, 1.35f)) }
    var appTheme by rememberSaveable { mutableStateOf(settings.getString("app_theme", "paper") ?: "paper") }
    var readerTheme by rememberSaveable { mutableStateOf(readerSettings.getString("theme", "paper") ?: "paper") }
    var readerFont by rememberSaveable { mutableStateOf(readerSettings.getString("font_family", "") ?: "") }
    var readerFontScale by rememberSaveable { mutableFloatStateOf(readerSettings.getFloat("font_scale", 1f)) }
    var fontDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var editingBackground by remember { mutableStateOf(true) }
    var customBackground by rememberSaveable { mutableIntStateOf(readerSettings.getInt("background", android.graphics.Color.rgb(250,249,246))) }
    var customForeground by rememberSaveable { mutableIntStateOf(readerSettings.getInt("foreground", android.graphics.Color.rgb(43,42,39))) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(resumeTick) {
        if (resumeTick > 0) {
            layoutMode = settings.getString("layout", "cards") ?: "cards"
            cardSize = settings.getFloat("card_size", 1f).coerceIn(.75f, 1.35f)
            appTheme = settings.getString("app_theme", "paper") ?: "paper"
            readerTheme = readerSettings.getString("theme", "paper") ?: "paper"
            readerFont = readerSettings.getString("font_family", "") ?: ""
            readerFontScale = readerSettings.getFloat("font_scale", 1f)
            customBackground = readerSettings.getInt("background", android.graphics.Color.rgb(250,249,246))
            customForeground = readerSettings.getInt("foreground", android.graphics.Color.rgb(43,42,39))
        }
    }
    fun saveBooks() {
        store.saveBooks(books.toList())
        refreshHomeWidgets(context.applicationContext)
    }
    fun openBook(book: BookItem) {
        scope.launch {
            val source = book.sourceUri ?: book.uri
            val readableUri = withContext(Dispatchers.IO) { ensureBookAccess(context, Uri.parse(book.uri), "$source.${book.format.lowercase()}") }
            if (readableUri == null) {
                Toast.makeText(context, "Can't access this file. Import it again to restore access.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val updated = book.copy(uri = readableUri, sourceUri = source)
            val index = books.indexOfFirst { it.uri == book.uri }
            if (index >= 0 && books[index] != updated) {
                migrateBookState(context, book.uri, readableUri)
                books[index] = updated
                saveBooks()
            }
            context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_URI, readableUri)
                putExtra(ReaderActivity.EXTRA_TITLE, book.title)
                putExtra(ReaderActivity.EXTRA_FORMAT, book.format)
            })
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "Untitled"
            val format = fileFormat(name)
            if (format == null) Toast.makeText(context, "This file type is not supported.", Toast.LENGTH_SHORT).show()
            else scope.launch {
                val retained = withContext(Dispatchers.IO) { ensureBookAccess(context, uri, name) }
                if (retained == null) Toast.makeText(context, "Couldn't keep access to this file. Try saving it to local storage first.", Toast.LENGTH_LONG).show()
                else if (books.none { it.uri == retained || it.sourceUri == uri.toString() }) {
                    books.add(BookItem(retained, name.substringBeforeLast('.'), format.uppercase(), sourceUri = uri.toString()))
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
            try {
                val found = scanFolder(context, rootUri)
                val excluded = store.exclusions()
                val existing = books.flatMapTo(mutableSetOf()) { listOf(it.uri, it.sourceUri ?: it.uri) }
                val added = found.filterNot { it.first.toString() in excluded || it.first.toString() in existing }
                var count = 0
                for ((uri, name, format) in added) {
                    val retained = withContext(Dispatchers.IO) { ensureBookAccess(context, uri, name) } ?: continue
                    books.add(BookItem(retained, name.substringBeforeLast('.'), format, sourceUri = uri.toString()))
                    count++
                }
                saveBooks()
                Toast.makeText(context, "Added $count supported files.", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, "Folder access expired. Choose the folder again, then populate.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not scan the selected folder. Your library was not changed.", Toast.LENGTH_LONG).show()
            } finally {
                scanning = false
            }
        }
    }

    val visibleBooks by remember {
        derivedStateOf {
            books.filter { book ->
                (selectedShelf == "All books" || book.shelf == selectedShelf) && book.title.contains(search, true)
            }
        }
    }
    val appColors = when (appTheme) {
        "dark" -> Triple(Color(0xFF17191C), Color(0xFF222529), Color(0xFFE4E6E8))
        "sepia" -> Triple(Color(0xFFF0E5D1), Color(0xFFF8F1E4), Color(0xFF493B2D))
        else -> Triple(Canvas, Color.White, Ink)
    }
    val hostActivity = context as? android.app.Activity
    SideEffect {
        hostActivity?.window?.apply {
            statusBarColor = appColors.first.toArgb()
            navigationBarColor = appColors.first.toArgb()
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = if (appTheme == "dark") 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
    val appColorScheme = if (appTheme == "dark") darkColorScheme(primary = Color(0xFFE0A08A), background = appColors.first, surface = appColors.second, onSurface = appColors.third, onBackground = appColors.third)
        else lightColorScheme(primary = Accent, background = appColors.first, surface = appColors.second, onSurface = appColors.third, onBackground = appColors.third)
    MaterialTheme(colorScheme = appColorScheme) {
        Column(Modifier.fillMaxSize().background(appColors.first).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VELLURIX", style = MaterialTheme.typography.labelLarge, color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Text("Your library", style = MaterialTheme.typography.headlineMedium, color = appColors.third, fontWeight = FontWeight.SemiBold)
                }
                Row {
                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Settings", tint = appColors.third) }
                    IconButton(onClick = { shelfDialog = true }) { Icon(Icons.Rounded.Add, "Create a shelf", tint = appColors.third) }
                    IconButton(onClick = { importFile.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, "Import a book", tint = appColors.third) }
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
                if (layoutMode == "cards") {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (154 * cardSize).dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(visibleBooks, key = { it.uri }) { book -> BookCard(book, cardSize, appColors.third, onClick = { openBook(book) }, onLongClick = { selectedBook = book }) }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visibleBooks.size, key = { visibleBooks[it].uri }) { index ->
                            val book = visibleBooks[index]
                            BookRow(book, cardSize, appColors.third, onClick = { openBook(book) }, onLongClick = { selectedBook = book })
                        }
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

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Settings") },
            text = {
                Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Library view", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = layoutMode == "cards", onClick = { layoutMode = "cards"; settings.edit().putString("layout", layoutMode).apply() }, label = { Text("Cards") })
                        FilterChip(selected = layoutMode == "list", onClick = { layoutMode = "list"; settings.edit().putString("layout", layoutMode).apply() }, label = { Text("List") })
                    }
                    Text(if (layoutMode == "cards") "Card size" else "List size", style = MaterialTheme.typography.titleSmall)
                    Slider(value = cardSize, onValueChange = { cardSize = it }, valueRange = .75f..1.35f, onValueChangeFinished = { settings.edit().putFloat("card_size", cardSize).apply() })
                    Text("App theme", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("paper" to "Paper", "sepia" to "Sepia", "dark" to "Dark").forEach { (key, label) ->
                            FilterChip(selected = appTheme == key, onClick = { appTheme = key; settings.edit().putString("app_theme", key).apply() }, label = { Text(label) })
                        }
                    }
                    Text("Global reader theme", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("paper" to "Paper", "white" to "White", "sepia" to "Sepia", "night" to "Night").forEach { (key, label) ->
                            FilterChip(selected = readerTheme == key, onClick = {
                                readerTheme = key
                                val (background, foreground) = when (key) {
                                    "white" -> android.graphics.Color.WHITE to android.graphics.Color.rgb(35,35,35)
                                    "sepia" -> android.graphics.Color.rgb(244,232,207) to android.graphics.Color.rgb(71,55,39)
                                    "night" -> android.graphics.Color.rgb(14,16,19) to android.graphics.Color.rgb(211,215,219)
                                    else -> android.graphics.Color.rgb(250,249,246) to android.graphics.Color.rgb(43,42,39)
                                }
                                context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).edit().putString("theme", key).putInt("background", background).putInt("foreground", foreground).apply()
                                settings.edit().putString("reader_theme", key).apply()
                            }, label = { Text(label) })
                        }
                    }
                    TextButton(onClick = { colorDialog = true }) { Text("Custom text and background colors") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Reading font", style = MaterialTheme.typography.titleSmall)
                            Text(if (readerFont.isBlank()) "Publisher default" else readerFont, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        TextButton(onClick = { fontDialog = true }) { Text("Choose") }
                    }
                    Text("Text size · ${(readerFontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(value = readerFontScale, onValueChange = { readerFontScale = it }, valueRange = .8f..1.8f, onValueChangeFinished = {
                        context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).edit().putFloat("font_scale", readerFontScale).apply()
                    })
                    Text("Hold a book for shelf and remove actions.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } },
        )
    }

    if (fontDialog) {
        val fonts = listOf("" to "Publisher default", "serif" to "Serif", "sans-serif" to "Sans serif", "cursive" to "Cursive", "fantasy" to "Fantasy", "monospace" to "Monospace", "OpenDyslexic" to "OpenDyslexic", "AccessibleDfA" to "Accessible DfA", "iA Writer Duospace" to "iA Writer Duospace")
        AlertDialog(onDismissRequest = { fontDialog = false }, title = { Text("Global reading font") }, text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                fonts.forEach { (value, label) ->
                    TextButton(onClick = {
                        readerFont = value
                        context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).edit().putString("font_family", value).apply()
                        fontDialog = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (readerFont == value) "●  $label" else label, modifier = Modifier.fillMaxWidth(), color = appColors.third)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { fontDialog = false }) { Text("Done") } })
    }

    if (colorDialog) {
        Dialog(onDismissRequest = { colorDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Global reader colors", style = MaterialTheme.typography.titleLarge, color = appColors.third)
                    Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(customBackground)), contentAlignment = Alignment.Center) {
                        Text("Reading preview", color = Color(customForeground), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = editingBackground, onClick = { editingBackground = true }, label = { Text("Background") })
                        FilterChip(selected = !editingBackground, onClick = { editingBackground = false }, label = { Text("Text") })
                    }
                    AndroidView(factory = { viewContext -> HueWheel(viewContext) { picked -> if (editingBackground) customBackground = picked else customForeground = picked } }, modifier = Modifier.fillMaxWidth().height(250.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { colorDialog = false }) { Text("Cancel") }
                        Button(onClick = {
                            readerTheme = "custom"
                            readerSettings.edit().putString("theme", "custom").putInt("background", customBackground).putInt("foreground", customForeground).apply()
                            settings.edit().putString("reader_theme", "custom").apply()
                            colorDialog = false
                        }) { Text("Apply") }
                    }
                }
            }
        }
    }

    selectedBook?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBook = null },
            icon = { Icon(Icons.Rounded.AutoStories, null, tint = Accent) },
            title = { Text(book.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${book.format} · Add to shelf")
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
            confirmButton = { TextButton(onClick = { books.remove(book); store.saveExclusions(store.exclusions() + listOfNotNull(book.uri, book.sourceUri).toSet()); removeManagedCopy(context, book.uri); saveBooks(); selectedBook = null }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Remove") } },
            dismissButton = { TextButton(onClick = { selectedBook = null }) { Text("Done") } },
        )
    }
}

private suspend fun scanFolder(context: android.content.Context, uri: Uri): List<Triple<Uri, String, String>> = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, uri) ?: error("The selected folder is unavailable.")
    if (!root.canRead()) throw SecurityException("Folder access was revoked.")
    val queue = ArrayDeque<DocumentFile>()
    val found = mutableListOf<Triple<Uri, String, String>>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        current.listFiles().forEach { child ->
            if (child.isDirectory) queue.addLast(child)
            else if (child.isFile) child.name?.let { name -> fileFormat(name)?.let { ext -> found.add(Triple(child.uri, name, ext.uppercase())) } }
        }
    }
    found
}

private fun ensureBookAccess(context: android.content.Context, uri: Uri, fileName: String): String? {
    if (uri.scheme == "file") return uri.toString()
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    val persisted = runCatching {
        context.contentResolver.persistedUriPermissions.any { grant ->
            if (!grant.isReadPermission) false
            else if (grant.uri == uri) true
            else if (!DocumentsContract.isTreeUri(grant.uri)) false
            else {
                val root = DocumentsContract.getTreeDocumentId(grant.uri)
                val child = DocumentsContract.getDocumentId(uri)
                child == root || child.startsWith("$root/")
            }
        }
    }.getOrDefault(false)
    if (persisted) return uri.toString()

    val directory = File(context.filesDir, "library-books")
    if (!directory.exists() && !directory.mkdirs()) return null
    val extension = fileName.substringAfterLast('.', "book").lowercase().filter { it.isLetterOrDigit() }.ifBlank { "book" }
    val temporary = runCatching { File.createTempFile("book-", ".tmp", directory) }.getOrNull() ?: return null
    return try {
        context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } } ?: return null
        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        if (!temporary.renameTo(destination)) null else Uri.fromFile(destination).toString()
    } catch (_: Exception) {
        null
    } finally {
        temporary.delete()
    }
}

private fun removeManagedCopy(context: android.content.Context, uriString: String) {
    val uri = Uri.parse(uriString)
    if (uri.scheme != "file") return
    runCatching {
        val folder = File(context.filesDir, "library-books").canonicalFile
        val file = File(uri.path.orEmpty()).canonicalFile
        if (file.parentFile == folder) file.delete()
    }
}

private fun migrateBookState(context: android.content.Context, fromUri: String, toUri: String) {
    if (fromUri == toUri) return
    listOf("reading_progress", "reader_book_appearance").forEach { name ->
        val prefs = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        val values = prefs.all.filterKeys { it == fromUri || it.startsWith("$fromUri|") }
        val editor = prefs.edit()
        values.forEach { (key, value) ->
            val newKey = toUri + key.removePrefix(fromUri)
            when (value) {
                is String -> editor.putString(newKey, value)
                is Int -> editor.putInt(newKey, value)
                is Long -> editor.putLong(newKey, value)
                is Float -> editor.putFloat(newKey, value)
                is Boolean -> editor.putBoolean(newKey, value)
                is Set<*> -> editor.putStringSet(newKey, value.filterIsInstance<String>().toSet())
            }
        }
        editor.apply()
    }
    val widgetPrefs = context.getSharedPreferences("folio_widgets", android.content.Context.MODE_PRIVATE)
    val editor = widgetPrefs.edit()
    widgetPrefs.all.filter { (key, value) -> key.startsWith("book_uri_") && value == fromUri }.forEach { (key, _) -> editor.putString(key, toUri) }
    editor.apply()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: BookItem, size: Float, textColor: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = CoverColors[(book.title.hashCode() and Int.MAX_VALUE) % CoverColors.size]
    var coverTone by remember(book.uri) { mutableStateOf(colors.first) }
    val cardColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, coverTone, if (MaterialTheme.colorScheme.background.luminance() < .5f) .42f else .34f)
    Card(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding((10 * size).dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.76f).clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(colors.first, colors.second)))) {
                BookCover(book, Modifier.fillMaxSize(), onDominantColor = { coverTone = Color(it) })
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.82f)))).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(book.format, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
                }
            }
            Text(book.title, Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp), style = MaterialTheme.typography.titleSmall, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.format, Modifier.padding(start = 4.dp, top = 3.dp, bottom = 2.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
            FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Open book") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(book: BookItem, size: Float, textColor: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    val fallback = CoverColors[(book.title.hashCode() and Int.MAX_VALUE) % CoverColors.size]
    var coverTone by remember(book.uri) { mutableStateOf(fallback.first) }
    val rowColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, coverTone, if (MaterialTheme.colorScheme.background.luminance() < .5f) .42f else .34f)
    Card(modifier = Modifier.fillMaxWidth().height((94 * size).dp).combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = rowColor)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Box(Modifier.width((54 * size).dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(fallback.first, fallback.second)))) { BookCover(book, Modifier.fillMaxSize(), onDominantColor = { coverTone = Color(it) }) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.format, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClick) { Icon(Icons.Rounded.AutoStories, "Open ${book.title}", tint = Accent) }
        }
    }
}

