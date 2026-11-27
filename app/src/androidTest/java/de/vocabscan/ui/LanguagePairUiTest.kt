package de.vocabscan.ui

import androidx.compose.material3.Surface
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.StateRestorationTester
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
class LanguagePairUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun latinCanBeChosenOnBothSidesAndSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        var saved: Pair<StudyLanguage, StudyLanguage>? = null
        restoration.setContent { VocabTheme(ThemeMode.LIGHT) {
            DeckDialog(null, {}, { _, source, target -> saved = source to target })
        } }
        compose.onNodeWithText("Name der Liste").performTextInput("Lateinische Synonyme")
        for (label in listOf("Von", "Nach")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNode(hasText("Latein") and isSelectable()).performScrollTo().performClick()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Beide Seiten sind Latein.", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("latin-pairs-light")
        compose.onNodeWithText("Liste erstellen").performClick()
        assertEquals(StudyLanguage.LATIN to StudyLanguage.LATIN, saved)
    }

    @Test fun latinTargetRemainsReachableWithLargeTextInDarkMode() {
        var saved: Pair<StudyLanguage, StudyLanguage>? = null
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DeckDialog(null, {}, { _, source, target -> saved = source to target })
            }
        } }
        compose.onNodeWithText("Name der Liste").performTextInput("English to Latin")
        compose.onNodeWithText("Nach").performScrollTo().performClick()
        compose.onNode(hasText("Latein") and isSelectable()).performScrollTo().assertIsDisplayed()
        screenshot("latin-selection-dark-large")
        compose.onNode(hasText("Latein") and isSelectable()).performClick()
        compose.onNodeWithText("Liste erstellen").assertIsDisplayed().performClick()
        assertEquals(StudyLanguage.ENGLISH to StudyLanguage.LATIN, saved)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Window exit animations are outside Compose's test clock.
        android.os.SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun creationSelectsTheSameLanguageAndSavesBothSides() {
        var saved: Pair<StudyLanguage, StudyLanguage>? = null
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            CreateDeckDialog({}, { _, source, target, _ -> saved = source to target })
        } }
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Liste erstellen").assertIsNotEnabled()
        compose.onNodeWithText("Name der Liste").performTextInput("Definitions")
        compose.onNodeWithText("Nach").performClick()
        compose.onNode(hasText("Englisch") and isSelectable()).performClick()
        compose.onNodeWithText("Begriff → Erklärung / Synonym.", substring = true).assertIsDisplayed()
        screenshot("language-pairs-light")
        compose.onNodeWithText("Liste erstellen").performClick()
        assertEquals(StudyLanguage.ENGLISH to StudyLanguage.ENGLISH, saved)
    }

    @Test fun sourceAndTargetCanBeChosenIndependentlyInDarkMode() {
        var saved: Pair<StudyLanguage, StudyLanguage>? = null
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            DeckDialog(null, {}, { _, source, target -> saved = source to target })
        } }
        compose.onNodeWithText("Name der Liste").performTextInput("Wörter")
        compose.onNodeWithText("Von").performClick()
        compose.onNode(hasText("Deutsch") and isSelectable()).performClick()
        compose.onNodeWithText("Nach").performClick()
        compose.onNode(hasText("Französisch") and isSelectable()).performClick()
        screenshot("language-pairs-dark")
        compose.onNodeWithText("Liste erstellen").performClick()
        assertEquals(StudyLanguage.GERMAN to StudyLanguage.FRENCH, saved)
    }

    @Test fun sameLanguageInputChecksTheStoredSynonym() {
        val card = StudyCard(Vocabulary("w", "d", "enormous", "very large"), "en", "Definitions",
            StudyDirection.FOREIGN_TO_GERMAN, targetLanguage = "en")
        compose.setContent { VocabTheme(ThemeMode.DARK) { Surface {
            StudyScreen(StudySession(StudyMode.INPUT, listOf(card), 1), false, {}, {}, {})
        } } }
        compose.onNodeWithText("Begriff → Erklärung / Synonym", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Deine Erklärung / dein Synonym").performTextInput("very large")
        compose.onNodeWithText("Antwort prüfen").performClick()
        compose.onNodeWithText("Deine Eingabe stimmt mit der Lösung überein.").assertIsDisplayed()
    }

    @Test fun latinAnswerExplainsMissingMacrons() {
        val card = StudyCard(Vocabulary("w", "d", "to love", "amāre"), "en", "Latein",
            StudyDirection.FOREIGN_TO_GERMAN, targetLanguage = "la")
        compose.setContent { VocabTheme(ThemeMode.LIGHT) { Surface {
            StudyScreen(StudySession(StudyMode.INPUT, listOf(card), 1), false, {}, {}, {})
        } } }
        compose.onNodeWithText("Deine Übersetzung").performTextInput("amare")
        compose.onNodeWithText("Antwort prüfen").performClick()
        compose.onNodeWithText("Fast richtig: Achte auf die Längenzeichen.", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun busyCreationDisablesLanguageChangesAndSaving() {
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            DeckDialog(null, {}, { _, _, _ -> fail("Saved while busy") }, busy = true, previewName = "Definitions")
        } }
        compose.onNodeWithText("Liste erstellen").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").assertIsNotEnabled()
        compose.onNodeWithText("Von").assertIsNotEnabled()
        compose.onNodeWithText("Nach").assertIsNotEnabled()
    }

    @Test fun nameAndPairSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        var saved: Pair<StudyLanguage, StudyLanguage>? = null
        restoration.setContent { VocabTheme(ThemeMode.LIGHT) {
            DeckDialog(null, {}, { name, source, target -> assertEquals("Definitions", name); saved = source to target })
        } }
        compose.onNodeWithText("Name der Liste").performTextInput("Definitions")
        compose.onNodeWithText("Nach").performClick()
        compose.onNode(hasText("Englisch") and isSelectable()).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Liste erstellen").performClick()
        assertEquals(StudyLanguage.ENGLISH to StudyLanguage.ENGLISH, saved)
    }

    @Test fun errorAndLargeTextKeepRetryActionReachable() {
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DeckDialog(null, {}, { _, _, _ -> }, previewName = "Definitions", previewTarget = StudyLanguage.ENGLISH,
                    errorMessage = "Speichern fehlgeschlagen. Bitte erneut versuchen.")
            }
        } }
        compose.onNodeWithText("Liste erstellen").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Speichern fehlgeschlagen. Bitte erneut versuchen.").performScrollTo().assertIsDisplayed()
        screenshot("language-pairs-dark-large-error")
    }
}
