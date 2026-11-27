package de.vocabscan.learn

import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyProgress

data class DirectionReviewSummary(
    val direction: StudyDirection,
    val reviews: Int,
    val again: Int,
    val errorPercent: Int?,
    val nextDueAt: Long?,
    val lastReviewedAt: Long?,
)

data class WordReviewSummary(
    val reviews: Int,
    val again: Int,
    val errorPercent: Int?,
    val nextDueAt: Long?,
    val directions: List<DirectionReviewSummary>,
)

object WordReviewSummaryCalculator {
    fun calculate(wordId: String, progress: List<StudyProgress>, logs: List<ReviewLog>): WordReviewSummary {
        val wordLogs = logs.filter { it.wordId == wordId }
        val wordProgress = progress.filter { it.wordId == wordId }.associateBy { it.direction }
        fun percent(again: Int, total: Int) = if (total == 0) null else (again * 100.0 / total).toInt()
        val directions = StudyDirection.entries.map { direction ->
            val attempts = wordLogs.filter { it.direction == direction.ordinal }
            val again = attempts.count { it.rating == ReviewRating.AGAIN.grade }
            val state = wordProgress[direction.ordinal]
            DirectionReviewSummary(direction, attempts.size, again, percent(again, attempts.size),
                state?.dueAt, state?.lastReviewedAt)
        }
        val again = wordLogs.count { it.rating == ReviewRating.AGAIN.grade }
        return WordReviewSummary(wordLogs.size, again, percent(again, wordLogs.size),
            wordProgress.values.minOfOrNull { it.dueAt }, directions)
    }
}
