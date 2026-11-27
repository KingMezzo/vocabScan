package de.vocabscan.learn

import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyProgress
import org.junit.Assert.*
import org.junit.Test

class WordReviewSummaryTest {
    @Test fun `uses ratings for error share and independent due dates`() {
        val logs = listOf(
            ReviewLog(wordId = "w", direction = 0, rating = 1, reviewedAt = 100),
            ReviewLog(wordId = "w", direction = 0, rating = 3, reviewedAt = 200),
            ReviewLog(wordId = "w", direction = 1, rating = 2, reviewedAt = 300),
            ReviewLog(wordId = "other", direction = 0, rating = 1, reviewedAt = 400),
        )
        val progress = listOf(
            StudyProgress("w", 0, 1.0, 5.0, 200, 1_000, 2, 1),
            StudyProgress("w", 1, 1.0, 5.0, 300, 2_000, 1, 0),
        )
        val result = WordReviewSummaryCalculator.calculate("w", progress, logs)
        assertEquals(3, result.reviews)
        assertEquals(33, result.errorPercent)
        assertEquals(1_000L, result.nextDueAt)
        assertEquals(50, result.directions[0].errorPercent)
        assertEquals(0, result.directions[1].errorPercent)
    }

    @Test fun `new word has no artificial zero error rate or due date`() {
        val result = WordReviewSummaryCalculator.calculate("new", emptyList(), emptyList())
        assertNull(result.errorPercent)
        assertNull(result.nextDueAt)
        assertEquals(0, result.reviews)
    }
}
