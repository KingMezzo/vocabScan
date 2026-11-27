package de.vocabscan.data

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.vocabscan.learn.StudyDirection

enum class DeckType { SPRACHE, LERNKARTEN;
    val isFlashcards get() = this == LERNKARTEN
    fun acceptsLanguage(language: String) = if (isFlashcards) language.isEmpty() else StudyLanguage.entries.any { it.code == language }
    fun acceptsLanguages(language: String, targetLanguage: String) = acceptsLanguage(language) &&
        if (isFlashcards) targetLanguage.isEmpty() else StudyLanguage.entries.any { it.code == targetLanguage }
}

enum class StudyLanguage(val code: String, val label: String) {
    ENGLISH("en", "Englisch"), FRENCH("fr", "Französisch"), GERMAN("de", "Deutsch"), LATIN("la", "Latein");
    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

@Entity(tableName = "folders", foreignKeys = [ForeignKey(
    entity = Folder::class, parentColumns = ["id"], childColumns = ["parentId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index("parentId"), Index(value = ["isArchived", "parentId", "name"], unique = true)])
data class Folder(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val parentId: String? = null,
)

/** Root-to-leaf path shared by navigation, archive transfers and exports. */
fun folderPath(folders: List<Folder>, id: String?): List<Folder> {
    val byId = folders.associateBy { it.id }
    val path = mutableListOf<Folder>()
    val visited = mutableSetOf<String>()
    var current = id
    while (current != null && visited.add(current)) {
        val folder = byId[current] ?: break
        path += folder
        current = folder.parentId
    }
    return path.asReversed()
}

@Entity(tableName = "decks", foreignKeys = [ForeignKey(
    entity = Folder::class, parentColumns = ["id"], childColumns = ["folderId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index("folderId")])
data class Deck(
    @PrimaryKey val id: String,
    val name: String,
    val language: String,
    val createdAt: Long = System.currentTimeMillis(),
    // Compatibility name for older CSV/ZIP readers. Membership is defined by folderId.
    @ColumnInfo(defaultValue = "''") val folder: String = "",
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val folderId: String? = null,
    @ColumnInfo(defaultValue = "'SPRACHE'") val type: DeckType = DeckType.SPRACHE,
    @ColumnInfo(defaultValue = "'de'") val targetLanguage: String = if (type.isFlashcards) "" else "de",
) {
    val isFlashcards get() = type.isFlashcards
    val sourceLabel get() = StudyLanguage.fromCode(language).label
    val targetLabel get() = StudyLanguage.fromCode(targetLanguage).label
    val subjectLabel get() = if (isFlashcards) "Lernkarten" else "$sourceLabel → $targetLabel"
    val termLabel get() = if (isFlashcards) "Begriff" else "Begriff · $sourceLabel"
    val translationLabel get() = if (isFlashcards) "Erklärung" else
        (if (language == targetLanguage) "Erklärung / Synonym" else "Übersetzung") + " · $targetLabel"
    fun directionLabel(reverse: Boolean = false): String {
        val front = if (isFlashcards || language == targetLanguage) "Begriff" else sourceLabel
        val back = if (isFlashcards) "Erklärung" else if (language == targetLanguage) "Erklärung / Synonym" else targetLabel
        return if (reverse) "$back → $front" else "$front → $back"
    }
    fun countLabel(count: Int) = "$count " + if (isFlashcards) {
        if (count == 1) "Karte" else "Karten"
    } else if (count == 1) "Vokabel" else "Vokabeln"
    fun compatibleWith(other: Deck) = type == other.type && language == other.language && targetLanguage == other.targetLanguage
}

@Entity(
    tableName = "vocabulary",
    foreignKeys = [ForeignKey(
        entity = Deck::class, parentColumns = ["id"], childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deckId")],
)
data class Vocabulary(
    @PrimaryKey val id: String,
    val deckId: String,
    val term: String,
    val translation: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val termAlternatives: String = "",
    @ColumnInfo(defaultValue = "''") val translationAlternatives: String = "",
    @ColumnInfo(defaultValue = "''") val chapter: String = "",
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    val scanGroupId: String? = null,
    val scanSource: String? = null,
)

data class DeckWithVocabulary(
    @Embedded val deck: Deck,
    @Relation(parentColumn = "id", entityColumn = "deckId") val words: List<Vocabulary>,
)

fun orderedScanGroupIds(words: List<Vocabulary>): List<String> = words
    .filter { it.scanGroupId != null }
    .groupBy { it.scanGroupId!! }
    .entries.sortedWith(compareBy<Map.Entry<String, List<Vocabulary>>> { it.value.minOf { word -> word.createdAt } }
        .thenBy { it.key })
    .map { it.key }

@Entity(
    tableName = "study_progress",
    primaryKeys = ["wordId", "direction"],
    foreignKeys = [ForeignKey(
        entity = Vocabulary::class, parentColumns = ["id"], childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("wordId")],
)
data class StudyProgress(
    val wordId: String,
    val direction: Int,
    val stability: Double,
    val difficulty: Double,
    val lastReviewedAt: Long,
    val dueAt: Long,
    val reviews: Int,
    val lapses: Int,
) {
    val studyDirection get() = StudyDirection.entries[direction]
}

@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(
        entity = Vocabulary::class, parentColumns = ["id"], childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("wordId"), Index("reviewedAt")],
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val direction: Int,
    val rating: Int,
    val reviewedAt: Long,
)

@Entity(
    tableName = "scan_pages",
    foreignKeys = [ForeignKey(entity = Deck::class, parentColumns = ["id"], childColumns = ["deckId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deckId")],
)
data class ScanPage(
    @PrimaryKey val id: String,
    val deckId: String,
    val fileName: String,
    val createdAt: Long,
    val scanGroupId: String? = null,
)
