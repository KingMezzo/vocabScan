package de.vocabscan.scan

import org.junit.Assert.*
import org.junit.Test

class LanguageHintTest {
    @Test fun latinColumnsAndDictionaryFormsAreDetectedIndependently() {
        val pairs = listOf(ImportPair(term = "puella, -ae f.", translation = "das Mädchen"),
            ImportPair(term = "amīcus", translation = "der Freund"))
        assertEquals("la", LanguageHint.suggest(pairs))
        assertEquals("de", LanguageHint.suggest(pairs, translationSide = true))
        assertEquals("la", LanguageHint.suggest(pairs.map { it.copy(translation = it.term) }, translationSide = true))
        assertEquals("la", LanguageHint.suggest(listOf(ImportPair(term = "aqua", translation = "Wasser"),
            ImportPair(term = "puella", translation = "Mädchen"))))
        assertNull(LanguageHint.suggest(pairs.take(1)))
        assertNull(LanguageHint.suggest(pairs + listOf(ImportPair(term = "la maison", translation = ""),
            ImportPair(term = "le jardin", translation = ""))))
    }
    @Test fun germanAndSameLanguageColumnsAreDetectedIndependently() {
        val german = listOf(ImportPair(term = "der Bär", translation = "the bear"), ImportPair(term = "die Tür", translation = "the door"))
        assertEquals("de", LanguageHint.suggest(german))
        assertEquals("en", LanguageHint.suggest(german, translationSide = true))
        val english = german.map { it.copy(term = it.translation) }
        assertEquals("en", LanguageHint.suggest(english))
        assertEquals("en", LanguageHint.suggest(english, translationSide = true))
    }
    @Test fun reportsOnlyStrongClues() {
        assertEquals("fr", LanguageHint.suggest(listOf(
            ImportPair(term = "la maison", translation = "das Haus"),
            ImportPair(term = "l'école", translation = "die Schule"),
        )))
        assertEquals("en", LanguageHint.suggest(listOf(
            ImportPair(term = "the house", translation = "das Haus"),
            ImportPair(term = "to learn", translation = "lernen"),
        )))
        assertNull(LanguageHint.suggest(listOf(ImportPair(term = "house", translation = "Haus"))))
    }
}
