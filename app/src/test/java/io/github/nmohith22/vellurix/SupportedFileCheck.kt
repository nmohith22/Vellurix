package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedFileCheck {
    @Test fun recognizesSupportedExtensionsCaseInsensitively() {
        assertEquals("epub", fileFormat("Novel.EPUB"))
        assertEquals("pdf", fileFormat("scan.PDF"))
        assertEquals("fb2", fileFormat("book.fb2"))
        assertEquals("rtf", fileFormat("notes.rtf"))
        assertNull(fileFormat("notes.docx"))
        assertNull(fileFormat("archive.cbz"))
    }

    @Test fun stripsCommonRtfControlsWithoutLosingText() {
        assertEquals("Hello world", formatText("{\\rtf1\\ansi Hello\\par world}", "RTF"))
    }

    @Test fun ignoresCorruptSavedReadingLocation() {
        assertNull(parseSavedLocator("not-json"))
    }
}

