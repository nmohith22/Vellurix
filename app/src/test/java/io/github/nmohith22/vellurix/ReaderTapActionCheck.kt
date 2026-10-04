package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapActionCheck {
    @Test fun topRightCornerAddsBookmarkAndReadingEdgesTurnPages() {
        assertEquals(ReaderTapAction.ADD_BOOKMARK, readerTapAction(950f, 40f, 1000f, 1000f, false))
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, readerTapAction(100f, 500f, 1000f, 1000f, false))
        assertEquals(ReaderTapAction.NEXT_PAGE, readerTapAction(900f, 500f, 1000f, 1000f, false))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, readerTapAction(500f, 500f, 1000f, 1000f, false))
    }

    @Test fun visibleMenusBlockReaderSurfaceGestures() {
        assertEquals(ReaderTapAction.IGNORE, readerTapAction(950f, 40f, 1000f, 1000f, true))
        assertEquals(ReaderTapAction.IGNORE, readerTapAction(900f, 500f, 1000f, 1000f, true))
    }
}
