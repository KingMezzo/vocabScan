package de.vocabscan.learn

import de.vocabscan.data.ReviewLog
import de.vocabscan.data.StudyProgress

enum class KnowledgeLevel(val label: String) {
    NEW("Neu"),
    STRUGGLING("Üben"),
    BUILDING("Im Aufbau"),
    GOOD("Gut"),
    STRONG("Sicher"),
}

/** The color reflects the latest answers in one translation direction. */
object WordKnowledge {
    fun level(reviews: List<ReviewLog>, progress: StudyProgress? = null): KnowledgeLevel {
        if (reviews.isEmpty()) return if (progress == null) KnowledgeLevel.NEW else KnowledgeLevel.BUILDING
        val latest = reviews.sortedWith(compareByDescending<ReviewLog> { it.reviewedAt }.thenByDescending { it.id })
        if (latest.first().rating <= ReviewRating.HARD.grade) return KnowledgeLevel.STRUGGLING
        val successfulInARow = latest.takeWhile { it.rating >= ReviewRating.GOOD.grade }.size
        return when {
            successfulInARow >= 3 -> KnowledgeLevel.STRONG
            successfulInARow >= 2 -> KnowledgeLevel.GOOD
            else -> KnowledgeLevel.BUILDING
        }
    }
}
