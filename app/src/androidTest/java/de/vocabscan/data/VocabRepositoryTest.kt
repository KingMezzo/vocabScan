package de.vocabscan.data

import androidx.room.Room
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.OcrBounds
import de.vocabscan.scan.ScanSource
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import de.vocabscan.ui.ThemeMode
import de.vocabscan.ui.ThemePreference
import de.vocabscan.learn.LearningGoal
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

@RunWith(AndroidJUnit4::class)
class VocabRepositoryTest {
    private lateinit var database: VocabDatabase
    private lateinit var repository: VocabRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VocabDatabase::class.java).build()
        repository = VocabRepository(database)
    }

    @After fun close() { database.close() }

    @Test fun emptyFoldersAndStableMembershipSurviveSnapshotAndDeletion() = runTest {
        val emptyId = repository.createFolder("Leer")
        val folderId = repository.createFolder("Schule")
        val deckId = repository.createDeck("Unit 1", StudyLanguage.ENGLISH, folderId)
        repository.saveWord(deckId, null, "house", "Haus", "")
        val word = database.vocabDao().wordsForDeck(deckId).single()
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1000)
        repository.setArchived(deckId, true)
        val archiveFolderId = database.vocabDao().deckById(deckId)!!.folderId!!
        assertNotEquals(folderId, archiveFolderId)
        repository.renameFolderById(folderId, "Prüfung")
        assertEquals(archiveFolderId, database.vocabDao().deckById(deckId)!!.folderId)
        assertEquals("Schule", database.vocabDao().deckById(deckId)!!.folder)
        val snapshot = repository.fullSnapshot()
        repository.deleteFolder(emptyId)
        repository.restoreFull(snapshot)
        assertEquals(setOf(emptyId, folderId, archiveFolderId), database.vocabDao().allFolders().map { it.id }.toSet())
        repository.deleteFolder(folderId)
        repository.deleteFolder(emptyId)
        assertEquals(archiveFolderId, database.vocabDao().deckById(deckId)!!.folderId)
        assertEquals(true, database.vocabDao().deckById(deckId)!!.isArchived)
        assertEquals(1, database.vocabDao().allLogs().size)
        assertEquals(1, database.vocabDao().allProgress().size)
    }

    @Test fun legacySnapshotCreatesFolderEntities() = runTest {
        repository.restoreFull(FullSnapshot(listOf(Deck("d", "Alt", "en", folder = "Schule", isArchived = true)),
            emptyList(), emptyList(), emptyList(), emptyList()))
        val folder = database.vocabDao().allFolders().single()
        assertEquals("Schule", folder.name)
        assertEquals(folder.id, database.vocabDao().deckById("d")!!.folderId)
        assertEquals(true, database.vocabDao().deckById("d")!!.isArchived)
    }

    @Test fun importsOnlyUniquePairsAndKeepsAccents() = runTest {
        val id = repository.createDeck("Französisch", StudyLanguage.FRENCH)
        val pairs = listOf(
            ImportPair(term = "école", translation = "Schule"),
            ImportPair(term = " ÉCOLE ", translation = " Schule "),
            ImportPair(term = "ecole", translation = "Schule"),
        )
        assertEquals(2, repository.importPairs(id, pairs))
        assertEquals(0, repository.importPairs(id, pairs))
        assertEquals(2, database.vocabDao().wordsForDeck(id).size)
    }

    @Test fun importedWordsAndPagesShareOneScanGroup() = runTest {
        val id = repository.createDeck("Scans", StudyLanguage.FRENCH)
        repository.importPairs(id, listOf(
            ImportPair(term = "bonjour", translation = "hallo", page = 0,
                termBounds = OcrBounds(10, 20, 90, 40), translationBounds = OcrBounds(200, 20, 260, 40)),
            ImportPair(term = "merci", translation = "danke", page = 1,
                termBounds = OcrBounds(12, 100, 72, 120)),
        ), listOf("first.img", "second.img"), listOf(300 to 500, 300 to 600))
        val groupId = database.vocabDao().wordsForDeck(id).first().scanGroupId
        assertNotNull(groupId)
        assertTrue(database.vocabDao().wordsForDeck(id).all { it.scanGroupId == groupId })
        assertTrue(database.vocabDao().pagesForDeck(id).all { it.scanGroupId == groupId })
        val pages = database.vocabDao().pagesForDeck(id).associateBy { it.fileName }
        val words = database.vocabDao().wordsForDeck(id).associateBy { it.term }
        assertEquals(ScanSource(pages.getValue("first.img").id, 300, 500,
            OcrBounds(10, 20, 90, 40), OcrBounds(200, 20, 260, 40)),
            ScanSource.decode(words.getValue("bonjour").scanSource))
        assertEquals(pages.getValue("second.img").id, ScanSource.decode(words.getValue("merci").scanSource)?.pageId)
        val original = words.getValue("bonjour")
        repository.saveWord(id, original, "bonjour", "Hallo", "")
        assertEquals(original.scanSource, database.vocabDao().wordById(original.id)?.scanSource)
    }

    @Test fun copyAndMoveKeepWordFieldsAndScanGroupButDropLearningState() = runTest {
        val source = repository.createDeck("Quelle", StudyLanguage.ENGLISH)
        val target = repository.createDeck("Ziel", StudyLanguage.ENGLISH)
        repository.importPairs(source, listOf(
            ImportPair(term = "apple", translation = "Apfel"),
            ImportPair(term = "house", translation = "Haus"),
        ), listOf("source.img"))
        val sourceWords = database.vocabDao().wordsForDeck(source)
        val apple = sourceWords.first { it.term == "apple" }
        val house = sourceWords.first { it.term == "house" }
        repository.saveWord(source, apple, "apple", "Apfel", "Obst", "fruit", "Frucht", "Kapitel 1", "Essen")
        repository.setFavorite(apple.id, true)
        repository.recordReview(apple.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1_700_000_000_000L)
        repository.recordReview(house.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.AGAIN, 1_700_000_000_000L)

        assertEquals(TransferResult(1, 0), repository.transferWords(source, target, setOf(apple.id), move = false))
        val copied = database.vocabDao().wordsForDeck(target).single()
        assertNotEquals(apple.id, copied.id)
        assertEquals("Obst", copied.note)
        assertEquals("fruit", copied.termAlternatives)
        assertEquals("Frucht", copied.translationAlternatives)
        assertEquals("Kapitel 1", copied.chapter)
        assertEquals("Essen", copied.tags)
        assertTrue(copied.isFavorite)
        assertEquals(apple.scanGroupId, copied.scanGroupId)
        assertTrue(database.vocabDao().progressForWord(copied.id).isEmpty())
        assertTrue(database.vocabDao().logsForWord(copied.id).isEmpty())

        assertEquals(TransferResult(0, 1), repository.transferWords(source, target, setOf(apple.id), move = true))
        assertNotNull(database.vocabDao().wordById(apple.id))
        assertEquals(1, database.vocabDao().progressForWord(apple.id).size)
        assertEquals(TransferResult(1, 0), repository.transferWords(source, target, setOf(house.id), move = true))
        assertEquals(target, database.vocabDao().wordById(house.id)?.deckId)
        assertTrue(database.vocabDao().progressForWord(house.id).isEmpty())
        assertTrue(database.vocabDao().logsForWord(house.id).isEmpty())
        assertEquals(apple.scanGroupId, database.vocabDao().wordById(house.id)?.scanGroupId)
    }

    @Test fun rejectsIncompleteImportWithoutPartialWrites() = runTest {
        val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        try {
            repository.importPairs(id, listOf(ImportPair(term = "apple", translation = "Apfel"), ImportPair(term = "house", translation = "")))
            fail("Incomplete pairs must not be imported")
        } catch (_: IllegalArgumentException) { /* expected */ }
        assertTrue(database.vocabDao().wordsForDeck(id).isEmpty())
    }

    @Test fun updatesWordsAndCascadesDeckDeletion() = runTest {
        val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(id, null, "apple", "Apfl", "")
        val original = database.vocabDao().wordsForDeck(id).single()
        repository.saveWord(id, original, "apple", "Apfel", "Obst")
        val updated = database.vocabDao().wordsForDeck(id).single()
        assertEquals(original.id, updated.id)
        assertEquals("Apfel", updated.translation)
        assertEquals("Obst", updated.note)
        val deck = repository.decks.first().single().deck
        repository.renameDeck(deck, "Unit 2")
        assertEquals("Unit 2", repository.decks.first().single().deck.name)
        repository.deleteDeck(deck)
        assertTrue(database.vocabDao().wordsForDeck(id).isEmpty())
        assertTrue(repository.decks.first().isEmpty())
    }

    @Test fun libraryFolderChangesLeaveArchiveAndReminderCountsIndependent() = runTest {
        val first = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        val second = repository.createDeck("Unit 2", StudyLanguage.ENGLISH)
        repository.saveWord(first, null, "house", "Haus", "")
        repository.saveWord(second, null, "apple", "Apfel", "")
        repository.setDeckFolder(first, " Schule ")
        repository.setDeckFolder(second, "schule")
        assertEquals(setOf("Schule"), database.vocabDao().allDecks().map { it.folder }.toSet())
        assertEquals(4, repository.dueCount(System.currentTimeMillis()))
        repository.setArchived(first, true)
        assertEquals(2, repository.dueCount(System.currentTimeMillis()))
        assertEquals(2, repository.fullSnapshot().words.size)
        repository.renameFolder("Schule", "Prüfung")
        assertEquals("Schule", database.vocabDao().deckById(first)!!.folder)
        assertEquals("Prüfung", database.vocabDao().deckById(second)!!.folder)
        repository.renameFolder("Prüfung", "")
        assertEquals("Schule", database.vocabDao().deckById(first)!!.folder)
        assertEquals("", database.vocabDao().deckById(second)!!.folder)
        repository.setArchived(first, false)
        assertEquals(4, repository.dueCount(System.currentTimeMillis()))
    }

    @Test fun duplicationRemapsImagesAndMergeKeepsAllReviewsAndDuplicatePairs() = runTest {
        val id = repository.createDeck("Original", StudyLanguage.ENGLISH)
        repository.setDeckFolder(id, "Schule")
        repository.setArchived(id, true)
        repository.importPairs(id, listOf(ImportPair(term = "house", translation = "Haus",
            termBounds = de.vocabscan.scan.OcrBounds(10, 20, 100, 60))), listOf("original.img"), listOf(1200 to 600))
        val word = database.vocabDao().wordsForDeck(id).single()
        repository.setFavorite(word.id, true)
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1_700_000_000_000L)
        val source = repository.deckSnapshot(id)
        val copiedId = repository.duplicateDeck(source, "Kopie", false, source.pages.associate { it.id to "copy.img" })
        val copied = repository.deckSnapshot(copiedId)
        assertFalse(copied.deck.isArchived)
        assertEquals("Schule", copied.deck.folder)
        assertTrue(copied.words.single().isFavorite)
        assertNotEquals(word.id, copied.words.single().id)
        assertNotEquals(source.pages.single().id, copied.pages.single().id)
        assertNotEquals(source.words.single().scanGroupId, copied.words.single().scanGroupId)
        assertEquals(copied.pages.single().id, de.vocabscan.scan.ScanSource.decode(copied.words.single().scanSource)?.pageId)
        assertEquals("copy.img", copied.pages.single().fileName)
        assertTrue(copied.progress.isEmpty())
        assertTrue(copied.logs.isEmpty())
        val withLearning = repository.duplicateDeck(source, "Mit Lernstand", true, source.pages.associate { it.id to "review-copy.img" })
        assertEquals(1, repository.deckSnapshot(withLearning).logs.size)
        assertEquals(1, repository.deckSnapshot(withLearning).progress.single().reviews)
        assertEquals(1, repository.mergeDecks(id, copiedId))
        assertNull(database.vocabDao().deckById(id))
        val merged = repository.deckSnapshot(copiedId)
        assertEquals(2, merged.words.size)
        assertTrue(merged.words.any { it.id == word.id })
        assertEquals(1, merged.logs.size)
        assertEquals(1, merged.progress.single().reviews)
        assertEquals(setOf("original.img", "copy.img"), merged.pages.map { it.fileName }.toSet())
    }

    @Test fun mergingDifferentLanguagesLeavesBothListsIntact() = runTest {
        val english = repository.createDeck("English", StudyLanguage.ENGLISH)
        val french = repository.createDeck("French", StudyLanguage.FRENCH)
        repository.saveWord(english, null, "house", "Haus", "")
        try { repository.mergeDecks(english, french); fail("Different languages accepted") }
        catch (_: IllegalArgumentException) { /* expected */ }
        assertEquals(2, database.vocabDao().allDecks().size)
        assertEquals(1, database.vocabDao().wordsForDeck(english).size)
    }

    @Test fun reviewDirectionsAreIndependentAndSaved() = runTest {
        val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(id, null, "apple", "Apfel", "")
        val word = database.vocabDao().wordsForDeck(id).single()
        val now = 1_700_000_000_000L
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, now)
        assertEquals(1, database.vocabDao().progressFor(word.id, 0)?.reviews)
        assertNull(database.vocabDao().progressFor(word.id, 1))
        repository.recordReview(word.id, StudyDirection.GERMAN_TO_FOREIGN, ReviewRating.AGAIN, now)
        assertEquals(now + 60_000L, database.vocabDao().progressFor(word.id, 1)?.dueAt)
        assertEquals(1, repository.dueCount(now + 60_000L))
    }

    @Test fun deletedWordAndDeckRestoreFavoritesAndLearningHistory() = runTest {
        val deckId = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(deckId, null, "apple", "Apfel", "Notiz")
        val original = database.vocabDao().wordsForDeck(deckId).single()
        repository.setFavorite(original.id, true)
        repository.recordReview(original.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1_700_000_000_000L)
        database.vocabDao().insertScanPages(listOf(ScanPage("undo-page", deckId, "undo-test.img", 100L)))
        val word = database.vocabDao().wordsForDeck(deckId).single()
        val deletedWord = repository.takeDeletedWord(word)
        assertTrue(database.vocabDao().wordsForDeck(deckId).isEmpty())
        repository.restoreDeletedWord(deletedWord)
        assertTrue(database.vocabDao().wordsForDeck(deckId).single().isFavorite)
        assertEquals(1, database.vocabDao().allLogs().size)
        val deletedDeck = repository.takeDeletedDeck(repository.decks.first().single().deck)
        assertTrue(repository.decks.first().isEmpty())
        repository.restoreDeletedDeck(deletedDeck)
        assertTrue(database.vocabDao().wordsForDeck(deckId).single().isFavorite)
        assertEquals(1, database.vocabDao().allProgress().size)
        assertEquals(1, database.vocabDao().allLogs().size)
        assertEquals("undo-test.img", database.vocabDao().allScanPages().single().fileName)
    }

    @Test fun qrFramesRoundTripInAnyOrderAndRejectMixing() = runTest {
        val deckId = repository.createDeck("Französisch", StudyLanguage.FRENCH)
        repeat(100) { repository.saveWord(deckId, null, "école $it", "Schule $it", "Notiz $it") }
        val first = database.vocabDao().wordsForDeck(deckId).first()
        repository.setFavorite(first.id, true)
        val deck = repository.decks.first().single()
        val frames = DeckQrCodec.encode(deck)
        assertTrue(frames.size > 1)
        val matrix = MultiFormatWriter().encode(frames.first(), BarcodeFormat.QR_CODE, 720, 720)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) -0x1000000 else -0x1
        }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(matrix.width, matrix.height, pixels))))
        assertEquals(frames.first(), decoded.text)
        val collector = DeckQrCodec.Collector()
        assertNull(collector.accept(frames.last()))
        assertEquals(setOf(frames.size), collector.receivedIndices)
        assertNull(collector.accept(frames.last()))
        assertEquals(1, collector.received)
        assertEquals((1 until frames.size).toList(), collector.missingIndices)
        val resumed = DeckQrCodec.Collector().apply { collector.savedFrames.forEach { accept(it) } }
        assertEquals(collector.receivedIndices, resumed.receivedIndices)
        var shared: SharedDeck? = null
        frames.reversed().forEach { shared = resumed.accept(it) ?: shared }
        assertEquals(100, shared!!.words.size)
        assertTrue(shared!!.words.any { it.isFavorite })
        assertEquals("Französisch", shared!!.name)
        val importedId = repository.importSharedDeck(shared!!.name, shared!!.language, shared!!.words)
        assertEquals(100, database.vocabDao().wordsForDeck(importedId).size)
        assertTrue(database.vocabDao().wordsForDeck(importedId).any { it.isFavorite })
    }

    @Test fun acceptedAlternativeIsStoredForCorrectDirection() = runTest {
        val deckId = repository.createDeck("English", StudyLanguage.ENGLISH)
        repository.saveWord(deckId, null, "house", "Haus", "")
        val word = database.vocabDao().wordsForDeck(deckId).single()
        repository.addAlternative(word.id, StudyDirection.FOREIGN_TO_GERMAN, "Gebäude")
        repository.addAlternative(word.id, StudyDirection.FOREIGN_TO_GERMAN, "Gebäude")
        repository.addAlternative(word.id, StudyDirection.GERMAN_TO_FOREIGN, "home")
        val updated = database.vocabDao().wordsForDeck(deckId).single()
        assertEquals("Gebäude", updated.translationAlternatives)
        assertEquals("home", updated.termAlternatives)
    }

    @Test fun resettingOneWordKeepsItsTextAndOtherWordsHistory() = runTest {
        val deckId = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(deckId, null, "apple", "Apfel", "Merksatz")
        repository.saveWord(deckId, null, "house", "Haus", "")
        val apple = database.vocabDao().wordsForDeck(deckId).first { it.term == "apple" }
        val house = database.vocabDao().wordsForDeck(deckId).first { it.term == "house" }
        val now = 1_700_000_000_000L
        repository.recordReview(apple.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.AGAIN, now)
        repository.recordReview(apple.id, StudyDirection.GERMAN_TO_FOREIGN, ReviewRating.GOOD, now)
        repository.recordReview(house.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.HARD, now)
        repository.resetWordLearning(apple.id)
        assertNull(database.vocabDao().progressFor(apple.id, 0))
        assertNull(database.vocabDao().progressFor(apple.id, 1))
        assertTrue(database.vocabDao().allLogs().none { it.wordId == apple.id })
        assertEquals(1, database.vocabDao().progressFor(house.id, 0)?.reviews)
        assertEquals("Merksatz", database.vocabDao().wordsForDeck(deckId).first { it.id == apple.id }.note)
    }

    @Test fun csvRestoresChapterTagsAndAlternativeAnswers() = runTest {
        val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(id, null, "house", "Haus", "Wohnen",
            termAlternatives = "home", translationAlternatives = "Gebäude", chapter = "Unit 1", tags = "Nomen, Wohnen")
        repository.setDeckFolder(id, "Schule")
        repository.setArchived(id, true)
        val csv = repository.exportCsv()
        repository.deleteDeck(repository.decks.first().single().deck)
        assertEquals(1 to 1, repository.importCsv(csv))
        val restored = database.vocabDao().wordsForDeck(id).single()
        assertEquals("home", restored.termAlternatives)
        assertEquals("Gebäude", restored.translationAlternatives)
        assertEquals("Unit 1", restored.chapter)
        assertEquals("Nomen, Wohnen", restored.tags)
        assertEquals("Schule", database.vocabDao().deckById(id)?.folder)
        assertEquals(true, database.vocabDao().deckById(id)?.isArchived)
    }

    @Test fun csvImportIsIdempotentAndPreservesEmptyDecks() = runTest {
        val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
        repository.saveWord(id, null, "apple", "Apfel", "Obst")
        repository.createDeck("Leer", StudyLanguage.FRENCH)
        val csv = repository.exportCsv()
        assertEquals(2, CsvCodec.decode(csv).size)
        assertEquals(0 to 0, repository.importCsv(csv))
        assertEquals(2, repository.decks.first().size)
        assertEquals("Obst", database.vocabDao().wordsForDeck(id).single().note)
    }

    @Test fun csvExportIncludesOnlySelectedListsIncludingEmptyOnes() = runTest {
        val chosen = repository.createDeck("Teilen", StudyLanguage.FRENCH)
        val excluded = repository.createDeck("Privat", StudyLanguage.ENGLISH)
        val empty = repository.createDeck("Noch leer", StudyLanguage.FRENCH)
        repository.saveWord(chosen, null, "bonjour", "hallo", "")
        repository.saveWord(excluded, null, "secret", "geheim", "")

        val rows = CsvCodec.decode(repository.exportCsv(setOf(chosen, empty)))
        assertEquals(setOf(chosen, empty), rows.map { it.deckId }.toSet())
        assertEquals("bonjour", rows.first { it.deckId == chosen }.term)
        assertEquals("", rows.first { it.deckId == empty }.term)
        assertFalse(rows.any { it.deckId == excluded || it.term == "secret" })

        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "selected-lists-test.csv")
        try {
            file.writeText(repository.exportCsv(setOf(chosen)), Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file)
            val shared = context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertEquals(listOf(chosen), CsvCodec.decode(shared).map { it.deckId })
        } finally { file.delete() }
    }

    @Test fun fullBackupRestoresReviewsMetadataAndScanImage() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scanDir = File(context.filesDir, "scans").apply { mkdirs() }
        val image = File(scanDir, "backup-test-${System.nanoTime()}.img")
        val archive = File(context.cacheDir, "backup-test-${System.nanoTime()}.zip")
        try {
            ThemePreference.write(context, ThemeMode.LIGHT)
            LearningGoal.write(context, 20)
            val id = repository.createDeck("Unit 1", StudyLanguage.ENGLISH)
            repository.setDeckFolder(id, "Sicherung")
            val emptyFolderId = repository.createFolder("Leerer Sicherungsordner")
            repository.setArchived(id, true)
            repository.saveWord(id, null, "house", "Haus", "Wohnen", "home", "Gebäude", "Unit 1", "Nomen")
            val word = database.vocabDao().wordsForDeck(id).single()
            database.vocabDao().updateWord(word.copy(scanGroupId = "backup-scan"))
            repository.setFavorite(word.id, true)
            repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1_700_000_000_000L)
            image.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            database.vocabDao().insertScanPages(listOf(ScanPage("page-test", id, image.name, 100L, "backup-scan")))
            FullBackup(context, repository).export(Uri.fromFile(archive))
            ThemePreference.write(context, ThemeMode.DARK)
            LearningGoal.write(context, 5)
            repository.deleteDeck(repository.decks.first().single().deck)
            FullBackup(context, repository).restore(Uri.fromFile(archive))
            val restored = repository.fullSnapshot()
            assertEquals("Sicherung", restored.decks.single().folder)
            assertEquals(setOf("Sicherung", "Leerer Sicherungsordner"), restored.folders.map { it.name }.toSet())
            assertTrue(restored.folders.any { it.id == emptyFolderId })
            assertTrue(restored.folders.any { it.id == restored.decks.single().folderId && it.name == "Sicherung" })
            assertTrue(restored.decks.single().isArchived)
            assertEquals("home", restored.words.single().termAlternatives)
            assertEquals("Nomen", restored.words.single().tags)
            assertTrue(restored.words.single().isFavorite)
            assertEquals("backup-scan", restored.words.single().scanGroupId)
            assertEquals("backup-scan", restored.pages.single().scanGroupId)
            assertEquals(20, LearningGoal.read(context))
            assertEquals(1, restored.progress.single().reviews)
            assertEquals(1, restored.logs.size)
            assertEquals(ThemeMode.LIGHT, ThemePreference.read(context))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(scanDir, restored.pages.single().fileName).readBytes())
            File(scanDir, restored.pages.single().fileName).delete()
        } finally {
            ThemePreference.write(context, ThemeMode.SYSTEM)
            LearningGoal.write(context, 10)
            image.delete()
            archive.delete()
        }
    }

    @Test fun brokenFullBackupLeavesExistingDataIntact() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = repository.createDeck("Behalten", StudyLanguage.FRENCH)
        val archive = File(context.cacheDir, "broken-backup-${System.nanoTime()}.zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{\"version\":1,\"decks\":[],\"words\":[],\"progress\":[],\"logs\":[],\"pages\":[{\"id\":\"missing\",\"deckId\":\"x\",\"createdAt\":0,\"size\":1,\"sha256\":\"x\"}]}".toByteArray())
                zip.closeEntry()
            }
            try { FullBackup(context, repository).restore(Uri.fromFile(archive)); fail("Damaged archive accepted") }
            catch (_: IllegalArgumentException) { /* expected */ }
            assertEquals(id, repository.fullSnapshot().decks.single().id)
        } finally { archive.delete() }
    }
    @Test fun nestedArchivePathsSurviveLibraryDeletionRestoreAndCsv() = runTest {
        val rootId = repository.createFolder("Schule")
        val childId = repository.createFolder("Englisch", rootId)
        val deckId = repository.createDeck("Unit 1", StudyLanguage.ENGLISH, childId)
        val secondId = repository.createDeck("Unit 2", StudyLanguage.ENGLISH, childId)
        repository.saveWord(deckId, null, "book", "Buch", "")
        val word = database.vocabDao().wordsForDeck(deckId).single()
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1000)
        repository.setArchived(deckId, true)
        repository.setArchived(secondId, true)
        val archived = database.vocabDao().deckById(deckId)!!
        assertNotEquals(childId, archived.folderId)
        assertEquals(archived.folderId, database.vocabDao().deckById(secondId)!!.folderId)
        assertEquals("Schule/Englisch", archived.folder)
        assertTrue(folderPath(database.vocabDao().allFolders(), archived.folderId).all { it.isArchived })
        repository.deleteFolder(rootId)
        assertEquals(archived, database.vocabDao().deckById(deckId))
        assertTrue(database.vocabDao().allFolders().all { it.isArchived })
        repository.setArchived(deckId, false)
        val restored = database.vocabDao().deckById(deckId)!!
        assertEquals("Schule/Englisch", restored.folder)
        assertTrue(folderPath(database.vocabDao().allFolders(), restored.folderId).none { it.isArchived })
        assertEquals(1, repository.deckSnapshot(deckId).logs.size)
        assertEquals(1, repository.deckSnapshot(deckId).progress.size)
        val snapshot = repository.fullSnapshot()
        repository.restoreFull(snapshot)
        assertEquals(snapshot, repository.fullSnapshot())
        val csv = repository.exportCsv()
        repository.restoreFull(FullSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        repository.importCsv(csv)
        val imported = database.vocabDao().allDecks()
        assertEquals(setOf(false, true), imported.map { it.isArchived }.toSet())
        assertTrue(imported.all { it.folder == "Schule/Englisch" })
        assertNotEquals(imported[0].folderId, imported[1].folderId)
    }

    @Test fun cannotMoveAnActiveListIntoAnArchiveFolder() = runTest {
        val folder = repository.createFolder("Schule")
        val archivedId = repository.createDeck("Archiv", StudyLanguage.ENGLISH, folder)
        val activeId = repository.createDeck("Aktiv", StudyLanguage.ENGLISH)
        repository.setArchived(archivedId, true)
        val archiveFolder = database.vocabDao().deckById(archivedId)!!.folderId
        try { repository.moveDeck(activeId, archiveFolder); fail("Cross-scope move accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(null, database.vocabDao().deckById(activeId)!!.folderId)
    }

}
