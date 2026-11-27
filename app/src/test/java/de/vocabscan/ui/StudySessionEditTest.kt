package de.vocabscan.ui

import de.vocabscan.data.Vocabulary
import de.vocabscan.learn.ReviewAttempt
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyCard
import de.vocabscan.learn.StudyDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class StudySessionEditTest {
    @Test fun correctionUpdatesAllCardsAndHistoryWithoutAdvancingRound() {
        val original = Vocabulary("word", "deck", "hause", "Haus")
        val other = Vocabulary("other", "deck", "tree", "Baum")
        val first = StudyCard(original, "en", "Liste", StudyDirection.FOREIGN_TO_GERMAN)
        val reverse = StudyCard(original, "en", "Liste", StudyDirection.GERMAN_TO_FOREIGN)
        val unrelated = StudyCard(other, "en", "Liste", StudyDirection.FOREIGN_TO_GERMAN)
        val session = StudySession(StudyMode.INPUT, listOf(first, unrelated, reverse), 4,
            completed = 1, attempt = 3, startedAt = 123L,
            results = listOf(ReviewAttempt(reverse, ReviewRating.AGAIN, 456L)))

        val corrected = session.withUpdatedWord(original.copy(term = "house"))

        assertEquals(listOf("house", "tree", "house"), corrected.queue.map { it.word.term })
        assertEquals("house", corrected.results.single().card.word.term)
        assertEquals(session.completed, corrected.completed)
        assertEquals(session.attempt, corrected.attempt)
        assertEquals(session.startedAt, corrected.startedAt)
        assertEquals(session.queue.map { it.key }, corrected.queue.map { it.key })
    }
}
