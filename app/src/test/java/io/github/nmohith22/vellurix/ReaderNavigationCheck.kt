package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNavigationCheck {
    private val positions = listOf(
        ReaderResourcePosition("Text/chapter1.xhtml", 1),
        ReaderResourcePosition("Text/chapter1.xhtml#part-two", 2),
        ReaderResourcePosition("Text/chapter2.xhtml", 3),
        ReaderResourcePosition("Text/chapter2.xhtml?view=epub", 4),
    )
    private val ranges = indexResourcePositions(positions)

    @Test fun resourceEdgesFindAdjacentSpineItemsAcrossFragmentsAndQueries() {
        assertTrue(isAtResourceBoundary("Text/chapter1.xhtml#part-two", 2, null, ranges, forward = true))
        assertTrue(isAtResourceBoundary("Text/chapter2.xhtml?view=epub", 3, null, ranges, forward = false))
        assertFalse(isAtResourceBoundary("Text/chapter1.xhtml", 1, null, ranges, forward = true))
        assertTrue(isAtResourceBoundary("Text/chapter1.xhtml", null, .995, ranges, forward = true))
        assertEquals(1, adjacentReadingOrderIndex("Text/chapter1.xhtml#part-two", listOf("Text/chapter1.xhtml", "Text/chapter2.xhtml"), true))
        assertEquals(0, adjacentReadingOrderIndex("Text/chapter2.xhtml", listOf("Text/chapter1.xhtml", "Text/chapter2.xhtml"), false))
    }

    @Test fun neverWrapsFromLastChapterToFirstOrJumpsForAnUnknownResource() {
        val spine = listOf("first.xhtml", "second.xhtml", "last.xhtml")
        assertEquals(null, adjacentReadingOrderIndex("first.xhtml", spine, forward = false))
        assertEquals(null, adjacentReadingOrderIndex("last.xhtml", spine, forward = true))
        assertEquals(null, adjacentReadingOrderIndex("toc.xhtml", spine, forward = true))
    }
}
