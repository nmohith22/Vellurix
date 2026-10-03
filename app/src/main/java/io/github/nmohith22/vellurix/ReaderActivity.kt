package io.github.nmohith22.vellurix

import android.content.pm.ActivityInfo
import android.app.AlertDialog
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Html
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReaderActivity : FragmentActivity() {
    private var containerId = View.NO_ID
    private var autoRotate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reading"
        autoRotate = getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).getBoolean("auto_rotate", true)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(AndroidColor.rgb(246, 243, 238)) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(14, 8, 14, 8); setBackgroundColor(AndroidColor.rgb(246, 243, 238)) }
        bar.addView(Button(this).apply { text = "‹"; contentDescription = "Close reader"; setOnClickListener { finish() } })
        bar.addView(TextView(this).apply { text = title; textSize = 18f; setTextColor(AndroidColor.rgb(41, 39, 36)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(10, 0, 8, 0) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val rotateButton = Button(this)
        fun refreshRotation() {
            rotateButton.text = if (autoRotate) "Rotate: on" else "Rotate: off"
            requestedOrientation = if (autoRotate) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        rotateButton.setOnClickListener {
            autoRotate = !autoRotate
            getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("auto_rotate", autoRotate).apply()
            refreshRotation()
        }
        bar.addView(rotateButton)
        bar.addView(Button(this).apply { text = "Aa"; contentDescription = "Reading appearance"; setOnClickListener { openAppearanceSettings() } })
        root.addView(bar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        containerId = View.generateViewId()
        root.addView(FrameLayout(this).apply { id = containerId }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val pageBar = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(12, 0, 12, 6); setBackgroundColor(AndroidColor.rgb(246,243,238)) }
        pageBar.addView(Button(this).apply { text = "‹ Previous"; setOnClickListener { (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.turnPage(false) } })
        pageBar.addView(Button(this).apply { text = "Next ›"; setOnClickListener { (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.turnPage(true) } })
        root.addView(pageBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setContentView(root)
        refreshRotation()
        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(containerId, ReaderHostFragment.create(uri.toString(), intent.getStringExtra(EXTRA_FORMAT).orEmpty())) }
        }
    }

    override fun onDestroy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroy()
    }

    private fun openAppearanceSettings() {
        val prefs = getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        val themes = arrayOf("Paper", "White", "Sepia", "Night", "Forest", "Slate", "Custom color wheel")
        AlertDialog.Builder(this).setTitle("Reading theme").setItems(themes) { _, which ->
            val e = prefs.edit()
            when (which) {
                0 -> e.putString("theme", "paper").putInt("background", AndroidColor.rgb(250,249,246)).putInt("foreground", AndroidColor.rgb(43,42,39))
                1 -> e.putString("theme", "white").putInt("background", AndroidColor.WHITE).putInt("foreground", AndroidColor.rgb(35,35,35))
                2 -> e.putString("theme", "sepia").putInt("background", AndroidColor.rgb(244,232,207)).putInt("foreground", AndroidColor.rgb(71,55,39))
                3 -> e.putString("theme", "night").putInt("background", AndroidColor.rgb(14,16,19)).putInt("foreground", AndroidColor.rgb(211,215,219))
                4 -> e.putString("theme", "forest").putInt("background", AndroidColor.rgb(22,35,27)).putInt("foreground", AndroidColor.rgb(218,229,218))
                5 -> e.putString("theme", "slate").putInt("background", AndroidColor.rgb(30,38,49)).putInt("foreground", AndroidColor.rgb(220,226,235))
                6 -> { showColorWheel(); return@setItems }
            }
            e.apply(); applyAppearance()
        }.setNeutralButton("Text size") { _, _ ->
            val sizes = (9..20).map { "${it * 10}%" }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Text size").setSingleChoiceItems(sizes, ((prefs.getFloat("font_scale",1f)*10)-9).toInt().coerceIn(0,11)) { d, i -> prefs.edit().putFloat("font_scale", .9f + i*.1f).apply(); d.dismiss(); applyAppearance() }.setNegativeButton("Close", null).show()
        }.setPositiveButton("Fonts") { _, _ ->
            val format = intent.getStringExtra(EXTRA_FORMAT).orEmpty()
            if (format == "PDF") {
                AlertDialog.Builder(this).setMessage("Fonts can be changed in reflowable EPUB and text books.").setPositiveButton("OK", null).show()
                return@setPositiveButton
            }
            val labels = if (format == "EPUB") arrayOf("Publisher default", "Serif", "Sans serif", "Cursive", "Fantasy", "Monospace", "OpenDyslexic", "Accessible DfA", "iA Writer Duospace")
                else arrayOf("Default", "Serif", "Sans serif", "Cursive", "Fantasy", "Monospace")
            val values = if (format == "EPUB") arrayOf("", "serif", "sans-serif", "cursive", "fantasy", "monospace", "OpenDyslexic", "AccessibleDfA", "iA Writer Duospace")
                else arrayOf("", "serif", "sans-serif", "cursive", "fantasy", "monospace")
            AlertDialog.Builder(this).setTitle("Reading font").setItems(labels) { _, i -> prefs.edit().putString("font_family", values[i]).apply(); applyAppearance() }.show()
        }.setNegativeButton("Transitions") { _, _ ->
            val modes = arrayOf("None", "Fade", "Slide", "Page turn")
            val current = prefs.getString("page_transition", "page turn")
            AlertDialog.Builder(this).setTitle("Page turn animation").setSingleChoiceItems(modes, modes.indexOfFirst { it.equals(current, true) }.coerceAtLeast(0)) { dialog, which -> prefs.edit().putString("page_transition", modes[which].lowercase()).apply(); dialog.dismiss() }.setNegativeButton("Close", null).show()
        }.show()
    }

    private fun showColorWheel() {
        val prefs = getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        val initialColor = prefs.getInt("background", AndroidColor.rgb(64,96,128))
        val preview = TextView(this).apply { text = " Aa "; textSize = 28f; gravity = Gravity.CENTER; tag = initialColor; setBackgroundColor(initialColor); setTextColor(AndroidColor.WHITE) }
        val wheel = HueWheel(this) { color ->
            preview.tag = color; preview.setBackgroundColor(color)
            val luma = AndroidColor.red(color)*.299 + AndroidColor.green(color)*.587 + AndroidColor.blue(color)*.114
            preview.setTextColor(if (luma > 145) AndroidColor.BLACK else AndroidColor.WHITE)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8); addView(preview, LinearLayout.LayoutParams(-1,64)); addView(wheel, LinearLayout.LayoutParams(-1,260)) }
        AlertDialog.Builder(this).setTitle("Choose a reading color").setView(content).setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ ->
            val bg = (preview.tag as? Int) ?: AndroidColor.rgb(64,96,128)
            val luma = AndroidColor.red(bg)*.299 + AndroidColor.green(bg)*.587 + AndroidColor.blue(bg)*.114
            prefs.edit().putString("theme", "custom").putInt("background", bg).putInt("foreground", if (luma > 145) AndroidColor.rgb(30,30,30) else AndroidColor.rgb(238,240,242)).apply(); applyAppearance()
        }.show()
    }

    private fun applyAppearance() { (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.applyAppearance() }

    companion object {
        const val EXTRA_URI = "book_uri"
        const val EXTRA_TITLE = "book_title"
        const val EXTRA_FORMAT = "book_format"
    }
}

private class ReaderHostFragment : Fragment() {
    private var containerId = View.NO_ID

    fun applyAppearance() {
        val prefs = requireContext().getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        (childFragmentManager.findFragmentByTag("publication_reader") as? EpubNavigatorFragment)?.submitPreferences(readingPreferences(prefs))
        if (arguments?.getString(ARG_FORMAT).orEmpty() in setOf("TXT", "HTML", "HTM", "FB2", "RTF")) {
            (view as? FrameLayout)?.removeAllViews()
            onViewCreated(view ?: return, null)
        }
    }

    fun turnPage(forward: Boolean) {
        val navView = (childFragmentManager.findFragmentByTag("publication_reader") as? Fragment)?.view
        val format = arguments?.getString(ARG_FORMAT).orEmpty()
        if (format in setOf("TXT", "HTML", "HTM", "FB2", "RTF")) {
            val scroll = (view as? FrameLayout)?.getChildAt(0) as? ScrollView ?: return
            scroll.smoothScrollBy(0, (if (forward) 1 else -1) * (scroll.height * .82f).toInt())
            return
        }
        val prefs = requireContext().getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        val animated = prefs.getString("page_transition", "page turn") == "page turn"
        val go: () -> Unit = {
            if (format == "PDF") (childFragmentManager.findFragmentByTag("publication_reader") as? PdfNavigatorFragment<*, *>)?.let { if (forward) it.goForward(false) else it.goBackward(false) }
            else (childFragmentManager.findFragmentByTag("publication_reader") as? EpubNavigatorFragment)?.let { if (forward) it.goForward(animated) else it.goBackward(animated) }
        }
        when (prefs.getString("page_transition", "page turn")) {
            "fade" -> navView?.animate()?.alpha(.12f)?.setDuration(100)?.withEndAction { go(); navView.animate().alpha(1f).setDuration(160).start() }?.start() ?: go()
            "slide" -> {
                val distance = (navView?.width ?: return) * .16f * if (forward) -1 else 1
                navView.animate().translationX(distance).alpha(.65f).setDuration(110).withEndAction { go(); navView.translationX = -distance; navView.animate().translationX(0f).alpha(1f).setDuration(160).start() }.start()
            }
            else -> go()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val format = arguments?.getString(ARG_FORMAT).orEmpty()
            childFragmentManager.fragmentFactory = if (format == "PDF") PdfNavigatorFragment.createDummyFactory(PdfiumEngineProvider()) else EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) requireActivity().finish()
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, parent: android.view.ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(requireContext()).also { containerId = View.generateViewId(); it.id = containerId; it.setBackgroundColor(AndroidColor.rgb(246, 243, 238)) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = arguments?.getString(ARG_URI)?.let(Uri::parse) ?: return showError("The selected book could not be opened.")
        val format = arguments?.getString(ARG_FORMAT).orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val context = requireContext()
                if (format in setOf("TXT", "HTML", "HTM", "FB2", "RTF")) {
                    val raw = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: error("The selected file is no longer available.")
                    }
                    showText(formatText(raw, format))
                    return@runCatching
                }
                if (format !in setOf("EPUB", "PDF")) error("$format reading is not available yet.")
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                val url = uri.toAbsoluteUrl() ?: error("Invalid file location")
                val asset = assetRetriever.retrieve(url).getOrElse { error("The file could not be read.") }
                val parser = DefaultPublicationParser(context, httpClient, assetRetriever, PdfiumDocumentFactory(context))
                val publication = PublicationOpener(parser).open(asset, allowUserInteraction = true).getOrElse { error("This ${format.ifBlank { "file" }} could not be opened by the reader.") }
                val progress = context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
                val initialLocator = parseSavedLocator(progress.getString(uri.toString(), null))
                val factory = if (format == "PDF") PdfNavigatorFactory(publication, PdfiumEngineProvider()).createFragmentFactory(initialLocator = initialLocator)
                else EpubNavigatorFactory(publication).createFragmentFactory(initialLocator = initialLocator, initialPreferences = readingPreferences(context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)))
                childFragmentManager.fragmentFactory = factory
                childFragmentManager.commitNow { replace(containerId, if (format == "PDF") PdfNavigatorFragment::class.java else EpubNavigatorFragment::class.java, Bundle(), "publication_reader") }
                val navigator = childFragmentManager.findFragmentByTag("publication_reader") as? Navigator
                if (navigator != null) navigator.currentLocator.collect { locator -> progress.edit().putString(uri.toString(), locator.toJSON().toString()).apply() }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (isAdded) showError(error.message ?: "This file could not be opened.")
            }
        }
    }

    private fun showError(message: String) {
        if (!isAdded) return
        val errorView = TextView(requireContext()).apply { text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(AndroidColor.rgb(73, 62, 55)); setPadding(28, 28, 28, 28) }
        (this.view as? FrameLayout)?.addView(errorView, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showText(text: String) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        val palette = Triple(prefs.getInt("background", AndroidColor.rgb(250,249,246)), prefs.getInt("foreground", AndroidColor.rgb(43,42,39)), AndroidColor.WHITE)
        val content = TextView(context).apply {
            this.text = text
            textSize = 18f * prefs.getFloat("font_scale", 1f)
            setTextColor(palette.second)
            typeface = prefs.getString("font_family", "")?.takeIf { it.isNotBlank() }?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
            setLineSpacing(8f, 1f)
            setPadding(26, 24, 26, 36)
        }
        val key = arguments?.getString(ARG_URI).orEmpty()
        val progress = context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
        val scroll = ScrollView(context).apply {
            setBackgroundColor(palette.first)
            addView(content)
            setOnScrollChangeListener { _, _, y, _, _ -> progress.edit().putInt(key, y).apply() }
            post { scrollTo(0, progress.getInt(key, 0)) }
        }
        (view as? FrameLayout)?.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_FORMAT = "format"
        fun create(uri: String, format: String) = ReaderHostFragment().apply { arguments = Bundle().apply { putString(ARG_URI, uri); putString(ARG_FORMAT, format) } }
    }
}

internal fun parseSavedLocator(json: String?): org.readium.r2.shared.publication.Locator? =
    json?.let { runCatching { org.readium.r2.shared.publication.Locator.fromJSON(JSONObject(it)) }.getOrNull() }

private fun readingPreferences(prefs: android.content.SharedPreferences) = EpubPreferences(
    backgroundColor = ReadiumColor(prefs.getInt("background", AndroidColor.rgb(250,249,246))),
    textColor = ReadiumColor(prefs.getInt("foreground", AndroidColor.rgb(43,42,39))),
    theme = when (prefs.getString("theme", "paper")) { "sepia" -> Theme.SEPIA; "night", "forest", "slate", "custom" -> Theme.DARK; else -> Theme.LIGHT },
    fontFamily = when (prefs.getString("font_family", "")) {
        "serif" -> FontFamily.SERIF; "sans-serif" -> FontFamily.SANS_SERIF; "cursive" -> FontFamily.CURSIVE; "fantasy" -> FontFamily.FANTASY; "monospace" -> FontFamily.MONOSPACE
        "OpenDyslexic" -> FontFamily.OPEN_DYSLEXIC; "AccessibleDfA" -> FontFamily.ACCESSIBLE_DFA; "iA Writer Duospace" -> FontFamily.IA_WRITER_DUOSPACE; else -> null
    },
    fontSize = prefs.getFloat("font_scale", 1f).toDouble(),
    publisherStyles = prefs.getString("font_family", "").isNullOrEmpty()
)

private class HueWheel(context: android.content.Context, private val changed: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFFF0000.toInt(),0xFFFFFF00.toInt(),0xFF00FF00.toInt(),0xFF00FFFF.toInt(),0xFF0000FF.toInt(),0xFFFF00FF.toInt(),0xFFFF0000.toInt())
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width,height)/2f - 12f
        paint.shader = SweepGradient(width/2f,height/2f,colors,null)
        canvas.drawCircle(width/2f,height/2f,radius,paint)
        paint.shader = null; paint.color = AndroidColor.WHITE
        canvas.drawCircle(width/2f,height/2f,radius*.53f,paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val dx = event.x-width/2f; val dy = event.y-height/2f
            val hue = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(),dx.toDouble()))+360.0)%360.0).toFloat()
            val saturation = (kotlin.math.hypot(dx.toDouble(),dy.toDouble())/(minOf(width,height)/2.0)/.53).toFloat().coerceIn(.35f,.85f)
            changed(AndroidColor.HSVToColor(floatArrayOf(hue,saturation,.88f)))
            return true
        }
        return true
    }
}

internal fun formatText(raw: String, format: String): String = when (format) {
    "HTML", "HTM" -> Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
    "FB2" -> XmlPullParserFactory.newInstance().newPullParser().run {
        setInput(StringReader(raw))
        buildString {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.TEXT) append(text)
                next()
            }
        }
    }
    "RTF" -> {
        // ponytail: strips common RTF control words for reading; full nested RTF formatting needs a real parser.
        raw.replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\s+"), " ").trim()
    }
    else -> raw
}

