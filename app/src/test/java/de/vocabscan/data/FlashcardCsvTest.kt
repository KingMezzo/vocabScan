package de.vocabscan.data

import org.junit.Assert.*
import org.junit.Test

class FlashcardCsvTest {
    @Test fun mixedBackupPreservesTypesEmptyLanguageMultilineNotesAndTags() {
        val rows = listOf(
            CsvRow("d", "Recht", "", "Vertrag", "Erste Zeile\nZweite Zeile", "Beispiel; Eselsbrücke",
                tags = "Recht, Prüfung", type = DeckType.LERNKARTEN),
            CsvRow("en", "Englisch", "en", "law", "Recht", ""),
            CsvRow("empty", "Leer", "", "", "", "", type = DeckType.LERNKARTEN))
        assertEquals(rows, CsvCodec.decode(CsvCodec.encode(rows)))
    }

    @Test fun legacyCsvDefaultsToLanguage() {
        val row = CsvCodec.decode("deckId,deck,language,term,translation,note\nd,English,en,law,Recht,\n").single()
        assertEquals(DeckType.SPRACHE, row.type)
    }

    @Test fun twoColumnTextAcceptsEveryOfferedSeparator() {
        listOf(';', ',', '\t', '|').forEach { separator ->
            assertEquals(listOf("Vertrag", "Erklärung"),
                CsvCodec.decodeCards("Vertrag" + separator + "Erklärung", separator).single())
        }
    }

    @Test fun quotedMultilineCsvAndOptionalMetadataSurvive() {
        val rows = CsvCodec.decodeCards("\uFEFFVertrag;\"Erste Zeile\nZweite; Zeile\";Beispiel;Recht\r\n", ';')
        assertEquals(listOf("Vertrag", "Erste Zeile\nZweite; Zeile", "Beispiel", "Recht"), rows.single())
    }

    @Test fun malformedInputAndInvalidTypesAreRejected() {
        listOf("Begriff", "Begriff;", ";Erklärung", "Begriff;Erklärung;Notiz;Tag;Extra", "Begriff;\"offen")
            .forEach { input -> assertThrows(IllegalArgumentException::class.java) { CsvCodec.decodeCards(input, ';') } }
        val invalid = CsvRow("d", "Recht", "en", "Vertrag", "Erklärung", "", type = DeckType.LERNKARTEN)
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(CsvCodec.encode(listOf(invalid))) }
    }
}
