package io.github.nmohith22.vellurix

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadingBackupCheck {
    @Test fun contentIdentityIsStableAcrossBookUriChanges() {
        val first = ReadingBackup.contentIdentity(ByteArrayInputStream("same epub bytes".toByteArray()))
        val second = ReadingBackup.contentIdentity(ByteArrayInputStream("same epub bytes".toByteArray()))
        assertEquals(first, second)
    }

    @Test fun metadataFallbackNormalizesTitleWhitespaceAndCase() {
        assertEquals(
            ReadingBackup.metadataIdentity("  A  Tale\tOf Two Cities ", "EPUB"),
            ReadingBackup.metadataIdentity("a tale of two cities", "epub"),
        )
    }

    @Test fun differentFileContentsCannotClaimTheSameReadingHistory() {
        fun identity(text: String) = ReadingBackup.contentIdentity(ByteArrayInputStream(text.toByteArray()))
        assertNotEquals(identity("same title, first edition"), identity("same title, second edition"))
        assertNotEquals(
            ReadingBackup.metadataIdentity("The Harbor", "EPUB"),
            ReadingBackup.metadataIdentity("The Harbor", "PDF"),
        )
    }
}
