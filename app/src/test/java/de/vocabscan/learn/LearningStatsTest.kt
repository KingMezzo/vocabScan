package de.vocabscan.learn

import de.vocabscan.data.Deck
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyProgress
import de.vocabscan.data.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningStatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 11, 2)
    private val now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun countsDirectionsDueDatesAndRatings() {
        val decks = listOf(DeckWithVocabulary(
            Deck("deck", "Unit 1", "en"),
            listOf(Vocabulary("a", "deck", "apple", "Apfel"), Vocabulary("b", "deck", "book", "Buch")),
        ))
        val progress = listOf(StudyProgress("a", 0, 2.0, 5.0, now, now + 86_400_000, 1, 0))
        val logs = listOf(
            ReviewLog(wordId = "a", direction = 0, rating = 3, reviewedAt = now),
            ReviewLog(wordId = "a", direction = 0, rating = 1, reviewedAt = now - 86_400_000),
        )
        val result = LearningStatsCalculator.calculate(decks, progress, logs, now, zone)
        assertEquals(1, result.today)
        assertEquals(3, result.due)
        assertEquals(2, result.streak)
        assertEquals(2, result.totalReviews)
        assertEquals(1, result.studiedDirections)
        assertEquals(4, result.totalDirections)
        assertEquals(50, result.goodOrEasyPercent)
        assertEquals(listOf(1, 1), result.week.takeLast(2).map { it.count })
        assertEquals(3, result.decks.single().due)
    }

    @Test fun emptyLearningHistoryHasNoSuccessPercentage() {
        val result = LearningStatsCalculator.calculate(emptyList(), emptyList(), emptyList(), now, zone)
        assertEquals(0, result.today)
        assertEquals(0, result.streak)
        assertEquals(null, result.goodOrEasyPercent)
    }
}
