package de.vocabscan.data

import androidx.room.withTransaction
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.ScanSource
import de.vocabscan.learn.FsrsScheduler
import de.vocabscan.learn.MemoryState
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyDirection
import de.vocabscan.learn.StudyData
import de.vocabscan.learn.wordTags
import de.vocabscan.learn.AnswerChecker
import java.util.Locale
import java.util.UUID

data class FullSnapshot(
    val decks: List<Deck>, val words: List<Vocabulary>, val progress: List<StudyProgress>,
    val logs: List<ReviewLog>, val pages: List<ScanPage>,
    val folders: List<Folder> = emptyList(),
)

data class DeletedWord(val word: Vocabulary, val progress: List<StudyProgress>, val logs: List<ReviewLog>)
data class DeletedDeck(val deck: Deck, val words: List<Vocabulary>, val progress: List<StudyProgress>,
    val logs: List<ReviewLog>, val pages: List<ScanPage>)
data class TransferResult(val transferred: Int, val skippedDuplicates: Int)

class VocabRepository(private val database: VocabDatabase) {
    private val dao = database.vocabDao()
    val decks = dao.observeDecks()
    val folders = dao.observeFolders()
    val progress = dao.observeProgress()
    val reviewLogs = dao.observeReviewLogs()
    val scanPages = dao.observeScanPages()
    fun reviewsSince(since: Long) = dao.observeReviewsSince(since)
    suspend fun dueCount(now: Long) = dao.countDue(now, 0) + dao.countDue(now, 1)
    suspend fun progressFor(wordId: String, direction: StudyDirection) = dao.progressFor(wordId, direction.ordinal)
    suspend fun studyData(): StudyData = database.withTransaction {
        StudyData(dao.allDecksWithWords(), dao.allProgress())
    }

    suspend fun fullSnapshot(): FullSnapshot = database.withTransaction {
        val decks = dao.allDecks()
        FullSnapshot(decks, decks.flatMap { dao.wordsForDeck(it.id) }, dao.allProgress(), dao.allLogs(), dao.allScanPages(), dao.allFolders())
    }

    suspend fun restoreFull(snapshot: FullSnapshot) = database.withTransaction {
        dao.deleteAllDecks()
        dao.deleteAllFolders()
        snapshot.folders.forEach { dao.insertFolder(it) }
        snapshot.decks.forEach { deck ->
            val folder = deck.folderId?.let { dao.folderById(it) } ?: folderForName(deck.folder)
            dao.insertDeck(deck.copy(folderId = folder?.id, folder = folder?.name.orEmpty()))
        }
        dao.insertWords(snapshot.words)
        dao.insertProgress(snapshot.progress)
        dao.insertLogs(snapshot.logs)
        dao.insertScanPages(snapshot.pages)
    }


    suspend fun exportCsv(deckIds: Set<String>? = null): String = database.withTransaction {
        require(deckIds == null || deckIds.isNotEmpty()) { "Wähle mindestens eine Liste aus." }
        val decks = dao.allDecks().filter { deckIds == null || it.id in deckIds }
        require(deckIds == null || decks.size == deckIds.size) { "Eine ausgewählte Liste ist nicht mehr vorhanden." }
        CsvCodec.encode(decks.flatMap { deck ->
            dao.wordsForDeck(deck.id).ifEmpty { listOf(null) }.map { word ->
                CsvRow(deck.id, deck.name, deck.language,
                    word?.term.orEmpty(), word?.translation.orEmpty(), word?.note.orEmpty(),
                    word?.termAlternatives.orEmpty(), word?.translationAlternatives.orEmpty(),
                    word?.chapter.orEmpty(), word?.tags.orEmpty(), word?.isFavorite ?: false,
                    word?.scanGroupId.orEmpty(), deck.folder, deck.isArchived, deck.type, deck.targetLanguage)
            }
        })
    }

    suspend fun importCsv(csv: String): Pair<Int, Int> {
        val rows = CsvCodec.decode(csv)
        return database.withTransaction {
            val existing = dao.allDecks().associateBy { it.id }.toMutableMap()
            val sourceIds = mutableMapOf<String, String>()
            val sourceLanguages = mutableMapOf<String, Triple<DeckType, String, String>>()
            var newDecks = 0
            var newWords = 0
            for (row in rows) {
                val sourceKey = row.deckId.ifBlank { "${row.type}\u0000${row.language}\u0000${row.targetLanguage}\u0000${row.deck}" }
                val languages = Triple(row.type, row.language, row.targetLanguage)
                require(sourceLanguages.putIfAbsent(sourceKey, languages) in listOf(null, languages)) {
                    "Typ- oder Sprachkonflikt in CSV-Datei"
                }
                val targetId = sourceIds[sourceKey] ?: run {
                    val match = if (row.deckId.isNotBlank()) existing[row.deckId]
                        else existing.values.firstOrNull { it.name == row.deck && it.language == row.language && it.targetLanguage == row.targetLanguage && it.type == row.type }
                    require(match == null || (match.language == row.language && match.targetLanguage == row.targetLanguage && match.type == row.type)) { "Typ- oder Sprachkonflikt beim Import" }
                    val target = match ?: Deck(
                        id = row.deckId.ifBlank { UUID.randomUUID().toString() },
                        name = row.deck, language = row.language, targetLanguage = row.targetLanguage, type = row.type, isArchived = row.isArchived,
                        folder = folderForName(row.folder)?.name.orEmpty(), folderId = folderForName(row.folder)?.id,
                    ).also { dao.insertDeck(it); existing[it.id] = it; newDecks++ }
                    sourceIds[sourceKey] = target.id
                    target.id
                }
                if (row.term.isNotBlank()) {
                    val key = pairKey(row.term, row.translation)
                    val already = dao.wordsForDeck(targetId).any { pairKey(it.term, it.translation) == key }
                    if (!already) {
                        dao.insertWords(listOf(Vocabulary(UUID.randomUUID().toString(), targetId,
                            row.term.trim(), row.translation.trim(), row.note.trim(),
                            termAlternatives = row.termAlternatives, translationAlternatives = row.translationAlternatives,
                            chapter = row.chapter, tags = row.tags, isFavorite = row.isFavorite,
                            scanGroupId = row.scanGroupId.ifBlank { null })))
                        newWords++
                    }
                }
            }
            newDecks to newWords
        }
    }

    suspend fun createDeck(name: String, language: StudyLanguage, folderId: String? = null, type: DeckType = DeckType.SPRACHE,
        targetLanguage: StudyLanguage = StudyLanguage.GERMAN): String = database.withTransaction {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        val folder = folderId?.let { dao.folderById(it) ?: error("Ordner nicht gefunden") }
        dao.insertDeck(Deck(id, name.trim(), if (type.isFlashcards) "" else language.code, type = type,
            targetLanguage = if (type.isFlashcards) "" else targetLanguage.code, folderId = folder?.id, folder = folder?.name.orEmpty()))
        id
    }

    suspend fun renameDeck(deck: Deck, name: String) {
        require(name.isNotBlank())
        val current = dao.deckById(deck.id) ?: error("Liste nicht gefunden")
        dao.updateDeck(current.copy(name = name.trim()))
    }

    suspend fun setDeckFolder(deckId: String, folder: String) = database.withTransaction {
        val current = dao.deckById(deckId) ?: error("Liste nicht gefunden")
        val target = folderForName(folder)
        dao.updateDeck(current.copy(folderId = target?.id, folder = target?.name.orEmpty()))
    }

    private suspend fun folderForName(value: String): Folder? {
        val name = value.trim()
        require(name.length <= 80) { "Ordnernamen dürfen höchstens 80 Zeichen enthalten." }
        if (name.isEmpty()) return null
        return dao.allFolders().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Folder(UUID.randomUUID().toString(), name).also { dao.insertFolder(it) }
    }

    suspend fun createFolder(value: String): String = database.withTransaction {
        val name = value.trim()
        require(name.isNotEmpty() && name.length <= 80)
        require(dao.allFolders().none { it.name.equals(name, ignoreCase = true) }) { "Dieser Ordner existiert bereits." }
        Folder(UUID.randomUUID().toString(), name).also { dao.insertFolder(it) }.id
    }

    suspend fun moveDeck(deckId: String, folderId: String?) = database.withTransaction {
        val current = dao.deckById(deckId) ?: error("Liste nicht gefunden")
        val target = folderId?.let { dao.folderById(it) ?: error("Ordner nicht gefunden") }
        dao.updateDeck(current.copy(folderId = target?.id, folder = target?.name.orEmpty()))
    }

    suspend fun renameFolderById(id: String, value: String) = database.withTransaction {
        val current = dao.folderById(id) ?: error("Ordner nicht gefunden")
        val name = value.trim()
        require(name.isNotEmpty() && name.length <= 80)
        require(dao.allFolders().none { it.id != id && it.name.equals(name, ignoreCase = true) })
        dao.updateFolder(current.copy(name = name))
        dao.allDecks().filter { it.folderId == id }.forEach { dao.updateDeck(it.copy(folder = name)) }
    }

    suspend fun deleteFolder(id: String) = database.withTransaction {
        val folder = dao.folderById(id) ?: return@withTransaction
        dao.allDecks().filter { it.folderId == id }.forEach { dao.updateDeck(it.copy(folderId = null, folder = "")) }
        dao.deleteFolder(folder)
    }

    // Legacy name-based operation also supports merging old folder groups.
    suspend fun renameFolder(from: String, to: String) = database.withTransaction {
        require(from.isNotBlank())
        val source = dao.allFolders().firstOrNull { it.name == from } ?: return@withTransaction
        if (to.isBlank()) deleteFolder(source.id)
        else {
            val target = dao.allFolders().firstOrNull { it.name.equals(to.trim(), ignoreCase = true) && it.id != source.id }
            if (target == null) renameFolderById(source.id, to)
            else {
                dao.allDecks().filter { it.folderId == source.id }.forEach {
                    dao.updateDeck(it.copy(folderId = target.id, folder = target.name))
                }
                dao.deleteFolder(source)
            }
        }
    }

    suspend fun setArchived(deckId: String, archived: Boolean) = database.withTransaction {
        val current = dao.deckById(deckId) ?: error("Liste nicht gefunden")
        dao.updateDeck(current.copy(isArchived = archived))
    }

    suspend fun deckSnapshot(deckId: String): DeletedDeck = database.withTransaction {
        val deck = dao.deckById(deckId) ?: error("Liste nicht gefunden")
        val words = dao.wordsForDeck(deckId)
        val ids = words.map { it.id }.toSet()
        DeletedDeck(deck, words, dao.allProgress().filter { it.wordId in ids },
            dao.allLogs().filter { it.wordId in ids }, dao.pagesForDeck(deckId))
    }

    suspend fun duplicateDeck(source: DeletedDeck, name: String, includeLearning: Boolean,
        copiedImages: Map<String, String>): String = database.withTransaction {
        require(name.isNotBlank() && source.pages.all { copiedImages[it.id]?.isNotBlank() == true })
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val wordIds = source.words.associate { it.id to UUID.randomUUID().toString() }
        val pageIds = source.pages.associate { it.id to UUID.randomUUID().toString() }
        val groupIds = (source.words.mapNotNull { it.scanGroupId } + source.pages.mapNotNull { it.scanGroupId })
            .distinct().associateWith { UUID.randomUUID().toString() }
        dao.insertDeck(source.deck.copy(id = id, name = name.trim(), createdAt = now, isArchived = false))
        dao.insertWords(source.words.map { word ->
            val originalSource = ScanSource.decode(word.scanSource)
            val scanSource = originalSource?.let { scan -> pageIds[scan.pageId]?.let { scan.copy(pageId = it).encode() } }
            word.copy(id = wordIds.getValue(word.id), deckId = id, createdAt = now,
                scanGroupId = groupIds[word.scanGroupId], scanSource = scanSource)
        })
        dao.insertScanPages(source.pages.map { page -> page.copy(id = pageIds.getValue(page.id), deckId = id,
            fileName = copiedImages.getValue(page.id), scanGroupId = groupIds[page.scanGroupId]) })
        if (includeLearning) {
            dao.insertProgress(source.progress.map { it.copy(wordId = wordIds.getValue(it.wordId)) })
            dao.insertLogs(source.logs.map { it.copy(id = 0, wordId = wordIds.getValue(it.wordId)) })
        }
        id
    }

    suspend fun mergeDecks(sourceDeckId: String, targetDeckId: String): Int = database.withTransaction {
        require(sourceDeckId != targetDeckId)
        val source = dao.deckById(sourceDeckId) ?: error("Ausgangsliste fehlt")
        val target = dao.deckById(targetDeckId) ?: error("Zielliste fehlt")
        require(source.compatibleWith(target)) { "Nur Listen mit gleichem Typ und gleichem Sprachpaar können zusammengeführt werden." }
        val words = dao.wordsForDeck(sourceDeckId)
        // Keeping word IDs also keeps every review and both scheduling directions.
        words.forEach { dao.updateWord(it.copy(deckId = targetDeckId)) }
        dao.moveScanPages(sourceDeckId, targetDeckId)
        dao.deleteDeck(source)
        words.size
    }

    suspend fun deleteDeck(deck: Deck) = dao.deleteDeck(deck)

    suspend fun takeDeletedDeck(deck: Deck): DeletedDeck = database.withTransaction {
        val words = dao.wordsForDeck(deck.id)
        val ids = words.map { it.id }.toSet()
        val deleted = DeletedDeck(deck, words, dao.allProgress().filter { it.wordId in ids },
            dao.allLogs().filter { it.wordId in ids }, dao.pagesForDeck(deck.id))
        dao.deleteDeck(deck)
        deleted
    }

    suspend fun restoreDeletedDeck(deleted: DeletedDeck) = database.withTransaction {
        val folder = deleted.deck.folderId?.let { dao.folderById(it) }
        dao.insertDeck(deleted.deck.copy(folderId = folder?.id, folder = folder?.name.orEmpty()))
        dao.insertWords(deleted.words)
        dao.insertProgress(deleted.progress)
        dao.insertLogs(deleted.logs)
        dao.insertScanPages(deleted.pages)
    }

    suspend fun saveWord(
        deckId: String, original: Vocabulary?, term: String, translation: String, note: String,
        termAlternatives: String = original?.termAlternatives.orEmpty(),
        translationAlternatives: String = original?.translationAlternatives.orEmpty(),
        chapter: String = original?.chapter.orEmpty(), tags: String = original?.tags.orEmpty(),
    ): Vocabulary {
        require(term.isNotBlank() && translation.isNotBlank())
        val word = Vocabulary(
            id = original?.id ?: UUID.randomUUID().toString(), deckId = deckId,
            term = term.trim(), translation = translation.trim(), note = note.trim(),
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            termAlternatives = termAlternatives.trim(), translationAlternatives = translationAlternatives.trim(),
            chapter = chapter.trim(), tags = wordTags(tags).joinToString(", "),
            isFavorite = original?.isFavorite ?: false,
            scanGroupId = original?.scanGroupId,
            scanSource = original?.scanSource,
        )
        if (original == null) dao.insertWords(listOf(word)) else dao.updateWord(word)
        return word
    }

    suspend fun deleteWord(word: Vocabulary) = dao.deleteWord(word)

    suspend fun setFavorite(wordId: String, favorite: Boolean) = database.withTransaction {
        val word = dao.wordById(wordId) ?: return@withTransaction
        dao.updateWord(word.copy(isFavorite = favorite))
    }

    suspend fun takeDeletedWord(word: Vocabulary): DeletedWord = database.withTransaction {
        val deleted = DeletedWord(word, dao.progressForWord(word.id), dao.logsForWord(word.id))
        dao.deleteWord(word)
        deleted
    }

    suspend fun restoreDeletedWord(deleted: DeletedWord) = database.withTransaction {
        dao.insertWords(listOf(deleted.word))
        dao.insertProgress(deleted.progress)
        dao.insertLogs(deleted.logs)
    }

    suspend fun importSharedDeck(name: String, language: String, words: List<Vocabulary>, folderId: String? = null, type: DeckType = DeckType.SPRACHE,
        targetLanguage: String = if (type.isFlashcards) "" else "de"): String = database.withTransaction {
        require(name.isNotBlank() && type.acceptsLanguages(language, targetLanguage) && words.isNotEmpty())
        val id = UUID.randomUUID().toString()
        val folder = folderId?.let { dao.folderById(it) ?: error("Ordner nicht gefunden") }
        dao.insertDeck(Deck(id, name, language, type = type, targetLanguage = targetLanguage, folderId = folder?.id, folder = folder?.name.orEmpty()))
        dao.insertWords(words.map {
            it.copy(id = UUID.randomUUID().toString(), deckId = id, createdAt = System.currentTimeMillis())
        })
        id
    }

    suspend fun importCardText(deckId: String, text: String, delimiter: Char): Int {
        val rows = CsvCodec.decodeCards(text, delimiter)
        return database.withTransaction {
            require(dao.deckById(deckId) != null) { "Liste nicht gefunden" }
            val seen = dao.wordsForDeck(deckId).map { pairKey(it.term, it.translation) }.toMutableSet()
            val words = rows.filter { seen.add(pairKey(it[0], it[1])) }.map {
                Vocabulary(UUID.randomUUID().toString(), deckId, it[0], it[1],
                    note = it.getOrElse(2) { "" }, tags = wordTags(it.getOrElse(3) { "" }).joinToString(", "))
            }
            dao.insertWords(words)
            words.size
        }
    }

    suspend fun addAlternative(wordId: String, direction: StudyDirection, answer: String) = database.withTransaction {
        val value = answer.trim()
        require(value.isNotEmpty() && value.length <= 200)
        val word = dao.wordById(wordId) ?: error("Vokabel nicht gefunden")
        val foreign = direction == StudyDirection.GERMAN_TO_FOREIGN
        val primary = if (foreign) word.term else word.translation
        val existing = if (foreign) word.termAlternatives else word.translationAlternatives
        val updated = AnswerChecker.alternatives(primary, existing + "\n" + value).drop(1).joinToString("\n")
        dao.updateWord(if (foreign) word.copy(termAlternatives = updated) else word.copy(translationAlternatives = updated))
    }

    suspend fun resetWordLearning(wordId: String) = database.withTransaction {
        dao.deleteProgressForWord(wordId)
        dao.deleteLogsForWord(wordId)
    }

    suspend fun transferWords(sourceDeckId: String, targetDeckId: String, wordIds: Set<String>, move: Boolean): TransferResult =
        database.withTransaction {
            require(sourceDeckId != targetDeckId && wordIds.isNotEmpty()) { "Wähle Wörter und eine andere Liste aus." }
            val decks = dao.allDecks().associateBy { it.id }
            val sourceDeck = decks[sourceDeckId] ?: error("Ausgangsliste nicht gefunden")
            val targetDeck = decks[targetDeckId] ?: error("Zielliste nicht gefunden")
            require(sourceDeck.compatibleWith(targetDeck)) { "Die Listen müssen denselben Typ und dasselbe Sprachpaar haben." }
            val source = dao.wordsForDeck(sourceDeckId).filter { it.id in wordIds }
            require(source.size == wordIds.size) { "Ein ausgewähltes Wort ist nicht mehr in der Liste." }
            val present = dao.wordsForDeck(targetDeckId).map { pairKey(it.term, it.translation) }.toMutableSet()
            val transferable = source.filter { present.add(pairKey(it.term, it.translation)) }
            if (move) {
                transferable.forEach { word ->
                    dao.deleteProgressForWord(word.id)
                    dao.deleteLogsForWord(word.id)
                    dao.updateWord(word.copy(deckId = targetDeckId))
                }
            } else {
                dao.insertWords(transferable.map { it.copy(id = UUID.randomUUID().toString(), deckId = targetDeckId) })
            }
            TransferResult(transferable.size, source.size - transferable.size)
        }

    suspend fun recordReview(wordId: String, direction: StudyDirection, rating: ReviewRating, now: Long) = database.withTransaction {
        val previous = dao.progressFor(wordId, direction.ordinal)?.let {
            MemoryState(it.stability, it.difficulty, it.lastReviewedAt, it.reviews, it.lapses)
        }
        val scheduled = FsrsScheduler.review(previous, rating, now)
        dao.upsertProgress(StudyProgress(
            wordId = wordId, direction = direction.ordinal,
            stability = scheduled.memory.stability, difficulty = scheduled.memory.difficulty,
            lastReviewedAt = scheduled.memory.lastReviewedAt, dueAt = scheduled.dueAt,
            reviews = scheduled.memory.reviews, lapses = scheduled.memory.lapses,
        ))
        dao.insertLog(ReviewLog(wordId = wordId, direction = direction.ordinal, rating = rating.grade, reviewedAt = now))
        scheduled
    }

    suspend fun importPairs(deckId: String, pairs: List<ImportPair>, scanFiles: List<String> = emptyList(),
        dimensions: List<Pair<Int, Int>> = emptyList()): Int = database.withTransaction {
        require(pairs.isNotEmpty() && pairs.all { it.term.isNotBlank() && it.translation.isNotBlank() })
        val scanGroupId = if (scanFiles.isEmpty()) null else UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val pages = scanFiles.map { ScanPage(UUID.randomUUID().toString(), deckId, it, importedAt, scanGroupId) }
        val seen = dao.wordsForDeck(deckId).map { pairKey(it.term, it.translation) }.toMutableSet()
        val words = pairs.filter { seen.add(pairKey(it.term, it.translation)) }.map { pair ->
            val page = pages.getOrNull(pair.page)
            val size = dimensions.getOrNull(pair.page)
            val source = if (page != null && size != null && size.first > 0 && size.second > 0)
                ScanSource(page.id, size.first, size.second, pair.termBounds, pair.translationBounds).encode()
                else null
            Vocabulary(UUID.randomUUID().toString(), deckId, pair.term.trim(), pair.translation.trim(),
                createdAt = importedAt, scanGroupId = scanGroupId, scanSource = source)
        }
        dao.insertWords(words)
        dao.insertScanPages(pages)
        words.size
    }
}

fun pairKey(term: String, translation: String): String =
    "${term.trim().lowercase(Locale.ROOT)}\u0000${translation.trim().lowercase(Locale.ROOT)}"
