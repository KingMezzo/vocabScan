package de.vocabscan.learn

import de.vocabscan.data.DeckType
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.StudyProgress
import de.vocabscan.data.Vocabulary
import kotlin.random.Random

enum class DirectionChoice(val label: String) {
    FOREIGN_TO_GERMAN("Vorwärts"), GERMAN_TO_FOREIGN("Umgekehrt"), MIXED("Gemischt");
    val directions: List<StudyDirection> get() = when (this) {
        FOREIGN_TO_GERMAN -> listOf(StudyDirection.FOREIGN_TO_GERMAN)
        GERMAN_TO_FOREIGN -> listOf(StudyDirection.GERMAN_TO_FOREIGN)
        MIXED -> StudyDirection.entries
    }
}
enum class StudyFilter(val label: String) { DUE("Fällig"), WEAK("Schwach"), NEW("Noch neu"), ALL("Alle üben") }
data class StudyOptions(
    val deckId: String? = null,
    val direction: DirectionChoice = DirectionChoice.MIXED,
    val filter: StudyFilter = StudyFilter.DUE,
    val chapter: String? = null,
    val tag: String? = null,
    val limit: Int = 20,
    val favoritesOnly: Boolean = false,
    val scanGroupId: String? = null,
)
// Direction ordinals remain stable: 0 is term → translation, 1 is the reverse.
data class StudyCard(val word: Vocabulary, val language: String, val deckName: String, val direction: StudyDirection,
    val type: DeckType = DeckType.SPRACHE, val targetLanguage: String = if (type.isFlashcards) "" else "de") {
    val isFlashcard get() = type.isFlashcards
    val key get() = "${word.id}:${direction.ordinal}"
    val promptLanguage get() = if (direction == StudyDirection.FOREIGN_TO_GERMAN) language else targetLanguage
    val answerLanguage get() = if (direction == StudyDirection.FOREIGN_TO_GERMAN) targetLanguage else language
    val directionLabel get() = Deck(word.deckId, deckName, language, type = type, targetLanguage = targetLanguage)
        .directionLabel(direction == StudyDirection.GERMAN_TO_FOREIGN)
}
data class StudyData(val decks: List<DeckWithVocabulary>, val progress: List<StudyProgress>)

fun wordTags(raw: String): List<String> = raw.split(',', ';', '\n')
    .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(java.util.Locale.ROOT) }

object StudyPlanner {
    fun candidates(data: StudyData, options: StudyOptions, now: Long): List<StudyCard> {
        val states = data.progress.associateBy { it.wordId to it.direction }
        return data.decks.filter {
            (options.deckId == null && !it.deck.isArchived) || it.deck.id == options.deckId
        }.flatMap { deck ->
            deck.words.filter { word ->
                (!options.favoritesOnly || word.isFavorite) &&
                    (options.scanGroupId == null || word.scanGroupId == options.scanGroupId) &&
                    (options.chapter == null || word.chapter.equals(options.chapter, ignoreCase = true)) &&
                    (options.tag == null || wordTags(word.tags).any { it.equals(options.tag, ignoreCase = true) })
            }.flatMap { word -> options.direction.directions.map { StudyCard(word, deck.deck.language, deck.deck.name, it, deck.deck.type, deck.deck.targetLanguage) } }
        }.filter { card ->
            val state = states[card.word.id to card.direction.ordinal]
            when (options.filter) {
                StudyFilter.DUE -> FsrsScheduler.isDue(state?.dueAt, now)
                StudyFilter.NEW -> state == null
                StudyFilter.WEAK -> state != null && (state.stability < 7.0 || state.difficulty >= 7.0)
                StudyFilter.ALL -> true
            }
        }.sortedWith(compareBy<StudyCard> {
            // Previously studied, overdue cards come before new cards.
            states[it.word.id to it.direction.ordinal]?.dueAt ?: Long.MAX_VALUE
        }.thenBy { it.word.createdAt }.thenBy { it.key })
    }

    fun build(data: StudyData, options: StudyOptions, now: Long, random: Random = Random.Default): List<StudyCard> =
        candidates(data, options, now).let { candidates ->
            if (options.limit == 0) candidates else candidates.take(options.limit.coerceAtLeast(1))
        }.shuffled(random)
}
