package de.vocabscan.ui

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckGroupingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scanGroupIsVisibleAndSelectedWordCanBeCopied() {
        val source = Deck("source", "Quelle", "en")
        val target = Deck("target", "Ziel", "en")
        val apple = Vocabulary("apple", source.id, "apple", "Apfel", scanGroupId = "scan-one")
        val house = Vocabulary("house", source.id, "house", "Haus")
        var studiedGroup: String? = null
        var transferred: Triple<Set<String>, String, Boolean>? = null
        compose.setContent {
            VocabTheme(ThemeMode.LIGHT) { Surface {
                DeckScreen(
                    data = DeckWithVocabulary(source, listOf(apple, house)),
                    availableDecks = listOf(source, target), progress = emptyList(), busy = false,
                    scanPages = emptyList(), scanFile = { Uri.EMPTY },
                    onEdit = {}, onFavorite = {}, onShareQr = {}, onShareCsv = {},
                    onScan = {}, onPhoto = {}, onStudy = {}, onPractice = {}, onPracticeAll = {},
                    onStudyScan = { studiedGroup = it },
                    onTransfer = { ids, deckId, move -> transferred = Triple(ids, deckId, move) },
                )
            } }
        }
        val list = compose.onAllNodes(hasScrollAction()).onFirst()
        list.performScrollToNode(hasText("Diese Scangruppe üben"))
        compose.onNodeWithText("Scan 1").assertExists()
        compose.onNodeWithText("Diese Scangruppe üben").performClick()
        compose.runOnIdle { assertEquals("scan-one", studiedGroup) }

        list.performScrollToNode(hasText("Auswählen"))
        compose.onNodeWithText("Auswählen").performClick()
        list.performScrollToNode(hasText("apple"))
        compose.onNodeWithText("apple").performClick()
        list.performScrollToNode(hasText("Kopieren oder verschieben"))
        compose.onNodeWithText("Kopieren oder verschieben").performClick()
        compose.onNodeWithText("Vokabeln übertragen").assertExists()
        compose.onAllNodesWithText("Kopieren").onLast().performClick()
        compose.runOnIdle { assertEquals(Triple(setOf("apple"), "target", false), transferred) }
    }
}
