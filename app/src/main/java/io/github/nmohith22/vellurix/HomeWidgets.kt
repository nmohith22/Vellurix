package io.github.nmohith22.vellurix

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.util.LruCache
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

private data class WidgetBook(override val uri: String, override val title: String, override val format: String, val shelf: String) : CoverBookInfo

private object WidgetLibrary {
    fun books(context: Context): List<WidgetBook> = runCatching {
        val json = JSONArray(context.getSharedPreferences("folio_library", Context.MODE_PRIVATE).getString("books", "[]"))
        (0 until json.length()).map { json.getJSONObject(it).let { book ->
            WidgetBook(book.getString("uri"), book.getString("title"), book.getString("format"), book.optString("shelf", "All books"))
        } }
    }.getOrDefault(emptyList())

    fun shelves(context: Context): List<String> = runCatching {
        val json = JSONArray(context.getSharedPreferences("folio_library", Context.MODE_PRIVATE).getString("shelves", "[]"))
        listOf("All books") + (0 until json.length()).map(json::getString)
    }.getOrDefault(listOf("All books"))
}

class WidgetConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className.orEmpty()
        val chooseShelf = provider.endsWith("ShelfWidgetProvider")
        val books = WidgetLibrary.books(this)
        val shelves = WidgetLibrary.shelves(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFE97824), background = Color(0xFFFFF8F1), surface = Color.White)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp)) {
                        Text("VELLURIX WIDGET", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(if (chooseShelf) "Choose a shelf" else "Choose a book", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(if (chooseShelf) "Tap any cover in the widget to open that book." else "Choose a cover for your home screen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if ((!chooseShelf && books.isEmpty()) || (chooseShelf && shelves.isEmpty())) {
                        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (chooseShelf) "Create a shelf first." else "Add a book to your library first.", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { startActivity(Intent(this@WidgetConfigureActivity, MainActivity::class.java)); finish() }) { Text("Open library") }
                        }
                    } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (chooseShelf) items(shelves, key = { it }) { shelf ->
                            WidgetShelfChoice(shelf, books.filter { shelf == "All books" || it.shelf == shelf }) { finishConfiguration(true, null, shelf) }
                        } else items(books, key = { it.uri }) { book ->
                            WidgetBookChoice(book) { finishConfiguration(false, book, null) }
                        }
                    }
                }
            }
        }
    }

    private fun finishConfiguration(chooseShelf: Boolean, book: WidgetBook?, shelf: String?) {
        val prefs = getSharedPreferences("folio_widgets", MODE_PRIVATE).edit()
        if (chooseShelf) prefs.putString("shelf_$widgetId", shelf)
        else book?.let {
            prefs.putString("book_uri_$widgetId", it.uri).putString("book_title_$widgetId", it.title).putString("book_format_$widgetId", it.format)
        }
        prefs.apply()
        val manager = AppWidgetManager.getInstance(this)
        if (chooseShelf) ShelfWidgetProvider.render(this, manager, widgetId)
        else BookWidgetProvider.render(this, manager, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun WidgetBookChoice(book: WidgetBook, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(width = 62.dp, height = 86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFE5D8))) {
                Image(CoverArtwork.create(book.title, book.format, 128, 184).asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                BookCover(book, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.format, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Choose", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WidgetShelfChoice(shelf: String, books: List<WidgetBook>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shelf, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${books.size} ${if (books.size == 1) "book" else "books"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Choose", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (books.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                books.take(5).forEach { book ->
                    Box(Modifier.weight(1f).aspectRatio(.7f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFE5D8))) {
                        Image(CoverArtwork.create(book.title, book.format, 96, 138).asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        BookCover(book, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

class BookWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { render(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, manager, id)

    companion object {
        fun render(context: Context, manager: AppWidgetManager, id: Int, loadMissingCovers: Boolean = true) {
            val prefs = context.getSharedPreferences("folio_widgets", Context.MODE_PRIVATE)
            val title = prefs.getString("book_title_$id", "Choose a book") ?: "Choose a book"
            val uri = prefs.getString("book_uri_$id", null)
            val format = prefs.getString("book_format_$id", "EPUB") ?: "EPUB"
            val views = RemoteViews(context.packageName, R.layout.widget_book)
            val book = uri?.let { WidgetBook(it, title, format, "") }
            views.setImageViewBitmap(R.id.book_cover, book?.let { widgetCover(context, it, 480, 700) } ?: CoverArtwork.create(title, format, 480, 700))
            views.setContentDescription(R.id.book_cover, title)
            if (uri != null) views.setOnClickPendingIntent(R.id.book_widget_root, openBook(context, id, uri, title, format))
            else views.setOnClickPendingIntent(R.id.book_widget_root, PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
            if (loadMissingCovers && book != null) loadWidgetCovers(context, listOf(book)) { render(context, AppWidgetManager.getInstance(context), id, false) }
        }
    }
}

class ShelfWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { render(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, manager, id)

    companion object {
        fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val shelf = context.getSharedPreferences("folio_widgets", Context.MODE_PRIVATE).getString("shelf_$id", "All books") ?: "All books"
            val allBooks = WidgetLibrary.books(context)
            val books = if (shelf == "All books") allBooks else allBooks.filter { it.shelf == shelf }
            val views = RemoteViews(context.packageName, R.layout.widget_shelf)
            views.setTextViewText(R.id.shelf_title, shelf)
            views.setTextViewText(R.id.shelf_count, "${books.size} books")
            val adapterIntent = Intent(context, ShelfWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("vellurix://shelf-widget/$id"))
            views.setRemoteAdapter(R.id.shelf_stack, adapterIntent)
            views.setEmptyView(R.id.shelf_stack, R.id.shelf_empty)
            val template = Intent(context, ReaderActivity::class.java).setData(Uri.parse("vellurix://shelf-open/$id"))
            val pending = PendingIntent.getActivity(context, id, template, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            views.setPendingIntentTemplate(R.id.shelf_stack, pending)
            views.setOnClickPendingIntent(R.id.shelf_title, PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.shelf_stack)
        }
    }
}

class ShelfWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ShelfWidgetViewsFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class ShelfWidgetViewsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var books = emptyList<WidgetBook>()

    override fun onCreate() = Unit
    override fun onDataSetChanged() {
        val shelf = context.getSharedPreferences("folio_widgets", Context.MODE_PRIVATE).getString("shelf_$widgetId", "All books") ?: "All books"
        val allBooks = WidgetLibrary.books(context)
        books = if (shelf == "All books") allBooks else allBooks.filter { it.shelf == shelf }
    }
    override fun onDestroy() { books = emptyList() }
    override fun getCount() = books.size
    override fun getViewAt(position: Int): RemoteViews? {
        val book = books.getOrNull(position) ?: return null
        val views = RemoteViews(context.packageName, R.layout.widget_shelf_book)
        val cover = widgetCover(context, book, 360, 520) ?: CoverArtwork.create(book.title, book.format, 360, 520)
        views.setImageViewBitmap(R.id.shelf_book_cover, cover)
        views.setContentDescription(R.id.shelf_book_cover, "Cover of ${book.title}")
        views.setOnClickFillInIntent(R.id.shelf_book_cell, Intent()
            .putExtra(ReaderActivity.EXTRA_URI, book.uri)
            .putExtra(ReaderActivity.EXTRA_TITLE, book.title)
            .putExtra(ReaderActivity.EXTRA_FORMAT, book.format))
        if (!cachedCoverFile(context, book.uri).isFile) loadCoverForWidget(context, book, widgetId)
        return views
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = books.getOrNull(position)?.uri?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
}

private val pendingShelfCoverLoads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

private fun loadCoverForWidget(context: Context, book: WidgetBook, widgetId: Int) {
    if (!pendingShelfCoverLoads.add(book.uri)) return
    Thread {
        try { runCatching { extractCover(context.applicationContext, book.uri, book.format)?.recycle() } }
        finally {
            pendingShelfCoverLoads.remove(book.uri)
            Handler(Looper.getMainLooper()).post {
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(widgetId, R.id.shelf_stack)
            }
        }
    }.start()
}

private fun widgetCover(context: Context, book: WidgetBook, targetWidth: Int = 192, targetHeight: Int = 280): Bitmap? {
    val file = cachedCoverFile(context, book.uri)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val sample = maxOf(bounds.outWidth / targetWidth, bounds.outHeight / targetHeight, 1)
    val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565; inDither = true }) ?: return null
    val scale = minOf(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height, 1f)
    val small = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    if (small !== source) source.recycle()
    return small
}

private fun loadWidgetCovers(context: Context, books: List<WidgetBook>, onLoaded: () -> Unit) {
    val missing = books.filterNot { cachedCoverFile(context, it.uri).isFile }
    if (missing.isEmpty()) return
    Thread {
        missing.forEach { runCatching { extractCover(context.applicationContext, it.uri, it.format)?.recycle() } }
        Handler(Looper.getMainLooper()).post(onLoaded)
    }.start()
}

private fun openBook(context: Context, requestCode: Int, uri: String, title: String, format: String): PendingIntent {
    val intent = Intent(context, ReaderActivity::class.java).apply {
        putExtra(ReaderActivity.EXTRA_URI, uri)
        putExtra(ReaderActivity.EXTRA_TITLE, title)
        putExtra(ReaderActivity.EXTRA_FORMAT, format)
    }
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

private object CoverArtwork {
    private val colors = intArrayOf(0xFF677767.toInt(), 0xFFA96B54.toInt(), 0xFF6E7791.toInt(), 0xFFC19B59.toInt(), 0xFF9B767F.toInt())
    private val cache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun create(title: String, format: String, width: Int = 360, height: Int = 520): Bitmap {
        val key = "$title\u0000$format\u0000$width\u0000$height"
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val index = (title.hashCode() and Int.MAX_VALUE) % colors.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors[index], darken(colors[index]), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = 0x33FFFFFF
        canvas.drawRect(width * .12f, height * .12f, width * .14f, height * .88f, paint)
        paint.color = 0xAAFFFFFF.toInt()
        paint.strokeWidth = width * .009f
        canvas.drawLine(width * .2f, height * .28f, width * .47f, height * .28f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create("serif", Typeface.NORMAL)
        paint.textSize = width * .075f
        val maxWidth = width * .68f
        val lines = mutableListOf<String>()
        var line = ""
        title.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) { lines.add(line); line = word } else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        var y = height * .39f
        lines.take(5).forEach { value -> canvas.drawText(value, width * .2f, y, paint); y += paint.textSize * 1.18f }
        paint.textSize = width * .035f
        paint.alpha = 210
        canvas.drawText(format.uppercase(), width * .2f, height * .83f, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    private fun darken(color: Int): Int = android.graphics.Color.rgb((android.graphics.Color.red(color) * .58).toInt(), (android.graphics.Color.green(color) * .58).toInt(), (android.graphics.Color.blue(color) * .58).toInt())
}

fun refreshHomeWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    manager.getAppWidgetIds(ComponentName(context, BookWidgetProvider::class.java)).forEach { BookWidgetProvider.render(context, manager, it) }
    manager.getAppWidgetIds(ComponentName(context, ShelfWidgetProvider::class.java)).forEach { ShelfWidgetProvider.render(context, manager, it) }
}

fun renameShelfWidgetSelections(context: Context, oldName: String, newName: String) {
    val manager = AppWidgetManager.getInstance(context)
    val prefs = context.getSharedPreferences("folio_widgets", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    var changed = false
    manager.getAppWidgetIds(ComponentName(context, ShelfWidgetProvider::class.java)).forEach { id ->
        if (prefs.getString("shelf_$id", "All books") == oldName) {
            editor.putString("shelf_$id", newName)
            changed = true
        }
    }
    if (changed) editor.apply()
}

