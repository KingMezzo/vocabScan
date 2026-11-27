package de.vocabscan.ui

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listMenusKeepAllExistingImportAndExportCallbacks() {
        val data = mutableStateOf(DeckWithVocabulary(Deck("d", "Unit 1", "en"), emptyList()))
        val calls = mutableListOf<String>()
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            DeckScreen(data.value, listOf(data.value.deck), emptyList(), busy = false,
                scanPages = emptyList(), scanFile = { Uri.EMPTY }, onEdit = {}, onFavorite = {},
                onShareQr = { calls += "qr" }, onShareCsv = { calls += "csv" },
                onScan = { calls += "scan" }, onPhoto = { calls += "photo" },
                onImportText = { calls += "text" }, onStudy = {}, onPractice = {}, onPracticeAll = {},
                onStudyScan = {}, onTransfer = { _, _, _ -> })
        } }
        compose.onNodeWithText("Arbeitsblatt scannen").assertDoesNotExist()
        compose.onNodeWithText("Bilder importieren").assertDoesNotExist()
        listOf("Arbeitsblatt scannen", "Bilder importieren", "Text oder CSV importieren").forEach { method ->
            compose.onNodeWithText("Importieren").performClick()
            compose.onNodeWithText(method).performClick()
            compose.onNodeWithText(method).assertDoesNotExist()
        }
        compose.onNodeWithText("Liste exportieren").performClick()
        compose.onNodeWithText("QR-Code").assertIsNotEnabled()
        compose.onNodeWithText("CSV").performClick()
        compose.runOnIdle {
            data.value = data.value.copy(words = listOf(Vocabulary("w", "d", "apple", "Apfel")))
        }
        compose.onNodeWithText("Liste exportieren").performClick()
        compose.onNodeWithText("QR-Code").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("scan", "photo", "text", "csv", "qr"), calls) }
    }

    @Test fun libraryImportSelectionKeepsAllCallbacks() {
        val calls = mutableListOf<String>()
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            LibraryAddSheet({}, {}, {}, { calls += "qr" }, { calls += "csv" }, { calls += "restore" })
        } }
        compose.onNodeWithText("QR-Code").assertDoesNotExist()
        compose.onNodeWithText("Importieren").performClick()
        listOf("QR-Code", "CSV-Datei", "Sicherung wiederherstellen").forEach { method ->
            compose.onNodeWithText(method).performClick()
        }
        compose.runOnIdle { assertEquals(listOf("qr", "csv", "restore"), calls) }
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Neue Liste").assertIsDisplayed()
    }

    @Test fun flashcardsOnlyOfferTextImportAndBusyMenusDisableEveryMethod() {
        val busy = mutableStateOf(false)
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            DeckImportSheet(true, busy.value, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Arbeitsblatt scannen").assertDoesNotExist()
        compose.onNodeWithText("Bilder importieren").assertDoesNotExist()
        compose.onNodeWithText("Text oder CSV importieren").assertIsEnabled()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Text oder CSV importieren").assertIsNotEnabled()
    }
}
