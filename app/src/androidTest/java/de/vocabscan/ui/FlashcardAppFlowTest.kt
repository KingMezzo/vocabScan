package de.vocabscan.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.MainActivity
import de.vocabscan.VocabScanApplication
import de.vocabscan.data.DeckType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FlashcardAppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.cacheDir, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun createsTwoCardsInSequenceAndCompletesRoundWithSharedStatistics() {
        val repository = (compose.activity.application as VocabScanApplication).repository
        val marker = "Recht UI " + System.currentTimeMillis()
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Überspringen").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Überspringen").performClick()
        try {
            compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
            compose.onNodeWithText("Neue Liste").performClick()
            compose.onNodeWithText("Lernkarten (Begriff + Erklärung)").performClick()
            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Name der Liste").performTextInput(marker)
            compose.onNodeWithText("Erstellen").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Platz für deine ersten Karten").fetchSemanticsNodes().isNotEmpty() }
            shot("flashcards-empty")
            compose.onNodeWithContentDescription("Karte hinzufügen").performClick()
            compose.onNodeWithText("Begriff").performTextInput("Vertrag")
            compose.onNodeWithText("Erklärung").performTextInput("Erklärung\nZweite Zeile")
            compose.onNode(isToggleable()).performClick()
            compose.onNodeWithText("Speichern").performClick()
            compose.waitUntil(10_000) {
                compose.onNode(hasSetTextAction() and hasText("Begriff")).fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText].text.isEmpty()
            }
            compose.onNodeWithText("Begriff").performTextInput("Angebot")
            compose.onNodeWithText("Erklärung").performTextInput("Eine empfangsbedürftige Willenserklärung.")
            compose.onNode(isToggleable()).performClick()
            compose.onNodeWithText("Speichern").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("2 Karten").fetchSemanticsNodes().isNotEmpty() }
            shot("flashcards-list")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Vertrag"))
            compose.onNodeWithText("Vertrag").performClick()
            compose.onNodeWithText("Kartendetails").assertIsDisplayed()
            compose.onNodeWithContentDescription("Karte anhören").assertDoesNotExist()
            shot("flashcards-details")
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText("Lernrunde starten").performClick()
            compose.onNodeWithText("Beide Richtungen gemischt").performClick()
            compose.onNodeWithText("Vorderseite → Rückseite").performClick()
            compose.onAllNodesWithText("Lernrunde starten").onLast().performScrollTo().performClick()
            repeat(2) {
                compose.onNodeWithText("Aufdecken").performScrollTo().performClick()
                compose.onNodeWithText("Gewusst").performScrollTo().performClick()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Runde geschafft!").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Gewusst").assertExists()
            compose.onNodeWithText("Nicht gewusst").assertExists()
            shot("flashcards-summary")
            runBlocking {
                val data = repository.fullSnapshot()
                val deck = data.decks.single { it.name == marker }
                assertEquals(DeckType.LERNKARTEN, deck.type)
                assertEquals("", deck.language)
                val ids = data.words.filter { it.deckId == deck.id }.map { it.id }.toSet()
                assertEquals(2, ids.size)
                assertEquals(2, data.logs.count { it.wordId in ids })
                assertEquals(2, data.progress.count { it.wordId in ids })
            }
        } finally {
            runBlocking { repository.fullSnapshot().decks.filter { it.name == marker }.forEach { repository.deleteDeck(it) } }
        }
    }
}
