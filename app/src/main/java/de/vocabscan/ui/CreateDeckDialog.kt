package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.vocabscan.data.DeckType
import de.vocabscan.data.StudyLanguage

@Composable
fun CreateDeckDialog(onDismiss: () -> Unit, onSave: (String, StudyLanguage, StudyLanguage, DeckType) -> Unit,
    previewType: DeckType? = null, busy: Boolean = false, errorMessage: String? = null) {
    var type by rememberSaveable { mutableStateOf(previewType) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(DeckType.SPRACHE) }
    if (type == DeckType.SPRACHE) {
        DeckDialog(initial = null, onDismiss = { type = null },
            busy = busy, errorMessage = errorMessage, onSave = { title, language, target -> onSave(title, language, target, DeckType.SPRACHE) })
        return
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (type == null) "Neue Liste" else if (type!!.isFlashcards) "Neue Lernkartenliste" else "Neue Sprachliste") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (type == null) {
                    DeckType.entries.forEach { option ->
                        OutlinedCard(Modifier.fillMaxWidth().selectable(selectedType == option, role = Role.RadioButton, enabled = !busy,
                            onClick = { selectedType = option })) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(selected = selectedType == option, onClick = null)
                                Column {
                                    Text(if (option.isFlashcards) "Lernkarten (Begriff + Erklärung)" else "Sprache lernen",
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(if (option.isFlashcards) "Begriffe und Erklärungen zu jedem Thema lernen."
                                        else "Vokabeln mit Übersetzung, Erklärung oder Synonym lernen.",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("Name der Liste") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            if (type == null) type = selectedType else onSave(name, StudyLanguage.ENGLISH, StudyLanguage.GERMAN, type!!)
        }, enabled = !busy && (type == null || name.isNotBlank())) { Text(if (type == null) "Weiter" else "Erstellen") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (type != null) type = null else onDismiss() }) {
            Text(if (type != null) "Zurück" else "Abbrechen")
        } })
}

@Preview(name = "Listentyp · Hell", showBackground = true)
@Composable private fun DeckTypeLightPreview() = VocabTheme(ThemeMode.LIGHT) { CreateDeckDialog({}, { _, _, _, _ -> }) }
@Preview(name = "Listentyp · Dunkel", showBackground = true)
@Composable private fun DeckTypeDarkPreview() = VocabTheme(ThemeMode.DARK) { CreateDeckDialog({}, { _, _, _, _ -> }) }
@Preview(name = "Lernkartenliste · Leer", showBackground = true)
@Composable private fun FlashcardListEmptyPreview() = VocabTheme(ThemeMode.LIGHT) {
    CreateDeckDialog({}, { _, _, _, _ -> }, DeckType.LERNKARTEN)
}
