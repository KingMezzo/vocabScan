package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import de.vocabscan.ui.components.StudyLanguageField
import de.vocabscan.data.Deck
import de.vocabscan.data.StudyLanguage

@Composable
fun DeckDialog(initial: Deck?, onDismiss: () -> Unit, onSave: (String, StudyLanguage, StudyLanguage) -> Unit,
    busy: Boolean = false, previewName: String = "", previewTarget: StudyLanguage = StudyLanguage.GERMAN,
    errorMessage: String? = null) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: previewName) }
    var languageCode by rememberSaveable(initial?.id) { mutableStateOf(initial?.language ?: "en") }
    var targetCode by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetLanguage ?: previewTarget.code) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Neue Sprachliste" else "Liste umbenennen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name der Liste") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lernrichtung", style = MaterialTheme.typography.titleSmall)
                        StudyLanguageField("Von", StudyLanguage.fromCode(languageCode), !busy) { languageCode = it.code }
                        StudyLanguageField("Nach", StudyLanguage.fromCode(targetCode), !busy) { targetCode = it.code }
                        if (languageCode == targetCode) Text("Begriff → Erklärung / Synonym. Beide Seiten sind ${StudyLanguage.fromCode(languageCode).label}.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, StudyLanguage.fromCode(languageCode), StudyLanguage.fromCode(targetCode)) },
            enabled = !busy && name.isNotBlank()) { Text(if (initial == null) "Liste erstellen" else "Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Preview(name = "Neue Liste · Hell · Leer", showBackground = true)
@Composable
private fun NewDeckDialogLightPreview() {
    VocabTheme(ThemeMode.LIGHT) { DeckDialog(initial = null, onDismiss = {}, onSave = { _, _, _ -> }) }
}

@Preview(name = "Neue Liste · Dunkel · Leer", showBackground = true)
@Composable
private fun NewDeckDialogDarkPreview() {
    VocabTheme(ThemeMode.DARK) { DeckDialog(initial = null, onDismiss = {}, onSave = { _, _, _ -> }) }
}

@Preview(name = "Neue Liste · Große Schrift", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun NewDeckDialogLargeTextPreview() {
    VocabTheme(ThemeMode.LIGHT) { DeckDialog(initial = null, onDismiss = {}, onSave = { _, _, _ -> }) }
}

@Preview(name = "Englisch → Englisch · Hell", showBackground = true)
@Composable private fun SameLanguageLightPreview() = VocabTheme(ThemeMode.LIGHT) {
    DeckDialog(null, {}, { _, _, _ -> }, previewName = "English definitions", previewTarget = StudyLanguage.ENGLISH)
}
@Preview(name = "Englisch → Englisch · Dunkel", showBackground = true)
@Composable private fun SameLanguageDarkPreview() = VocabTheme(ThemeMode.DARK) {
    DeckDialog(null, {}, { _, _, _ -> }, previewName = "English definitions", previewTarget = StudyLanguage.ENGLISH)
}

@Preview(name = "Neue Sprachliste · Laden", showBackground = true)
@Composable private fun LanguageDeckLoadingPreview() = VocabTheme(ThemeMode.LIGHT) {
    DeckDialog(null, {}, { _, _, _ -> }, busy = true, previewName = "English definitions", previewTarget = StudyLanguage.ENGLISH)
}
@Preview(name = "Neue Sprachliste · Fehler", showBackground = true)
@Composable private fun LanguageDeckErrorPreview() = VocabTheme(ThemeMode.DARK) {
    DeckDialog(null, {}, { _, _, _ -> }, previewName = "English definitions", previewTarget = StudyLanguage.ENGLISH,
        errorMessage = "Liste konnte nicht gespeichert werden. Bitte erneut versuchen.")
}
