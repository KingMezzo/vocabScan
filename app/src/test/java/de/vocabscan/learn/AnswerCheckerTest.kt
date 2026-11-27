package de.vocabscan.learn

import org.junit.Assert.*
import org.junit.Test

class AnswerCheckerTest {
    @Test fun acceptsStoredVariantsAndNormalizedCase() {
        assertEquals(AnswerKind.MATCH, AnswerChecker.check("  GEBÄUDE ", "Haus", "Gebäude; Behausung", "de").kind)
        assertEquals(AnswerKind.MATCH, AnswerChecker.check("école", "école", "", "fr").kind)
    }

    @Test fun acceptsOmittedArticlesWithoutAcceptingWrongGermanGender() {
        assertTrue(AnswerChecker.check("Haus", "das Haus", "", "de").accepted)
        assertTrue(AnswerChecker.check("the house", "house", "", "en").accepted)
        assertTrue(AnswerChecker.check("école", "l’école", "", "fr").accepted)
        assertFalse(AnswerChecker.check("der Haus", "das Haus", "", "de").accepted)
    }

    @Test fun suggestsTyposWithoutSilentlyAcceptingThemOrRemovingAccents() {
        assertEquals(AnswerKind.TYPO, AnswerChecker.check("Huas", "Haus", "", "de").kind)
        assertEquals(AnswerKind.ACCENT, AnswerChecker.check("ecole", "école", "", "fr").kind)
        assertFalse(AnswerChecker.check("ecole", "école", "", "fr").accepted)
        assertEquals(AnswerKind.DIFFERENT, AnswerChecker.check("car", "cat", "", "en").kind)
        assertEquals(AnswerKind.EMPTY, AnswerChecker.check(" ", "Haus", "", "de").kind)
    }
}
