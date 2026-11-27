package de.vocabscan.learn

import de.vocabscan.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class FlashcardLearningTest {
    private val now = 1_700_000_000_000L
    private val word = Vocabulary("w", "cards", "Vertrag", "Zwei übereinstimmende Willenserklärungen.",
        note = "Angebot und Annahme", tags = "Recht", isFavorite = true)
    private val decks = listOf(
        DeckWithVocabulary(Deck("cards", "Recht", "", type = DeckType.LERNKARTEN), listOf(word)),
        DeckWithVocabulary(Deck("en", "Englisch", "en"), listOf(Vocabulary("e", "en", "law", "Recht"))))

    @Test fun mixedRoundKeepsTypesAndBothDirections() {
        val cards = StudyPlanner.candidates(StudyData(decks, emptyList()), StudyOptions(), now)
        assertEquals(4, cards.size)
        assertEquals(2, cards.count { it.isFlashcard })
        assertTrue(cards.filter { it.isFlashcard }.all { it.language.isEmpty() })
        assertEquals(StudyDirection.entries.toSet(), cards.filter { it.isFlashcard }.map { it.direction }.toSet())
    }

    @Test fun ratingUsesSameIntervalsAndOnlyRemovesReviewedDirectionFromDuePool() {
        val good = FsrsScheduler.review(null, ReviewRating.GOOD, now)
        val state = StudyProgress(word.id, 0, good.memory.stability, good.memory.difficulty,
            now, good.dueAt, good.memory.reviews, good.memory.lapses)
        val data = StudyData(decks, listOf(state))
        val options = StudyOptions(deckId = "cards", favoritesOnly = true, tag = "Recht")
        assertEquals(listOf("w:1"), StudyPlanner.candidates(data, options, now).map { it.key })
        assertEquals(listOf("w:1"), StudyPlanner.candidates(data, options.copy(filter = StudyFilter.NEW), now).map { it.key })
        assertEquals(2, StudyPlanner.candidates(data, options.copy(filter = StudyFilter.ALL), now).size)
        val again = FsrsScheduler.review(good.memory, ReviewRating.AGAIN, now + 86_400_000)
        assertTrue(again.memory.lapses > good.memory.lapses)
        assertTrue(again.dueAt < now + 2 * 86_400_000)
    }

    @Test fun reverseAndArchivedFlashcardsFollowExistingFilters() {
        val data = StudyData(decks, emptyList())
        val reverse = StudyOptions(deckId = "cards", direction = DirectionChoice.GERMAN_TO_FOREIGN)
        assertEquals("w:1", StudyPlanner.build(data, reverse, now).single().key)
        val archived = data.copy(decks = decks.map { it.copy(deck = it.deck.copy(isArchived = it.deck.isFlashcards)) })
        assertFalse(StudyPlanner.candidates(archived, StudyOptions(), now).any { it.isFlashcard })
        assertTrue(StudyPlanner.build(archived, reverse, now).single().isFlashcard)
    }

    @Test fun dailyGoalStatisticsCountReviewsAcrossBothTypes() {
        val stats = LearningStatsCalculator.calculate(decks, emptyList(), listOf(
            ReviewLog(wordId = "w", direction = 0, rating = 3, reviewedAt = now),
            ReviewLog(wordId = "e", direction = 0, rating = 3, reviewedAt = now)), now, ZoneId.of("Europe/Berlin"))
        assertEquals(2, stats.today)
        assertEquals(2, stats.totalReviews)
        assertEquals(4, stats.totalDirections)
        assertEquals(DeckType.LERNKARTEN, stats.decks.first().type)
    }
}
