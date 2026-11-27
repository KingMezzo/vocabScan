package de.vocabscan.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckType
import de.vocabscan.data.Vocabulary
import de.vocabscan.scan.ScanSource
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordEditorScreen(
    original: Vocabulary?, language: String, busy: Boolean,
    scanImage: File? = null, scanSource: ScanSource? = null,
    type: DeckType = DeckType.SPRACHE, resetKey: Int = 0,
    keepAdding: Boolean = false, onKeepAddingChange: (Boolean) -> Unit = {},
    targetLanguage: String = if (type.isFlashcards) "" else "de",
    onCancel: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
) {
    var term by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.term.orEmpty()) }
    var translation by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.translation.orEmpty()) }
    var note by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.note.orEmpty()) }
    var termAlternatives by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.termAlternatives.orEmpty()) }
    var translationAlternatives by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.translationAlternatives.orEmpty()) }
    var chapter by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.chapter.orEmpty()) }
    var tags by rememberSaveable(original?.id, resetKey) { mutableStateOf(original?.tags.orEmpty()) }
    var showAnswers by rememberSaveable(original?.id, resetKey) {
        mutableStateOf(!original?.termAlternatives.isNullOrBlank() || !original?.translationAlternatives.isNullOrBlank())
    }
    var showOrganization by rememberSaveable(original?.id, resetKey) {
        mutableStateOf(!original?.chapter.isNullOrBlank() || !original?.tags.isNullOrBlank())
    }
    val flashcards = type.isFlashcards
    val deck = Deck("", "", language, type = type, targetLanguage = targetLanguage)
    val explanation = !flashcards && language == targetLanguage
    fun example(code: String) = when (code) { "fr" -> "la maison"; "de" -> "das Haus"; "la" -> "domus"; else -> "house" }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(if (flashcards) { if (original == null) "Neue Karte" else "Karte bearbeiten" }
                    else if (original == null) "Neues Wortpaar" else "Wortpaar bearbeiten",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (flashcards) "Begriff und Erklärung eintragen. Beispiel und Tags sind optional."
                    else if (explanation) "Trage den Begriff und seine Erklärung oder ein Synonym ein."
                    else "Trage den Begriff und seine Übersetzung ein. Alles Weitere ist optional.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scanImage != null && scanSource != null && scanImage.isFile) item {
                ScanExcerpt(scanImage, scanSource)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(if (flashcards) "Vorderseite und Rückseite" else "Wortpaar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(term, { term = it }, label = { Text(deck.termLabel) },
                            placeholder = { Text(if (flashcards) "z. B. Willenserklärung" else "z. B. ${example(language)}") },
                            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(translation, { translation = it }, label = { Text(deck.translationLabel) },
                            placeholder = { Text(if (flashcards || explanation) "Erklärung oder Synonym eingeben" else "z. B. ${example(targetLanguage)}") },
                            singleLine = !flashcards && !explanation, minLines = if (flashcards) 5 else if (explanation) 3 else 1, enabled = !busy,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Persönliche Notiz", style = MaterialTheme.typography.titleMedium)
                        Text("Merksatz, Hinweis oder Beispiel – nur für dich.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") },
                            minLines = 2, maxLines = 5, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (!flashcards) item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().semantics { stateDescription = if (showAnswers) "Ausgeklappt" else "Eingeklappt" }
                            .clickable(enabled = !busy, onClickLabel = "Weitere Antworten ein- oder ausklappen") { showAnswers = !showAnswers }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LibraryAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Weitere richtige Antworten", style = MaterialTheme.typography.titleMedium)
                                Text(if (showAnswers) "Alternative Lösungen eintragen" else "Optional · nur bei mehreren gültigen Lösungen",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (showAnswers) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        AnimatedVisibility(visible = showAnswers,
                            enter = expandVertically(tween(210)) + fadeIn(tween(210)),
                            exit = shrinkVertically(tween(170)) + fadeOut(tween(170))) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(termAlternatives, { termAlternatives = it },
                                label = { Text("Weitere Antworten · ${deck.termLabel}") },
                                minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(translationAlternatives, { translationAlternatives = it },
                                label = { Text("Weitere Antworten · ${deck.translationLabel}") },
                                minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            Text("Eine Lösung pro Zeile oder mit Semikolon trennen.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        }
                    }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().semantics { stateDescription = if (showOrganization) "Ausgeklappt" else "Eingeklappt" }
                            .clickable(enabled = !busy, onClickLabel = "Kapitel und Tags ein- oder ausklappen") { showOrganization = !showOrganization }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sell, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Ordnen & wiederfinden", style = MaterialTheme.typography.titleMedium)
                                Text(if (showOrganization) "Kapitel und Tags" else "Optional · Kapitel und Tags",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (showOrganization) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        AnimatedVisibility(visible = showOrganization,
                            enter = expandVertically(tween(210)) + fadeIn(tween(210)),
                            exit = shrinkVertically(tween(170)) + fadeOut(tween(170))) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(chapter, { chapter = it }, label = { Text("Kapitel (optional)") },
                                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (mit Komma)") },
                                placeholder = { Text("z. B. Verben, Schule") }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth())
                        }
                        }
                    }
                }
            }
        }
        if (original == null && flashcards) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = keepAdding, onCheckedChange = onKeepAddingChange, enabled = !busy)
            Text("Weitere Karte hinzufügen", style = MaterialTheme.typography.bodyMedium)
        }
        Surface(shadowElevation = 8.dp) {
            FlowRow(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
                Button(onClick = { onSave(term, translation, note, termAlternatives, translationAlternatives, chapter, tags) },
                    enabled = !busy && term.isNotBlank() && translation.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("Speichern")
                }
            }
        }
    }
}


@Composable
private fun CardEditorPreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    Surface {
        WordEditorScreen(if (empty) null else Vocabulary("w", "d", "Vertrag",
            "Zwei übereinstimmende Willenserklärungen."), "", false, type = DeckType.LERNKARTEN,
            onCancel = {}, onSave = { _, _, _, _, _, _, _ -> })
    }
}
@Preview(name = "Karteneditor · Hell", showBackground = true)
@Composable private fun CardEditorLightPreview() = CardEditorPreview(ThemeMode.LIGHT)
@Preview(name = "Karteneditor · Dunkel", showBackground = true)
@Composable private fun CardEditorDarkPreview() = CardEditorPreview(ThemeMode.DARK)
@Preview(name = "Karteneditor · Leer", showBackground = true)
@Composable private fun CardEditorEmptyPreview() = CardEditorPreview(ThemeMode.LIGHT, true)
