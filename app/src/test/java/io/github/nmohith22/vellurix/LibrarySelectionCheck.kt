package io.github.nmohith22.vellurix

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySelectionCheck {
    @Test fun restoresAnAvailableShelf() {
        assertEquals("Favorites", resolveSelectedShelf("Favorites", listOf("All books", "Favorites")))
    }

    @Test fun fallsBackWhenTheSavedShelfIsGone() {
        assertEquals("All books", resolveSelectedShelf("Old shelf", listOf("All books", "Favorites")))
    }

    @Test fun restoresAllBooksWhenThatWasTheLastSelection() {
        assertEquals("All books", resolveSelectedShelf("All books", listOf("All books", "Favorites")))
    }
}
