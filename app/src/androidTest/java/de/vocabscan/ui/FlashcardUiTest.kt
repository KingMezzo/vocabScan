package de.vocabscan.ui

import androidx.compose.material3.Surface
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.*
import de.vocabscan.learn.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlashcardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun largeTextEditorKeepsPrimaryActionVisible() {
        compose.setContent { VocabTheme(ThemeMode.DARK) { Surface {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                WordEditorScreen(Vocabulary("w", "d", "Vertrag", "Zwei übereinstimmende Willenserklärungen."),
                    "", false, type = DeckType.LERNKARTEN, onCancel = {},
                    onSave = { _, _, _, _, _, _, _ -> })
            }
        } } }
        compose.onNodeWithText("Speichern").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").assertIsDisplayed()
        shot("flashcards-editor-dark-large")
    }

    @Test fun newListSelectsTypeBeforeNameAndOmitsLanguageFields() {
        var saved: DeckType? = null
        compose.setContent { VocabTheme(ThemeMode.LIGHT) { Surface {
            CreateDeckDialog({}, { _, _, _, type -> saved = type })
        } } }
        compose.onNodeWithText("Name der Liste").assertDoesNotExist()
        compose.onNodeWithText("Lernkarten (Begriff + Erklärung)").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Erstellen").assertIsNotEnabled()
        compose.onNodeWithText("Name der Liste").performTextInput("Fachbegriffe Recht")
        compose.onNodeWithText("Englisch").assertDoesNotExist()
        compose.onNodeWithText("Französisch").assertDoesNotExist()
        compose.onNodeWithText("Erstellen").performClick()
        assertEquals(DeckType.LERNKARTEN, saved)
    }

    @Test fun editorSavesMultilineExplanationAndHidesTranslationAlternatives() {
        var back = ""
        compose.setContent { VocabTheme(ThemeMode.DARK) { Surface {
            WordEditorScreen(null, "", false, type = DeckType.LERNKARTEN, onCancel = {},
                onSave = { _, explanation, _, _, _, _, _ -> back = explanation })
        } } }
        compose.onNodeWithText("Speichern").assertIsNotEnabled()
        compose.onNodeWithText("Begriff").performTextInput("Vertrag")
        compose.onNodeWithText("Erklärung").performTextInput("Erste Zeile\nZweite Zeile")
        compose.onNodeWithText("Weitere richtige Antworten").assertDoesNotExist()
        compose.onNodeWithText("Speichern").performClick()
        assertEquals("Erste Zeile\nZweite Zeile", back)
    }

    @Test fun inputPreferenceStillUsesSelfAssessmentForFlashcards() {
        var grade: ReviewRating? = null
        val card = StudyCard(Vocabulary("w", "d", "Vertrag", "Erklärung"),
            "", "Recht", StudyDirection.FOREIGN_TO_GERMAN, DeckType.LERNKARTEN)
        compose.setContent { VocabTheme(ThemeMode.LIGHT) { Surface {
            StudyScreen(StudySession(StudyMode.INPUT, listOf(card), 1), false, { grade = it }, {}, {})
        } } }
        compose.onNodeWithText("Deine Übersetzung").assertDoesNotExist()
        compose.onNodeWithContentDescription("Karte anhören").assertDoesNotExist()
        compose.onNodeWithText("Aufdecken").performScrollTo().performClick()
        compose.onNodeWithText("Gewusst").performScrollTo().performClick()
        assertEquals(ReviewRating.GOOD, grade)
    }

    @Test fun reverseFlashcardSupportsNotKnownRatingWithoutSpeech() {
        var grade: ReviewRating? = null
        val card = StudyCard(Vocabulary("w", "d", "Vertrag", "Erklärung"),
            "", "Recht", StudyDirection.GERMAN_TO_FOREIGN, DeckType.LERNKARTEN)
        compose.setContent { VocabTheme(ThemeMode.DARK) { Surface {
            StudyScreen(StudySession(StudyMode.INPUT, listOf(card), 1), false, { grade = it }, {}, {})
        } } }
        compose.onNodeWithText("Erklärung").assertIsDisplayed()
        shot("flashcards-study-dark")
        compose.onNodeWithText("Aufdecken").performScrollTo().performClick()
        compose.onNodeWithText("Nicht gewusst").performScrollTo().performClick()
        assertEquals(ReviewRating.AGAIN, grade)
    }

    @Test fun importShowsValidationAndPreservesSelectedDelimiter() {
        var imported = false
        compose.setContent { VocabTheme(ThemeMode.LIGHT) { Surface {
            CardTextImportDialog(false, {}, { _, _ -> }, { _, delimiter -> imported = delimiter == ';' })
        } } }
        compose.onNodeWithText("Importieren").assertIsNotEnabled()
        compose.onNodeWithText("Karten einfügen").performTextInput("Begriff")
        compose.onNodeWithText("Importieren").assertIsNotEnabled()
        compose.onNodeWithText("Karten einfügen").performTextReplacement("Begriff;Erklärung")
        compose.onNodeWithText("Importieren").performClick()
        assertTrue(imported)
    }
}
