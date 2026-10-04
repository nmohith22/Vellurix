package io.github.nmohith22.vellurix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ReaderNavItem(val title: String, val depth: Int, val locator: String)
internal data class ReaderBookmark(val title: String, val locator: String, val page: Int? = null)
internal data class ReaderProgressUi(val fraction: Float = 0f, val position: Int? = null, val total: Int? = null, val sections: List<ReaderProgressSection> = emptyList())

@Composable
internal fun ReaderOverlay(
    title: String,
    visible: Boolean,
    drawerOpen: Boolean,
    settingsOpen: Boolean,
    appearance: ReaderAppearance,
    appAccent: Int,
    customizeBook: Boolean,
    autoRotate: Boolean,
    transition: String,
    twoColumns: Boolean,
    supportsTwoColumns: Boolean,
    toc: List<ReaderNavItem>,
    bookmarks: List<ReaderBookmark>,
    progress: ReaderProgressUi,
    onDrawerOpenChange: (Boolean) -> Unit,
    onSettingsOpenChange: (Boolean) -> Unit,
    onHideControls: () -> Unit,
    onClose: () -> Unit,
    onRotate: () -> Unit,
    onTurn: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onSeek: (Float) -> Unit,
    onBookmark: () -> Unit,
    onRemoveBookmark: (ReaderBookmark) -> Unit,
    onAppearance: (ReaderAppearance) -> Unit,
    onTheme: (String) -> Unit,
    onCustomizeBook: (Boolean) -> Unit,
    onResetBook: () -> Unit,
    onTransition: (String) -> Unit,
    onReadingMode: (String) -> Unit,
) {
    var bookmarkTab by remember { mutableStateOf(false) }
    var themeCarousel by remember { mutableStateOf(false) }
    val bg = Color(appearance.background)
    val fg = Color(appearance.foreground)
    val accent = Color(appAccent)
    val panel = if (bg.luminance() < .3f) Color(0xEE202124) else Color(0xEEF8F6F1)
    BackHandler(enabled = drawerOpen || settingsOpen || visible) {
        when {
            settingsOpen -> onSettingsOpenChange(false)
            drawerOpen -> onDrawerOpenChange(false)
            else -> onHideControls()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Only the narrow edge region listens for the drawer gesture; Readium keeps the rest of the page surface.
        val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
        Box(Modifier.align(Alignment.CenterStart).width(30.dp).fillMaxHeight().pointerInput(drawerOpen) {
            awaitEachGesture {
                val down = awaitPointerEvent()
                val pointer = down.changes.firstOrNull() ?: return@awaitEachGesture
                if (drawerOpen || pointer.position.x > edgeWidth) return@awaitEachGesture
                val startX = pointer.position.x
                var dragged = false
                val slop = awaitHorizontalTouchSlopOrCancellation(pointer.id) { change, amount ->
                    dragged = true
                    if (amount > 0 && startX <= edgeWidth) {
                        change.consume()
                        onDrawerOpenChange(true)
                    } else {
                        change.consume()
                    }
                }
                if (slop != null) slop.consume()
                else if (!dragged) onTurn(false)
            }
        })

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp),
                    shape = RoundedCornerShape(22.dp), color = panel, shadowElevation = 12.dp,
                ) {
                    Row(Modifier.heightIn(min = 58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close reader", tint = fg) }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text("NOW READING", color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                            Text(title, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onBookmark) { Icon(Icons.Rounded.BookmarkAdd, "Add bookmark", tint = fg) }
                        IconButton(onClick = { onSettingsOpenChange(true) }) { Icon(Icons.Rounded.Tune, "Reader settings", tint = fg) }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
                    shape = RoundedCornerShape(24.dp), color = panel, shadowElevation = 12.dp,
                ) {
                    Column {
                        ReaderProgressSeeker(progress, fg, accent, onSeek)
                        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(modifier = Modifier.size(40.dp), onClick = { onTurn(false) }) { Icon(Icons.Rounded.KeyboardArrowLeft, "Previous page", tint = fg, modifier = Modifier.size(30.dp)) }
                            AnimatedVisibility(visible = !themeCarousel) { IconButton(modifier = Modifier.size(40.dp), onClick = { themeCarousel = true }) { Icon(Icons.Rounded.ColorLens, "Theme", tint = fg) } }
                            AnimatedVisibility(visible = themeCarousel, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                                    listOf("paper" to "Paper", "white" to "White", "sepia" to "Sepia", "night" to "Night", "forest" to "Forest", "slate" to "Slate").forEach { (id, label) ->
                                        val pair = readerPreset(id)
                                        Surface(onClick = { onTheme(id); themeCarousel = false }, color = Color(pair.first), shape = CircleShape, modifier = Modifier.padding(horizontal = 3.dp)) { Text(label, Modifier.padding(horizontal = 9.dp, vertical = 8.dp), color = Color(pair.second), style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                            IconButton(modifier = Modifier.size(40.dp), onClick = onRotate) { Icon(if (autoRotate) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockPortrait, if (autoRotate) "Auto rotate on" else "Portrait locked", tint = if (autoRotate) accent else fg) }
                            IconButton(modifier = Modifier.size(40.dp), onClick = { bookmarkTab = false; onDrawerOpenChange(true) }) { Icon(Icons.Rounded.MenuBook, "Contents", tint = fg) }
                            IconButton(modifier = Modifier.size(40.dp), onClick = { bookmarkTab = true; onDrawerOpenChange(true) }) { Icon(Icons.Rounded.Bookmark, "Bookmarks", tint = fg) }
                            IconButton(modifier = Modifier.size(40.dp), onClick = { onTurn(true) }) { Icon(Icons.Rounded.KeyboardArrowRight, "Next page", tint = fg, modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(drawerOpen, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)).clickable { onDrawerOpenChange(false) }) {
                Surface(
                    Modifier.widthIn(max = 500.dp).fillMaxHeight().fillMaxWidth(.88f).align(Alignment.CenterStart).clickable(enabled = false) {},
                    color = panel, shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp), shadowElevation = 18.dp,
                ) {
                    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("YOUR BOOK", color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                                Text(title, color = fg, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDrawerOpenChange(false) }) { Icon(Icons.Rounded.Close, "Close drawer", tint = fg) }
                        }
                        TabRow(selectedTabIndex = if (bookmarkTab) 1 else 0, containerColor = Color.Transparent, contentColor = accent, modifier = Modifier.padding(top = 14.dp)) {
                            Tab(selected = !bookmarkTab, onClick = { bookmarkTab = false }, text = { Text("Contents") })
                            Tab(selected = bookmarkTab, onClick = { bookmarkTab = true }, text = { Text("Bookmarks") })
                        }
                        if (!bookmarkTab) {
                            LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
                                if (toc.isEmpty()) item { Text("This book has no table of contents.", Modifier.padding(22.dp), color = fg.copy(alpha = .72f)) }
                                items(toc) { item ->
                                    Text(item.title, Modifier.fillMaxWidth().clickable { onDrawerOpenChange(false); onNavigate(item.locator) }.padding(start = (20 + item.depth * 16).dp, end = 20.dp, top = 14.dp, bottom = 14.dp), color = fg, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
                                if (bookmarks.isEmpty()) item { Text("Save a place to find it here.", Modifier.padding(22.dp), color = fg.copy(alpha = .72f)) }
                                items(bookmarks, key = { it.locator }) { mark ->
                                    Row(Modifier.fillMaxWidth().clickable { onDrawerOpenChange(false); onNavigate(mark.locator) }.padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(mark.title, color = fg, style = MaterialTheme.typography.bodyLarge); Text("Page ${mark.page ?: "—"}", color = fg.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall) }
                                        TextButton(onClick = { onRemoveBookmark(mark) }) { Text("Remove", color = accent) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) ReaderSettingsDialog(
        appearance, customizeBook, transition, supportsTwoColumns, onDismiss = { onSettingsOpenChange(false) },
        onAppearance = onAppearance, onCustomizeBook = onCustomizeBook, onResetBook = onResetBook,
        onTransition = onTransition, onReadingMode = onReadingMode,
    )
}

@Composable
private fun ReaderSettingsDialog(current: ReaderAppearance, customizeBook: Boolean, transition: String, supportsTwoColumns: Boolean, onDismiss: () -> Unit, onAppearance: (ReaderAppearance) -> Unit, onCustomizeBook: (Boolean) -> Unit, onResetBook: () -> Unit, onTransition: (String) -> Unit, onReadingMode: (String) -> Unit) {
    var appearance by remember(current) { mutableStateOf(current) }
    var fontMenu by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf("background") }
    val fonts = listOf("" to "Publisher default", "serif" to "Serif", "sans-serif" to "Sans serif", "cursive" to "Cursive", "fantasy" to "Fantasy", "monospace" to "Monospace", "OpenDyslexic" to "OpenDyslexic", "AccessibleDfA" to "Accessible DfA", "iA Writer Duospace" to "iA Writer Duospace")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (wide) .10f else .34f)))
            Surface(modifier = if (wide) Modifier.align(Alignment.CenterEnd).width(440.dp).fillMaxHeight().padding(vertical = 12.dp, horizontal = 14.dp) else Modifier.align(Alignment.Center).fillMaxWidth(.94f).widthIn(max = 560.dp).heightIn(max = maxHeight - 32.dp), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 22.dp) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Reading experience", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close settings") } }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Customize this book", style = MaterialTheme.typography.titleSmall); Text("Otherwise global defaults apply", style = MaterialTheme.typography.bodySmall) }; Switch(checked = customizeBook, onCheckedChange = onCustomizeBook) }
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(appearance.background), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("LIVE PREVIEW", color = Color(appearance.foreground).copy(alpha = .65f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp); Text("A chapter begins here", color = Color(appearance.foreground), fontSize = (16 * appearance.fontScale).sp, fontWeight = FontWeight.SemiBold); Text("Your colors, typeface, spacing and text size update as you change them.", color = Color(appearance.foreground).copy(alpha = .82f), fontSize = (13 * appearance.fontScale).sp, maxLines = 2) } }
                        Text("Reading theme", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("paper" to "Paper", "white" to "White", "sepia" to "Sepia", "night" to "Night", "forest" to "Forest", "slate" to "Slate").forEach { (id, label) -> val colors = readerPreset(id); Surface(onClick = { appearance = appearance.copy(theme = id, background = colors.first, foreground = colors.second); onAppearance(appearance) }, shape = RoundedCornerShape(14.dp), color = Color(colors.first), border = if (appearance.theme == id) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(label, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = Color(colors.second), style = MaterialTheme.typography.labelMedium) } } }
                        Text("Text size · ${(appearance.fontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.fontScale, onValueChange = { appearance = appearance.copy(fontScale = it); onAppearance(appearance) }, valueRange = .8f..2f)
                        if (supportsTwoColumns) { Text("Page layout", style = MaterialTheme.typography.titleSmall); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("single" to "Single", "double" to "Double", "continuous" to "Continuous").forEach { (mode, label) -> val selected = when (mode) { "double" -> appearance.twoColumns && !appearance.continuous; "continuous" -> appearance.continuous; else -> !appearance.twoColumns && !appearance.continuous }; FilterChip(selected = selected, onClick = { onReadingMode(mode) }, label = { Text(label) }) } }; Text("Paginated by default. Continuous mode scrolls through a long section.", style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Typeface", style = MaterialTheme.typography.titleSmall); Text(fonts.firstOrNull { it.first == appearance.fontFamily }?.second ?: "Publisher default", style = MaterialTheme.typography.bodySmall) }; Box { TextButton(onClick = { fontMenu = true }) { Text("Change") }; DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) { fonts.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { appearance = appearance.copy(fontFamily = value); onAppearance(appearance); fontMenu = false }) } } } }
                        Text("Top margin · ${appearance.topMarginDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.topMarginDp, onValueChange = { appearance = appearance.copy(topMarginDp = it); onAppearance(appearance) }, valueRange = 0f..80f)
                        Text("Bottom margin · ${appearance.bottomMarginDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.bottomMarginDp, onValueChange = { appearance = appearance.copy(bottomMarginDp = it); onAppearance(appearance) }, valueRange = 0f..80f)
                        Text("Page transition", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("none", "fade", "slide", "page turn").forEach { mode -> FilterChip(selected = transition == mode, onClick = { onTransition(mode) }, label = { Text(mode.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall) }) } }
                        Text("Colors", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = colorTarget == "background", onClick = { colorTarget = "background" }, label = { Text("Page") }); FilterChip(selected = colorTarget == "foreground", onClick = { colorTarget = "foreground" }, label = { Text("Text") }); Box(Modifier.size(30.dp).clip(CircleShape).background(Color(if (colorTarget == "background") appearance.background else appearance.foreground))) }
                        ReaderColorWheel(Modifier.fillMaxWidth().height(180.dp)) { color -> appearance = if (colorTarget == "background") appearance.copy(theme = "custom", background = color) else appearance.copy(theme = "custom", foreground = color); onAppearance(appearance) }
                        if (customizeBook) TextButton(onClick = onResetBook) { Text("Reset this book to global defaults") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Done") } }
                }
            }
        }
    }
}

@Composable
private fun ReaderProgressSeeker(progress: ReaderProgressUi, foreground: Color, accent: Color, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var fingerProgress by remember { mutableFloatStateOf(progress.fraction.coerceIn(0f, 1f)) }
    val scope = rememberCoroutineScope()
    val expansion by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = .72f, stiffness = 520f), label = "progress seeker size")
    val position = if (dragging) fingerProgress else progress.fraction.coerceIn(0f, 1f)
    val section = sectionIndexAt(position, progress.sections)
    val page = if (dragging && progress.total != null) (position * progress.total).toInt().plus(1).coerceAtMost(progress.total) else progress.position
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${page ?: "—"} / ${progress.total ?: "—"}", color = foreground.copy(alpha = .76f), style = MaterialTheme.typography.labelSmall)
            Text("${(position * 100).toInt()}%", color = foreground.copy(alpha = .76f), style = MaterialTheme.typography.labelSmall)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp)) {
            val currentWidth = maxWidth
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).pointerInput(progress.sections.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fingerProgress = (down.position.x / size.width).coerceIn(0f, 1f)
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        dragging = true
                        change.consume()
                    }
                    if (slop == null) {
                        dragging = true
                        onSeek(fingerProgress)
                        scope.launch { delay(360); dragging = false }
                    }
                    else {
                        fingerProgress = (slop.position.x / size.width).coerceIn(0f, 1f)
                        val completed = drag(slop.id) { change ->
                            fingerProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        }
                        dragging = false
                        if (completed) onSeek(fingerProgress)
                    }
                }
            }) {
                val boundaries = if (progress.sections.isEmpty()) (0..24).map { it / 24f } else progress.sections.map { it.start.coerceIn(0f, 1f) }.distinct().sorted().let { starts ->
                    (if (starts.firstOrNull() == 0f) starts else listOf(0f) + starts).let { if (it.lastOrNull() == 1f) it else it + 1f }
                }
                val centerY = size.height * .64f
                val base = 1.6.dp.toPx() + expansion * 1.3.dp.toPx()
                val peak = 8.dp.toPx() * expansion
                val sigma = (size.width * .11f).coerceAtLeast(1f)
                repeat((boundaries.size - 1).coerceAtLeast(1)) { index ->
                    val start = boundaries.getOrNull(index) ?: 0f
                    val end = boundaries.getOrNull(index + 1) ?: 1f
                    val gap = 2.dp.toPx().coerceAtMost(size.width * (end - start) / 3f)
                    val left = start * size.width + gap / 2f
                    val right = end * size.width - gap / 2f
                    val x = (left + right) / 2f
                    val distance = (x - position * size.width) / sigma
                    val envelope = kotlin.math.exp(-.5f * distance * distance)
                    val height = base + peak * envelope
                    val color = if (x / size.width <= position) accent else foreground.copy(alpha = .24f)
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(left, centerY - height / 2f),
                        size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), height.coerceAtLeast(1f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height, height),
                    )
                }
                val thumbX = position * size.width
                drawCircle(accent, radius = (2.2.dp.toPx() + 4.dp.toPx() * expansion), center = androidx.compose.ui.geometry.Offset(thumbX, centerY))
            }
            if (dragging && section != null) {
                val title = progress.sections.getOrNull(section)?.title?.takeIf(String::isNotBlank) ?: "Section ${section + 1}"
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (currentWidth * position - 82.dp).coerceIn(0.dp, (currentWidth - 164.dp).coerceAtLeast(0.dp))),
                    shape = RoundedCornerShape(9.dp), color = foreground.copy(alpha = .92f),
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text("${section + 1}. $title", color = if (foreground.luminance() < .5f) Color.White else Color(0xFF202020), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Page ${page ?: "—"}", color = if (foreground.luminance() < .5f) Color.LightGray else Color(0xFF666666), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                    }
                }
            }
        }
    }
}
@Composable
private fun ReaderColorWheel(modifier: Modifier, onColor: (Int) -> Unit) {
    Canvas(modifier.pointerInput(Unit) {
        detectTapGestures { emitWheelColor(it.x, it.y, size.width.toFloat(), size.height.toFloat(), onColor) }
    }.pointerInput(Unit) {
        detectDragGestures { change, _ -> emitWheelColor(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), onColor); change.consume() }
    }) {
        val radius = kotlin.math.min(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), radius)
        drawCircle(Color.White.copy(alpha = .8f), radius * .14f)
    }
}

private fun emitWheelColor(x: Float, y: Float, width: Float, height: Float, onColor: (Int) -> Unit) {
    val dx = x - width / 2f; val dy = y - height / 2f
    val radius = kotlin.math.sqrt(dx * dx + dy * dy); val maxRadius = kotlin.math.min(width, height) / 2f
    if (radius <= maxRadius) {
        val hue = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
        onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, (radius / maxRadius).coerceIn(.12f, 1f), 1f)))
    }
}

internal fun readerPreset(id: String): Pair<Int, Int> = when (id) {
    "white" -> android.graphics.Color.WHITE to android.graphics.Color.rgb(35, 35, 35)
    "sepia" -> android.graphics.Color.rgb(244, 232, 207) to android.graphics.Color.rgb(71, 55, 39)
    "night" -> android.graphics.Color.rgb(14, 16, 19) to android.graphics.Color.rgb(211, 215, 219)
    "forest" -> android.graphics.Color.rgb(22, 35, 27) to android.graphics.Color.rgb(218, 229, 218)
    "slate" -> android.graphics.Color.rgb(30, 38, 49) to android.graphics.Color.rgb(220, 226, 235)
    else -> android.graphics.Color.rgb(250, 249, 246) to android.graphics.Color.rgb(43, 42, 39)
}
