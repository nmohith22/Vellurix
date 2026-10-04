package io.github.nmohith22.vellurix

internal data class ReaderAppearance(
    val theme: String = "paper",
    val background: Int,
    val foreground: Int,
    val fontFamily: String = "",
    val fontScale: Float = 1f,
    val twoColumns: Boolean = false,
    val continuous: Boolean = false,
    val topMarginDp: Float = 0f,
    val bottomMarginDp: Float = 0f,
)

internal data class ReaderAppearanceOverrides(
    val theme: String? = null,
    val background: Int? = null,
    val foreground: Int? = null,
    val fontFamily: String? = null,
    val fontScale: Float? = null,
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
    twoColumns = book?.twoColumns ?: global.twoColumns,
    continuous = book?.continuous ?: global.continuous,
    topMarginDp = book?.topMarginDp ?: global.topMarginDp,
    bottomMarginDp = book?.bottomMarginDp ?: global.bottomMarginDp,
)

internal fun usesTwoColumns(preferred: Boolean, continuous: Boolean, widthPx: Int, heightPx: Int): Boolean =
    preferred && !continuous && widthPx > heightPx
