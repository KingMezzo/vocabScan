package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.Vocabulary
import de.vocabscan.learn.StudyCard
import de.vocabscan.learn.StudyDirection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyLearningStateLeadsToLibrary() {
        var openedLibrary = false
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            LearnScreen(0, false, false, 0, 10, {}, {}, { openedLibrary = true })
        } }
        compose.onNodeWithText("Zur Bibliothek").performClick()
        compose.runOnIdle { assertTrue(openedLibrary) }
        compose.onNodeWithContentDescription("Lernoptionen").assertDoesNotExist()
    }

    @Test fun completedLearningStateAllowsFreePractice() {
        var openedPractice = false
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            LearnScreen(0, true, false, 10, 10, {}, { openedPractice = true }, {})
        } }
        compose.onNodeWithText("Nichts fällig").assertIsDisplayed()
        compose.onNodeWithText("Frei üben").performClick()
        compose.runOnIdle { assertTrue(openedPractice) }
    }

    @Test fun learningActionFitsAtDoubleFontScale() {
        var started = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VocabTheme(ThemeMode.DARK) { Box(Modifier.width(320.dp)) {
                    LearnScreen(10, true, false, 3, 10, { started = true }, {}, {})
                } }
            }
        }
        val button = compose.onNodeWithText("Jetzt lernen").performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        button.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("Learning button clipped: size=${layout.size}; lines=${layout.lineCount}; width=${layout.didOverflowWidth}; height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        button.performClick()
        compose.runOnIdle { assertTrue(started) }
    }

    @Test fun qrCarouselAdvancesPausesAndWrapsWhenManual() {
        compose.mainClock.autoAdvance = false
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                QrCarousel(listOf("one", "two", "three"), 30)
            }
        } }
        compose.onNodeWithText("Code 1 von 3").assertExists()
        compose.mainClock.advanceTimeBy(QR_FRAME_MILLIS + 100)
        compose.onNodeWithText("Code 2 von 3").assertExists()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Pausieren").performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(QR_FRAME_MILLIS * 2)
        compose.onNodeWithText("Code 2 von 3").assertExists()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Nächster Code").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Code 3 von 3").assertExists()
        compose.onNodeWithContentDescription("Nächster Code").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Code 1 von 3").assertExists()
    }

    @Test fun favoriteAnnouncesStateAndProducesOneHapticTick() {
        var favorite by mutableStateOf(false)
        var ticks = 0
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks++ }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                VocabTheme(ThemeMode.LIGHT) { FavoriteButton(favorite, "apple", true) { favorite = !favorite } }
            }
        }
        compose.onNodeWithContentDescription("Als Favorit merken").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Favorit entfernen").assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "apple, als Favorit gespeichert"))
        compose.runOnIdle { assertEquals(1, ticks) }
    }

    @Test fun ratingLabelsFitAtDoubleFontScaleAndStillGrade() {
        val card = StudyCard(Vocabulary("w", "d", "house", "Haus"), "en", "Liste", StudyDirection.FOREIGN_TO_GERMAN)
        var grades = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VocabTheme(ThemeMode.LIGHT) {
                    Box(Modifier.width(320.dp)) {
                        StudyScreen(StudySession(StudyMode.CARDS, listOf(card), 1), false,
                            onGrade = { grades++ }, onSaveAlternative = {}, onEdit = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Zum Umdrehen tippen").performScrollTo().performClick()
        listOf("Nochmal", "Schwer", "Gut", "Leicht").forEach { label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("Label clipped: $label; size=${layout.size}; lines=${layout.lineCount}; " +
                "width=${layout.didOverflowWidth}; height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        }
        compose.onNodeWithText("Gut").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, grades) }
    }

    @Test fun qrImportShowsOnlyMissingCodes() {
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            QrImportProgress(setOf(1, 3), 4, scanning = false, automatic = false,
                onAutomaticChange = {}, onScan = {}, onDismiss = {})
        } }
        compose.onNodeWithText("2 von 4 Codes erfasst").assertIsDisplayed()
        compose.onNodeWithText("Noch offen: 2, 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Automatisch weiterscannen").assertIsOff()
    }
}
