package io.github.nmohith22.vellurix

internal data class ReaderAppearance(
    val theme: String = "paper",
    val background: Int,
    val foreground: Int,
    val fontFamily: String = "",
    val fontScale: Float = 1f,
    val lineSpacing: Float = 1f,
    val twoColumns: Boolean = false,
    val continuous: Boolean = false,
    val topMarginDp: Float = 0f,
    val bottomMarginDp: Float = 0f,
)

internal data class ReaderThemePreset(val id: String, val name: String, val background: Int, val foreground: Int)

internal val readerThemePresets = listOf(
    ReaderThemePreset("paper", "Paper", 0xFFFAF9F6.toInt(), 0xFF2B2A27.toInt()),
    ReaderThemePreset("white", "White", 0xFFFFFFFF.toInt(), 0xFF232323.toInt()),
    ReaderThemePreset("sepia", "Sepia", 0xFFF4E8CF.toInt(), 0xFF473727.toInt()),
    ReaderThemePreset("night", "Night", 0xFF000000.toInt(), 0xFFD3D7DB.toInt()),
    ReaderThemePreset("forest", "Forest", 0xFF16231B.toInt(), 0xFFD8E5DA.toInt()),
    ReaderThemePreset("slate", "Slate", 0xFF1E2631.toInt(), 0xFFDCE2EB.toInt()),
    ReaderThemePreset("deep_black", "Deep Black", 0xFF000000.toInt(), 0xFFD4D4D4.toInt()),
    ReaderThemePreset("deep_orange", "Deep Orange", 0xFF000000.toInt(), 0xFFC58B55.toInt()),
    ReaderThemePreset("deep_amber", "Deep Amber", 0xFF000000.toInt(), 0xFFD6AA62.toInt()),
    ReaderThemePreset("deep_green", "Deep Green", 0xFF000000.toInt(), 0xFF9DBB9F.toInt()),
    ReaderThemePreset("deep_blue", "Deep Blue", 0xFF000000.toInt(), 0xFF9BB8D8.toInt()),
    ReaderThemePreset("deep_purple", "Deep Purple", 0xFF000000.toInt(), 0xFFB7A3D2.toInt()),
    ReaderThemePreset("deep_rose", "Deep Rose", 0xFF000000.toInt(), 0xFFD1A0AA.toInt()),
    ReaderThemePreset("oled_ink", "OLED Ink", 0xFF000000.toInt(), 0xFFBFC7D5.toInt()),
    ReaderThemePreset("oled_sepia", "OLED Sepia", 0xFF000000.toInt(), 0xFFC8AE88.toInt()),
    ReaderThemePreset("ocean", "Ocean", 0xFF14232D.toInt(), 0xFFD8E7EF.toInt()),
    ReaderThemePreset("rose_pine", "Rosé Pine", 0xFF191724.toInt(), 0xFFE0DEF4.toInt()),
    ReaderThemePreset("solarized", "Solarized", 0xFF002B36.toInt(), 0xFFEEE8D5.toInt()),
)

internal data class ReaderAppearanceOverrides(
    val theme: String? = null,
    val background: Int? = null,
    val foreground: Int? = null,
    val fontFamily: String? = null,
    val fontScale: Float? = null,
    val lineSpacing: Float? = null,
    val twoColumns: Boolean? = null,
    val continuous: Boolean? = null,
    val topMarginDp: Float? = null,
    val bottomMarginDp: Float? = null,
)

internal fun resolveReaderAppearance(
    global: ReaderAppearance,
    book: ReaderAppearanceOverrides?,
) = ReaderAppearance(
    theme = book?.theme ?: global.theme,
    background = book?.background ?: global.background,
    foreground = book?.foreground ?: global.foreground,
    fontFamily = book?.fontFamily ?: global.fontFamily,
    fontScale = book?.fontScale ?: global.fontScale,
    lineSpacing = book?.lineSpacing ?: global.lineSpacing,
    twoColumns = book?.twoColumns ?: global.twoColumns,
    continuous = book?.continuous ?: global.continuous,
    topMarginDp = book?.topMarginDp ?: global.topMarginDp,
    bottomMarginDp = book?.bottomMarginDp ?: global.bottomMarginDp,
)

internal fun usesTwoColumns(preferred: Boolean, continuous: Boolean, widthPx: Int, heightPx: Int): Boolean =
    preferred && !continuous && widthPx > heightPx
