package de.vocabscan.learn

data class ReviewAttempt(val card: StudyCard, val rating: ReviewRating, val nextDueAt: Long)
data class StudySummary(
    val planned: Int,
    val completed: Int,
    val attempts: List<ReviewAttempt>,
    val durationMillis: Long,
) {
    val firstAttempts get() = attempts.distinctBy { it.card.key }
    val wordCount get() = firstAttempts.map { it.card.word.id }.distinct().size
    val repeats get() = attempts.size - firstAttempts.size
    val nextDueAt get() = attempts.groupBy { it.card.key }.values.map { it.last().nextDueAt }.minOrNull()
}
