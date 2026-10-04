package io.github.nmohith22.vellurix

import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.os.Build
import android.view.WindowInsets
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Html
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.File
import java.security.MessageDigest
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
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

private const val READER_CONTAINER_ID = 0x00F01101
private const val READER_CONTENT_ID = 0x00F01102

private class PassThroughComposeContainer(context: android.content.Context, private val shouldHandle: (MotionEvent) -> Boolean) : FrameLayout(context) {
    private var handleCurrentGesture = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) handleCurrentGesture = shouldHandle(event)
        if (!handleCurrentGesture) return false
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) handleCurrentGesture = false
        return handled
    }
}

class ReaderActivity : FragmentActivity() {
    private var containerId = View.NO_ID
    private var autoRotate by mutableStateOf(true)
    private var controlsVisible by mutableStateOf(false)
    private var drawerOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var appearance by mutableStateOf<ReaderAppearance?>(null)
    private var toc by mutableStateOf(emptyList<ReaderNavItem>())
    private var bookmarks by mutableStateOf(emptyList<ReaderBookmark>())
    private var customizeThisBook by mutableStateOf(false)
    private var twoColumns by mutableStateOf(false)
    private var transition by mutableStateOf("page turn")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reading"
        autoRotate = getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE).getBoolean("auto_rotate", true)
        containerId = READER_CONTAINER_ID
        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.rgb(250, 249, 246)) }
        root.addView(FrameLayout(this).apply { id = containerId }, FrameLayout.LayoutParams(-1, -1))
        val overlay = PassThroughComposeContainer(this) { event ->
            drawerOpen || settingsOpen || (controlsVisible && (event.y <= 140 * resources.displayMetrics.density || event.y >= root.height - 180 * resources.displayMetrics.density)) || event.x <= 30 * resources.displayMetrics.density
        }
        val composeOverlay = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentAppearance = appearance
                if (currentAppearance != null) {
                    val appPalette = resolveAppTheme(getSharedPreferences("library_settings", MODE_PRIVATE).getString("app_theme", "quiet_light") ?: "quiet_light")
                    val scheme = if (appPalette.dark) darkColorScheme(primary = Color(appPalette.accent), background = Color(appPalette.background), surface = Color(appPalette.surface), onSurface = Color(appPalette.text), onBackground = Color(appPalette.text))
                    else lightColorScheme(primary = Color(appPalette.accent), background = Color(appPalette.background), surface = Color(appPalette.surface), onSurface = Color(appPalette.text), onBackground = Color(appPalette.text))
                    MaterialTheme(colorScheme = scheme) { ReaderOverlay(
                    title = title,
                    visible = controlsVisible,
                    drawerOpen = drawerOpen,
                    settingsOpen = settingsOpen,
                    appearance = currentAppearance,
                    appAccent = appPalette.accent,
                    customizeBook = customizeThisBook,
                    autoRotate = autoRotate,
                    transition = transition,
                    twoColumns = twoColumns,
                    supportsTwoColumns = intent.getStringExtra(EXTRA_FORMAT) == "EPUB",
                    toc = toc,
                    bookmarks = bookmarks,
                    onDrawerOpenChange = { drawerOpen = it },
                    onSettingsOpenChange = { settingsOpen = it },
                    onHideControls = { controlsVisible = false },
                    onClose = { finish() },
                    onRotate = {
                        autoRotate = !autoRotate
                        getSharedPreferences("reader_settings", MODE_PRIVATE).edit().putBoolean("auto_rotate", autoRotate).apply()
                        applyRotation()
                    },
                    onTurn = { (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.turnPage(it) },
                    onNavigate = { value ->
                        val host = supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment
                        if (value.startsWith("toc:")) host?.navigateToToc(value.substringAfter(':').toIntOrNull() ?: -1)
                        else parseSavedLocator(value)?.let { host?.navigateTo(it) }
                    },
                    onBookmark = ::addBookmark,
                    onRemoveBookmark = ::removeBookmark,
                    onAppearance = ::saveAppearance,
                    onTheme = { id ->
                        val colors = readerPreset(id)
                        saveAppearance((appearance ?: loadGlobalReaderAppearanceForActivity()).copy(theme = id, background = colors.first, foreground = colors.second))
                    },
                    onCustomizeBook = { enabled ->
                        customizeThisBook = enabled
                        getSharedPreferences("reader_settings", MODE_PRIVATE).edit().putBoolean("customize_this_book", enabled).apply()
                        appearance = if (enabled) loadReaderAppearance(this@ReaderActivity, uri.toString()) else loadGlobalReaderAppearanceForActivity()
                        twoColumns = getScopedBoolean("two_columns", false)
                    },
                    onResetBook = {
                        val prefs = getSharedPreferences("reader_book_appearance", MODE_PRIVATE)
                        val prefix = "${uri}|"
                        prefs.edit().also { e -> listOf("theme", "background", "foreground", "font_family", "font_scale", "two_columns").forEach { e.remove(prefix + it) } }.apply()
                        appearance = loadReaderAppearance(this@ReaderActivity, uri.toString())
                        twoColumns = getScopedBoolean("two_columns", false)
                    },
                    onTransition = { mode -> transition = mode; getSharedPreferences("reader_settings", MODE_PRIVATE).edit().putString("page_transition", mode).apply() },
                    onColumns = { enabled -> twoColumns = enabled; saveScopedBoolean("two_columns", enabled); (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.applyAppearance() },
                    ) }
                }
            }
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isClickable = false
            isFocusable = false
        }
        overlay.addView(composeOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        enterImmersiveReader()
        customizeThisBook = getSharedPreferences("reader_settings", MODE_PRIVATE).getBoolean("customize_this_book", false)
        appearance = loadReaderAppearance(this, uri.toString())
        twoColumns = getScopedBoolean("two_columns", false)
        transition = getSharedPreferences("reader_settings", MODE_PRIVATE).getString("page_transition", "page turn") ?: "page turn"
        applyRotation()
        loadBookmarks()
        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(containerId, ReaderHostFragment.create(uri.toString(), intent.getStringExtra(EXTRA_FORMAT).orEmpty())) }
        }
    }

    override fun onDestroy() {
        if (isFinishing) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onDestroy()
    }

    private fun applyRotation() { requestedOrientation = if (autoRotate) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }

    private fun loadGlobalReaderAppearanceForActivity(): ReaderAppearance {
        val prefs = getSharedPreferences("reader_settings", MODE_PRIVATE)
        return ReaderAppearance(prefs.getString("theme", "paper") ?: "paper", prefs.getInt("background", AndroidColor.rgb(250,249,246)), prefs.getInt("foreground", AndroidColor.rgb(43,42,39)), prefs.getString("font_family", "") ?: "", prefs.getFloat("font_scale", 1f), prefs.getBoolean("two_columns", false))
    }

    private fun saveAppearance(value: ReaderAppearance) {
        appearance = value
        val prefs = scopedAppearancePreferences()
        val prefix = scopedAppearanceKey("")
        prefs.edit().putString(prefix + "theme", value.theme).putInt(prefix + "background", value.background)
            .putInt(prefix + "foreground", value.foreground).putString(prefix + "font_family", value.fontFamily)
            .putFloat(prefix + "font_scale", value.fontScale).apply()
        (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.applyAppearance()
    }

    private fun getScopedBoolean(key: String, default: Boolean): Boolean = if (customizeThisBook) {
        getSharedPreferences("reader_book_appearance", MODE_PRIVATE).getBoolean(scopedAppearanceKey(key), getSharedPreferences("reader_settings", MODE_PRIVATE).getBoolean(key, default))
    } else getSharedPreferences("reader_settings", MODE_PRIVATE).getBoolean(key, default)

    private fun saveScopedBoolean(key: String, value: Boolean) = scopedAppearancePreferences().edit().putBoolean(scopedAppearanceKey(key), value).apply()

    private fun loadBookmarks() {
        val key = "bookmarks:${intent.getStringExtra(EXTRA_URI).orEmpty()}"
        val data = getSharedPreferences("reader_bookmarks", MODE_PRIVATE).getString(key, "[]")
        bookmarks = runCatching {
            val array = org.json.JSONArray(data)
            (0 until array.length()).map { array.getJSONObject(it).let { item ->
                val locator = item.getString("locator")
                val saved = parseSavedLocator(locator)
                ReaderBookmark(item.getString("title"), locator, item.optInt("page").takeIf { item.has("page") && it > 0 } ?: saved?.locations?.position)
            } }
        }.getOrDefault(emptyList())
    }

    private fun saveBookmarks(values: List<ReaderBookmark>) {
        bookmarks = values
        val key = "bookmarks:${intent.getStringExtra(EXTRA_URI).orEmpty()}"
        getSharedPreferences("reader_bookmarks", MODE_PRIVATE).edit().putString(key, org.json.JSONArray().apply { values.forEach { put(org.json.JSONObject().put("title", it.title).put("locator", it.locator).put("page", it.page)) } }.toString()).apply()
    }

    private fun addBookmark() {
        val locator = (supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment)?.currentLocator() ?: return
        val json = locator.toJSON().toString()
        val host = supportFragmentManager.findFragmentById(containerId) as? ReaderHostFragment
        if (bookmarks.none { it.locator == json }) saveBookmarks(bookmarks + ReaderBookmark(host?.chapterTitle(locator) ?: locator.title?.takeIf { it.isNotBlank() } ?: "Saved place", json, locator.locations.position))
    }

    private fun removeBookmark(bookmark: ReaderBookmark) = saveBookmarks(bookmarks.filterNot { it.locator == bookmark.locator })

    internal fun toggleReaderControls() { controlsVisible = !controlsVisible }

    internal fun onNavigatorReady(host: ReaderHostFragment, nav: VisualNavigator, links: List<org.readium.r2.shared.publication.Link>, depths: List<Int>) {
        toc = links.mapIndexed { index, link -> ReaderNavItem(link.title ?: "Section ${index + 1}", depths.getOrElse(index) { 0 }, "toc:$index") }
        nav.addInputListener(object : InputListener {
            override fun onTap(event: org.readium.r2.navigator.input.TapEvent): Boolean {
                val width = nav.publicationView.width.toFloat().coerceAtLeast(1f)
                when {
                    event.point.x < width * .30f -> host.turnPage(false)
                    event.point.x > width * .70f -> host.turnPage(true)
                    else -> controlsVisible = !controlsVisible
                }
                return true
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveReader()
    }

    private fun enterImmersiveReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun scopedAppearancePreferences() = getSharedPreferences(
        if (customizeThisBook) "reader_book_appearance" else "reader_settings",
        android.content.Context.MODE_PRIVATE,
    )

    private fun scopedAppearanceKey(key: String) =
        (if (customizeThisBook) "${intent.getStringExtra(EXTRA_URI).orEmpty()}|" else "") + key

    companion object {
        const val EXTRA_URI = "book_uri"
        const val EXTRA_TITLE = "book_title"
        const val EXTRA_FORMAT = "book_format"
    }
}

class ReaderHostFragment : Fragment() {
    private var containerId = View.NO_ID
    private var scrollSaveJob: Job? = null
    private var pendingScrollY = 0
    private var tocLinks: List<org.readium.r2.shared.publication.Link> = emptyList()
    private var tocDepths: List<Int> = emptyList()
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var flowingText: TextView? = null
    private var flowingScroll: ScrollView? = null
    private var publication: org.readium.r2.shared.publication.Publication? = null
    private var locatorSaveJob: Job? = null
    private var configuredColumns: Boolean? = null

    fun applyAppearance() {
        val appearance = loadReaderAppearance(requireContext(), arguments?.getString(ARG_URI).orEmpty())
        when (arguments?.getString(ARG_FORMAT).orEmpty()) {
            "TXT", "HTML", "HTM", "FB2", "RTF" -> {
                flowingScroll?.setBackgroundColor(appearance.background)
                flowingText?.apply {
                    setTextColor(appearance.foreground)
                    textSize = 18f * appearance.fontScale
                    typeface = appearance.fontFamily.takeIf { it.isNotBlank() }?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
                }
            }
            "EPUB" -> {
                val desiredColumns = appearance.twoColumns
                if (publication != null && configuredColumns != null && configuredColumns != desiredColumns) installPublication(publication!!, currentLocator())
                else (childFragmentManager.findFragmentByTag("publication_reader") as? EpubNavigatorFragment)?.submitPreferences(readingPreferences(appearance))
                configuredColumns = desiredColumns
            }
            "PDF" -> childFragmentManager.findFragmentByTag("publication_reader")?.view?.let { page ->
                page.pivotX = page.width / 2f
                page.pivotY = page.height / 2f
                page.scaleX = appearance.fontScale
                page.scaleY = appearance.fontScale
            }
        }
    }

    fun turnPage(forward: Boolean) {
        val format = arguments?.getString(ARG_FORMAT).orEmpty()
        val prefs = requireContext().getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getString("page_transition", "page turn") ?: "page turn"
        val navView = (childFragmentManager.findFragmentByTag("publication_reader") as? Fragment)?.view ?: view
        val scroll = (view as? FrameLayout)?.getChildAt(0) as? ScrollView
        val isText = format in setOf("TXT", "HTML", "HTM", "FB2", "RTF")
        val go: () -> Unit = {
            if (isText) {
                scroll?.let {
                    val distance = (it.height * .82f).toInt() * if (forward) 1 else -1
                    if (mode == "page turn") it.smoothScrollBy(0, distance) else it.scrollBy(0, distance)
                }
            } else if (format == "PDF") {
                (childFragmentManager.findFragmentByTag("publication_reader") as? PdfNavigatorFragment<*, *>)?.let { if (forward) it.goForward(mode == "page turn") else it.goBackward(mode == "page turn") }
            } else {
                (childFragmentManager.findFragmentByTag("publication_reader") as? EpubNavigatorFragment)?.let { if (forward) it.goForward(mode == "page turn") else it.goBackward(mode == "page turn") }
            }
        }
        when (mode) {
            "fade" -> navView?.let { target ->
                target.animate().cancel()
                target.animate().alpha(.12f).setDuration(100).withEndAction { go(); target.animate().alpha(1f).setDuration(160).start() }.start()
            } ?: go()
            "slide" -> {
                val target = navView
                if (target == null || target.width == 0) go() else {
                    target.animate().cancel()
                    val distance = target.width * .16f * if (forward) -1 else 1
                    target.animate().translationX(distance).alpha(.65f).setDuration(110).withEndAction { go(); target.translationX = -distance; target.animate().translationX(0f).alpha(1f).setDuration(160).start() }.start()
                }
            }
            else -> go()
        }
    }

    fun navigateToToc(index: Int) {
        val link = tocLinks.getOrNull(index) ?: return
        (childFragmentManager.findFragmentByTag("publication_reader") as? Navigator)?.go(link, true)
    }

    fun navigateTo(locator: org.readium.r2.shared.publication.Locator) {
        (childFragmentManager.findFragmentByTag("publication_reader") as? Navigator)?.go(locator, true)
    }

    fun currentLocator(): org.readium.r2.shared.publication.Locator? =
        (childFragmentManager.findFragmentByTag("publication_reader") as? Navigator)?.currentLocator?.value

    fun chapterTitle(locator: org.readium.r2.shared.publication.Locator): String? {
        val href = locator.href.toString().substringBefore('#').substringBefore('?')
        return tocLinks.indices.reversed().firstNotNullOfOrNull { index ->
            val item = tocLinks[index]
            val target = item.href.toString().substringBefore('#').substringBefore('?')
            item.title?.takeIf { target == href || target.endsWith(href) || href.endsWith(target) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val format = arguments?.getString(ARG_FORMAT).orEmpty()
            childFragmentManager.fragmentFactory = if (format == "PDF") PdfNavigatorFragment.createDummyFactory(PdfiumEngineProvider()) else EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, parent: android.view.ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(requireContext()).also { containerId = READER_CONTENT_ID; it.id = containerId; it.setBackgroundColor(AndroidColor.rgb(246, 243, 238)) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uri = arguments?.getString(ARG_URI)?.let(Uri::parse) ?: return showError("The selected book could not be opened.")
        val format = arguments?.getString(ARG_FORMAT).orEmpty()
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val context = requireContext()
                if (format in setOf("TXT", "HTML", "HTM", "FB2", "RTF")) {
                    val raw = withContext(Dispatchers.IO) {
                        openBookStream(context, uri).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    clearLoading()
                    showText(formatText(raw, format))
                    return@runCatching
                }
                if (format !in setOf("EPUB", "PDF")) error("$format reading is not available yet.")
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                val parser = DefaultPublicationParser(context, httpClient, assetRetriever, PdfiumDocumentFactory(context))
                suspend fun openPublication(source: Uri) = run {
                    val url = if (source.scheme == "file") File(source.path ?: error("Invalid local file location")).toUrl(isDirectory = false)
                    else source.toAbsoluteUrl() ?: error("Invalid file location")
                    val asset = assetRetriever.retrieve(url).getOrElse { error("The file could not be read.") }
                    PublicationOpener(parser).open(asset, allowUserInteraction = true).getOrElse { error("This ${format.ifBlank { "file" }} could not be opened by the reader.") }
                }
                val publication = try {
                    openPublication(uri)
                } catch (originalError: Throwable) {
                    if (originalError is CancellationException) throw originalError
                    val localFile = withContext(Dispatchers.IO) { makeReaderCopy(context, uri, format) }
                        ?: throw originalError
                    try {
                        openPublication(Uri.fromFile(localFile))
                    } catch (retryError: Throwable) {
                        if (retryError is CancellationException) throw retryError
                        retryError.addSuppressed(originalError)
                        throw retryError
                    }
                }
                val flattenedToc = publication.tableOfContents.flatMap { flattenToc(it, 0) }
                tocLinks = flattenedToc.map { it.first }
                tocDepths = flattenedToc.map { it.second }
                this@ReaderHostFragment.publication = publication
                val progress = context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
                val initialLocator = parseSavedLocator(progress.getString(uri.toString(), null))
                if (format == "EPUB") configuredColumns = loadReaderAppearance(context, uri.toString()).twoColumns
                clearLoading()
                installPublication(publication, initialLocator)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (isAdded) showError(error.message ?: "This file could not be opened.")
            }
        }
    }

    private fun installPublication(value: org.readium.r2.shared.publication.Publication, locator: org.readium.r2.shared.publication.Locator?) {
        val format = arguments?.getString(ARG_FORMAT).orEmpty()
        val appearance = loadReaderAppearance(requireContext(), arguments?.getString(ARG_URI).orEmpty())
        childFragmentManager.fragmentFactory = if (format == "PDF") PdfNavigatorFactory(value, PdfiumEngineProvider()).createFragmentFactory(initialLocator = locator)
        else EpubNavigatorFactory(value).createFragmentFactory(initialLocator = locator, initialPreferences = readingPreferences(appearance))
        childFragmentManager.commitNow { replace(containerId, if (format == "PDF") PdfNavigatorFragment::class.java else EpubNavigatorFragment::class.java, Bundle(), "publication_reader") }
        val fragment = childFragmentManager.findFragmentByTag("publication_reader")
        (fragment as? VisualNavigator)?.let { (activity as? ReaderActivity)?.onNavigatorReady(this, it, tocLinks, tocDepths) }
        locatorSaveJob?.cancel()
        val progress = requireContext().getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
        val key = arguments?.getString(ARG_URI).orEmpty()
        (fragment as? Navigator)?.let { navigator ->
            locatorSaveJob = viewLifecycleOwner.lifecycleScope.launch {
                navigator.currentLocator.collect { current -> progress.edit().putString(key, current.toJSON().toString()).apply() }
            }
        }
    }

    private fun showError(message: String) {
        if (!isAdded) return
        clearLoading()
        val errorView = TextView(requireContext()).apply { text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(AndroidColor.rgb(73, 62, 55)); setPadding(28, 28, 28, 28) }
        (this.view as? FrameLayout)?.addView(errorView, FrameLayout.LayoutParams(-1, -1))
    }

    private var loadingView: View? = null

    private fun showLoading() {
        val label = TextView(requireContext()).apply {
            text = "Opening book…"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.rgb(73, 62, 55))
        }
        loadingView = label
        (view as? FrameLayout)?.addView(label, FrameLayout.LayoutParams(-1, -1))
    }

    private fun clearLoading() {
        loadingView?.let { (view as? FrameLayout)?.removeView(it) }
        loadingView = null
    }

    private fun showText(text: String) {
        val context = requireContext()
        val key = arguments?.getString(ARG_URI).orEmpty()
        val appearance = loadReaderAppearance(context, key)
        val palette = Triple(appearance.background, appearance.foreground, AndroidColor.WHITE)
        val content = TextView(context).apply {
            this.text = text
            textSize = 18f * appearance.fontScale
            setTextColor(palette.second)
            typeface = appearance.fontFamily.takeIf { it.isNotBlank() }?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
            setLineSpacing(8f, 1f)
            setPadding(26, 24, 26, 36)
        }
        flowingText = content
        val progress = context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
        pendingScrollY = progress.getInt(key, 0)
        val scroll = ScrollView(context).apply {
            setBackgroundColor(palette.first)
            addView(content)
            setOnScrollChangeListener { _, _, y, _, _ ->
                pendingScrollY = y
                scrollSaveJob?.cancel()
                scrollSaveJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(350)
                    progress.edit().putInt(key, pendingScrollY).apply()
                }
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.x
                    touchDownY = event.y
                } else if (event.action == MotionEvent.ACTION_UP && kotlin.math.abs(event.x - touchDownX) < 18f && kotlin.math.abs(event.y - touchDownY) < 18f) {
                    val width = width.toFloat().coerceAtLeast(1f)
                    when {
                        touchDownX < width * .30f -> turnPage(false)
                        touchDownX > width * .70f -> turnPage(true)
                        else -> (activity as? ReaderActivity)?.toggleReaderControls()
                    }
                }
                false
            }
            post { scrollTo(0, pendingScrollY) }
        }
        flowingScroll = scroll
        (view as? FrameLayout)?.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onDestroyView() {
        scrollSaveJob?.cancel()
        locatorSaveJob?.cancel()
        flowingText = null
        flowingScroll = null
        if (arguments?.getString(ARG_FORMAT).orEmpty() in setOf("TXT", "HTML", "HTM", "FB2", "RTF")) arguments?.getString(ARG_URI)?.let { key ->
            requireContext().getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
                .edit().putInt(key, pendingScrollY).apply()
        }
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_FORMAT = "format"
        fun create(uri: String, format: String) = ReaderHostFragment().apply { arguments = Bundle().apply { putString(ARG_URI, uri); putString(ARG_FORMAT, format) } }
    }
}

private fun flattenToc(link: org.readium.r2.shared.publication.Link, depth: Int): List<Pair<org.readium.r2.shared.publication.Link, Int>> =
    listOf(link to depth) + link.children.flatMap { flattenToc(it, depth + 1) }

private fun openBookStream(context: android.content.Context, uri: Uri): java.io.InputStream =
    if (uri.scheme == "file") File(uri.path ?: error("Invalid local file location")).inputStream()
    else context.contentResolver.openInputStream(uri) ?: error("The selected file is no longer available.")

private fun makeReaderCopy(context: android.content.Context, uri: Uri, format: String): File? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
    val directory = File(context.cacheDir, "reader-fallback").apply { mkdirs() }
    val target = File(directory, "$digest.${format.lowercase()}")
    if (target.isFile && target.length() > 0L) return target
    val temporary = File.createTempFile("reader-", ".tmp", directory)
    try {
        openBookStream(context, uri).use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
        if (temporary.length() == 0L || !temporary.renameTo(target)) null else target
    } finally {
        temporary.delete()
    }
}.getOrNull()

internal fun loadReaderAppearance(context: android.content.Context, uri: String): ReaderAppearance {
    val global = loadGlobalReaderAppearance(context)
    val bookPrefs = context.getSharedPreferences("reader_book_appearance", android.content.Context.MODE_PRIVATE)
    val prefix = "$uri|"
    val overrides = if (listOf("theme", "background", "foreground", "font_family", "font_scale", "two_columns").any { bookPrefs.contains(prefix + it) }) {
        ReaderAppearanceOverrides(
            theme = if (bookPrefs.contains(prefix + "theme")) bookPrefs.getString(prefix + "theme", null) else null,
            background = if (bookPrefs.contains(prefix + "background")) bookPrefs.getInt(prefix + "background", global.background) else null,
            foreground = if (bookPrefs.contains(prefix + "foreground")) bookPrefs.getInt(prefix + "foreground", global.foreground) else null,
            fontFamily = if (bookPrefs.contains(prefix + "font_family")) bookPrefs.getString(prefix + "font_family", "") else null,
            fontScale = if (bookPrefs.contains(prefix + "font_scale")) bookPrefs.getFloat(prefix + "font_scale", global.fontScale) else null,
            twoColumns = if (bookPrefs.contains(prefix + "two_columns")) bookPrefs.getBoolean(prefix + "two_columns", global.twoColumns) else null,
        )
    } else null
    return resolveReaderAppearance(global, overrides)
}

    private fun loadGlobalReaderAppearance(context: android.content.Context): ReaderAppearance {
    val prefs = context.getSharedPreferences("reader_settings", android.content.Context.MODE_PRIVATE)
    return ReaderAppearance(
        theme = prefs.getString("theme", "paper") ?: "paper",
        background = prefs.getInt("background", AndroidColor.rgb(250,249,246)),
        foreground = prefs.getInt("foreground", AndroidColor.rgb(43,42,39)),
        fontFamily = prefs.getString("font_family", "") ?: "",
        fontScale = prefs.getFloat("font_scale", 1f),
        twoColumns = prefs.getBoolean("two_columns", false),
    )
}

internal fun parseSavedLocator(json: String?): org.readium.r2.shared.publication.Locator? =
    json?.let { runCatching { org.readium.r2.shared.publication.Locator.fromJSON(JSONObject(it)) }.getOrNull() }

private fun readingPreferences(appearance: ReaderAppearance) = EpubPreferences(
    backgroundColor = ReadiumColor(appearance.background),
    columnCount = if (appearance.twoColumns) org.readium.r2.navigator.preferences.ColumnCount.TWO else org.readium.r2.navigator.preferences.ColumnCount.ONE,
    textColor = ReadiumColor(appearance.foreground),
    theme = when (appearance.theme) { "sepia" -> Theme.SEPIA; "night", "forest", "slate", "custom" -> Theme.DARK; else -> Theme.LIGHT },
    fontFamily = when (appearance.fontFamily) {
        "serif" -> FontFamily.SERIF; "sans-serif" -> FontFamily.SANS_SERIF; "cursive" -> FontFamily.CURSIVE; "fantasy" -> FontFamily.FANTASY; "monospace" -> FontFamily.MONOSPACE
        "OpenDyslexic" -> FontFamily.OPEN_DYSLEXIC; "AccessibleDfA" -> FontFamily.ACCESSIBLE_DFA; "iA Writer Duospace" -> FontFamily.IA_WRITER_DUOSPACE; else -> null
    },
    fontSize = appearance.fontScale.toDouble(),
    scroll = !appearance.twoColumns,
    publisherStyles = appearance.fontFamily.isEmpty()
)

internal class HueWheel(context: android.content.Context, private val changed: (Int) -> Unit) : View(context) {
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

