package de.vocabscan.learn

import de.vocabscan.data.*
import org.junit.Assert.*
import org.junit.Test

class LanguagePairTest {
    @Test fun allLanguagePairsKeepTheirSidesInBothDirectionsAndCsv() {
        for (source in StudyLanguage.entries) for (target in StudyLanguage.entries) {
            val deck = Deck("d", "Wörter", source.code, targetLanguage = target.code)
            val word = Vocabulary("w", "d", "term", "answer")
            val cards = StudyPlanner.candidates(StudyData(listOf(DeckWithVocabulary(deck, listOf(word))), emptyList()),
                StudyOptions(filter = StudyFilter.ALL), 1000)
            val forward = cards.single { it.direction.ordinal == 0 }
            val reverse = cards.single { it.direction.ordinal == 1 }
            assertEquals(source.code, forward.promptLanguage)
            assertEquals(target.code, forward.answerLanguage)
            assertEquals(target.code, reverse.promptLanguage)
            assertEquals(source.code, reverse.answerLanguage)
            assertEquals("w:0", forward.key)
            assertEquals("w:1", reverse.key)
            assertNotEquals(forward.directionLabel, reverse.directionLabel)
            val row = CsvRow("d", "Wörter", source.code, word.term, word.translation, "", targetLanguage = target.code)
            assertEquals(row, CsvCodec.decode(CsvCodec.encode(listOf(row))).single())
        }
    }

    @Test fun latinUsesItsOwnLanguageCodeAndAnswerRules() {
        assertEquals(StudyLanguage.LATIN, StudyLanguage.fromCode("la"))
        assertTrue(DeckType.SPRACHE.acceptsLanguages("la", "la"))
        assertEquals("Latein → Deutsch", Deck("d", "Latein", "la").subjectLabel)
        assertEquals(AnswerKind.MATCH, AnswerChecker.check("AMICUS", "amicus", "", "la").kind)
        assertEquals(AnswerKind.ACCENT, AnswerChecker.check("amare", "amāre", "", "la").kind)
        assertFalse(AnswerChecker.check("amicus", "a amicus", "", "la").accepted)
        assertFalse(AnswerChecker.check("amicus", "la amicus", "", "la").accepted)
    }

    @Test fun answerCheckingUsesTheAnswerSideLanguageInBothDirections() {
        val forward = StudyCard(Vocabulary("w", "d", "the house", "la maison"), "en", "Wörter",
            StudyDirection.FOREIGN_TO_GERMAN, targetLanguage = "fr")
        val reverse = forward.copy(direction = StudyDirection.GERMAN_TO_FOREIGN)
        assertEquals(AnswerKind.ARTICLE, AnswerChecker.check("maison", forward.word.translation, "", forward.answerLanguage).kind)
        assertEquals(AnswerKind.ARTICLE, AnswerChecker.check("house", reverse.word.term, "", reverse.answerLanguage).kind)
        assertEquals(AnswerKind.MATCH, AnswerChecker.check("very large", "enormous", "very large", "en").kind)
    }

    @Test fun sameLanguageUsesRolesAndCompatibilityIncludesBothSides() {
        val deck = Deck("d", "Definitions", "en", targetLanguage = "en")
        assertEquals("Englisch → Englisch", deck.subjectLabel)
        assertEquals("Begriff · Englisch", deck.termLabel)
        assertEquals("Erklärung / Synonym · Englisch", deck.translationLabel)
        assertEquals("Begriff → Erklärung / Synonym", deck.directionLabel())
        assertFalse(deck.compatibleWith(deck.copy(targetLanguage = "de")))
        assertFalse(deck.compatibleWith(deck.copy(language = "fr")))
        assertTrue(deck.compatibleWith(deck.copy(id = "other")))
        assertFalse(DeckType.SPRACHE.acceptsLanguages("en", "es"))
        assertFalse(DeckType.LERNKARTEN.acceptsLanguages("", "en"))
    }

    @Test fun previousCsvFormatsDefaultToGermanOrNoLanguageForFlashcards() {
        val old = "deckId,deck,language,term,translation,note\nd,List,fr,maison,Haus,\n"
        assertEquals("de", CsvCodec.decode(old).single().targetLanguage)
        val typed = old.lineSequence().first() + ",termAlternatives,translationAlternatives,chapter,tags,isFavorite,scanGroupId,folder,isArchived,type\n"
        assertEquals("de", CsvCodec.decode(typed + "d,List,en,house,Haus,,,,,,false,,,false,SPRACHE\n").single().targetLanguage)
        assertEquals("", CsvCodec.decode(typed + "d,List,,Term,Explanation,,,,,,false,,,false,LERNKARTEN\n").single().targetLanguage)
    }
}
