package de.vocabscan.learn

import de.vocabscan.data.DeckType
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyReviews(val date: LocalDate, val count: Int)
data class DeckLearningStats(
    val id: String,
    val name: String,
    val language: String,
    val due: Int,
    val studied: Int,
    val total: Int,
    val archived: Boolean = false,
    val type: DeckType = DeckType.SPRACHE,
)
data class LearningStats(
    val today: Int,
    val due: Int,
    val streak: Int,
    val totalReviews: Int,
    val studiedDirections: Int,
    val totalDirections: Int,
    val goodOrEasyPercent: Int?,
    val week: List<DailyReviews>,
    val decks: List<DeckLearningStats>,
)

object LearningStatsCalculator {
    fun calculate(
        decks: List<DeckWithVocabulary>, progress: List<StudyProgress>, logs: List<ReviewLog>,
        now: Long, zone: ZoneId,
    ): LearningStats {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val progressByKey = progress.associateBy { it.wordId to it.direction }
        val deckStats = decks.map { item ->
            val keys = item.words.flatMap { word -> listOf(word.id to 0, word.id to 1) }
            DeckLearningStats(
                item.deck.id, item.deck.name, item.deck.language,
                due = if (item.deck.isArchived) 0 else keys.count { key -> FsrsScheduler.isDue(progressByKey[key]?.dueAt, now) },
                studied = keys.count { key -> progressByKey.containsKey(key) },
                total = keys.size,
                archived = item.deck.isArchived,
                type = item.deck.type,
            )
        }
        val dated = logs.map { it to Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
        val counts = dated.groupingBy { it.second }.eachCount()
        val week = (6L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            DailyReviews(date, counts[date] ?: 0)
        }
        val activeDays = counts.keys
        var cursor = if (today in activeDays) today else today.minusDays(1)
        var streak = 0
        while (cursor in activeDays) { streak++; cursor = cursor.minusDays(1) }
        val recent = dated.filter { it.second >= today.minusDays(29) }
        val goodPercent = if (recent.isEmpty()) null else
            (100.0 * recent.count { it.first.rating >= ReviewRating.GOOD.grade } / recent.size).toInt()
        return LearningStats(
            today = counts[today] ?: 0,
            due = deckStats.sumOf { it.due },
            streak = streak,
            totalReviews = logs.size,
            studiedDirections = deckStats.sumOf { it.studied },
            totalDirections = deckStats.sumOf { it.total },
            goodOrEasyPercent = goodPercent,
            week = week,
            decks = deckStats,
        )
    }
}
