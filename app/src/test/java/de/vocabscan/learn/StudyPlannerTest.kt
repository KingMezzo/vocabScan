package de.vocabscan.learn

import de.vocabscan.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StudyPlannerTest {
    private val now = 1_700_000_000_000L
    private val apple = Vocabulary("a", "en", "apple", "Apfel", chapter = "Unit 1", tags = "Schule, Obst")
    private val school = Vocabulary("b", "fr", "école", "Schule", chapter = "Unité 2", tags = "Schule")
    private val progress = listOf(
        StudyProgress("a", 0, 2.0, 8.0, now - 100, now - 10, 2, 1),
        StudyProgress("b", 1, 20.0, 3.0, now - 100, now + 10_000, 5, 0),
    )
    private val data = StudyData(listOf(
        DeckWithVocabulary(Deck("en", "English", "en"), listOf(apple)),
        DeckWithVocabulary(Deck("fr", "French", "fr"), listOf(school)),
    ), progress)

    @Test fun mixedRoundIncludesBothLanguagesAndKeepsDirectionProgressSeparate() {
        val cards = StudyPlanner.build(data, StudyOptions(), now, Random(1))
        assertEquals(3, cards.size)
        assertEquals(setOf("en", "fr"), cards.map { it.language }.toSet())
        assertTrue(cards.any { it.word.id == "b" && it.direction == StudyDirection.FOREIGN_TO_GERMAN })
        assertFalse(cards.any { it.word.id == "b" && it.direction == StudyDirection.GERMAN_TO_FOREIGN })
        assertEquals("a:0", StudyPlanner.build(data, StudyOptions(limit = 1), now).single().key)
    }

    @Test fun combinesListChapterTagAndLearningFilter() {
        assertEquals(2, StudyPlanner.candidates(data, StudyOptions(filter = StudyFilter.NEW), now).size)
        assertEquals(listOf("a:0"), StudyPlanner.candidates(data, StudyOptions(filter = StudyFilter.WEAK), now).map { it.key })
        val options = StudyOptions(deckId = "en", chapter = "Unit 1", tag = "Obst", filter = StudyFilter.NEW)
        assertEquals(listOf("a:1"), StudyPlanner.candidates(data, options, now).map { it.key })
        assertTrue(StudyPlanner.candidates(data, options.copy(tag = "Unbekannt"), now).isEmpty())
    }

    @Test fun archiveIsExcludedFromAutomaticRoundsButCanBePracticedExplicitly() {
        val archived = data.copy(decks = data.decks.map { item ->
            if (item.deck.id == "en") item.copy(deck = item.deck.copy(isArchived = true)) else item
        })
        val automatic = StudyPlanner.candidates(archived, StudyOptions(filter = StudyFilter.ALL), now)
        assertTrue(automatic.all { it.word.deckId == "fr" })
        val explicit = StudyPlanner.candidates(archived, StudyOptions(deckId = "en", filter = StudyFilter.ALL), now)
        assertEquals(2, explicit.size)
        assertTrue(explicit.all { it.word.deckId == "en" })
    }

    @Test fun allCardsInOneListAreNotCappedAtFortyOrOneHundred() {
        val words = (1..120).map { index -> Vocabulary("word-$index", "en", "word $index", "Wort $index") }
        val many = StudyData(listOf(
            DeckWithVocabulary(Deck("en", "English", "en"), words),
            DeckWithVocabulary(Deck("fr", "French", "fr"), listOf(school)),
        ), emptyList())
        val options = StudyOptions(deckId = "en", direction = DirectionChoice.FOREIGN_TO_GERMAN,
            filter = StudyFilter.ALL, limit = 0)
        val cards = StudyPlanner.build(many, options, now, Random(1))
        assertEquals(120, cards.size)
        assertEquals(120, cards.map { it.word.id }.toSet().size)
        assertTrue(cards.all { it.word.deckId == "en" })
        assertEquals(40, StudyPlanner.build(many, options.copy(limit = 40), now).size)
    }

    @Test fun scanGroupRoundContainsOnlyWordsFromThatImport() {
        val grouped = StudyData(listOf(DeckWithVocabulary(Deck("en", "English", "en"), listOf(
            apple.copy(scanGroupId = "scan-one"),
            apple.copy(id = "other", term = "pear", scanGroupId = "scan-two"),
            apple.copy(id = "manual", term = "plum", scanGroupId = null),
        ))), emptyList())
        val cards = StudyPlanner.build(grouped, StudyOptions(deckId = "en", direction = DirectionChoice.FOREIGN_TO_GERMAN,
            filter = StudyFilter.ALL, limit = 0, scanGroupId = "scan-one"), now)
        assertEquals(listOf("a"), cards.map { it.word.id })
    }

    @Test fun summaryKeepsFirstDifficultyAndUsesLatestNextReview() {
        val a = StudyCard(apple, "en", "English", StudyDirection.FOREIGN_TO_GERMAN)
        val b = StudyCard(school, "fr", "French", StudyDirection.GERMAN_TO_FOREIGN)
        val summary = StudySummary(2, 2, listOf(
            ReviewAttempt(a, ReviewRating.AGAIN, now + 60_000),
            ReviewAttempt(b, ReviewRating.HARD, now + 100_000),
            ReviewAttempt(a, ReviewRating.GOOD, now + 200_000),
        ), 120_000)
        assertEquals(2, summary.wordCount)
        assertEquals(1, summary.repeats)
        assertEquals(ReviewRating.AGAIN, summary.firstAttempts.first().rating)
        assertEquals(now + 100_000, summary.nextDueAt)
    }
}
