package de.vocabscan.learn

import de.vocabscan.data.ReviewLog
import org.junit.Assert.assertEquals
import org.junit.Test

class WordKnowledgeTest {
    private fun log(rating: ReviewRating, time: Long) = ReviewLog(
        wordId = "word", direction = 0, rating = rating.grade, reviewedAt = time,
    )

    @Test fun recentRatingsMoveFromNewThroughGreenToPurple() {
        assertEquals(KnowledgeLevel.NEW, WordKnowledge.level(emptyList()))
        assertEquals(KnowledgeLevel.BUILDING, WordKnowledge.level(listOf(log(ReviewRating.GOOD, 1))))
        assertEquals(KnowledgeLevel.GOOD, WordKnowledge.level(listOf(log(ReviewRating.GOOD, 1), log(ReviewRating.EASY, 2))))
        assertEquals(KnowledgeLevel.STRONG, WordKnowledge.level(listOf(
            log(ReviewRating.GOOD, 1), log(ReviewRating.EASY, 2), log(ReviewRating.GOOD, 3))))
    }

    @Test fun latestHardOrAgainTurnsRedAndSuccessesRecover() {
        val prior = listOf(log(ReviewRating.GOOD, 1), log(ReviewRating.EASY, 2), log(ReviewRating.GOOD, 3))
        assertEquals(KnowledgeLevel.STRUGGLING, WordKnowledge.level(prior + log(ReviewRating.HARD, 4)))
        assertEquals(KnowledgeLevel.STRUGGLING, WordKnowledge.level(prior + log(ReviewRating.AGAIN, 4)))
        assertEquals(KnowledgeLevel.BUILDING, WordKnowledge.level(prior + log(ReviewRating.HARD, 4) + log(ReviewRating.GOOD, 5)))
        assertEquals(KnowledgeLevel.GOOD, WordKnowledge.level(prior + log(ReviewRating.HARD, 4) + log(ReviewRating.GOOD, 5) + log(ReviewRating.GOOD, 6)))
    }
}
