package de.vocabscan.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.MainActivity
import de.vocabscan.VocabScanApplication
import de.vocabscan.data.StudyLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createsAndStudiesAnEnglishDefinitionList() {
        val repository = (compose.activity.application as VocabScanApplication).repository
        val name = "Definitions-${System.currentTimeMillis()}"
        try {
            compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
            compose.onNodeWithText("Neue Liste").performClick()
            compose.onNodeWithText("Weiter").performClick()
            compose.onNodeWithText("Name der Liste").performTextInput(name)
            compose.onNodeWithText("Nach").performClick()
            compose.onNode(hasText("Englisch") and isSelectable()).performClick()
            compose.onNodeWithText("Liste erstellen").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Vokabel hinzufügen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Englisch → Englisch").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vokabel hinzufügen").performClick()
            compose.onNodeWithText("Begriff · Englisch").performTextInput("enormous")
            compose.onNodeWithText("Erklärung / Synonym · Englisch").performTextInput("very large")
            compose.onNodeWithText("Speichern").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Begriff → Erklärung / Synonym · 1 fällig").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Begriff → Erklärung / Synonym · 1 fällig").performClick()
            compose.onNodeWithText("Karteikarten").performScrollTo().performClick()
            compose.onNodeWithText("Eingeben").performClick()
            compose.onNodeWithText("Lernrunde starten").performScrollTo().performClick()
            compose.onNodeWithText("Deine Erklärung / dein Synonym").performTextInput("very large")
            compose.onNodeWithText("Antwort prüfen").performClick()
            compose.onNodeWithText("Deine Eingabe stimmt mit der Lösung überein.").assertIsDisplayed()
            compose.onNodeWithText("Gut").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Runde geschafft!").fetchSemanticsNodes().isNotEmpty() }
            val snapshot = runBlocking { repository.fullSnapshot() }
            val deck = snapshot.decks.single { it.name == name }
            org.junit.Assert.assertEquals("en", deck.targetLanguage)
            org.junit.Assert.assertEquals(0, snapshot.progress.single { p -> snapshot.words.any { it.deckId == deck.id && it.id == p.wordId } }.direction)
            compose.onNodeWithText("Weiter").performScrollTo().performClick()
        } finally {
            runBlocking { repository.decks.first().firstOrNull { it.deck.name == name }?.let { repository.deleteDeck(it.deck) } }
        }
    }

    @Before fun finishFirstRunIfNeeded() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Überspringen").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Überspringen").performClick()
    }

    @Test fun onboardingCanBeReplayedAndCompletionSurvivesRecreation() {
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Einführung noch einmal ansehen").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Los geht’s").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Überspringen").assertDoesNotExist()
        org.junit.Assert.assertTrue(compose.activity.getSharedPreferences("onboarding", 0).getBoolean("completed", false))
    }

    @Test fun createsEditsAndDeletesVocabularyList() {
        val name = "UI-Test ${System.currentTimeMillis()}"
        compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
        compose.onNodeWithText("Neue Liste").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Name der Liste").performTextInput(name)
        compose.onNodeWithText("Liste erstellen").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Liste exportieren").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Vokabel hinzufügen").performClick()
        compose.onNodeWithText("Begriff · Englisch").performTextInput("apple")
        compose.onNodeWithText("Übersetzung · Deutsch").performTextInput("Apfl")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Notiz (optional)"))
        compose.onNodeWithText("Notiz (optional)").performTextInput("Ein Apfel am Tag")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ordnen & wiederfinden"))
        compose.onNodeWithText("Ordnen & wiederfinden").performClick()
        compose.onNodeWithText("Kapitel (optional)").performScrollTo().performTextInput("Unit 1")
        compose.onNodeWithText("Tags (mit Komma)").performScrollTo().performTextInput("Obst")
        compose.onNodeWithText("Speichern").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("apple"))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("apple").performClick()
        compose.onNodeWithText("Vokabeldetails").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ein Apfel am Tag"))
        compose.onNodeWithText("Ein Apfel am Tag").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Noch nicht geplant"))
        compose.onNodeWithText("Noch nicht geplant").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Vokabel bearbeiten"))
        compose.onNodeWithText("Vokabel bearbeiten").performClick()
        compose.onNode(hasSetTextAction() and hasText("Übersetzung · Deutsch")).performTextReplacement("Apfel")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Apfel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Apfel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithContentDescription("Liste verwalten").performClick()
        compose.onNodeWithText("Liste löschen").performScrollTo().performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Zur Bibliothek hinzufügen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).assertDoesNotExist()
    }

    @Test fun globalSearchFavoritesAndDeleteUndoKeepWord() {
        val repository = (compose.activity.application as VocabScanApplication).repository
        val marker = "Suche-${System.currentTimeMillis()}"
        val deckId = runBlocking {
            repository.createDeck(marker, StudyLanguage.ENGLISH).also {
                repository.saveWord(it, null, "unique-$marker", "einzigartig", "")
            }
        }
        try {
            compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Bibliothek durchsuchen").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(marker)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("unique-$marker").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Als Favorit merken").performClick()
            compose.onNodeWithText("Nur Favoriten").performClick()
            compose.onNodeWithText("unique-$marker").assertExists()
            compose.onNodeWithText("unique-$marker").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Vokabeldetails").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Vokabel löschen"))
            compose.onNodeWithText("Vokabel löschen").performClick()
            compose.onNodeWithText("Löschen").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Rückgängig").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Rückgängig").performClick()
            compose.waitUntil(10_000) {
                runBlocking { repository.decks.first().first { it.deck.id == deckId }.words.any { it.term == "unique-$marker" } }
            }
            org.junit.Assert.assertTrue(runBlocking { repository.decks.first().first { it.deck.id == deckId }.words.single().isFavorite })
        } finally {
            runBlocking { repository.decks.first().firstOrNull { it.deck.id == deckId }?.let { repository.deleteDeck(it.deck) } }
        }
    }

    @Test fun studiesAndSchedulesWord() {
        val name = "Lern-Test ${System.currentTimeMillis()}"
        compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Zur Bibliothek hinzufügen").performClick()
        compose.onNodeWithText("Neue Liste").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Name der Liste").performTextInput(name)
        compose.onNodeWithText("Liste erstellen").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Liste exportieren").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Vokabel hinzufügen").performClick()
        compose.onNodeWithText("Begriff · Englisch").performTextInput("apple")
        compose.onNodeWithText("Übersetzung · Deutsch").performTextInput("Apfel")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Englisch → Deutsch · 1 fällig").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Englisch → Deutsch · 1 fällig").performClick()
        compose.onNodeWithText("Karteikarten").performScrollTo().performClick()
        compose.onNodeWithText("Eingeben").performClick()
        compose.onNodeWithText("Lernrunde starten").performScrollTo().performClick()
        compose.onNodeWithText("Deine Übersetzung").performTextInput("Apfel")
        compose.onNodeWithText("Antwort prüfen").performClick()
        compose.onNodeWithText("Deine Eingabe stimmt mit der Lösung überein.").assertIsDisplayed()
        compose.onNodeWithText("Wie lief's?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gut").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Weiter").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Englisch → Deutsch · 0 fällig").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Deutsch → Englisch · 1 fällig").performClick()
        compose.onNodeWithText("Lernrunde starten").performScrollTo().performClick()
        compose.onNodeWithText("Zum Umdrehen tippen").performClick()
        compose.onNodeWithText("Leicht").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Weiter").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Deutsch → Englisch · 0 fällig").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("apple"))
        compose.onNodeWithText("apple").assertExists()
        compose.onNodeWithText("Englisch → Deutsch · Im Aufbau", substring = true).assertExists()
        compose.onNodeWithText("Deutsch → Englisch · Im Aufbau", substring = true).assertExists()
        compose.onNodeWithText("Im Aufbau", substring = true).assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.onNodeWithText("Statistik").performClick()
        compose.onNodeWithText("Heute wiederholt").assertIsDisplayed()
        compose.onNodeWithText("Einstellungen").performClick()
        compose.onNodeWithText("Dunkel").performClick()
        compose.onNodeWithText("System").performClick()
        compose.onNode(hasText("Bibliothek") and hasClickAction()).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithContentDescription("Liste verwalten").performClick()
        compose.onNodeWithText("Liste löschen").performScrollTo().performClick()
        compose.onNodeWithText("Löschen").performClick()
    }

    @Test fun centralRoundMixesLanguagesAndDirectionsWithTagFilter() {
        val repository = (compose.activity.application as VocabScanApplication).repository
        val marker = "Runde-${System.currentTimeMillis()}"
        val ids = runBlocking {
            val en = repository.createDeck("English $marker", StudyLanguage.ENGLISH)
            val fr = repository.createDeck("French $marker", StudyLanguage.FRENCH)
            repository.saveWord(en, null, "house", "Haus", "", translationAlternatives = "Gebäude", chapter = "Unit 1", tags = marker)
            repository.saveWord(fr, null, "école", "Schule", "", chapter = "Unit 1", tags = marker)
            listOf(en, fr)
        }
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Runde anpassen").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Runde anpassen").performClick()
            compose.onNodeWithText("Alle Tags").performScrollTo().performClick()
            compose.onNodeWithText(marker).performClick()
            compose.onNodeWithText("Lernrunde starten").performScrollTo().performClick()
            val seen = mutableSetOf<String>()
            repeat(4) { index ->
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("${index + 1} von 4 ·", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                if (compose.onAllNodesWithText("English $marker · Englisch → Deutsch").fetchSemanticsNodes().isNotEmpty()) seen += "en"
                if (compose.onAllNodesWithText("French $marker · Französisch → Deutsch").fetchSemanticsNodes().isNotEmpty()) seen += "fr"
                compose.onNodeWithText("Zum Umdrehen tippen").performScrollTo().performClick()
                compose.onNodeWithText("Gut").performScrollTo().performClick()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Runde geschafft!").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Runde geschafft!").assertIsDisplayed()
            compose.onNodeWithText("4 / 4").assertIsDisplayed()
            org.junit.Assert.assertEquals(setOf("en", "fr"), seen)
            compose.onNodeWithText("Weiter").performScrollTo().performClick()
        } finally {
            runBlocking { repository.decks.first().filter { it.deck.id in ids }.forEach { repository.deleteDeck(it.deck) } }
        }
    }
}
