package de.vocabscan.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.vocabscan.scan.ImportPair
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MergeRowsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsActualResultInPageOrderBeforeConfirming() {
        var confirmed = false
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            MergeRowsDialog(listOf(
                ImportPair(id = "a", term = "to look", translation = "sich"),
                ImportPair(id = "b", term = "forward to", translation = "freuen auf")),
                "b", "a", "en", "de", false, {}, { confirmed = true })
        } }
        compose.onNodeWithText("to look forward to").assertIsDisplayed()
        compose.onNodeWithText("sich freuen auf").assertIsDisplayed()
        compose.onNodeWithText("Ergebnis · 1 Wortpaar").assertIsDisplayed()
        assertFalse(confirmed)
        compose.onNodeWithText("Zu einem Wortpaar zusammenführen").performClick()
        assertTrue(confirmed)
    }

    @Test fun emptySelectionCannotBeConfirmed() {
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            MergeRowsDialog(emptyList(), "a", "b", "en", "de", false, {}, {})
        } }
        compose.onNodeWithText("Wähle zwei verschiedene Zeilen derselben Seite aus.").assertIsDisplayed()
        compose.onNodeWithText("Zu einem Wortpaar zusammenführen").assertIsNotEnabled()
    }
}
