package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBookmarkCheck {
    @Test fun togglesTheCurrentLocatorOffAndOn() {
        val page = ReaderBookmark("Chapter 1", "locator-1", 1)
        assertEquals(listOf(page), toggleReaderBookmark(emptyList(), page))
        assertEquals(emptyList<ReaderBookmark>(), toggleReaderBookmark(listOf(page), page))
    }

    @Test fun togglingOnePagePreservesOtherBookmarks() {
        val saved = ReaderBookmark("Chapter 1", "locator-1", 1)
        val current = ReaderBookmark("Chapter 2", "locator-2", 2)
        assertEquals(listOf(saved, current), toggleReaderBookmark(listOf(saved), current))
    }
}
