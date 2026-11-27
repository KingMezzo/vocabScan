package de.vocabscan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.ui.ThemeMode
import de.vocabscan.ui.VocabTheme
import de.vocabscan.update.ReleaseNotes

@Composable
fun ReleaseNotesDialog(
    title: String,
    notes: ReleaseNotes,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    actionEnabled: Boolean = true,
    statusContent: (@Composable () -> Unit)? = null,
) {
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val markdown = androidx.compose.runtime.remember(notes.changelog) { releaseNotesMarkdown(notes.changelog) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                statusContent?.invoke()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Version ${notes.versionName}", style = MaterialTheme.typography.titleMedium)
                    if (markdown.isBlank()) {
                        Text("Für diese Version sind keine weiteren Details hinterlegt.",
                            style = MaterialTheme.typography.bodyMedium)
                    } else MarkdownContent(markdown)
                }
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dismissLabel?.let { label ->
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                    }
                    Button(onClick = onAction, enabled = actionEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(
    notes: ReleaseNotes,
    downloading: Boolean,
    progress: Float,
    ready: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    ReleaseNotesDialog(
        title = if (ready) "Update bereit" else "Update verfügbar",
        notes = notes,
        actionLabel = when {
            downloading -> "Wird heruntergeladen …"
            ready -> "Installieren"
            error != null -> "Erneut herunterladen"
            else -> "Herunterladen"
        },
        onAction = if (ready) onInstall else onDownload,
        onDismiss = onDismiss,
        dismissLabel = if (downloading) "Abbrechen" else "Später",
        actionEnabled = !downloading,
        statusContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (downloading) {
                    Text(if (progress >= 1f) "Download abgeschlossen · APK wird geprüft …"
                        else "Download: ${(progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                if (ready) Text("Download geprüft. Bestätige die Installation anschließend im Android-Dialog.",
                    style = MaterialTheme.typography.bodyMedium)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Preview(name = "Download · Hell", showBackground = true)
@Composable private fun DownloadLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    UpdateDialog(previewNotes, true, 0.42f, false, null, {}, {}, {})
}

@Preview(name = "Download · Dunkel", showBackground = true)
@Composable private fun DownloadDarkPreview() = VocabTheme(ThemeMode.DARK) {
    UpdateDialog(previewNotes, true, 0.42f, false, null, {}, {}, {})
}

@Preview(name = "Download · Leer", showBackground = true)
@Composable private fun DownloadEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    UpdateDialog(previewNotes.copy(changelog = emptyList()), false, 0f, false, null, {}, {}, {})
}

@Preview(name = "Download · Fehler", showBackground = true)
@Composable private fun DownloadErrorPreview() = VocabTheme(ThemeMode.DARK) {
    UpdateDialog(previewNotes, false, 0f, false, "Download fehlgeschlagen. Bitte erneut versuchen.", {}, {}, {})
}

@Preview(name = "Installation · Bereit", showBackground = true)
@Composable private fun InstallPreview() = VocabTheme(ThemeMode.LIGHT) {
    UpdateDialog(previewNotes, false, 1f, true, null, {}, {}, {})
}

private val previewNotes = ReleaseNotes(15, "0.13.0", listOf(
    "Die App prüft einmal täglich, ob eine neue Version verfügbar ist.",
    "In den Einstellungen kannst du jederzeit nach Updates suchen.",
))

@Preview(name = "Update · Hell", showBackground = true)
@Composable private fun UpdateLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    ReleaseNotesDialog("Update verfügbar", previewNotes, "Herunterladen", {}, {}, "Später")
}

@Preview(name = "Update · Dunkel", showBackground = true)
@Composable private fun UpdateDarkPreview() = VocabTheme(ThemeMode.DARK) {
    ReleaseNotesDialog("Update verfügbar", previewNotes, "Herunterladen", {}, {}, "Später")
}

@Preview(name = "Update · Leer", showBackground = true)
@Composable private fun UpdateEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    ReleaseNotesDialog("Update verfügbar", previewNotes.copy(changelog = emptyList()), "Herunterladen", {}, {}, "Später")
}

@Preview(name = "Was ist neu · Hell", showBackground = true)
@Composable private fun WhatsNewLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    ReleaseNotesDialog("Was ist neu", previewNotes, "Alles klar", {}, {})
}

@Preview(name = "Was ist neu · Dunkel", showBackground = true)
@Composable private fun WhatsNewDarkPreview() = VocabTheme(ThemeMode.DARK) {
    ReleaseNotesDialog("Was ist neu", previewNotes, "Alles klar", {}, {})
}

@Preview(name = "Was ist neu · Leer", showBackground = true)
@Composable private fun WhatsNewEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    ReleaseNotesDialog("Was ist neu", previewNotes.copy(changelog = emptyList()), "Alles klar", {}, {})
}

@Preview(name = "Markdown · Lange Notizen", showBackground = true, widthDp = 360, heightDp = 640)
@Composable private fun MarkdownNotesPreview() = VocabTheme(ThemeMode.LIGHT) {
    ReleaseNotesDialog("Was ist neu", previewNotes.copy(changelog = listOf(
        "## Verbesserungen\n\n- **Ordner** getrennt verwalten\n- *Schneller* lernen\n\n### Details\n\n" +
            List(12) { "- Weitere Verbesserung ${it + 1}" }.joinToString("\n"))), "Alles klar", {}, {})
}
@Preview(name = "Markdown · Große Schrift", showBackground = true, widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable private fun MarkdownLargeFontPreview() = VocabTheme(ThemeMode.DARK) {
    UpdateDialog(previewNotes.copy(changelog = listOf("## Neu\n\n**Besser lernen**\n\n" +
        List(20) { "- Änderung ${it + 1}" }.joinToString("\n"))), false, 0f, false, null, {}, {}, {})
}
