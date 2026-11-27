package de.vocabscan.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.learn.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class FlashcardRepositoryTest {
    private lateinit var database: VocabDatabase
    private lateinit var repository: VocabRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).build()
        repository = VocabRepository(database)
    }
    @After fun close() = database.close()

    @Test fun bothTypesShareFolderAndSaveIndependentReviewDirections() = runTest {
        val folder = repository.createFolder("Prüfung")
        val cards = repository.createDeck("Recht", StudyLanguage.ENGLISH, folder, DeckType.LERNKARTEN)
        val language = repository.createDeck("Englisch", StudyLanguage.ENGLISH, folder)
        val card = repository.saveWord(cards, null, "Vertrag", "Erklärung\nzweite Zeile", "Beispiel", tags = "Recht, Prüfung")
        val word = repository.saveWord(language, null, "law", "Recht", "")
        val now = 1_700_000_000_000L
        repository.recordReview(card.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, now)
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, now)
        repository.recordReview(card.id, StudyDirection.GERMAN_TO_FOREIGN, ReviewRating.AGAIN, now)
        val snapshot = repository.fullSnapshot()
        assertEquals(setOf(folder), snapshot.decks.map { it.folderId }.toSet())
        assertEquals("", snapshot.decks.first { it.id == cards }.language)
        assertEquals(3, snapshot.logs.size)
        assertEquals(2, snapshot.progress.count { it.wordId == card.id })
        val stats = LearningStatsCalculator.calculate(repository.studyData().decks, snapshot.progress, snapshot.logs, now, ZoneId.of("Europe/Berlin"))
        assertEquals(3, stats.today)
        assertEquals(4, stats.totalDirections)
        repository.setArchived(snapshot.decks.first { it.id == cards }.id, true)
        assertFalse(StudyPlanner.candidates(repository.studyData(), StudyOptions(filter = StudyFilter.ALL), now).any { it.isFlashcard })
    }

    @Test fun csvAndQrRoundTripPreserveFlashcardMetadata() = runTest {
        val id = repository.createDeck("Recht", StudyLanguage.FRENCH, type = DeckType.LERNKARTEN)
        val word = repository.saveWord(id, null, "Vertrag", "Erklärung\nzweite Zeile", "Beispiel", tags = "Recht")
        val csv = repository.exportCsv()
        val deck = database.vocabDao().allDecksWithWords().single()
        val collector = DeckQrCodec.Collector()
        DeckQrCodec.encode(deck).reversed().forEach { collector.accept(it) }
        val shared = collector.completed!!
        assertEquals(DeckType.LERNKARTEN, shared.type)
        assertEquals("", shared.language)
        assertEquals(word.translation, shared.words.single().translation)
        assertEquals(word.note, shared.words.single().note)
        assertEquals(word.tags, shared.words.single().tags)
        val importedId = repository.importSharedDeck(shared.name, shared.language, shared.words, type = shared.type)
        assertEquals(DeckType.LERNKARTEN, database.vocabDao().deckById(importedId)!!.type)
        database.vocabDao().deleteAllDecks()
        assertEquals(1 to 1, repository.importCsv(csv))
        assertEquals(DeckType.LERNKARTEN, database.vocabDao().allDecks().single().type)
        assertEquals(word.translation, database.vocabDao().wordsForDeck(id).single().translation)
    }

    @Test fun zipRoundTripKeepsBothTypesAndLearningHistory() = runTest {
        val id = repository.createDeck("Recht", StudyLanguage.ENGLISH, type = DeckType.LERNKARTEN)
        repository.createDeck("English", StudyLanguage.ENGLISH)
        val word = repository.saveWord(id, null, "Vertrag", "Erklärung\nzweite Zeile", "Beispiel", tags = "Recht")
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1000)
        val original = repository.fullSnapshot()
        val archive = File(context.cacheDir, "flashcards-backup-test.zip")
        try {
            FullBackup(context, repository).export(Uri.fromFile(archive))
            database.vocabDao().deleteAllDecks()
            FullBackup(context, repository).restore(Uri.fromFile(archive))
            assertEquals(original.decks.toSet(), repository.fullSnapshot().decks.toSet())
            assertEquals(original.words, repository.fullSnapshot().words)
            assertEquals(original.progress, repository.fullSnapshot().progress)
            assertEquals(original.logs, repository.fullSnapshot().logs)
        } finally { archive.delete() }
    }

    @Test fun textImportIsAtomicSkipsDuplicatesAndPreservesMultilineBack() = runTest {
        val id = repository.createDeck("Recht", StudyLanguage.ENGLISH, type = DeckType.LERNKARTEN)
        assertEquals(1, repository.importCardText(id, "Vertrag;\"Erklärung\nzweite Zeile\";Beispiel;Recht", ';'))
        assertEquals(0, repository.importCardText(id, "Vertrag;\"Erklärung\nzweite Zeile\"", ';'))
        try { repository.importCardText(id, "Begriff;Erklärung\nUngültig", ';'); fail("Invalid row accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, database.vocabDao().wordsForDeck(id).size)
        assertEquals("Beispiel", database.vocabDao().wordsForDeck(id).single().note)
    }

    @Test fun transferAndMergeRejectDifferentTypesButAcceptOtherFlashcardLists() = runTest {
        val first = repository.createDeck("Recht", StudyLanguage.ENGLISH, type = DeckType.LERNKARTEN)
        val second = repository.createDeck("Mehr Recht", StudyLanguage.ENGLISH, type = DeckType.LERNKARTEN)
        val language = repository.createDeck("English", StudyLanguage.ENGLISH)
        val word = repository.saveWord(first, null, "Vertrag", "Erklärung", "")
        try { repository.mergeDecks(first, language); fail("Incompatible merge accepted") }
        catch (_: IllegalArgumentException) { }
        try { repository.transferWords(first, language, setOf(word.id), true); fail("Incompatible transfer accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, repository.transferWords(first, second, setOf(word.id), false).transferred)
        assertEquals(1, repository.mergeDecks(first, second))
        assertEquals(DeckType.LERNKARTEN, database.vocabDao().deckById(second)!!.type)
    }
}
