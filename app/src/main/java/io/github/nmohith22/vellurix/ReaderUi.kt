package io.github.nmohith22.vellurix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

internal data class ReaderNavItem(val title: String, val depth: Int, val locator: String)
internal data class ReaderBookmark(val title: String, val locator: String)

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
    toc: List<ReaderNavItem>,
    bookmarks: List<ReaderBookmark>,
    onDrawerOpenChange: (Boolean) -> Unit,
    onSettingsOpenChange: (Boolean) -> Unit,
    onHideControls: () -> Unit,
    onClose: () -> Unit,
    onRotate: () -> Unit,
    onTurn: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBookmark: () -> Unit,
    onRemoveBookmark: (ReaderBookmark) -> Unit,
    onAppearance: (ReaderAppearance) -> Unit,
    onCustomizeBook: (Boolean) -> Unit,
    onResetBook: () -> Unit,
    onTransition: (String) -> Unit,
    onColumns: (Boolean) -> Unit,
) {
    var bookmarkTab by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { bookmarkTab = true; onDrawerOpenChange(true) }) { Icon(Icons.Rounded.Bookmark, "Bookmarks", tint = fg) }
                        IconButton(onClick = { onSettingsOpenChange(true) }) { Icon(Icons.Rounded.Tune, "Reader settings", tint = fg) }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
                    shape = RoundedCornerShape(24.dp), color = panel, shadowElevation = 12.dp,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onTurn(false) }) { Text("Previous", color = fg) }
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(onClick = { bookmarkTab = false; onDrawerOpenChange(true) }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Contents")
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = onBookmark) { Icon(Icons.Rounded.BookmarkAdd, "Add bookmark", tint = accent) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onTurn(true) }) { Text("Next", color = fg) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (autoRotate) "Auto rotate" else "Portrait locked", color = Color(appearance.foreground).copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                            Text("  ·  ", color = fg.copy(alpha = .45f))
                            TextButton(onClick = onRotate, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Change", style = MaterialTheme.typography.labelSmall) }
                            Text("  ·  ", color = fg.copy(alpha = .45f))
                            TextButton(onClick = { onTransition(if (transition == "none") "fade" else "none") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(transition.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall) }
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
                                        Column(Modifier.weight(1f)) { Text(mark.title, color = fg, style = MaterialTheme.typography.bodyLarge); Text("Saved location", color = fg.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall) }
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
        appearance, customizeBook, transition, twoColumns, onDismiss = { onSettingsOpenChange(false) },
        onAppearance = onAppearance, onCustomizeBook = onCustomizeBook, onResetBook = onResetBook,
        onTransition = onTransition, onColumns = onColumns,
    )
}

@Composable
private fun ReaderSettingsDialog(
    current: ReaderAppearance,
    customizeBook: Boolean,
    transition: String,
    twoColumns: Boolean,
    onDismiss: () -> Unit,
    onAppearance: (ReaderAppearance) -> Unit,
    onCustomizeBook: (Boolean) -> Unit,
    onResetBook: () -> Unit,
    onTransition: (String) -> Unit,
    onColumns: (Boolean) -> Unit,
) {
    var appearance by remember(current) { mutableStateOf(current) }
    var fontMenu by remember { mutableStateOf(false) }
    val fonts = listOf("" to "Publisher default", "serif" to "Serif", "sans-serif" to "Sans serif", "cursive" to "Cursive", "fantasy" to "Fantasy", "monospace" to "Monospace", "OpenDyslexic" to "OpenDyslexic", "AccessibleDfA" to "Accessible DfA", "iA Writer Duospace" to "iA Writer Duospace")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading experience", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Customize this book", style = MaterialTheme.typography.titleSmall); Text("Otherwise these settings follow your global defaults", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = customizeBook, onCheckedChange = onCustomizeBook)
                }
                Text("Reading theme", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("paper" to "Paper", "white" to "White", "sepia" to "Sepia", "night" to "Night", "forest" to "Forest", "slate" to "Slate").forEach { (id, label) ->
                        val colors = readerPreset(id)
                        Surface(onClick = { appearance = appearance.copy(theme = id, background = colors.first, foreground = colors.second) }, shape = RoundedCornerShape(14.dp), color = Color(colors.first), border = if (appearance.theme == id) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Text(label, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = Color(colors.second), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text size and zoom: ${(appearance.fontScale * 100).toInt()}%", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                Slider(value = appearance.fontScale, onValueChange = { appearance = appearance.copy(fontScale = it) }, valueRange = .8f..2f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ViewColumn, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Two columns", style = MaterialTheme.typography.titleSmall); Text("Paginated EPUB reading", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = twoColumns, onCheckedChange = onColumns)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Typeface", style = MaterialTheme.typography.titleSmall); Text(fonts.firstOrNull { it.first == appearance.fontFamily }?.second ?: "Publisher default", style = MaterialTheme.typography.bodySmall) }
                    Box {
                        TextButton(onClick = { fontMenu = true }) { Text("Change") }
                        DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) { fonts.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { appearance = appearance.copy(fontFamily = value); fontMenu = false }) } }
                    }
                }
                Text("Page transition", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("none", "fade", "slide", "page turn").forEach { mode -> FilterChip(selected = transition == mode, onClick = { onTransition(mode) }, label = { Text(mode.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall) }) }
                }
                Text("Colors", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("background" to "Page", "foreground" to "Text").forEach { (which, label) ->
                        AndroidView(factory = { context -> HueWheel(context) { color -> appearance = if (which == "background") appearance.copy(theme = "custom", background = color) else appearance.copy(theme = "custom", foreground = color) } }, modifier = Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(14.dp)))
                        Text("$label color", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (customizeBook) TextButton(onClick = onResetBook) { Text("Reset this book to global defaults") }
            }
        },
        confirmButton = { TextButton(onClick = { onAppearance(appearance); onDismiss() }) { Text("Apply") } },
    )
}

private fun readerPreset(id: String): Pair<Int, Int> = when (id) {
    "white" -> android.graphics.Color.WHITE to android.graphics.Color.rgb(35, 35, 35)
    "sepia" -> android.graphics.Color.rgb(244, 232, 207) to android.graphics.Color.rgb(71, 55, 39)
    "night" -> android.graphics.Color.rgb(14, 16, 19) to android.graphics.Color.rgb(211, 215, 219)
    "forest" -> android.graphics.Color.rgb(22, 35, 27) to android.graphics.Color.rgb(218, 229, 218)
    "slate" -> android.graphics.Color.rgb(30, 38, 49) to android.graphics.Color.rgb(220, 226, 235)
    else -> android.graphics.Color.rgb(250, 249, 246) to android.graphics.Color.rgb(43, 42, 39)
}
