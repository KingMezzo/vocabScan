package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import de.vocabscan.data.Deck
import de.vocabscan.data.DeckType
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Vocabulary
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vocabscan.learn.*
import de.vocabscan.data.orderedScanGroupIds

@Composable
fun ChoiceDropdown(label: String, value: String, choices: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEachIndexed { index, text ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(index) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySetupSheet(data: StudyData, initial: StudyOptions, onDismiss: () -> Unit,
    onStart: (StudyOptions, StudyMode) -> Unit) {
    var deckId by rememberSaveable { mutableStateOf(initial.deckId) }
    var direction by rememberSaveable { mutableStateOf(initial.direction) }
    var filter by rememberSaveable { mutableStateOf(initial.filter) }
    var chapter by rememberSaveable { mutableStateOf(initial.chapter) }
    var tag by rememberSaveable { mutableStateOf(initial.tag) }
    var limit by rememberSaveable { mutableIntStateOf(initial.limit) }
    var favoritesOnly by rememberSaveable { mutableStateOf(initial.favoritesOnly) }
    var scanGroupId by rememberSaveable { mutableStateOf(initial.scanGroupId) }
    var mode by rememberSaveable { mutableStateOf(StudyMode.CARDS) }
    val scopedDecks = data.decks.filter { deckId == null || it.deck.id == deckId }
    val flashcardsOnly = scopedDecks.isNotEmpty() && scopedDecks.all { it.deck.isFlashcards }
    val containsFlashcards = scopedDecks.any { it.deck.isFlashcards }
    val options = StudyOptions(deckId, direction, filter, chapter, tag, limit, favoritesOnly, scanGroupId)
    val scoped = data.decks.filter { deckId == null || it.deck.id == deckId }.flatMap { it.words }
    val scanGroups = orderedScanGroupIds(scoped)
    val chapters = scoped.map { it.chapter }.filter { it.isNotBlank() }.distinct().sorted()
    val tags = scoped.flatMap { wordTags(it.tags) }.distinct().sorted()
    val count = StudyPlanner.candidates(data, options, System.currentTimeMillis()).size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Deine Lernrunde", style = MaterialTheme.typography.headlineSmall)
            Text("Wähle Karten, Richtung und Lernmodus.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceDropdown("Liste", data.decks.firstOrNull { it.deck.id == deckId }?.deck?.name ?: "Alle Listen",
                listOf("Alle Listen") + data.decks.map { "${it.deck.name} · ${it.deck.subjectLabel}" }) { index ->
                deckId = if (index == 0) null else data.decks[index - 1].deck.id
                direction = if (deckId == null || data.decks[index - 1].deck.isFlashcards) DirectionChoice.MIXED else DirectionChoice.FOREIGN_TO_GERMAN
                if (deckId == null && limit == 0) limit = 20
                chapter = null; tag = null; scanGroupId = null
            }
            Text("Welche Karten?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Nur Favoriten") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) })
            Text(when (filter) {
                StudyFilter.DUE -> "Neue Karten und Karten, deren Wiederholung ansteht."
                StudyFilter.WEAK -> "Bereits geübte Karten, die sich noch nicht gut gefestigt haben."
                StudyFilter.NEW -> "Lernrichtungen, die du noch nie bewertet hast."
                StudyFilter.ALL -> "Alle passenden Karten. Eine Bewertung aktualisiert ihren nächsten Termin."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val pairs = scopedDecks.map { it.deck }.distinctBy { Triple(it.type, it.language, it.targetLanguage) }
            val directions = if (flashcardsOnly) listOf("Vorderseite → Rückseite", "Rückseite → Vorderseite", "Beide Richtungen gemischt")
                else if (pairs.size == 1) listOf(pairs.single().directionLabel(), pairs.single().directionLabel(true), "Beide Richtungen gemischt")
                else listOf("Vorwärts (wie in der Liste)", "Umgekehrt", "Beide Richtungen gemischt")
            ChoiceDropdown("Richtung", directions[direction.ordinal], directions) { direction = DirectionChoice.entries[it] }
            if (chapters.isNotEmpty()) ChoiceDropdown("Kapitel", chapter ?: "Alle Kapitel", listOf("Alle Kapitel") + chapters) {
                chapter = if (it == 0) null else chapters[it - 1]
            }
            if (tags.isNotEmpty()) ChoiceDropdown("Tag", tag ?: "Alle Tags", listOf("Alle Tags") + tags) {
                tag = if (it == 0) null else tags[it - 1]
            }
            if (deckId != null && scanGroups.isNotEmpty()) ChoiceDropdown("Import-Scan",
                scanGroupId?.let { "Scan ${scanGroups.indexOf(it) + 1}" } ?: "Alle Scans",
                listOf("Alle Scans") + scanGroups.mapIndexed { index, _ -> "Scan ${index + 1}" }) {
                scanGroupId = if (it == 0) null else scanGroups[it - 1]
            }
            Text("Umfang (Lernrichtungen)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 40).forEach { amount ->
                    FilterChip(selected = limit == amount, onClick = { limit = amount }, label = { Text("$amount") })
                }
                if (deckId != null) FilterChip(selected = limit == 0, onClick = { limit = 0 }, label = { Text("Alle") })
            }
            Text(if (count == 0) "Keine Karten für diese Auswahl. Ändere einen Filter."
                else "${if (limit == 0) count else minOf(limit, count)} von $count passenden Lernrichtungen in dieser Runde.",
                style = MaterialTheme.typography.titleSmall)
            if (!flashcardsOnly) {
                ChoiceDropdown("Lernmodus", if (mode == StudyMode.CARDS) "Karteikarten" else "Eingeben",
                    listOf("Karteikarten", "Eingeben")) { mode = if (it == 0) StudyMode.CARDS else StudyMode.INPUT }
                if (containsFlashcards && mode == StudyMode.INPUT) Text("Lernkarten werden aufgedeckt und selbst bewertet. Die Eingabe gilt für Sprachlisten.",
                    style = MaterialTheme.typography.bodySmall)
            } else Text("Aufdecken und mit „Gewusst“ oder „Nicht gewusst“ bewerten.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onStart(options, if (flashcardsOnly) StudyMode.CARDS else mode) }, enabled = count > 0,
                modifier = Modifier.fillMaxWidth()) { Text("Lernrunde starten") }
        }
    }
}


@Composable private fun FlashcardSetupPreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    val deck = Deck("d", "Recht", "", type = DeckType.LERNKARTEN)
    val words = if (empty) emptyList() else listOf(Vocabulary("w", "d", "Vertrag", "Erklärung"))
    StudySetupSheet(StudyData(listOf(DeckWithVocabulary(deck, words)), emptyList()), StudyOptions(deckId = deck.id), {}, { _, _ -> })
}
@Preview(name = "Lernkartenauswahl · Hell", showBackground = true)
@Composable private fun FlashcardSetupLightPreview() = FlashcardSetupPreview(ThemeMode.LIGHT)
@Preview(name = "Lernkartenauswahl · Dunkel", showBackground = true)
@Composable private fun FlashcardSetupDarkPreview() = FlashcardSetupPreview(ThemeMode.DARK)
@Preview(name = "Lernkartenauswahl · Leer", showBackground = true)
@Composable private fun FlashcardSetupEmptyPreview() = FlashcardSetupPreview(ThemeMode.LIGHT, true)
