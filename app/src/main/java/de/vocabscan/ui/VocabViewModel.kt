package de.vocabscan.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckType
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Folder
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.VocabRepository
import de.vocabscan.data.FullBackup
import de.vocabscan.data.Vocabulary
import de.vocabscan.data.DeletedWord
import de.vocabscan.data.DeletedDeck
import de.vocabscan.data.TransferResult
import de.vocabscan.learn.*
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.OcrToken
import de.vocabscan.scan.PairParser
import de.vocabscan.scan.OcrImporter
import de.vocabscan.scan.ScanCrop
import de.vocabscan.scan.ScanEdits
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ImportDraft(
    val deckId: String, val pages: List<Uri>, val pairs: List<ImportPair>,
    val tokens: List<List<OcrToken>> = emptyList(), val manualSplit: Float? = null,
    val dimensions: List<Pair<Int, Int>> = emptyList(),
)
enum class StudyMode { INPUT, CARDS }
data class StudySession(
    val mode: StudyMode,
    val queue: List<StudyCard>,
    val total: Int,
    val completed: Int = 0,
    val attempt: Int = 0,
    val startedAt: Long = SystemClock.elapsedRealtime(),
    val results: List<ReviewAttempt> = emptyList(),
) {
    fun withUpdatedWord(word: Vocabulary): StudySession = copy(
        queue = queue.map { card -> if (card.word.id == word.id) card.copy(word = word) else card },
        results = results.map { result ->
            if (result.card.word.id == word.id) result.copy(card = result.card.copy(word = word)) else result
        },
    )
}
data class EditorState(
    val canUndoImport: Boolean = false,
    val busy: Boolean = false,
    val draft: ImportDraft? = null,
    val session: StudySession? = null,
    val summary: StudySummary? = null,
    val message: String? = null,
    val deletion: DeletionNotice? = null,
    val archiveChange: ArchiveChangeNotice? = null,
)
data class DeletionNotice(val token: Long, val label: String)
data class ArchiveChangeNotice(val token: Long, val deckId: String, val archived: Boolean)
data class LibraryContents(
    val decks: List<DeckWithVocabulary> = emptyList(), val folders: List<Folder> = emptyList(),
    val loading: Boolean = true, val failed: Boolean = false,
)

class VocabViewModel(
    private val repository: VocabRepository,
    private val importer: OcrImporter,
    private val savedState: SavedStateHandle,
    private val context: Context,
) : ViewModel() {
    private val libraryRetry = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val libraryContents = libraryRetry.flatMapLatest {
        combine(repository.decks, repository.folders) { decks, folders ->
            LibraryContents(decks, folders, loading = false)
        }.onStart { emit(LibraryContents()) }
            .catch { emit(LibraryContents(loading = false, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryContents())
    fun retryLibrary() { libraryRetry.value++ }
    val decks = repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = repository.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewLogs = repository.reviewLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scanPages = repository.scanPages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedDeckId = savedState.getStateFlow<String?>("selectedDeckId", null)
    private val mutableState = MutableStateFlow(EditorState())
    private val importHistory = ArrayDeque<ImportDraft>()
    private var lastImportEditKey: String? = null
    private val temporaryScans = mutableSetOf<Uri>()
    private var pendingDeletion: Any? = null
    private var deletionToken = 0L
    private var archiveToken = 0L
    val state = mutableState.asStateFlow()

    fun selectDeck(id: String?) { savedState["selectedDeckId"] = id }
    fun clearMessage() { mutableState.update { it.copy(message = null) } }
    fun showMessage(message: String) { mutableState.update { it.copy(message = message) } }

    private fun operation(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showMessage("Das hat nicht geklappt. Bitte erneut versuchen; deine gespeicherten Listen bleiben erhalten.") }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun createDeck(name: String, language: StudyLanguage, folderId: String? = null, type: DeckType = DeckType.SPRACHE,
        targetLanguage: StudyLanguage = StudyLanguage.GERMAN, onSaved: () -> Unit = {}) = operation {
        selectDeck(repository.createDeck(name, language, folderId, type, targetLanguage))
        onSaved()
    }
    fun createFolder(name: String, parentId: String? = null, onSaved: () -> Unit = {}) = operation {
        repository.createFolder(name, parentId)
        onSaved()
    }
    fun renameFolderById(id: String, name: String, onSaved: () -> Unit = {}) = operation {
        repository.renameFolderById(id, name)
        onSaved()
    }
    fun deleteFolder(id: String, onSaved: () -> Unit = {}) = operation {
        repository.deleteFolder(id)
        onSaved()
        showMessage("Ordner gelöscht. Alle Listen bleiben in der Bibliothek erhalten.")
    }
    fun moveDeck(deckId: String, folderId: String?, onSaved: () -> Unit = {}) = operation {
        repository.moveDeck(deckId, folderId)
        onSaved()
    }
    fun renameDeck(deck: Deck, name: String) = operation { repository.renameDeck(deck, name) }
    fun setDeckFolder(deckId: String, folder: String, onSaved: () -> Unit = {}) = operation {
        repository.setDeckFolder(deckId, folder)
        onSaved()
    }
    fun renameFolder(from: String, to: String, onSaved: () -> Unit = {}) = operation {
        repository.renameFolder(from, to)
        onSaved()
    }
    fun setArchived(deck: Deck, archived: Boolean, onSaved: () -> Unit = {}) = operation {
        repository.setArchived(deck.id, archived)
        if (archived && selectedDeckId.value == deck.id) selectDeck(null)
        onSaved()
        mutableState.update { it.copy(archiveChange = ArchiveChangeNotice(++archiveToken, deck.id, archived)) }
    }
    fun finishArchiveChange(token: Long, undo: Boolean) {
        // A timed-out snackbar must also be cleared while an import is running.
        if (!undo) mutableState.update { state ->
            if (state.archiveChange?.token == token) state.copy(archiveChange = null) else state
        } else operation {
            val change = mutableState.value.archiveChange?.takeIf { it.token == token } ?: return@operation
            repository.setArchived(change.deckId, !change.archived)
            mutableState.update { it.copy(archiveChange = null) }
        }
    }
    fun duplicateDeck(deckId: String, name: String, includeLearning: Boolean, onSaved: () -> Unit = {}) = operation {
        val source = repository.deckSnapshot(deckId)
        val copies = mutableListOf<File>()
        val id = try {
            val images = withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "scans").apply { mkdirs() }
                source.pages.associate { page ->
                    val original = File(directory, page.fileName)
                    require(original.canonicalFile.parentFile == directory.canonicalFile && original.isFile)
                    val target = File(directory, "${UUID.randomUUID()}.img")
                    copies += target
                    original.copyTo(target)
                    page.id to target.name
                }
            }
            repository.duplicateDeck(source, name, includeLearning, images)
        } catch (error: Exception) {
            copies.forEach { it.delete() }
            throw error
        }
        selectDeck(id)
        onSaved()
        showMessage("Liste dupliziert.")
    }
    fun mergeDecks(sourceId: String, targetId: String, onSaved: () -> Unit = {}) = operation {
        val count = repository.mergeDecks(sourceId, targetId)
        commitPendingDeletion()
        selectDeck(targetId)
        onSaved()
        showMessage("Listen zusammengeführt: $count Karten mit Lernverlauf übernommen.")
    }
    private fun commitPendingDeletion() {
        (pendingDeletion as? DeletedDeck)?.pages?.forEach { page ->
            File(context.filesDir, "scans/${page.fileName}").delete()
        }
        pendingDeletion = null
        mutableState.update { it.copy(deletion = null) }
    }
    fun deleteDeck(deck: Deck) = operation {
        commitPendingDeletion()
        mutableState.update { it.copy(archiveChange = it.archiveChange?.takeUnless { notice -> notice.deckId == deck.id }) }
        pendingDeletion = repository.takeDeletedDeck(deck)
        mutableState.update { it.copy(deletion = DeletionNotice(++deletionToken, "Liste gelöscht")) }
        selectDeck(null)
    }
    fun saveWord(deckId: String, original: Vocabulary?, term: String, translation: String, note: String,
        termAlternatives: String, translationAlternatives: String, chapter: String, tags: String,
        onSaved: () -> Unit = {}) = operation {
        val saved = repository.saveWord(deckId, original, term, translation, note, termAlternatives,
            translationAlternatives, chapter, tags)
        mutableState.update { state -> state.copy(session = state.session?.withUpdatedWord(saved)) }
        onSaved()
    }
    fun deleteWord(word: Vocabulary) = operation {
        commitPendingDeletion()
        pendingDeletion = repository.takeDeletedWord(word)
        mutableState.update { it.copy(deletion = DeletionNotice(++deletionToken, "Karte gelöscht")) }
    }
    fun finishDeletion(token: Long, undo: Boolean) = operation {
        if (mutableState.value.deletion?.token != token) return@operation
        if (undo) {
            when (val deleted = pendingDeletion) {
                is DeletedWord -> repository.restoreDeletedWord(deleted)
                is DeletedDeck -> repository.restoreDeletedDeck(deleted)
            }
            pendingDeletion = null
            mutableState.update { it.copy(deletion = null) }
        } else commitPendingDeletion()
    }
    fun setFavorite(word: Vocabulary) = operation { repository.setFavorite(word.id, !word.isFavorite) }
    fun transferWords(sourceDeckId: String, targetDeckId: String, wordIds: Set<String>, move: Boolean) = operation {
        val result: TransferResult = repository.transferWords(sourceDeckId, targetDeckId, wordIds, move)
        val action = if (move) "verschoben" else "kopiert"
        val duplicates = if (result.skippedDuplicates > 0) " ${result.skippedDuplicates} identische Paare übersprungen." else ""
        showMessage("${result.transferred} Karten $action.$duplicates")
    }
    fun importSharedDeck(name: String, language: String, words: List<Vocabulary>, folderId: String? = null, type: DeckType = DeckType.SPRACHE,
        targetLanguage: String = if (type.isFlashcards) "" else "de") = operation {
        val id = repository.importSharedDeck(name, language, words, folderId, type, targetLanguage)
        selectDeck(id)
        showMessage("${words.size} Karten per QR-Code importiert.")
    }
    fun importCardText(deckId: String, text: String, delimiter: Char, onSaved: () -> Unit) = operation {
        val count = repository.importCardText(deckId, text, delimiter)
        onSaved()
        showMessage("$count Karten importiert. Identische Karten wurden übersprungen.")
    }
    fun readCardFile(uri: Uri, onLoaded: (String) -> Unit) = operation {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Datei kann nicht geöffnet werden")
        }
        onLoaded(text)
    }
    fun resetWordLearning(wordId: String) = operation {
        repository.resetWordLearning(wordId)
        showMessage("Lernstand dieser Karte zurückgesetzt.")
    }
    fun addAlternative(wordId: String, direction: StudyDirection, answer: String) = operation {
        repository.addAlternative(wordId, direction, answer)
        val updated = repository.studyData().decks.flatMap { it.words }.firstOrNull { it.id == wordId }
        if (updated != null) mutableState.update { state -> state.copy(session = state.session?.let { session ->
            session.copy(queue = session.queue.map { card ->
                if (card.word.id == wordId) card.copy(word = updated) else card
            })
        }) }
        showMessage("Antwort als weitere richtige Lösung gespeichert.")
    }

    fun startStudy(options: StudyOptions, mode: StudyMode) = operation {
        val queue = StudyPlanner.build(repository.studyData(), options, System.currentTimeMillis())
        if (queue.isEmpty()) showMessage("Für diese Auswahl gibt es gerade keine passenden Karten.")
        else mutableState.update { it.copy(session = StudySession(mode, queue, queue.size), summary = null, message = null) }
    }

    fun gradeCurrent(cardKey: String, attempt: Int, rating: ReviewRating) = operation {
        val session = mutableState.value.session ?: return@operation
        val card = session.queue.firstOrNull() ?: return@operation
        if (card.key != cardKey || session.attempt != attempt) return@operation
        val scheduled = repository.recordReview(card.word.id, card.direction, rating, System.currentTimeMillis())
        val remaining = session.queue.drop(1) + if (rating == ReviewRating.AGAIN) listOf(card) else emptyList()
        val updated = session.copy(queue = remaining,
            completed = session.completed + if (rating == ReviewRating.AGAIN) 0 else 1,
            attempt = session.attempt + 1,
            results = session.results + ReviewAttempt(card, rating, scheduled.dueAt))
        mutableState.update {
            it.copy(session = updated.takeUnless { remaining.isEmpty() },
                summary = if (remaining.isEmpty()) summaryFor(updated) else null)
        }
    }

    private fun summaryFor(session: StudySession) = StudySummary(session.total, session.completed, session.results,
        (SystemClock.elapsedRealtime() - session.startedAt).coerceAtLeast(0L))

    fun endStudy() {
        if (!mutableState.value.busy) mutableState.update {
            it.copy(summary = it.session?.takeIf { session -> session.results.isNotEmpty() }?.let(::summaryFor), session = null)
        }
    }
    fun closeSummary() { mutableState.update { it.copy(summary = null) } }

    fun exportCsv(uri: Uri, deckIds: Set<String>) = operation {
        val csv = repository.exportCsv(deckIds)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
                ?: error("Datei kann nicht geöffnet werden")
        }
        showMessage("${deckIds.size} Listen als CSV gespeichert.")
    }

    fun shareCsv(deckIds: Set<String>, onReady: (Uri) -> Unit) = operation {
        val uri = withContext(Dispatchers.IO) {
            val csv = repository.exportCsv(deckIds)
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(directory, "vocabscan-${UUID.randomUUID()}.csv")
            file.writeText(csv, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        onReady(uri)
    }

    fun importCsv(uri: Uri) = operation {
        val csv = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Datei kann nicht geöffnet werden")
        }
        val (decks, words) = repository.importCsv(csv)
        showMessage("$decks Listen und $words Karten importiert. Vorhandene Paare wurden übersprungen.")
    }

    fun exportFull(uri: Uri) = operation {
        try {
            FullBackup(context, repository).export(uri)
            showMessage("Vollständige Sicherung gespeichert.")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            showMessage("Sicherung fehlgeschlagen: ${error.message ?: "Datei konnte nicht geschrieben werden."}")
        }
    }

    fun restoreFull(uri: Uri) = operation {
        try {
            FullBackup(context, repository).restore(uri)
            commitPendingDeletion()
            selectDeck(null)
            mutableState.update { it.copy(session = null, summary = null, draft = null) }
            showMessage("Sicherung vollständig wiederhergestellt.")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            showMessage("Wiederherstellung fehlgeschlagen: ${error.message ?: "Archiv konnte nicht gelesen werden."}")
        }
    }

    fun startImport(deckId: String, pages: List<Uri>) = operation {
        try {
            val recognized = importer.recognizePages(pages)
            val pairs = recognized.flatMap { it.pairs }
            clearImportHistory()
            mutableState.update { it.copy(draft = ImportDraft(deckId, pages, pairs, recognized.map { it.tokens },
                dimensions = recognized.map { it.width to it.height })) }
            if (pairs.isEmpty()) showMessage("Kein Text erkannt. Du kannst Einträge ergänzen oder ein schärferes Bild verwenden.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { showMessage("Das Bild konnte nicht gelesen werden. Bitte ein anderes Bild wählen oder erneut scannen.") }
    }

    private fun changeImport(editKey: String? = null, transform: (ImportDraft) -> ImportDraft) {
        if (mutableState.value.busy) return
        val draft = mutableState.value.draft ?: return
        val updated = transform(draft)
        if (updated == draft) return
        if (editKey == null || editKey != lastImportEditKey) rememberImport(draft)
        lastImportEditKey = editKey
        mutableState.update { it.copy(draft = updated, canUndoImport = true) }
    }
    private fun rememberImport(draft: ImportDraft) {
        importHistory.addLast(draft)
        if (importHistory.size > 30) importHistory.removeFirst()
    }
    fun undoImport() {
        if (mutableState.value.busy || importHistory.isEmpty()) return
        val previous = importHistory.removeLast()
        lastImportEditKey = null
        selectDeck(previous.deckId)
        mutableState.update { it.copy(draft = previous, canUndoImport = importHistory.isNotEmpty()) }
    }
    private fun clearImportHistory() {
        importHistory.clear()
        lastImportEditKey = null
        temporaryScans.forEach(::deleteTemporaryScan)
        temporaryScans.clear()
        mutableState.update { it.copy(canUndoImport = false) }
    }

    fun updatePair(id: String, term: String, translation: String) = changeImport(editKey = id) { draft ->
        draft.copy(pairs = draft.pairs.map { if (it.id == id) it.copy(term = term, translation = translation, reviewed = true) else it })
    }
    fun removePair(id: String) = changeImport { it.copy(pairs = it.pairs.filterNot { pair -> pair.id == id }) }
    fun removePairs(ids: Set<String>) = changeImport { it.copy(pairs = it.pairs.filterNot { pair -> pair.id in ids }) }
    fun reviewPairs(ids: Set<String>) = changeImport { it.copy(pairs = it.pairs.map { pair ->
        if (pair.id in ids && pair.term.isNotBlank() && pair.translation.isNotBlank()) pair.copy(reviewed = true) else pair
    }) }
    fun changeImportTarget(deckId: String) {
        if (decks.value.none { it.deck.id == deckId && !it.deck.isFlashcards } || mutableState.value.draft?.deckId == deckId) return
        changeImport { it.copy(deckId = deckId) }
        selectDeck(deckId)
    }
    fun addPair() = changeImport { it.copy(pairs = it.pairs + ImportPair(term = "", translation = "")) }
    fun swapColumns() = changeImport {
        it.copy(pairs = it.pairs.map { pair -> pair.copy(term = pair.translation, translation = pair.term,
            termBounds = pair.translationBounds, translationBounds = pair.termBounds, reviewed = false) })
    }
    fun mergePairs(first: String, second: String) = changeImport { it.copy(pairs = ScanEdits.merge(it.pairs, first, second)) }
    fun splitPair(id: String, termAt: Int, translationAt: Int) = changeImport {
        it.copy(pairs = ScanEdits.split(it.pairs, id, termAt, translationAt))
    }
    fun movePairWord(id: String, fromTerm: Boolean, index: Int) = changeImport {
        it.copy(pairs = ScanEdits.moveWord(it.pairs, id, fromTerm, index))
    }
    fun setColumnSplit(fraction: Float?) = changeImport { draft ->
            val pairs = draft.tokens.flatMapIndexed { page, tokens ->
                val boundary = fraction?.let { ratio ->
                    val width = draft.dimensions.getOrNull(page)?.first ?: tokens.maxOfOrNull { it.right } ?: 0
                    width * ratio.coerceIn(0.05f, 0.95f)
                }
                PairParser.parse(tokens, page, boundary)
            }
            draft.copy(pairs = pairs, manualSplit = fraction)
    }
    fun removeDuplicates(existing: List<Vocabulary>) {
        val present = existing.map { de.vocabscan.data.pairKey(it.term, it.translation) }.toMutableSet()
        changeImport { draft ->
            draft.copy(pairs = draft.pairs.filter { pair ->
                pair.term.isBlank() || pair.translation.isBlank() ||
                    present.add(de.vocabscan.data.pairKey(pair.term, pair.translation))
            })
        }
    }
    fun editScanPage(index: Int, rotation: Int, crop: Float) = transformScanPage(index, rotation, crop, null)
    fun cropScanPage(index: Int, crop: ScanCrop) = transformScanPage(index, 0, 0f, crop)
    private fun transformScanPage(index: Int, rotation: Int, crop: Float, area: ScanCrop?) = operation {
        val draft = mutableState.value.draft ?: return@operation
        val original = draft.pages.getOrNull(index) ?: return@operation
        val transformed = importer.transform(original, rotation, crop, area)
        try {
            val recognized = importer.recognizePage(transformed, index)
            // A new crop or rotation changes coordinates; detect its columns again.
            val pairs = recognized.pairs
            rememberImport(draft)
            lastImportEditKey = null
            temporaryScans.add(transformed)
            mutableState.update { state -> state.copy(canUndoImport = true, draft = state.draft?.let {
                it.copy(manualSplit = null, pages = it.pages.toMutableList().also { pages -> pages[index] = transformed },
                    tokens = it.tokens.toMutableList().also { tokens -> tokens[index] = recognized.tokens },
                    dimensions = it.dimensions.toMutableList().also { sizes -> sizes[index] = recognized.width to recognized.height },
                    pairs = (it.pairs.filterNot { pair -> pair.page == index } + pairs).sortedBy { pair -> pair.page })
            }) }
        } catch (error: Exception) {
            deleteTemporaryScan(transformed)
            throw error
        }
    }
    private fun deleteTemporaryScan(uri: Uri) {
        val file = uri.path?.let(::File) ?: return
        if (uri.scheme == "file" && file.parentFile?.canonicalPath == context.cacheDir.canonicalPath &&
            file.name.startsWith("scan-edit-") && file.extension in setOf("jpg", "png")) file.delete()
    }
    override fun onCleared() {
        commitPendingDeletion()
        temporaryScans.forEach(::deleteTemporaryScan)
        temporaryScans.clear()
        importHistory.clear()
        super.onCleared()
    }
    fun discardImport() { if (!mutableState.value.busy) {
        clearImportHistory()
        mutableState.update { it.copy(draft = null) }
    } }
    fun confirmImport() = operation {
        val draft = mutableState.value.draft ?: return@operation
        val directory = File(context.filesDir, "scans").apply { mkdirs() }
        val copies = mutableListOf<File>()
        val count = try {
            val names = withContext(Dispatchers.IO) {
                draft.pages.map { uri ->
                    val target = File(directory, "${UUID.randomUUID()}.img")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        } ?: error("Scanbild kann nicht gelesen werden")
                        copies += target
                        target.name
                    } catch (error: Exception) { target.delete(); throw error }
                }
            }
            repository.importPairs(draft.deckId, draft.pairs, names, draft.dimensions)
        } catch (error: Exception) {
            copies.forEach { it.delete() }
            throw error
        }
        mutableState.update { it.copy(draft = null, message = "$count Karten übernommen. Identische Paare wurden übersprungen.") }
        clearImportHistory()
    }
}
