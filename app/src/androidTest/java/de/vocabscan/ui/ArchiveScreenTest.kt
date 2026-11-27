package de.vocabscan.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsOnlyFoldersWithArchivedListsAndNeverAnIncorrectEmptyMessage() {
        val folder = Folder("school", "Schule", isArchived = true)
        val data = LibraryContents(
            listOf(DeckWithVocabulary(Deck("list", "Unit 1", "en", folderId = folder.id, isArchived = true), emptyList())),
            listOf(folder, Folder("empty", "Leerer Ordner")), loading = false)
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            ArchiveScreen(data, null, false, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Schule").assertIsDisplayed()
        compose.onNodeWithText("Leerer Ordner").assertDoesNotExist()
        compose.onNodeWithText("Ohne Ordner").assertDoesNotExist()
        compose.onNodeWithText("Keine archivierten Listen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ordner Schule verwalten").assertDoesNotExist()
    }

    @Test fun loadingFailureAndEmptyStatesProvideTheCorrectNextStep() {
        val contents = mutableStateOf(LibraryContents())
        var retried = false
        var returned = false
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            ArchiveScreen(contents.value, null, false, {}, {}, {}, { returned = true }, { retried = true })
        } }
        compose.onNodeWithText("Archiv wird geladen …").assertIsDisplayed()
        compose.onNodeWithText("Noch keine archivierten Listen").assertDoesNotExist()
        compose.runOnIdle { contents.value = LibraryContents(loading = false, failed = true) }
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.runOnIdle { assertTrue(retried); contents.value = LibraryContents(loading = false) }
        compose.onNodeWithText("Noch keine archivierten Listen").assertIsDisplayed()
        compose.onNodeWithText("Zur Bibliothek").performClick()
        compose.runOnIdle { assertTrue(returned) }
    }

    @Test fun archivedListKeepsLearningAndExportActionsInsideTheSheet() {
        val deck = Deck("archived", "Alte Wörter", "en", isArchived = true)
        val word = Vocabulary("word", deck.id, "house", "Haus")
        var practiced = false
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            DeckScreen(DeckWithVocabulary(deck, listOf(word)), listOf(deck), emptyList(),
                busy = false, scanPages = emptyList(), scanFile = { android.net.Uri.EMPTY },
                onEdit = {}, onFavorite = {}, onShareQr = {}, onShareCsv = {}, onScan = {}, onPhoto = {},
                onStudy = {}, onPractice = { practiced = true }, onPracticeAll = {}, onStudyScan = {},
                onTransfer = { _, _, _ -> })
        } }
        compose.onNodeWithText("Arbeitsblatt scannen").assertDoesNotExist()
        compose.onNodeWithText("Weitere Listenaktionen").performClick()
        compose.onNodeWithText("Importieren").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Liste exportieren").performScrollTo().performClick()
        compose.onNodeWithText("QR-Code").assertIsDisplayed()
        compose.onNodeWithText("CSV").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("Weitere Listenaktionen").performClick()
        compose.onNodeWithText("Gezielte Lernrunde").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(practiced) }
        compose.onNodeWithText("Listenaktionen").assertDoesNotExist()
    }
}
