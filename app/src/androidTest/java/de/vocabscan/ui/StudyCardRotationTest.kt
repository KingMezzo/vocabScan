package de.vocabscan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.Vocabulary
import de.vocabscan.learn.StudyCard
import de.vocabscan.learn.StudyDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyCardRotationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nextCardNeverShowsItsBackDuringPreviousCardsReturnRotation() {
        fun card(id: String, prompt: String, solution: String) = StudyCard(
            Vocabulary(id, "deck", prompt, solution), "en", "Liste", StudyDirection.FOREIGN_TO_GERMAN)
        val first = card("first", "front-one", "back-one")
        val next = card("next", "front-two", "back-two")
        var session by mutableStateOf(StudySession(StudyMode.CARDS, listOf(first, next), 2, startedAt = 0L))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                StudyScreen(session, busy = false, onGrade = {}, onSaveAlternative = {}, onEdit = {})
            }
        }

        compose.onNodeWithText("front-one").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("back-one").assertExists()

        compose.runOnUiThread { session = session.copy(queue = listOf(next), completed = 1, attempt = 1) }
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("front-two").assertExists()
            compose.onNodeWithText("back-two").assertDoesNotExist()
        }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText("back-one").assertDoesNotExist()
        compose.onNodeWithText("front-two").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("back-two").assertExists()
    }
}
