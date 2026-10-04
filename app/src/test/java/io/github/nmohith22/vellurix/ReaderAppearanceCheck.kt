package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAppearanceCheck {
    @Test fun absentBookOverridesKeepGlobalAppearance() {
        val global = ReaderAppearance(theme = "sepia", background = 0xFFF4E8CF.toInt(), foreground = 0xFF473727.toInt(), fontFamily = "serif", fontScale = 1.2f)
        assertEquals(global, resolveReaderAppearance(global, null))
    }

    @Test fun partialBookOverridesFallBackToGlobalForOtherSettings() {
        val global = ReaderAppearance(theme = "paper", background = 0xFFFAF9F6.toInt(), foreground = 0xFF2B2A27.toInt(), fontFamily = "serif", fontScale = 1f, lineSpacing = 1.25f)
        val actual = resolveReaderAppearance(global, ReaderAppearanceOverrides(theme = "night", fontFamily = "", fontScale = 1.4f))
        assertEquals("night", actual.theme)
        assertEquals(global.background, actual.background)
        assertEquals(global.foreground, actual.foreground)
        assertEquals("", actual.fontFamily)
        assertEquals(1.4f, actual.fontScale)
        assertEquals(1.25f, actual.lineSpacing)
    }

    @Test fun columnsFollowViewportAspectAndKeepLandscapeSingleColumnAvailable() {
        assertEquals(false, usesTwoColumns(true, false, 800, 1200))
        assertEquals(true, usesTwoColumns(true, false, 1200, 800))
        assertEquals(false, usesTwoColumns(false, false, 1200, 800))
        assertEquals(false, usesTwoColumns(true, true, 1200, 800))
    }

    @Test fun readerMarginsDefaultToCompactPlatformSpacing() {
        val appearance = ReaderAppearance(background = 0, foreground = 0)
        assertEquals(0f, appearance.topMarginDp)
        assertEquals(0f, appearance.bottomMarginDp)
    }
}
