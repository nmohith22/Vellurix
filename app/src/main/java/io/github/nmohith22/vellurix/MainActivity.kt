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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
        setContent { FolioApp(resumeTick) { resumeTick++ } }
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
private fun FolioApp(resumeTick: Int, onBackupRestored: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LibraryStore(context.applicationContext) }
    val settings = remember { context.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE) }
    val books = remember { mutableStateListOf<BookItem>().apply { addAll(store.books()) } }
    val shelves = remember { mutableStateListOf("All books").apply { addAll(store.shelves().filterNot { it in this }) } }
    var selectedShelf by remember {
        mutableStateOf(resolveSelectedShelf(saved = settings.getString("selected_shelf", null), shelves = store.shelves()))
    }
    var folderUri by remember { mutableStateOf(store.folder()) }
    var scanning by remember { mutableStateOf(false) }
    var shelfDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    var shelfName by remember { mutableStateOf("") }
    var shelfRenameTarget by remember { mutableStateOf<String?>(null) }
    var selectedBook by remember { mutableStateOf<BookItem?>(null) }
    val selectedBooks = remember { mutableStateListOf<String>() }
    var bulkShelfDialog by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val readerSettings = remember { context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE) }
    var layoutMode by rememberSaveable { mutableStateOf(settings.getString("layout", "cards") ?: "cards") }
    var cardSize by rememberSaveable { mutableFloatStateOf(settings.getFloat("card_size", 1f).coerceIn(.75f, 1.35f)) }
    var progressDisplay by rememberSaveable { mutableStateOf(settings.getString("progress_display", "percent") ?: "percent") }
    var compactListProgress by rememberSaveable { mutableStateOf(settings.getBoolean("compact_list_progress", true)) }
    var recentBookUri by remember { mutableStateOf(context.getSharedPreferences("reading_summary", android.content.Context.MODE_PRIVATE).getString("last_uri", null)) }
    var coverOnlyCards by rememberSaveable { mutableStateOf(settings.getBoolean("cover_only_cards", false)) }
    var appTheme by rememberSaveable { mutableStateOf(settings.getString("app_theme", "paper") ?: "paper") }
    var readerTheme by rememberSaveable { mutableStateOf(readerSettings.getString("theme", "paper") ?: "paper") }
    var readerFont by rememberSaveable { mutableStateOf(readerSettings.getString("font_family", "") ?: "") }
    var readerFontScale by rememberSaveable { mutableFloatStateOf(readerSettings.getFloat("font_scale", 1f)) }
    var readerLineSpacing by rememberSaveable { mutableFloatStateOf(readerSettings.getFloat("line_spacing", 1f).coerceIn(1f, 2f)) }
    var readerTopMargin by rememberSaveable { mutableFloatStateOf(readerSettings.getFloat("top_margin_dp", 0f)) }
    var readerBottomMargin by rememberSaveable { mutableFloatStateOf(readerSettings.getFloat("bottom_margin_dp", 0f)) }
    var readerTwoColumns by rememberSaveable { mutableStateOf(readerSettings.getBoolean("two_columns", false)) }
    var readerContinuous by rememberSaveable { mutableStateOf(readerSettings.getBoolean("continuous", false)) }
    var fontDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var editingBackground by remember { mutableStateOf(true) }
    var customBackground by rememberSaveable { mutableIntStateOf(readerSettings.getInt("background", android.graphics.Color.rgb(250,249,246))) }
    var customForeground by rememberSaveable { mutableIntStateOf(readerSettings.getInt("foreground", android.graphics.Color.rgb(43,42,39))) }
    var search by remember { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { destination ->
        if (destination != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = ReadingBackup.create(context.applicationContext)
                    context.contentResolver.openOutputStream(destination, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Could not create the backup file.")
                }
                Toast.makeText(context, "Vellurix backup exported.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Could not export the backup.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use { ReadingBackup.restore(context.applicationContext, it) }
                        ?: error("Could not open the backup file.")
                }
                if (result.matchedBooks == 0 && result.backupBooks > 0) {
                    Toast.makeText(context, "No matching books found. Add the original EPUB or PDF files, then restore again.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Backup restored for ${result.matchedBooks} books.", Toast.LENGTH_LONG).show()
                    books.clear()
                    books.addAll(store.books())
                    shelves.clear()
                    shelves.add("All books")
                    shelves.addAll(store.shelves().filterNot { it in shelves })
                    onBackupRestored()
                }
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Could not restore the backup.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val compactScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380
    LaunchedEffect(resumeTick) {
        if (resumeTick > 0) {
            layoutMode = settings.getString("layout", "cards") ?: "cards"
            cardSize = settings.getFloat("card_size", 1f).coerceIn(.75f, 1.35f)
            progressDisplay = settings.getString("progress_display", "percent") ?: "percent"
            compactListProgress = settings.getBoolean("compact_list_progress", true)
            recentBookUri = context.getSharedPreferences("reading_summary", android.content.Context.MODE_PRIVATE).getString("last_uri", null)
            coverOnlyCards = settings.getBoolean("cover_only_cards", false)
            appTheme = settings.getString("app_theme", "paper") ?: "paper"
            readerTheme = readerSettings.getString("theme", "paper") ?: "paper"
            readerFont = readerSettings.getString("font_family", "") ?: ""
            readerFontScale = readerSettings.getFloat("font_scale", 1f)
            readerLineSpacing = readerSettings.getFloat("line_spacing", 1f).coerceIn(1f, 2f)
            readerTopMargin = readerSettings.getFloat("top_margin_dp", 0f)
            readerBottomMargin = readerSettings.getFloat("bottom_margin_dp", 0f)
            readerTwoColumns = readerSettings.getBoolean("two_columns", false)
            readerContinuous = readerSettings.getBoolean("continuous", false)
            customBackground = readerSettings.getInt("background", android.graphics.Color.rgb(250,249,246))
            customForeground = readerSettings.getInt("foreground", android.graphics.Color.rgb(43,42,39))
            selectedShelf = resolveSelectedShelf(settings.getString("selected_shelf", null), shelves)
            settings.edit().putString("selected_shelf", selectedShelf).apply()
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

    fun populate(manual: Boolean = true) {
        val rootUri = folderUri
        if (rootUri == null) { if (manual) pickFolder.launch(null); return }
        if (scanning) return
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
                if (manual || count > 0) Toast.makeText(context, "Added $count supported files.", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                if (manual) Toast.makeText(context, "Folder access expired. Choose the folder again, then populate.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                if (manual) Toast.makeText(context, "Could not scan the selected folder. Your library was not changed.", Toast.LENGTH_LONG).show()
            } finally {
                scanning = false
            }
        }
    }

    LaunchedEffect(resumeTick) { if (resumeTick > 0) populate(manual = false) }
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }

    fun toggleSelection(book: BookItem) {
        if (book.uri in selectedBooks) selectedBooks.remove(book.uri) else selectedBooks.add(book.uri)
    }

    val visibleBooks by remember {
        derivedStateOf {
            books.filter { book ->
                (selectedShelf == "All books" || book.shelf == selectedShelf) && book.title.contains(search, true)
            }
        }
    }
    val palette = resolveAppTheme(appTheme)
    val appColors = Triple(Color(palette.background), Color(palette.surface), Color(palette.text))
    val appAccent = Color(palette.accent)
    val hostActivity = context as? android.app.Activity
    SideEffect {
        hostActivity?.window?.apply {
            statusBarColor = appColors.first.toArgb()
            navigationBarColor = appColors.first.toArgb()
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = if (palette.dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
    val appColorScheme = if (palette.dark) darkColorScheme(primary = appAccent, background = appColors.first, surface = appColors.second, onSurface = appColors.third, onBackground = appColors.third)
        else lightColorScheme(primary = appAccent, background = appColors.first, surface = appColors.second, onSurface = appColors.third, onBackground = appColors.third)
    MaterialTheme(colorScheme = appColorScheme) {
        Column(Modifier.fillMaxSize().background(appColors.first).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VELLURIX", style = MaterialTheme.typography.labelLarge, color = appAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                    Text("Your library", style = MaterialTheme.typography.headlineMedium, color = appColors.third, fontWeight = FontWeight.SemiBold)
                }
                Row {
                    IconButton(modifier = Modifier.size(40.dp), onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Settings", tint = appColors.third) }
                    IconButton(modifier = Modifier.size(40.dp), onClick = { shelfDialog = true }) { Icon(Icons.Rounded.Add, "Create a shelf", tint = appColors.third) }
                    IconButton(modifier = Modifier.size(40.dp), onClick = { importFile.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, "Import a book", tint = appColors.third) }
                    IconButton(modifier = Modifier.size(40.dp), onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Icon(Icons.Rounded.Search, "Search your library", tint = appColors.third) }
                }
            }
            val recentBook = books.firstOrNull { it.uri == recentBookUri }
            AnimatedVisibility(
                visible = recentBook != null && !searchOpen,
                enter = fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(260)) { -it / 5 } + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically(),
            ) {
                if (recentBook != null) {
                    val recentProgress = loadBookProgress(context, recentBook.uri)
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clickable { openBook(recentBook) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(width = 44.dp, height = 60.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(CoverColors[(recentBook.title.hashCode() and Int.MAX_VALUE) % CoverColors.size].let { listOf(it.first, it.second) }))) {
                                BookCover(recentBook, Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("CONTINUE READING", style = MaterialTheme.typography.labelSmall, color = appAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(recentBook.title, style = MaterialTheme.typography.titleSmall, color = appColors.third, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(progressLabel(recentProgress, progressDisplay), style = MaterialTheme.typography.bodySmall, color = Muted)
                                LinearProgressIndicator(progress = { recentProgress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = appAccent, trackColor = appAccent.copy(alpha = .18f))
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = searchOpen, enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) + expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(220)) + fadeIn(tween(160)), exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(180)) + fadeOut(tween(120))) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).focusRequester(searchFocus), singleLine = true, placeholder = { Text("Search your library") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { IconButton(onClick = { search = ""; searchOpen = false }) { Icon(Icons.Rounded.Close, "Close search") } }, shape = RoundedCornerShape(18.dp))
            }
            AnimatedVisibility(
                visible = selectedBooks.isNotEmpty(),
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 3 } + expandVertically(),
                exit = fadeOut(tween(110)) + shrinkVertically(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedBooks.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = appColors.third)
                    TextButton(onClick = { bulkShelfDialog = true }) { Text("Move to shelf") }
                    TextButton(onClick = { selectedBooks.clear() }) { Text("Cancel") }
                }
            }
            LazyRow(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shelves.size) { index ->
                    val shelf = shelves[index]
                    val isSelected = shelf == selectedShelf
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(180),
                        label = "shelf selection color",
                    )
                    Surface(
                        modifier = Modifier.combinedClickable(
                            onClick = { selectedShelf = shelf; settings.edit().putString("selected_shelf", shelf).apply() },
                            onLongClick = {
                                if (shelf != "All books") {
                                    shelfName = shelf
                                    shelfRenameTarget = shelf
                                }
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        color = chipColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AnimatedVisibility(isSelected, enter = fadeIn(tween(120)) + expandHorizontally(tween(150)), exit = fadeOut(tween(80)) + shrinkHorizontally(tween(120))) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                            }
                            Text(shelf, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item { Surface(modifier = Modifier.clickable { shelfDialog = true }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)); Text("New shelf", style = MaterialTheme.typography.labelLarge) } } }
            }
            AnimatedContent(
                targetState = layoutMode to visibleBooks.isEmpty(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = .985f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = .99f, animationSpec = tween(160)))
                },
                label = "library content",
            ) { (displayMode, isEmpty) ->
                if (isEmpty) {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Rounded.AutoStories, null, Modifier.size(42.dp), tint = appAccent)
                            Text(if (books.isEmpty()) "A quieter place to read" else "No books in this shelf", style = MaterialTheme.typography.titleLarge, color = Ink)
                            Text(if (books.isEmpty()) "Import a book or scan a folder you choose." else "Choose another shelf or add a book.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { importFile.launch(arrayOf("*/*")) }) { Text("Import book") }
                                FilledTonalButton(onClick = ::populate) { Text("Populate library") }
                            }
                        }
                    }
                } else {
                    if (displayMode == "cards") {
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (154 * cardSize).dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(visibleBooks, key = { it.uri }) { book -> BookCard(book, cardSize, appColors.third, showTitle = !coverOnlyCards, selected = book.uri in selectedBooks, progress = loadBookProgress(context, book.uri), progressDisplay = progressDisplay, modifier = Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(110), placementSpec = spring(stiffness = Spring.StiffnessMediumLow)), onClick = { if (selectedBooks.isNotEmpty()) toggleSelection(book) else openBook(book) }, onLongClick = { selectedBook = book }) }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(visibleBooks.size, key = { visibleBooks[it].uri }) { index ->
                                val book = visibleBooks[index]
                                BookRow(book, cardSize, appColors.third, selected = book.uri in selectedBooks, progress = loadBookProgress(context, book.uri), progressDisplay = progressDisplay, compactProgress = compactScreen && compactListProgress, modifier = Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(110), placementSpec = spring(stiffness = Spring.StiffnessMediumLow)), onClick = { if (selectedBooks.isNotEmpty()) toggleSelection(book) else openBook(book) }, onLongClick = { selectedBook = book })
                            }
                        }
                    }
                }
            }
        }
    }

    if (shelfDialog || shelfRenameTarget != null) AlertDialog(onDismissRequest = { shelfDialog = false; shelfRenameTarget = null; shelfName = "" }, title = { Text(if (shelfRenameTarget != null) "Rename shelf" else "Create a shelf") }, text = { OutlinedTextField(shelfName, { shelfName = it }, singleLine = true, label = { Text("Shelf name") }) }, confirmButton = {
        TextButton(onClick = {
            val name = shelfName.trim()
            val targetShelf = shelfRenameTarget
            val conflicts = shelves.any { it.equals(name, true) && !it.equals(targetShelf, true) }
            if (name.isNotEmpty() && !conflicts) {
                if (targetShelf != null) {
                    val target = targetShelf
                    val index = shelves.indexOf(target)
                    if (index >= 0) {
                        shelves[index] = name
                        store.saveShelves(shelves.drop(1))
                        if (selectedShelf == target) { selectedShelf = name; settings.edit().putString("selected_shelf", name).apply() }
                        books.indices.forEach { i -> if (books[i].shelf == target) books[i] = books[i].copy(shelf = name) }
                        renameShelfWidgetSelections(context.applicationContext, target, name)
                        saveBooks()
                    }
                } else {
                    shelves.add(name); store.saveShelves(shelves.drop(1)); refreshHomeWidgets(context.applicationContext); selectedShelf = name; settings.edit().putString("selected_shelf", name).apply()
                }
            }
            shelfName = ""; shelfDialog = false; shelfRenameTarget = null
        }) { Text(if (shelfRenameTarget != null) "Rename" else "Create") }
    }, dismissButton = { TextButton(onClick = { shelfDialog = false; shelfRenameTarget = null; shelfName = "" }) { Text("Cancel") } })

    if (hiddenDialog) AlertDialog(onDismissRequest = { hiddenDialog = false }, title = { Text("Clear removed-book exclusions?") }, text = { Text("Previously removed books can appear again the next time you populate the selected folder.") }, confirmButton = { TextButton(onClick = { store.saveExclusions(emptySet()); hiddenDialog = false }) { Text("Clear exclusions") } }, dismissButton = { TextButton(onClick = { hiddenDialog = false }) { Text("Cancel") } })

    if (settingsOpen) {
        var settingsTab by rememberSaveable { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Settings") },
            text = {
                Column(Modifier.heightIn(max = 500.dp)) {
                    ScrollableTabRow(selectedTabIndex = settingsTab, edgePadding = 0.dp) {
                        listOf("Library", "Reader", "Data").forEachIndexed { index, title ->
                            Tab(selected = settingsTab == index, onClick = { settingsTab = index }, text = { Text(title) })
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (settingsTab) {
                            0 -> {
                                Text("Folder library", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { populate() }, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Populate from folder") }
                                TextButton(onClick = { hiddenDialog = true }) { Text("Clear removed-book exclusions") }
                                Text("Library view", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = layoutMode == "cards", onClick = { layoutMode = "cards"; settings.edit().putString("layout", layoutMode).apply() }, label = { Text("Cards") })
                                    FilterChip(selected = layoutMode == "list", onClick = { layoutMode = "list"; settings.edit().putString("layout", layoutMode).apply() }, label = { Text("List") })
                                }
                                Text(if (layoutMode == "cards") "Card size" else "List size", style = MaterialTheme.typography.titleSmall)
                                Slider(value = cardSize, onValueChange = { cardSize = it }, valueRange = .75f..1.35f, onValueChangeFinished = { settings.edit().putFloat("card_size", cardSize).apply() })
                                Text("Book progress", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = progressDisplay == "percent", onClick = { progressDisplay = "percent"; settings.edit().putString("progress_display", progressDisplay).apply() }, label = { Text("Percent") })
                                    FilterChip(selected = progressDisplay == "pages", onClick = { progressDisplay = "pages"; settings.edit().putString("progress_display", progressDisplay).apply() }, label = { Text("Pages") })
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Compact list progress", style = MaterialTheme.typography.titleSmall); Text("On small screens, show a percentage instead of a progress bar.", style = MaterialTheme.typography.bodySmall, color = Muted) }
                                    Switch(checked = compactListProgress, onCheckedChange = { compactListProgress = it; settings.edit().putBoolean("compact_list_progress", it).apply() })
                                }
                                if (layoutMode == "cards") {
                                    Text("Card labels", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = !coverOnlyCards, onClick = { coverOnlyCards = false; settings.edit().putBoolean("cover_only_cards", false).apply() }, label = { Text("Cover + title") })
                                        FilterChip(selected = coverOnlyCards, onClick = { coverOnlyCards = true; settings.edit().putBoolean("cover_only_cards", true).apply() }, label = { Text("Cover only") })
                                    }
                                }
                                Text("Hold a book for shelf and remove actions.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            1 -> {
                                Text("App theme", style = MaterialTheme.typography.titleSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                    items(appThemes.size) { index ->
                                        val theme = appThemes[index]
                                        val selectedTheme = appTheme == theme.id || (appTheme == "paper" && theme.id == "quiet_light") || (appTheme == "dark" && theme.id == "quiet_dark")
                                        Surface(onClick = { appTheme = theme.id; settings.edit().putString("app_theme", theme.id).apply() }, shape = RoundedCornerShape(14.dp), color = Color(theme.surface), border = if (selectedTheme) androidx.compose.foundation.BorderStroke(2.dp, Color(theme.accent)) else androidx.compose.foundation.BorderStroke(1.dp, Color(theme.muted).copy(alpha = .35f)), modifier = Modifier.width(148.dp)) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(theme.name, color = Color(theme.text), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(theme.background, theme.surface, theme.accent, theme.text).forEach { color -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(Color(color))) } }
                                            }
                                        }
                                    }
                                }
                                Text("Global reader theme", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("paper" to "Paper", "white" to "White", "sepia" to "Sepia", "night" to "Night").forEach { (key, label) ->
                                        FilterChip(selected = readerTheme == key, onClick = {
                                            readerTheme = key
                                            val (background, foreground) = when (key) {
                                                "white" -> android.graphics.Color.WHITE to android.graphics.Color.rgb(35,35,35)
                                                "sepia" -> android.graphics.Color.rgb(244,232,207) to android.graphics.Color.rgb(71,55,39)
                                                "night" -> android.graphics.Color.rgb(14,16,19) to android.graphics.Color.rgb(211,215,219)
                                                else -> android.graphics.Color.rgb(250,249,246) to android.graphics.Color.rgb(43,42,39)
                                            }
                                            readerSettings.edit().putString("theme", key).putInt("background", background).putInt("foreground", foreground).apply()
                                            settings.edit().putString("reader_theme", key).apply()
                                        }, label = { Text(label) })
                                    }
                                }
                                TextButton(onClick = { colorDialog = true }) { Text("Custom text and background colors") }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column { Text("Reading font", style = MaterialTheme.typography.titleSmall); Text(if (readerFont.isBlank()) "Publisher default" else readerFont, style = MaterialTheme.typography.bodySmall, color = Muted) }
                                    TextButton(onClick = { fontDialog = true }) { Text("Choose") }
                                }
                                Text("Text size · ${(readerFontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                Slider(value = readerFontScale, onValueChange = { readerFontScale = it }, valueRange = .8f..1.8f, onValueChangeFinished = { readerSettings.edit().putFloat("font_scale", readerFontScale).apply() })
                                Text("Line spacing · ${(readerLineSpacing * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                Slider(value = readerLineSpacing, onValueChange = { readerLineSpacing = it }, valueRange = 1f..2f, onValueChangeFinished = { readerSettings.edit().putFloat("line_spacing", readerLineSpacing).apply() })
                                Text("Higher line spacing overrides publisher text styles in EPUBs.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text("Default EPUB layout", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("single" to "Single", "double" to "Double", "continuous" to "Continuous").forEach { (mode, label) ->
                                        val selected = when (mode) { "double" -> readerTwoColumns && !readerContinuous; "continuous" -> readerContinuous; else -> !readerTwoColumns && !readerContinuous }
                                        FilterChip(selected = selected, onClick = { readerTwoColumns = mode == "double"; readerContinuous = mode == "continuous"; readerSettings.edit().putBoolean("two_columns", readerTwoColumns).putBoolean("continuous", readerContinuous).apply() }, label = { Text(label) })
                                    }
                                }
                                Text("Paginated by default. Continuous mode scrolls through long sections.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text("Top reading margin · ${readerTopMargin.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                                Slider(value = readerTopMargin, onValueChange = { readerTopMargin = it }, valueRange = 0f..80f, onValueChangeFinished = { readerSettings.edit().putFloat("top_margin_dp", readerTopMargin).apply() })
                                Text("Bottom reading margin · ${readerBottomMargin.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                                Slider(value = readerBottomMargin, onValueChange = { readerBottomMargin = it }, valueRange = 0f..80f, onValueChangeFinished = { readerSettings.edit().putFloat("bottom_margin_dp", readerBottomMargin).apply() })
                            }
                            else -> {
                                Text("Reading backups include positions, bookmarks, shelves, and settings. Book files are not included; import your books before restoring on another device.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { exportBackup.launch("Vellurix-backup.json") }) { Text("Export backup") }
                                    OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("Import backup") }
                                }
                            }
                        }
                    }
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

    if (bulkShelfDialog) AlertDialog(
        onDismissRequest = { bulkShelfDialog = false },
        title = { Text("Move ${selectedBooks.size} books") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { shelves.forEach { shelf -> TextButton(onClick = {
            val selected = selectedBooks.toSet()
            books.indices.forEach { index -> if (books[index].uri in selected) books[index] = books[index].copy(shelf = shelf) }
            saveBooks(); selectedBooks.clear(); bulkShelfDialog = false
        }) { Text(shelf) } } } },
        confirmButton = { TextButton(onClick = { bulkShelfDialog = false }) { Text("Cancel") } },
    )

    selectedBook?.let { book ->
        var renamedTitle by remember(book.uri) { mutableStateOf(book.title) }
        AlertDialog(
            onDismissRequest = { selectedBook = null },
            icon = { Icon(Icons.Rounded.AutoStories, null, tint = appAccent) },
            title = { Text("Book options") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = renamedTitle, onValueChange = { renamedTitle = it }, singleLine = true, label = { Text("Title in Vellurix") })
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val title = renamedTitle.trim()
                            val index = books.indexOfFirst { it.uri == book.uri }
                            if (title.isNotEmpty() && index >= 0) { books[index] = book.copy(title = title); saveBooks() }
                            selectedBook = null
                        }) { Text("Rename") }
                        TextButton(onClick = { if (book.uri !in selectedBooks) selectedBooks.add(book.uri); selectedBook = null }) { Text("Select multiple") }
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                            putExtra(ReaderActivity.EXTRA_URI, book.uri)
                            putExtra(ReaderActivity.EXTRA_TITLE, book.title)
                            putExtra(ReaderActivity.EXTRA_FORMAT, book.format)
                            putExtra(ReaderActivity.EXTRA_BOOK_SETTINGS, true)
                        })
                        selectedBook = null
                    }) { Text("Reader settings with live preview") }
                    HorizontalDivider()
                    Text("Move to shelf")
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
            confirmButton = { TextButton(onClick = { books.remove(book); store.saveExclusions(store.exclusions() + listOfNotNull(book.uri, book.sourceUri).toSet()); removeManagedCopy(context, book.uri); saveBooks(); selectedBooks.remove(book.uri); selectedBook = null }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Remove") } },
            dismissButton = { TextButton(onClick = { selectedBook = null }) { Text("Done") } },
        )
    }
}

internal fun resolveSelectedShelf(saved: String?, shelves: List<String>): String =
    saved?.takeIf { it == "All books" || it in shelves } ?: "All books"

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
    listOf("reading_progress", "reader_book_appearance", "reading_summary").forEach { name ->
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

private fun loadBookProgress(context: android.content.Context, uri: String): BookProgress {
    val summary = context.getSharedPreferences("reading_summary", android.content.Context.MODE_PRIVATE)
    var fraction = summary.getFloat("${uri}|fraction", -1f)
    var position = summary.getInt("${uri}|position", 0).takeIf { it > 0 }
    val total = summary.getInt("${uri}|total", 0).takeIf { it > 0 }
    if (fraction < 0f) {
        val saved = context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE).getString(uri, null)
        runCatching {
            val locations = JSONObject(saved).getJSONObject("locations")
            fraction = locations.optDouble("totalProgression", -1.0).toFloat()
            position = locations.optInt("position", 0).takeIf { it > 0 }
        }
    }
    return BookProgress(fraction.takeIf { it >= 0f }?.coerceIn(0f, 1f) ?: 0f, position, total)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: BookItem, size: Float, textColor: Color, showTitle: Boolean, selected: Boolean, progress: BookProgress, progressDisplay: String, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = CoverColors[(book.title.hashCode() and Int.MAX_VALUE) % CoverColors.size]
    var coverTone by remember(book.uri) { mutableStateOf(colors.first) }
    val cardColor by animateColorAsState(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, coverTone, if (MaterialTheme.colorScheme.background.luminance() < .5f) .42f else .34f), tween(260), label = "book cover tint")
    val pressScale by animateFloatAsState(if (selected) .985f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "book selection scale")
    Card(modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }.combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding((10 * size).dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.76f).clip(RoundedCornerShape(15.dp)).background(Brush.verticalGradient(listOf(colors.first, colors.second)))) {
                BookCover(book, Modifier.fillMaxSize(), onDominantColor = { coverTone = Color(it) })
                if (selected) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) { Icon(Icons.Rounded.Check, "Selected", Modifier.padding(5.dp).size(18.dp), tint = Color.White) }
            }
            if (showTitle) Text(book.title, Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp), style = MaterialTheme.typography.titleSmall, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showTitle) Text(book.format, Modifier.padding(start = 4.dp, top = 3.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 7.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(3.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                Text(progressLabel(progress, progressDisplay), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = .72f), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(book: BookItem, size: Float, textColor: Color, selected: Boolean, progress: BookProgress, progressDisplay: String, compactProgress: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val fallback = CoverColors[(book.title.hashCode() and Int.MAX_VALUE) % CoverColors.size]
    var coverTone by remember(book.uri) { mutableStateOf(fallback.first) }
    val rowColor by animateColorAsState(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, coverTone, if (MaterialTheme.colorScheme.background.luminance() < .5f) .42f else .34f), tween(260), label = "book row cover tint")
    val pressScale by animateFloatAsState(if (selected) .99f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "book row selection scale")
    Card(modifier = modifier.fillMaxWidth().height((110 * size).dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale }.combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = rowColor)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Box(Modifier.width((54 * size).dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(fallback.first, fallback.second)))) { BookCover(book, Modifier.fillMaxSize(), onDominantColor = { coverTone = Color(it) }) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.format, color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (compactProgress) {
                        Text(progressLabel(progress, "percent"), color = textColor.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                    } else {
                        LinearProgressIndicator(progress = { progress.fraction.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(3.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                        Text(progressLabel(progress, progressDisplay), color = textColor.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

