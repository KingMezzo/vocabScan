package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Deck
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.ScanEdits

@Composable
internal fun MergeRowsDialog(
    pairs: List<ImportPair>, firstId: String, secondId: String,
    language: String, targetLanguage: String, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    val selected = pairs.filter { it.id == firstId || it.id == secondId }
    val valid = firstId != secondId && selected.size == 2 && selected[0].page == selected[1].page
    val result = if (valid) ScanEdits.merge(pairs, firstId, secondId).firstOrNull { it.id == selected[0].id } else null
    val deck = Deck("", "", language, targetLanguage = targetLanguage)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aus zwei Zeilen wird ein Wortpaar") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Nutze das, wenn der Scan ein zusammengehöriges Wortpaar auf zwei Zeilen verteilt hat. Die Texte jeder Spalte werden in der Reihenfolge auf der Seite aneinandergefügt.")
            if (result == null) Text("Wähle zwei verschiedene Zeilen derselben Seite aus.")
            else {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ergebnis · 1 Wortpaar", style = MaterialTheme.typography.titleSmall)
                        Text(deck.termLabel, style = MaterialTheme.typography.labelMedium)
                        Text(result.term.ifBlank { "Begriff fehlt" })
                        HorizontalDivider()
                        Text(deck.translationLabel, style = MaterialTheme.typography.labelMedium)
                        Text(result.translation.ifBlank { "Übersetzung fehlt" })
                    }
                }
                Text("Die beiden bisherigen Zeilen werden durch dieses Wortpaar ersetzt. Danach kannst du die Korrektur über den Rückgängig-Pfeil zurücknehmen.",
                    style = MaterialTheme.typography.bodySmall)
            }
        } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = valid && !busy) { Text("Zu einem Wortpaar zusammenführen") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } })
}

@Composable
private fun MergePreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    MergeRowsDialog(if (empty) emptyList() else listOf(
        ImportPair(id = "a", term = "to look", translation = "sich"),
        ImportPair(id = "b", term = "forward to", translation = "freuen auf")),
        "a", "b", "en", "de", false, {}, {})
}

@Preview(name = "Zusammenführen · Hell", showBackground = true)
@Composable private fun MergeLightPreview() = MergePreview(ThemeMode.LIGHT)
@Preview(name = "Zusammenführen · Dunkel", showBackground = true)
@Composable private fun MergeDarkPreview() = MergePreview(ThemeMode.DARK)
@Preview(name = "Zusammenführen · Keine Auswahl", showBackground = true)
@Composable private fun MergeEmptyPreview() = MergePreview(ThemeMode.LIGHT, empty = true)
