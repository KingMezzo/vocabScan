package de.vocabscan.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.vocabscan.ui.components.ReleaseNotesDialog
import de.vocabscan.ui.components.UpdateDialog
import de.vocabscan.update.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    private val notes = ReleaseNotes(16, "0.14.0", listOf("Neue Funktionen"))

    @Test fun downloadingShowsProgressAndAllowsCancellation() {
        var cancelled = 0
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            UpdateDialog(notes, true, 0.42f, false, null, {}, {}, { cancelled++ })
        } }
        compose.onNodeWithText("Download: 42 %").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.42f, 0f..1f)))
            .assertIsDisplayed()
        compose.onNodeWithText("Wird heruntergeladen …").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun failedDownloadOffersRetry() {
        var retries = 0
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            UpdateDialog(notes, false, 0f, false, "Download fehlgeschlagen", { retries++ }, {}, {})
        } }
        compose.onNodeWithText("Download fehlgeschlagen").assertIsDisplayed()
        compose.onNodeWithText("Erneut herunterladen").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun verifiedDownloadOffersInstallation() {
        var installs = 0
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            UpdateDialog(notes, false, 1f, true, null, {}, { installs++ }, {})
        } }
        compose.onNodeWithText("Update bereit").assertIsDisplayed()
        compose.onNodeWithText("Installieren").performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }

    @Test fun lightUpdateDialogOffersDownloadAndPostpone() {
        var downloads = 0
        var postponed = 0
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            ReleaseNotesDialog("Update verfügbar", notes, "Herunterladen", { downloads++ }, { postponed++ }, "Später")
        } }
        compose.onNodeWithText("Version 0.14.0").assertIsDisplayed()
        compose.onNodeWithText("Neue Funktionen").assertIsDisplayed()
        compose.onNodeWithText("Herunterladen").performClick()
        compose.onNodeWithText("Später").performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(1, postponed)
        }
    }

    @Test fun darkWhatsNewHandlesEmptyNotesWithOneAction() {
        var acknowledged = 0
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            ReleaseNotesDialog("Was ist neu", notes.copy(changelog = emptyList()), "Alles klar",
                { acknowledged++ }, { acknowledged++ })
        } }
        compose.onNodeWithText("Für diese Version sind keine weiteren Details hinterlegt.").assertIsDisplayed()
        compose.onNodeWithText("Später").assertDoesNotExist()
        compose.onNodeWithText("Alles klar").performClick()
        compose.runOnIdle { assertEquals(1, acknowledged) }
    }

    @Test fun releaseNotesRenderHeadingsEmphasisAndLinksWithoutMarkdownMarkers() {
        compose.setContent { VocabTheme(ThemeMode.LIGHT) {
            ReleaseNotesDialog("Was ist neu", notes.copy(changelog = listOf(
                "## Verbesserungen\n\n**Schneller** und *übersichtlicher*.\n\n[Details](https://example.org)")),
                "Alles klar", {}, {})
        } }
        compose.onNodeWithText("Verbesserungen").assertIsDisplayed()
        compose.onNodeWithText("Schneller und übersichtlicher.").assertIsDisplayed()
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithText("## Verbesserungen").assertDoesNotExist()
        compose.onNodeWithText("Alles klar").assertIsDisplayed()
    }

    @Test fun longChangelogKeepsActionsVisibleAtLargeFontSize() {
        compose.setContent { VocabTheme(ThemeMode.DARK) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 2f)
            ) {
                ReleaseNotesDialog("Update verfügbar", notes.copy(changelog = List(30) { "Änderung $it" }),
                    "Herunterladen", {}, {}, "Später")
            }
        } }
        compose.onNodeWithText("Herunterladen").assertIsDisplayed()
        compose.onNodeWithText("Später").assertIsDisplayed()
        compose.onNodeWithText("Änderung 29").performScrollTo().assertIsDisplayed()
    }
}
