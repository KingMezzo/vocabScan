package de.vocabscan.learn

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

// Historical names; persisted ordinals describe card sides, independently of their languages.
// 0 = term → translation/explanation, 1 = translation/explanation → term. Never reorder.
enum class StudyDirection { FOREIGN_TO_GERMAN, GERMAN_TO_FOREIGN }
enum class ReviewRating(val grade: Int, val label: String) {
    AGAIN(1, "Nochmal"), HARD(2, "Schwer"), GOOD(3, "Gut"), EASY(4, "Leicht"),
}

data class MemoryState(
    val stability: Double,
    val difficulty: Double,
    val lastReviewedAt: Long,
    val reviews: Int,
    val lapses: Int,
)

data class ScheduledReview(val memory: MemoryState, val dueAt: Long)

/** FSRS-6 with the published default weights and 90% target retention. Times are UTC epoch millis. */
object FsrsScheduler {
    const val DAY_MS = 86_400_000L
    private val w = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    private const val RETENTION = 0.9
    private val factor = RETENTION.pow(-1.0 / w[20]) - 1.0

    fun review(previous: MemoryState?, rating: ReviewRating, now: Long): ScheduledReview {
        val grade = rating.grade
        val difficulty: Double
        val stability: Double
        if (previous == null) {
            difficulty = initialDifficulty(grade)
            stability = w[grade - 1]
        } else {
            val oldD = previous.difficulty.coerceIn(1.0, 10.0)
            val oldS = previous.stability.coerceIn(0.01, 36_500.0)
            val delta = -w[6] * (grade - 3)
            val damped = oldD + delta * (10.0 - oldD) / 9.0
            difficulty = (w[7] * initialDifficulty(4) + (1.0 - w[7]) * damped).coerceIn(1.0, 10.0)

            val elapsedDays = max(0.0, (now - previous.lastReviewedAt).toDouble() / DAY_MS)
            val recall = (1.0 + factor * elapsedDays / oldS).pow(-w[20])
            stability = when {
                elapsedDays < 1.0 -> {
                    val next = oldS * exp(w[17] * (grade - 3 + w[18])) * oldS.pow(-w[19])
                    if (rating == ReviewRating.AGAIN) next else max(oldS, next)
                }
                rating == ReviewRating.AGAIN ->
                    w[11] * oldD.pow(-w[12]) * ((oldS + 1).pow(w[13]) - 1) * exp(w[14] * (1 - recall))
                else -> {
                    val modifier = when (rating) {
                        ReviewRating.HARD -> w[15]
                        ReviewRating.EASY -> w[16]
                        else -> 1.0
                    }
                    oldS * (1 + exp(w[8]) * (11 - oldD) * oldS.pow(-w[9]) *
                        (exp(w[10] * (1 - recall)) - 1) * modifier)
                }
            }.coerceIn(0.01, 36_500.0)
        }

        val dueAt = if (rating == ReviewRating.AGAIN) now + 60_000L else {
            val days = (stability / factor * (RETENTION.pow(-1.0 / w[20]) - 1.0))
                .coerceIn(1.0, 36_500.0).roundToLong()
            now + days * DAY_MS
        }
        return ScheduledReview(
            memory = MemoryState(
                stability = stability,
                difficulty = difficulty,
                lastReviewedAt = now,
                reviews = (previous?.reviews ?: 0) + 1,
                lapses = (previous?.lapses ?: 0) + if (rating == ReviewRating.AGAIN) 1 else 0,
            ),
            dueAt = dueAt,
        )
    }

    fun isDue(dueAt: Long?, now: Long) = dueAt == null || dueAt <= now

    private fun initialDifficulty(grade: Int) =
        (w[4] - exp(w[5] * (grade - 1)) + 1).coerceIn(1.0, 10.0)
}
