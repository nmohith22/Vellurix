package io.github.nmohith22.vellurix

internal data class ReaderAppearance(
    val theme: String = "paper",
    val background: Int,
    val foreground: Int,
    val fontFamily: String = "",
    val fontScale: Float = 1f,
)

internal data class ReaderAppearanceOverrides(
    val theme: String? = null,
    val background: Int? = null,
    val foreground: Int? = null,
    val fontFamily: String? = null,
    val fontScale: Float? = null,
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
)
