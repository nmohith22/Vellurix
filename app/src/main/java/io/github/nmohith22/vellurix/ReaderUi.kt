package io.github.nmohith22.vellurix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ReaderNavItem(val title: String, val depth: Int, val locator: String)
internal data class ReaderBookmark(val title: String, val locator: String, val page: Int? = null)
internal data class ReaderProgressUi(val fraction: Float = 0f, val position: Int? = null, val total: Int? = null, val sections: List<ReaderProgressSection> = emptyList())

internal fun toggleReaderBookmark(bookmarks: List<ReaderBookmark>, target: ReaderBookmark): List<ReaderBookmark> =
    if (bookmarks.any { it.locator == target.locator }) bookmarks.filterNot { it.locator == target.locator } else bookmarks + target

private val BookmarkRibbonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h * .78f)
    lineTo(w * .75f, h * .98f)
    lineTo(w * .5f, h * .78f)
    lineTo(w * .25f, h * .98f)
    lineTo(0f, h * .78f)
    close()
}

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
    isCurrentPageBookmarked: Boolean,
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
    LaunchedEffect(customizeBook, visible) { if (!customizeBook || !visible) themeCarousel = false }
    val bg = Color(appearance.background)
    val fg = Color(appearance.foreground)
    val bookmarkTint = bookmarkRibbonTint(appearance.background)
    val panel = androidx.compose.ui.graphics.lerp(bg, fg, if (bg.luminance() < .3f) .10f else .05f)
    val preferredAccent = Color(appAccent)
    val panelLightness = panel.luminance()
    val accentLightness = preferredAccent.luminance()
    val accentContrast = (maxOf(panelLightness, accentLightness) + .05f) / (minOf(panelLightness, accentLightness) + .05f)
    val accent = if (accentContrast >= 3f) preferredAccent else fg
    BackHandler(enabled = drawerOpen || settingsOpen || visible) {
        when {
            settingsOpen -> onSettingsOpenChange(false)
            drawerOpen -> onDrawerOpenChange(false)
            themeCarousel -> themeCarousel = false
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

        AnimatedVisibility(visible && !settingsOpen, enter = fadeIn(tween(150)), exit = fadeOut(tween(120)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    shape = RoundedCornerShape(18.dp), color = panel, shadowElevation = 4.dp,
                ) {
                    Row(Modifier.heightIn(min = 58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close reader", tint = fg) }
                        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onBookmark) { Icon(if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd, if (isCurrentPageBookmarked) "Remove bookmark" else "Add bookmark", tint = if (isCurrentPageBookmarked) bookmarkTint else fg) }
                        IconButton(onClick = { onSettingsOpenChange(true) }) { Icon(Icons.Rounded.Tune, "Reader settings", tint = fg) }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp), color = panel, shadowElevation = 4.dp,
                ) {
                    Column {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            ReaderProgressSeeker(progress, fg, panel, accent, onSeek)
                        }
                        ReaderToolbar(
                            themesOpen = themeCarousel && customizeBook, customizeBook = customizeBook,
                            theme = appearance.theme, autoRotate = autoRotate, foreground = fg, accent = accent,
                            onThemesOpen = { themeCarousel = it },
                            onTheme = { onTheme(it); themeCarousel = false },
                            onRotate = onRotate, onTurn = onTurn,
                            onDrawer = { bookmarksTab -> bookmarkTab = bookmarksTab; onDrawerOpenChange(true) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isCurrentPageBookmarked,
            enter = slideInVertically(spring(dampingRatio = .5f, stiffness = 620f)) { -it } +
                scaleIn(spring(dampingRatio = .55f, stiffness = 680f), initialScale = .88f) + fadeIn(androidx.compose.animation.core.tween(90)),
            exit = slideOutVertically(androidx.compose.animation.core.tween(190)) { -it } + fadeOut(androidx.compose.animation.core.tween(140)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 6.dp),
        ) {
            Surface(
                modifier = Modifier.size(width = 20.dp, height = 32.dp),
                shape = BookmarkRibbonShape,
                color = bookmarkTint,
                border = androidx.compose.foundation.BorderStroke(.75.dp, if (bg.luminance() > .5f) Color(0xFF4A3428) else Color(0xFFF2E7D2)),
                shadowElevation = 3.dp,
            ) { }
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
private fun ReaderToolbar(
    themesOpen: Boolean, customizeBook: Boolean, theme: String, autoRotate: Boolean,
    foreground: Color, accent: Color, onThemesOpen: (Boolean) -> Unit, onTheme: (String) -> Unit,
    onRotate: () -> Unit, onTurn: (Boolean) -> Unit, onDrawer: (Boolean) -> Unit,
) {
    val selectedFill = accent.copy(alpha = .2f)
    AnimatedContent(
        targetState = themesOpen,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
        label = "reader theme carousel",
    ) { expanded ->
        if (expanded) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onThemesOpen(false) }) { Icon(Icons.Rounded.Close, "Close themes", tint = foreground) }
                val selectedIndex = readerThemePresets.indexOfFirst { it.id == theme }.coerceAtLeast(0)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex),
                    contentPadding = PaddingValues(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(readerThemePresets, key = { it.id }) { preset ->
                        val selected = preset.id == theme
                        FilterChip(
                            selected = selected, onClick = { onTheme(preset.id) },
                            label = { Text(preset.name, maxLines = 1) },
                            leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(preset.background), labelColor = Color(preset.foreground),
                                selectedContainerColor = Color(preset.background), selectedLabelColor = Color(preset.foreground),
                                selectedLeadingIconColor = Color(preset.foreground),
                            ),
                            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Color(preset.foreground).copy(alpha = .3f)),
                        )
                    }
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val scroll = if (maxWidth < 288.dp) Modifier.horizontalScroll(rememberScrollState()) else Modifier
                Row(Modifier.fillMaxSize().then(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { onTurn(false) }) { Icon(Icons.Rounded.KeyboardArrowLeft, "Previous page", tint = foreground) }
                    IconButton(enabled = customizeBook, onClick = { onThemesOpen(true) }) {
                        val name = readerThemePresets.firstOrNull { it.id == theme }?.name ?: "Custom"
                        Icon(Icons.Rounded.ColorLens, "Reading theme: $name", tint = foreground.copy(alpha = if (customizeBook) 1f else .38f))
                    }
                    FilledTonalIconToggleButton(
                        checked = autoRotate, onCheckedChange = { onRotate() },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = Color.Transparent, contentColor = foreground,
                            checkedContainerColor = selectedFill, checkedContentColor = foreground,
                        ),
                    ) {
                        Icon(if (autoRotate) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockPortrait, "Auto rotate")
                    }
                    IconButton(onClick = { onDrawer(false) }) { Icon(Icons.Rounded.MenuBook, "Contents", tint = foreground) }
                    IconButton(onClick = { onDrawer(true) }) { Icon(Icons.Rounded.Bookmark, "Bookmarks", tint = foreground) }
                    IconButton(onClick = { onTurn(true) }) { Icon(Icons.Rounded.KeyboardArrowRight, "Next page", tint = foreground) }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsDialog(current: ReaderAppearance, customizeBook: Boolean, transition: String, supportsTwoColumns: Boolean, onDismiss: () -> Unit, onAppearance: (ReaderAppearance) -> Unit, onCustomizeBook: (Boolean) -> Unit, onResetBook: () -> Unit, onTransition: (String) -> Unit, onReadingMode: (String) -> Unit) {
    var appearance by remember(current) { mutableStateOf(current) }
    var fontMenu by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf("background") }
    var settingsTab by remember { mutableIntStateOf(0) }
    val fonts = listOf("" to "Publisher default", "serif" to "Serif", "sans-serif" to "Sans serif", "cursive" to "Cursive", "fantasy" to "Fantasy", "monospace" to "Monospace", "OpenDyslexic" to "OpenDyslexic", "AccessibleDfA" to "Accessible DfA", "iA Writer Duospace" to "iA Writer Duospace")
    val panel: @Composable (Boolean) -> Unit = { wide ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Reader settings", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close settings") } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Customize this book", style = MaterialTheme.typography.titleSmall); Text(if (customizeBook) "Book-only overrides are enabled" else "Using global defaults", style = MaterialTheme.typography.bodySmall) }; Switch(checked = customizeBook, onCheckedChange = onCustomizeBook) }
            ScrollableTabRow(selectedTabIndex = settingsTab, edgePadding = 0.dp) {
                listOf("Appearance", "Layout", "Controls").forEachIndexed { index, title -> Tab(selected = settingsTab == index, onClick = { settingsTab = index }, text = { Text(title) }) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (settingsTab) {
                    0 -> {
                        if (!wide) ReaderAppearancePreview(appearance)
                        Text("Reading theme", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { readerThemePresets.forEach { preset -> Surface(onClick = { appearance = appearance.copy(theme = preset.id, background = preset.background, foreground = preset.foreground); onAppearance(appearance) }, enabled = customizeBook, shape = RoundedCornerShape(14.dp), color = Color(preset.background), border = if (appearance.theme == preset.id) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.alpha(if (customizeBook) 1f else .48f)) { Text(preset.name, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = Color(preset.foreground), style = MaterialTheme.typography.labelMedium) } } }
                        Text("Text size · ${(appearance.fontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.fontScale, onValueChange = { appearance = appearance.copy(fontScale = it); onAppearance(appearance) }, valueRange = .8f..2f, enabled = customizeBook)
                        Text("Line spacing · ${(appearance.lineSpacing * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.lineSpacing, onValueChange = { appearance = appearance.copy(lineSpacing = it); onAppearance(appearance) }, valueRange = 1f..2f, enabled = customizeBook)
                        Text("Higher line spacing overrides publisher text styles in EPUBs.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Typeface", style = MaterialTheme.typography.titleSmall); Text(fonts.firstOrNull { it.first == appearance.fontFamily }?.second ?: "Publisher default", style = MaterialTheme.typography.bodySmall) }; Box { TextButton(onClick = { fontMenu = true }, enabled = customizeBook) { Text("Change") }; DropdownMenu(expanded = fontMenu && customizeBook, onDismissRequest = { fontMenu = false }) { fonts.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, enabled = customizeBook, onClick = { appearance = appearance.copy(fontFamily = value); onAppearance(appearance); fontMenu = false }) } } } }
                        Text("Colors", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = colorTarget == "background", onClick = { colorTarget = "background" }, enabled = customizeBook, label = { Text("Page") }); FilterChip(selected = colorTarget == "foreground", onClick = { colorTarget = "foreground" }, enabled = customizeBook, label = { Text("Text") }); Box(Modifier.size(30.dp).clip(CircleShape).background(Color(if (colorTarget == "background") appearance.background else appearance.foreground))) }
                        if (colorTarget == "background") Text("Tap the wheel center for OLED black (#000000).", style = MaterialTheme.typography.bodySmall)
                        ReaderColorWheel(Modifier.fillMaxWidth().height(180.dp).alpha(if (customizeBook) 1f else .42f), enabled = customizeBook) { color -> appearance = if (colorTarget == "background") appearance.copy(theme = "custom", background = color) else appearance.copy(theme = "custom", foreground = color); onAppearance(appearance) }
                        if (customizeBook) TextButton(onClick = onResetBook) { Text("Reset this book to global defaults") }
                    }
                    1 -> {
                        if (supportsTwoColumns) { Text("Page layout", style = MaterialTheme.typography.titleSmall); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("single" to "Single", "double" to "Double", "continuous" to "Continuous").forEach { (mode, label) -> val selected = when (mode) { "double" -> appearance.twoColumns && !appearance.continuous; "continuous" -> appearance.continuous; else -> !appearance.twoColumns && !appearance.continuous }; FilterChip(selected = selected, onClick = { onReadingMode(mode) }, enabled = customizeBook, label = { Text(label) }) } }; Text("Paginated by default. Continuous mode scrolls through a long section.", style = MaterialTheme.typography.bodySmall) }
                        if (supportsTwoColumns && appearance.twoColumns && !appearance.continuous) {
                            Text("Middle column gap · ${appearance.columnGapDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = appearance.columnGapDp,
                                onValueChange = { appearance = appearance.copy(columnGapDp = it) },
                                onValueChangeFinished = { onAppearance(appearance) },
                                valueRange = 0f..MAX_COLUMN_GAP_DP,
                                enabled = customizeBook,
                            )
                        }
                        Text("Top margin · ${appearance.topMarginDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.topMarginDp, onValueChange = { appearance = appearance.copy(topMarginDp = it); onAppearance(appearance) }, valueRange = 0f..80f, enabled = customizeBook)
                        Text("Bottom margin · ${appearance.bottomMarginDp.toInt()} dp", style = MaterialTheme.typography.titleSmall)
                        Slider(value = appearance.bottomMarginDp, onValueChange = { appearance = appearance.copy(bottomMarginDp = it); onAppearance(appearance) }, valueRange = 0f..80f, enabled = customizeBook)
                    }
                    else -> {
                        Text("Page transition (global)", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("none", "fade", "slide", "page turn").forEach { mode -> FilterChip(selected = transition == mode, onClick = { onTransition(mode) }, label = { Text(mode.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall) }) } }
                        Text("This setting applies across books.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Done") } }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (maxWidth >= 600.dp && maxHeight >= 420.dp) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp)
                    .width((maxWidth * .46f).coerceIn(300.dp, 396.dp)).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) { panel(true) }
        } else {
            Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(12.dp)
                            .widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight - 24.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) { panel(false) }
                }
            }
        }
    }
}

@Composable
private fun ReaderAppearancePreview(appearance: ReaderAppearance) {
    val foreground = Color(appearance.foreground)
    val typeface = when (appearance.fontFamily) {
        "sans-serif" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        "monospace", "iA Writer Duospace" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(appearance.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, foreground.copy(alpha = .24f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Preview", color = foreground.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
            Text(
                "By the time the rain lifted, the lights across the harbor had come on.",
                color = foreground, fontFamily = typeface, fontSize = (16 * appearance.fontScale).sp,
                lineHeight = (21 * appearance.fontScale * appearance.lineSpacing).sp,
            )
        }
    }
}

@Composable
private fun ReaderProgressSeeker(progress: ReaderProgressUi, foreground: Color, panel: Color, accent: Color, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var fingerProgress by remember { mutableFloatStateOf(progress.fraction.coerceIn(0f, 1f)) }
    val scope = rememberCoroutineScope()
    val expansion by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = .72f, stiffness = 520f), label = "progress seeker size")
    val position = if (dragging) fingerProgress else progress.fraction.coerceIn(0f, 1f)
    val section = sectionIndexAt(position, progress.sections)
    val page = if (dragging && progress.total != null) (position * progress.total).toInt().plus(1).coerceAtMost(progress.total) else progress.position
    val chapterProgress = chapterPageProgress(page, progress.total, progress.sections)
    val counterStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Book · ${page ?: "—"} / ${progress.total ?: "—"}", color = foreground, style = counterStyle, fontWeight = FontWeight.SemiBold)
            Text("${(position * 100).toInt()}%", color = foreground, style = counterStyle)
        }
        if (chapterProgress != null) {
            Text(
                "Chapter · ${chapterProgress.page} / ${chapterProgress.total}",
                color = foreground.copy(alpha = .85f),
                style = counterStyle,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp)) {
            val currentWidth = maxWidth
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).semantics {
                contentDescription = "Reading progress"
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                setProgress { onSeek(it.coerceIn(0f, 1f)); true }
            }.pointerInput(progress.sections.size) {
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
                val previewWidth = minOf(currentWidth, 184.dp)
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (currentWidth * position - previewWidth / 2).coerceIn(0.dp, currentWidth - previewWidth)).width(previewWidth),
                    shape = RoundedCornerShape(10.dp),
                    color = panel,
                    shadowElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, foreground.copy(alpha = .2f)),
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(title, color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Book page ${page ?: "—"}", color = foreground.copy(alpha = .85f), style = counterStyle)
                    }
                }
            }
        }
    }
}
@Composable
private fun ReaderColorWheel(modifier: Modifier, enabled: Boolean, onColor: (Int) -> Unit) {
    Canvas(modifier.pointerInput(enabled) {
        if (enabled) detectTapGestures { emitWheelColor(it.x, it.y, size.width.toFloat(), size.height.toFloat(), onColor) }
    }.pointerInput(enabled) {
        if (enabled) detectDragGestures { change, _ -> emitWheelColor(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), onColor); change.consume() }
    }) {
        val radius = kotlin.math.min(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), radius)
        drawCircle(Color.Black, radius * .14f)
        drawCircle(Color.White, radius * .14f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

private fun emitWheelColor(x: Float, y: Float, width: Float, height: Float, onColor: (Int) -> Unit) {
    val dx = x - width / 2f; val dy = y - height / 2f
    val radius = kotlin.math.sqrt(dx * dx + dy * dy); val maxRadius = kotlin.math.min(width, height) / 2f
    if (radius <= maxRadius * .14f) {
        onColor(android.graphics.Color.BLACK)
    } else if (radius <= maxRadius) {
        val hue = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
        onColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, (radius / maxRadius).coerceIn(.12f, 1f), 1f)))
    }
}

internal fun readerPreset(id: String): Pair<Int, Int> = readerThemePresets.firstOrNull { it.id == id }
    ?.let { it.background to it.foreground }
    ?: readerThemePresets.first { it.id == "paper" }.let { it.background to it.foreground }

internal fun bookmarkRibbonTint(background: Int): Color =
    if (Color(background).luminance() < .4f) Color(0xFFFFC45E) else Color(0xFF945033)
