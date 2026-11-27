package de.vocabscan.data

import org.junit.Assert.*
import org.junit.Test

class CsvCodecTest {
    @Test fun roundTripKeepsCommasQuotesNewlinesAndAccents() {
        val rows = listOf(
            CsvRow("one", "Unité, 1", "fr", "l'école", "die \"Schule\"", "Zeile 1\nZeile 2",
                termAlternatives = "école", translationAlternatives = "Schulgebäude; Schule", chapter = "Unité 1", tags = "Schule, Nomen",
                isFavorite = true, scanGroupId = "scan-one", folder = "Schule, Französisch", isArchived = true),
            CsvRow("two", "Leer", "en", "", "", ""),
        )
        assertEquals(rows, CsvCodec.decode(CsvCodec.encode(rows)))
    }

    @Test fun acceptsBomAndWindowsLineEndings() {
        val csv = "\uFEFFdeckId,deck,language,term,translation,note\r\nabc,Unit 1,en,apple,Apfel,\r\n"
        assertEquals("apple", CsvCodec.decode(csv).single().term)
    }

    @Test fun rejectsIncompleteRows() {
        val csv = "deckId,deck,language,term,translation,note\nabc,Unit 1,en,apple,,\n"
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(csv) }
    }

    @Test fun importsPreviousTenColumnCsvWithoutFavorites() {
        val csv = "deckId,deck,language,term,translation,note,termAlternatives,translationAlternatives,chapter,tags\n" +
            "abc,Unit 1,en,apple,Apfel,,,,,\n"
        assertFalse(CsvCodec.decode(csv).single().isFavorite)
    }

    @Test fun importsPreviousElevenColumnCsvWithoutScanGroups() {
        val csv = "deckId,deck,language,term,translation,note,termAlternatives,translationAlternatives,chapter,tags,isFavorite\n" +
            "abc,Unit 1,en,apple,Apfel,,,,,,true\n"
        assertNull(CsvCodec.decode(csv).single().scanGroupId.takeIf { it.isNotEmpty() })
    }

    @Test fun importsPreviousTwelveColumnCsvWithoutOrganization() {
        val csv = "deckId,deck,language,term,translation,note,termAlternatives,translationAlternatives,chapter,tags,isFavorite,scanGroupId\n" +
            "abc,Unit 1,en,apple,Apfel,,,,,,true,scan-one\n"
        val row = CsvCodec.decode(csv).single()
        assertEquals("", row.folder)
        assertFalse(row.isArchived)
        assertEquals("scan-one", row.scanGroupId)
    }
    @Test fun nestedPathsRoundTripWithIndependentArchiveFlags() {
        val path = "Schule/" + "Englisch".repeat(10) + "/Unit 1"
        val rows = listOf(
            CsvRow("active", "Aktiv", "en", "book", "Buch", "", folder = path),
            CsvRow("archived", "Archiv", "en", "house", "Haus", "", folder = path, isArchived = true),
        )
        assertEquals(rows, CsvCodec.decode(CsvCodec.encode(rows)))
    }

}
