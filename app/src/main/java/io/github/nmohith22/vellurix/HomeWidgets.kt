package io.github.nmohith22.vellurix

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.RemoteViews
import org.json.JSONArray

private data class WidgetBook(val uri: String, val title: String, val format: String, val shelf: String)

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

class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className.orEmpty()
        val chooseShelf = provider.endsWith("ShelfWidgetProvider")
        val items = if (chooseShelf) WidgetLibrary.shelves(this) else WidgetLibrary.books(this).map { it.title }
        val title = if (chooseShelf) "Choose a shelf" else "Choose a book"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 16) }
        root.addView(TextView(this).apply { text = title; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, 14) })
        if (items.isEmpty()) {
            root.addView(TextView(this).apply { text = "Add a book to Folio Reader first."; textSize = 16f })
            root.addView(Button(this).apply { text = "Open library"; setOnClickListener { startActivity(Intent(this@WidgetConfigureActivity, MainActivity::class.java)); finish() } })
        } else {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            items.forEachIndexed { index, item ->
                list.addView(Button(this).apply {
                    text = item
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setOnClickListener { finishConfiguration(chooseShelf, index, item) }
                }, LinearLayout.LayoutParams(-1, 52))
            }
            root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun finishConfiguration(chooseShelf: Boolean, index: Int, selected: String) {
        val prefs = getSharedPreferences("folio_widgets", MODE_PRIVATE).edit()
        if (chooseShelf) prefs.putString("shelf_$widgetId", selected)
        else WidgetLibrary.books(this).getOrNull(index)?.let { book ->
            prefs.putString("book_uri_$widgetId", book.uri).putString("book_title_$widgetId", book.title).putString("book_format_$widgetId", book.format)
        }
        prefs.apply()
        val manager = AppWidgetManager.getInstance(this)
        if (chooseShelf) ShelfWidgetProvider.render(this, manager, widgetId)
        else BookWidgetProvider.render(this, manager, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

class BookWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { render(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, manager, id)

    companion object {
        fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("folio_widgets", Context.MODE_PRIVATE)
            val title = prefs.getString("book_title_$id", "Choose a book") ?: "Choose a book"
            val uri = prefs.getString("book_uri_$id", null)
            val format = prefs.getString("book_format_$id", "EPUB") ?: "EPUB"
            val views = RemoteViews(context.packageName, R.layout.widget_book)
            views.setImageViewBitmap(R.id.book_cover, CoverArtwork.create(title, format))
            views.setContentDescription(R.id.book_cover, title)
            if (uri != null) views.setOnClickPendingIntent(R.id.book_widget_root, openBook(context, id, uri, title, format))
            else views.setOnClickPendingIntent(R.id.book_widget_root, PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
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
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220).coerceAtLeast(110)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220).coerceAtLeast(110)
            val columns = (width / 100).coerceIn(2, 4)
            val rows = (height / 106).coerceIn(2, 5)
            val visible = books.take(columns * rows)
            val views = RemoteViews(context.packageName, R.layout.widget_shelf)
            views.setTextViewText(R.id.shelf_title, shelf)
            views.setTextViewText(R.id.shelf_count, "${books.size} books")
            views.setInt(R.id.shelf_grid, "setGravity", Gravity.CENTER)
            views.removeAllViews(R.id.shelf_grid)
            visible.chunked(columns).forEach { rowBooks ->
                val row = RemoteViews(context.packageName, R.layout.widget_shelf_row)
                rowBooks.forEach { book ->
                    val cell = RemoteViews(context.packageName, R.layout.widget_shelf_book)
                    cell.setImageViewBitmap(R.id.shelf_book_cover, CoverArtwork.create(book.title, book.format, 220, 300))
                    cell.setTextViewText(R.id.shelf_book_title, book.title)
                    cell.setOnClickPendingIntent(R.id.shelf_book_cell, openBook(context, id * 100 + book.uri.hashCode(), book.uri, book.title, book.format))
                    row.addView(R.id.shelf_widget_row, cell)
                }
                views.addView(R.id.shelf_grid, row)
            }
            views.setOnClickPendingIntent(R.id.shelf_title, PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
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

    fun create(title: String, format: String, width: Int = 360, height: Int = 520): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
        return bitmap
    }

    private fun darken(color: Int): Int = android.graphics.Color.rgb((android.graphics.Color.red(color) * .58).toInt(), (android.graphics.Color.green(color) * .58).toInt(), (android.graphics.Color.blue(color) * .58).toInt())
}

fun refreshHomeWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    manager.getAppWidgetIds(ComponentName(context, BookWidgetProvider::class.java)).forEach { BookWidgetProvider.render(context, manager, it) }
    manager.getAppWidgetIds(ComponentName(context, ShelfWidgetProvider::class.java)).forEach { ShelfWidgetProvider.render(context, manager, it) }
}

