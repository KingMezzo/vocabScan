package de.vocabscan.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.*
import de.vocabscan.learn.ReviewRating
import de.vocabscan.learn.StudyDirection
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.OcrImporter
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeckOrganizationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: VocabDatabase
    private lateinit var repository: VocabRepository
    private lateinit var model: VocabViewModel
    private lateinit var image: File
    private lateinit var sourceId: String
    private lateinit var targetId: String
    private val store = ViewModelStore()

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("onboarding", 0).edit().putBoolean("completed", true).commit()
        database = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).build()
        repository = VocabRepository(database)
        runBlocking { repository.createFolder("Schule") }
        image = File(File(context.filesDir, "scans").apply { mkdirs() }, "organization-${System.nanoTime()}.img")
        image.writeBytes(byteArrayOf(8, 5, 3, 2, 1))
        runBlocking {
            sourceId = repository.createDeck("Quellliste", StudyLanguage.ENGLISH)
            targetId = repository.createDeck("Zielliste", StudyLanguage.ENGLISH)
            repository.importPairs(sourceId, listOf(ImportPair(term = "house", translation = "Haus")),
                listOf(image.name), listOf(1200 to 600))
            val word = database.vocabDao().wordsForDeck(sourceId).single()
            repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1_700_000_000_000L)
        }
        compose.runOnUiThread {
            model = VocabViewModel(repository, OcrImporter(context), SavedStateHandle(), context)
            store.put("organization", model)
        }
        compose.setContent { VocabTheme(ThemeMode.LIGHT) { VocabApp(model, ThemeMode.LIGHT, {}) } }
        compose.waitUntil(10_000) { model.decks.value.size == 2 && model.folders.value.size == 1 }
        compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
    }

    @After fun cleanup() {
        val files = runBlocking { repository.fullSnapshot().pages.map { it.fileName } }.toSet() + image.name
        compose.runOnUiThread { store.clear() }
        files.forEach { File(image.parentFile, it).delete() }
        database.close()
    }

    private fun openSourceManagement() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Quellliste"))
        compose.onNodeWithContentDescription("Liste Quellliste verwalten").performClick()
    }

    @Test fun folderNavigationArchiveAndRestoreKeepLearningState() {
        openSourceManagement()
        compose.onNodeWithText("Verschieben").performClick()
        compose.onNode(hasText("Schule") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.onNodeWithText("Hierher verschieben").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.first { it.deck.id == sourceId }.deck.folder == "Schule" }
        compose.onNodeWithText("Schule").performClick()
        openSourceManagement()
        compose.onNodeWithText("Liste archivieren").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.first { it.deck.id == sourceId }.deck.isArchived }
        compose.onNodeWithContentDescription("Bibliotheksoptionen").performClick()
        compose.onNodeWithText("Archiv öffnen").performClick()
        compose.onNodeWithText("Schule").performClick()
        openSourceManagement()
        compose.onNodeWithText("Wieder aktivieren").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.decks.value.first { it.deck.id == sourceId }.deck.isArchived }
        assertEquals(1, runBlocking { repository.deckSnapshot(sourceId).progress.single().reviews })
    }

    @Test fun copyingAndMergingThroughUiKeepsIndependentImageFiles() {
        openSourceManagement()
        compose.onNodeWithText("Liste duplizieren").performScrollTo().performClick()
        compose.onNodeWithText("Name der Kopie").performTextReplacement("Kopie")
        compose.onNodeWithContentDescription("Lernstand mitkopieren").performScrollTo().performClick()
        compose.onNodeWithText("Kopie erstellen").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.any { it.deck.name == "Kopie" } && !model.state.value.busy }
        val copiedId = model.decks.value.first { it.deck.name == "Kopie" }.deck.id
        val copied = runBlocking { repository.deckSnapshot(copiedId) }
        val copyFile = File(image.parentFile, copied.pages.single().fileName)
        assertNotEquals(image.name, copyFile.name)
        assertArrayEquals(image.readBytes(), copyFile.readBytes())
        assertEquals(1, copied.progress.single().reviews)
        compose.onNodeWithContentDescription("Liste verwalten").performClick()
        compose.onNodeWithText("Mit anderer Liste zusammenführen").performScrollTo().performClick()
        compose.onNodeWithText("Zielliste wählen").performClick()
        compose.onNode(hasText("Zielliste") and hasClickAction()).performClick()
        compose.onNodeWithText("Jetzt zusammenführen").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.decks.value.none { it.deck.id == copiedId } && !model.state.value.busy }
        val merged = runBlocking { repository.deckSnapshot(targetId) }
        assertEquals(1, merged.words.size)
        assertEquals(1, merged.logs.size)
        assertEquals(copyFile.name, merged.pages.single().fileName)
        assertTrue(copyFile.isFile)
        assertTrue(image.isFile)
    }
}
