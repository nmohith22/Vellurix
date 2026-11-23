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

    @Test fun seekerShowsChapterLocalPageCount() {
        val sections = listOf(
            ReaderProgressSection("Opening", 0f, firstPosition = 1),
            ReaderProgressSection("Middle", .68f, firstPosition = 11),
            ReaderProgressSection("End", .94f, firstPosition = 31),
        )
        assertEquals(ChapterPageProgress(10, 10), chapterPageProgress(10, 40, sections))
        assertEquals(ChapterPageProgress(1, 20), chapterPageProgress(11, 40, sections))
        assertEquals(ChapterPageProgress(8, 20), chapterPageProgress(18, 40, sections))
        assertEquals(ChapterPageProgress(20, 20), chapterPageProgress(30, 40, sections))
        assertEquals(ChapterPageProgress(1, 10), chapterPageProgress(31, 40, sections))
        assertEquals(null, chapterPageProgress(null, 100, sections))
    }

    @Test fun duplicateTocPositionsDoNotCollapseChapterPageCount() {
        val sections = listOf(
            ReaderProgressSection("Part", 0f, firstPosition = 1),
            ReaderProgressSection("Chapter 1", 0f, firstPosition = 1),
            ReaderProgressSection("Chapter 2", .5f, firstPosition = 11),
        )
        assertEquals(ChapterPageProgress(5, 10), chapterPageProgress(5, 20, sections))
    }
}
