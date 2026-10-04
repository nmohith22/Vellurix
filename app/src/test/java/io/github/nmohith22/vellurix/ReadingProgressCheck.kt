package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressCheck {
    @Test fun libraryProgressCanShowPercentOrPositions() {
        val progress = BookProgress(.437f, 44, 100)
        assertEquals("43%", progressLabel(progress, "percent"))
        assertEquals("44 / 100", progressLabel(progress, "pages"))
    }

    @Test fun seekerFindsTheChapterAtTheFingerProgress() {
        val sections = listOf(ReaderProgressSection("Opening", 0f), ReaderProgressSection("Middle", .35f), ReaderProgressSection("End", .8f))
        assertEquals(0, sectionIndexAt(.2f, sections))
        assertEquals(1, sectionIndexAt(.5f, sections))
        assertEquals(2, sectionIndexAt(1f, sections))
    }
}
