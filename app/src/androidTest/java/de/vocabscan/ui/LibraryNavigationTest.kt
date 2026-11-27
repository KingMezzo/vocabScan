package de.vocabscan.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: VocabDatabase
    private lateinit var repository: VocabRepository
    private lateinit var model: VocabViewModel
    private lateinit var deckId: String
    private lateinit var restoration: StateRestorationTester
    private val store = ViewModelStore()

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("onboarding", 0).edit().putBoolean("completed", true).commit()
        context.getSharedPreferences("learning", 0).edit().putString("preferred_mode", "INPUT").commit()
        database = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).build()
        repository = VocabRepository(database)
        runBlocking {
            deckId = repository.createDeck("Reisewörter", StudyLanguage.ENGLISH)
            repeat(12) { repository.saveWord(deckId, null, "word-$it", "Wort-$it", "") }
            val archived = repository.createDeck("Alte Wörter", StudyLanguage.FRENCH)
            repository.saveWord(archived, null, "école", "Schule", "")
            repository.setArchived(archived, true)
        }
        compose.runOnUiThread {
            model = VocabViewModel(repository, de.vocabscan.scan.OcrImporter(context), SavedStateHandle(), context)
            store.put("navigation", model)
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent { VocabTheme(ThemeMode.LIGHT) { VocabApp(model, ThemeMode.LIGHT, {}) } }
        compose.waitUntil(10_000) { model.decks.value.size == 2 }
    }

    @After fun cleanup() {
        compose.runOnUiThread { store.clear() }
        database.close()
    }

    private fun library() = compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()

    @Test fun openFolderAndArchiveSurviveStateRestoration() {
        val folderId = runBlocking {
            repository.createFolder("Schule").also { id ->
                repository.moveDeck(deckId, id)
                repository.moveDeck(repository.fullSnapshot().decks.first { it.isArchived }.id, id)
            }
        }
        compose.waitUntil(10_000) { model.folders.value.any { it.id == folderId } }
        library()
        compose.onNodeWithText("Schule").performClick()
        compose.onNodeWithContentDescription("Bibliotheksoptionen").performClick()
        compose.onNodeWithText("Archiv öffnen").performClick()
        compose.onNodeWithText("Schule").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Schule").assertIsDisplayed()
        compose.onNodeWithText("Archiv · Schule").assertIsDisplayed()
        compose.onNodeWithText("Alte Wörter").assertIsDisplayed()
        compose.onNodeWithText("Reisewörter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Reisewörter").assertIsDisplayed()
    }

    private fun archive() {
        library()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Archiv"))
        compose.onNodeWithText("Archiv").performClick()
    }

    @Test fun archiveEntryOpensDedicatedDestinationAndRestoresWithoutLosingWords() {
        archive()
        compose.onNodeWithText("Alte Wörter").assertIsDisplayed()
        compose.onNodeWithText("Reisewörter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").assertDoesNotExist()
        compose.onNodeWithText("Alte Wörter").performClick()
        compose.onNodeWithText("Wieder aktivieren").assertIsDisplayed()
        compose.onNodeWithText("Arbeitsblatt scannen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Vokabel hinzufügen").assertDoesNotExist()
        compose.onNodeWithText("Wieder aktivieren").performClick()
        compose.waitUntil(10_000) { model.decks.value.none { it.deck.isArchived } }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Alte Wörter").assertIsDisplayed()
        assertEquals("école", runBlocking { repository.fullSnapshot().words.first { it.term == "école" }.term })
    }

    @Test fun archiveAndRestoreOfferUndoAndPreserveTheFolder() {
        val folderId = runBlocking { repository.createFolder("Schule").also { repository.moveDeck(deckId, it) } }
        compose.waitUntil(10_000) { model.folders.value.any { it.id == folderId } }
        library()
        compose.onNodeWithText("Schule").performClick()
        compose.onNodeWithContentDescription("Liste Reisewörter verwalten").performClick()
        compose.onNodeWithText("Liste archivieren").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.first { it.deck.id == deckId }.deck.isArchived }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(10_000) { !model.decks.value.first { it.deck.id == deckId }.deck.isArchived }
        assertEquals(folderId, model.decks.value.first { it.deck.id == deckId }.deck.folderId)
        compose.onNodeWithText("Reisewörter").assertIsDisplayed()

        // Restoring a list has the same undo affordance, including after a rotation.
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Archiv"))
        compose.onNodeWithText("Archiv").performClick()
        compose.onNodeWithText("Alte Wörter").performClick()
        compose.onNodeWithText("Wieder aktivieren").performClick()
        compose.waitUntil(10_000) { model.decks.value.none { it.deck.isArchived } }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(10_000) { model.decks.value.any { it.deck.isArchived } }
        compose.onNodeWithText("Wieder aktivieren").assertIsDisplayed()
    }

    @Test fun homeShowsOnlyTheNextRoundAndStartsExactlyTenCards() {
        compose.onNodeWithText("10 Karten fällig").assertIsDisplayed()
        compose.onNodeWithText("Reisewörter").assertDoesNotExist()
        compose.onNodeWithText("24 Übersetzungen", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Bibliothek durchsuchen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").assertDoesNotExist()
        compose.onNodeWithText("Lernrunde anpassen").assertDoesNotExist()
        compose.onNodeWithText("Jetzt lernen").performClick()
        compose.waitUntil(10_000) { model.state.value.session != null }
        compose.runOnIdle {
            val session = model.state.value.session!!
            assertEquals(10, session.total)
            assertEquals(StudyMode.INPUT, session.mode)
            assertTrue(session.queue.all { it.word.deckId == deckId })
        }
    }

    @Test fun libraryHasOneAddMenuAndSearchFindsListNames() {
        library()
        compose.onNodeWithText("Reisewörter").assertIsDisplayed()
        compose.onNodeWithText("Alte Wörter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
        listOf("Neue Liste", "Neuer Ordner", "Importieren").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("QR-Code").assertDoesNotExist()
        compose.onNodeWithText("Importieren").performClick()
        listOf("QR-Code", "CSV-Datei", "Sicherung wiederherstellen").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("CSV-Datei").assertIsDisplayed()
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Neuer Ordner").performClick()
        compose.onNodeWithText("Ordnername").performTextInput("Schule")
        compose.onNodeWithText("Erstellen").performClick()
        compose.waitUntil(10_000) { model.folders.value.any { it.name == "Schule" } }
        compose.onNodeWithText("Schule").assertIsDisplayed()
        compose.onNodeWithText("0 Listen · 0 Karten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bibliothek durchsuchen").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Reise")
        compose.onNode(hasText("Reisewörter") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { model.selectedDeckId.value == deckId }
    }

    @Test fun movingRenamingAndDeletingFolderKeepsAllLists() {
        val folderId = runBlocking { repository.createFolder("Schule") }
        compose.waitUntil(10_000) { model.folders.value.any { it.id == folderId } }
        library()
        compose.onNodeWithContentDescription("Liste Reisewörter verwalten").performClick()
        compose.onNodeWithText("Verschieben").performClick()
        compose.onNode(hasText("Schule") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.onNodeWithText("Hierher verschieben").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.first { it.deck.id == deckId }.deck.folderId == folderId }
        compose.onNodeWithText("1 Liste · 12 Karten").assertIsDisplayed()
        compose.onNodeWithText("Reisewörter").assertDoesNotExist()
        compose.onNodeWithText("Schule").performClick()
        compose.onNodeWithText("Reisewörter").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bibliotheksoptionen").performClick()
        compose.onNodeWithText("Ordner umbenennen").performClick()
        compose.onNodeWithText("Ordnername").performTextReplacement("Prüfung")
        compose.onNodeWithText("Umbenennen").performClick()
        compose.waitUntil(10_000) { model.folders.value.any { it.id == folderId && it.name == "Prüfung" } }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Prüfung").performClick()
        compose.onNodeWithContentDescription("Bibliotheksoptionen").performClick()
        compose.onNodeWithText("Ordner löschen").performClick()
        compose.onNode(hasText("Ordner löschen") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { model.folders.value.none { it.id == folderId } }
        compose.onNodeWithText("Reisewörter").assertIsDisplayed()
        assertEquals(12, runBlocking { repository.deckSnapshot(deckId).words.size })
    }

    @Test fun newListsAreCreatedInsideTheOpenFolder() {
        val folderId = runBlocking { repository.createFolder("Schule") }
        compose.waitUntil(10_000) { model.folders.value.any { it.id == folderId } }
        library()
        compose.onNodeWithText("Schule").performClick()
        compose.onNodeWithText("Dieser Ordner ist noch leer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
        compose.onNodeWithText("Neue Liste").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Name der Liste").performTextInput("Unit 1")
        compose.onNodeWithText("Liste erstellen").performClick()
        compose.waitUntil(10_000) { model.decks.value.any { it.deck.name == "Unit 1" } }
        assertEquals(folderId, model.decks.value.first { it.deck.name == "Unit 1" }.deck.folderId)
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Unit 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("1 Liste · 0 Karten").assertIsDisplayed()
    }
}
