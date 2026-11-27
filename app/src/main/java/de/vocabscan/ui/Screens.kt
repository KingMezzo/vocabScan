package de.vocabscan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import de.vocabscan.data.DeckType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.Deck
import de.vocabscan.data.orderedScanGroupIds
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.Vocabulary
import de.vocabscan.data.StudyProgress
import de.vocabscan.data.ReviewLog
import de.vocabscan.data.ScanPage
import android.net.Uri
import de.vocabscan.learn.FsrsScheduler
import de.vocabscan.learn.StudyDirection
import de.vocabscan.learn.KnowledgeLevel
import de.vocabscan.learn.WordKnowledge
import de.vocabscan.learn.wordTags

private enum class WordSort(val label: String) {
    RECENT("Neueste"), DUE("Fällige"), WEAK("Schwache"), NEW("Noch neu")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    data: DeckWithVocabulary,
    availableDecks: List<Deck>,
    progress: List<StudyProgress>,
    reviewLogs: List<ReviewLog> = emptyList(),
    busy: Boolean,
    scanPages: List<ScanPage>, scanFile: (ScanPage) -> Uri,
    onEdit: (Vocabulary) -> Unit,
    onFavorite: (Vocabulary) -> Unit,
    onShareQr: () -> Unit,
    onShareCsv: () -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onStudy: (StudyDirection) -> Unit,
    onPractice: () -> Unit,
    onPracticeAll: () -> Unit,
    onStudyScan: (String) -> Unit,
    onTransfer: (Set<String>, String, Boolean) -> Unit,
    onManage: () -> Unit = {},
    onAddWord: () -> Unit = {},
    onImportText: () -> Unit = {},
) {
    var shownScan by remember { mutableStateOf<Uri?>(null) }
    var groupByScan by rememberSaveable(data.deck.id) { mutableStateOf(!data.deck.isArchived) }
    var showArchivedActions by rememberSaveable(data.deck.id) { mutableStateOf(false) }
    var transferOptions by rememberSaveable(data.deck.id) { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable(data.deck.id) { mutableStateOf(false) }
    var selectedWordIds by remember(data.deck.id) { mutableStateOf(emptySet<String>()) }
    var showTransfer by remember { mutableStateOf(false) }
    var targetDeckId by remember { mutableStateOf<String?>(null) }
    var moveWords by remember { mutableStateOf(false) }
    var query by rememberSaveable(data.deck.id) { mutableStateOf("") }
    var chapterFilter by rememberSaveable(data.deck.id) { mutableStateOf<String?>(null) }
    var tagFilter by rememberSaveable(data.deck.id) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(data.deck.id) { mutableStateOf(WordSort.RECENT) }
    val chapters = data.words.map { it.chapter }.filter { it.isNotBlank() }.distinct().sorted()
    val tags = data.words.flatMap { wordTags(it.tags) }.distinct().sorted()
    val filtered = data.words.filter {
        (chapterFilter == null || it.chapter == chapterFilter) &&
            (tagFilter == null || tagFilter in wordTags(it.tags)) &&
            listOf(it.term, it.translation, it.termAlternatives, it.translationAlternatives, it.chapter, it.tags)
                .any { text -> text.contains(query, ignoreCase = true) }
    }
    val now = System.currentTimeMillis()
    val progressByWord = progress.associateBy { it.wordId to it.direction }
    val reviewsByDirection = remember(reviewLogs) { reviewLogs.groupBy { it.wordId to it.direction } }
    fun states(word: Vocabulary) = StudyDirection.entries.map { progressByWord[word.id to it.ordinal] }
    val words = when (sort) {
        WordSort.RECENT -> filtered.sortedWith(compareByDescending<Vocabulary> { it.createdAt }.thenBy { it.id })
        WordSort.DUE -> filtered.sortedWith(compareByDescending<Vocabulary> { word ->
            states(word).count { FsrsScheduler.isDue(it?.dueAt, now) }
        }.thenBy { it.term })
        WordSort.WEAK -> filtered.sortedWith(compareByDescending<Vocabulary> { word ->
            states(word).count { it != null && (it.stability < 7 || it.difficulty >= 7) }
        }.thenBy { it.term })
        WordSort.NEW -> filtered.sortedWith(compareByDescending<Vocabulary> { word -> states(word).count { it == null } }.thenBy { it.term })
    }
    val scanGroupIds = orderedScanGroupIds(data.words)
    val generalPages = if (groupByScan && scanGroupIds.isNotEmpty())
        scanPages.filter { it.scanGroupId !in scanGroupIds } else scanPages
    val transferTargets = availableDecks.filter { it.id != data.deck.id && it.compatibleWith(data.deck) }
    val visibleGroups: List<Pair<String?, List<Vocabulary>>> = if (groupByScan && scanGroupIds.isNotEmpty()) {
        val grouped = words.groupBy { it.scanGroupId }
        scanGroupIds.mapNotNull { id -> grouped[id]?.let { id to it } } +
            listOfNotNull(grouped[null]?.let { null to it })
    } else listOf(null to words)
    fun due(direction: StudyDirection) = data.words.count { word ->
        FsrsScheduler.isDue(progressByWord[word.id to direction.ordinal]?.dueAt, now)
    }
    fun runDeckAction(action: () -> Unit) { showArchivedActions = false; action() }
    val deckActions: @Composable () -> Unit = {
        if (data.deck.isArchived) TextButton(onClick = { runDeckAction(onAddWord) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text(if (data.deck.isFlashcards) "Karte hinzufügen" else "Vokabel hinzufügen") }
        if (data.words.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lernen", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { runDeckAction { onStudy(StudyDirection.FOREIGN_TO_GERMAN) } }, enabled = !busy && due(StudyDirection.FOREIGN_TO_GERMAN) > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(data.deck.directionLabel() + " · ${due(StudyDirection.FOREIGN_TO_GERMAN)} fällig")
                    }
                    OutlinedButton(onClick = { runDeckAction { onStudy(StudyDirection.GERMAN_TO_FOREIGN) } }, enabled = !busy && due(StudyDirection.GERMAN_TO_FOREIGN) > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(data.deck.directionLabel(true) + " · ${due(StudyDirection.GERMAN_TO_FOREIGN)} fällig")
                    }
                    TextButton(onClick = { runDeckAction(onPractice) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Gezielte Lernrunde")
                    }
                    TextButton(onClick = { runDeckAction(onPracticeAll) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (data.deck.isFlashcards) "Alle Karten üben" else "Alle Vokabeln üben")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { runDeckAction { transferOptions = "import" } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importieren")
                }
                TextButton(onClick = { runDeckAction { transferOptions = "export" } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.IosShare, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Liste exportieren")
                }
                if (generalPages.isNotEmpty()) {
                    Text("${generalPages.size} Originalseiten gespeichert", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    generalPages.forEachIndexed { index, page ->
                        TextButton(onClick = { runDeckAction { shownScan = scanFile(page) } }, enabled = !busy) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Originalseite ${index + 1} ansehen")
                        }
                    }
                }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (data.deck.isArchived) Card(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Im Archiv", style = MaterialTheme.typography.titleMedium)
                    Text("Wörter und Lernstände bleiben erhalten. Aktiviere die Liste wieder, um sie regelmäßig zu lernen.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(data.deck.subjectLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(data.deck.countLabel(data.words.size), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            if (data.deck.isFlashcards && !data.deck.isArchived && data.words.isNotEmpty())
                OutlinedButton(onClick = onPractice, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Lernrunde starten") }
            if (data.deck.isArchived || data.deck.isFlashcards) TextButton(onClick = { showArchivedActions = true }, enabled = !busy) {
                Text("Weitere Listenaktionen")
            } else deckActions()
        }
        if (scanGroupIds.isNotEmpty() && !data.deck.isArchived) item(key = "scan-group-switch") {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nach Import-Scans gruppieren", style = MaterialTheme.typography.titleMedium)
                        Text("${scanGroupIds.size} Scangruppen", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = groupByScan, onCheckedChange = { groupByScan = it },
                        modifier = Modifier.semantics { contentDescription = "Nach Import-Scans gruppieren" })
                }
            }
        }
        if (data.words.isNotEmpty()) item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text(if (data.deck.isFlashcards) "Karten durchsuchen" else "Vokabeln durchsuchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (chapters.isNotEmpty()) ChoiceDropdown("Kapitel", chapterFilter ?: "Alle Kapitel", listOf("Alle Kapitel") + chapters) {
                chapterFilter = if (it == 0) null else chapters[it - 1]
            }
            if (tags.isNotEmpty()) ChoiceDropdown("Tag", tagFilter ?: "Alle Tags", listOf("Alle Tags") + tags) {
                tagFilter = if (it == 0) null else tags[it - 1]
            }
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordSort.entries.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
                }
            }
        }
        if (data.words.isNotEmpty()) item(key = "word-selection") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (data.deck.isFlashcards) "Karten" else "Vokabeln", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selecting = !selecting
                    if (!selecting) selectedWordIds = emptySet()
                }, enabled = !busy) { Text(if (selecting) "Fertig" else "Auswählen") }
            }
            if (selecting) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${selectedWordIds.size} Karten ausgewählt", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedWordIds = selectedWordIds + words.map { it.id } }) {
                            Text("Sichtbare wählen")
                        }
                        TextButton(onClick = { selectedWordIds = emptySet() }) { Text("Auswahl leeren") }
                    }
                    OutlinedButton(onClick = {
                        targetDeckId = transferTargets.firstOrNull()?.id
                        moveWords = false
                        showTransfer = true
                    }, enabled = selectedWordIds.isNotEmpty() && transferTargets.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Kopieren oder verschieben") }
                    if (transferTargets.isEmpty()) Text("Lege zuerst eine weitere Liste mit demselben Sprachpaar an.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (words.isEmpty()) item {
            if (data.words.isEmpty()) EmptyStatePanel(if (data.deck.isArchived) "Diese Liste ist leer" else if (data.deck.isFlashcards) "Platz für deine ersten Karten" else "Platz für deine ersten Wörter",
                if (data.deck.isArchived) "Aktiviere die Liste wieder oder füge über „Weitere Listenaktionen“ Wörter hinzu."
                else if (data.deck.isFlashcards) "Füge über + eine Karte mit Begriff und Erklärung hinzu. Text- und CSV-Import findest du unter „Weitere Listenaktionen“."
                else "Scanne ein Arbeitsblatt, importiere ein Foto oder füge über die Schaltfläche unten eine Vokabel hinzu.")
            else EmptyStatePanel("Keine passenden Karten", "Passe deine Suche, das Kapitel oder den Tag an.",
                "Filter zurücksetzen", onAction = { query = ""; chapterFilter = null; tagFilter = null })
        }
        visibleGroups.forEach { (groupId, groupWords) ->
            if (groupByScan && scanGroupIds.isNotEmpty()) item(key = "scan-${groupId ?: "other"}") {
                val groupPages = scanPages.filter { it.scanGroupId == groupId && groupId != null }
                val importedAt = groupPages.firstOrNull()?.createdAt ?: groupWords.minOf { it.createdAt }
                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (groupId == null) "Ohne Scan" else "Scan ${scanGroupIds.indexOf(groupId) + 1}",
                            style = MaterialTheme.typography.titleLarge)
                        if (groupId != null) Text(java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(importedAt)),
                            style = MaterialTheme.typography.bodySmall)
                        Text("${groupWords.size} Vokabeln" + if (groupPages.isNotEmpty()) " · ${groupPages.size} Originalseiten" else "",
                            style = MaterialTheme.typography.bodySmall)
                        if (groupId != null) TextButton(onClick = { onStudyScan(groupId) }, enabled = !busy) {
                            Text("Diese Scangruppe üben")
                        }
                        groupPages.forEachIndexed { index, page ->
                            TextButton(onClick = { shownScan = scanFile(page) }, enabled = !busy) {
                                Icon(Icons.Outlined.Image, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Originalseite ${index + 1} ansehen")
                            }
                        }
                    }
                }
            }
            items(groupWords, key = { it.id }) { word ->
            Surface(onClick = {
                if (selecting) selectedWordIds = if (word.id in selectedWordIds) selectedWordIds - word.id else selectedWordIds + word.id
                else onEdit(word)
            }, enabled = !busy, shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().animateItem().semantics {
                    onClick(label = if (selecting) "Karte auswählen" else "Kartendetails öffnen", action = null)
                }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) Checkbox(checked = word.id in selectedWordIds,
                        modifier = Modifier.semantics { contentDescription = "${word.term} auswählen" },
                        onCheckedChange = { selectedWordIds = if (it) selectedWordIds + word.id else selectedWordIds - word.id })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WordPairText(word.term, word.translation)
                        val labels = listOfNotNull(word.chapter.takeIf { it.isNotBlank() }) + wordTags(word.tags).map { "#$it" }
                        if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val largeText = LocalDensity.current.fontScale >= 1.3f
                        if (largeText) Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DirectionKnowledge(data.deck.directionLabel(),
                                progressByWord[word.id to 0], reviewsByDirection[word.id to 0].orEmpty(), now, Modifier.fillMaxWidth())
                            DirectionKnowledge(data.deck.directionLabel(true),
                                progressByWord[word.id to 1], reviewsByDirection[word.id to 1].orEmpty(), now, Modifier.fillMaxWidth())
                        } else Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DirectionKnowledge(
                                label = data.deck.directionLabel(),
                                progress = progressByWord[word.id to StudyDirection.FOREIGN_TO_GERMAN.ordinal],
                                reviews = reviewsByDirection[word.id to StudyDirection.FOREIGN_TO_GERMAN.ordinal].orEmpty(),
                                now = now, modifier = Modifier.weight(1f),
                            )
                            DirectionKnowledge(
                                label = data.deck.directionLabel(true),
                                progress = progressByWord[word.id to StudyDirection.GERMAN_TO_FOREIGN.ordinal],
                                reviews = reviewsByDirection[word.id to StudyDirection.GERMAN_TO_FOREIGN.ordinal].orEmpty(),
                                now = now, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (!selecting) FavoriteButton(word.isFavorite, word.term, !busy, onClick = { onFavorite(word) })
                }
            }
        }
        }
    }
    if (showArchivedActions) ModalBottomSheet(onDismissRequest = { showArchivedActions = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Listenaktionen", style = MaterialTheme.typography.headlineSmall)
            deckActions()
            if (scanGroupIds.isNotEmpty()) TextButton(onClick = {
                groupByScan = !groupByScan; showArchivedActions = false
            }, enabled = !busy) { Text(if (groupByScan) "Alle Wörter zusammen anzeigen" else "Nach Import-Scans gruppieren") }
        }
    }
    fun runTransferOption(action: () -> Unit) { transferOptions = null; action() }
    if (transferOptions == "import") DeckImportSheet(data.deck.isFlashcards, busy,
        onDismiss = { transferOptions = null },
        onScan = { runTransferOption(onScan) }, onPhoto = { runTransferOption(onPhoto) },
        onImportText = { runTransferOption(onImportText) })
    if (transferOptions == "export") DeckExportSheet(data.words.isNotEmpty(), busy,
        onDismiss = { transferOptions = null },
        onShareQr = { runTransferOption(onShareQr) }, onShareCsv = { runTransferOption(onShareCsv) })
    if (showTransfer) AlertDialog(
        onDismissRequest = { showTransfer = false },
        title = { Text(if (data.deck.isFlashcards) "Karten übertragen" else "Vokabeln übertragen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selectedWordIds.size} Karten mit Notizen, Alternativen, Kapiteln, Tags, Favoriten und Scangruppe, falls vorhanden.")
                ChoiceDropdown("Zielliste", transferTargets.firstOrNull { it.id == targetDeckId }?.name ?: "Liste wählen",
                    transferTargets.map { it.name }) { index -> targetDeckId = transferTargets[index].id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !moveWords, onClick = { moveWords = false })
                    Text("Kopieren", Modifier.clickable { moveWords = false })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = moveWords, onClick = { moveWords = true })
                    Text("Verschieben", Modifier.clickable { moveWords = true })
                }
                Text("Lernstand und Bewertungen werden nicht übertragen. Originalbilder bleiben in der Ausgangsliste.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = {
            val target = targetDeckId ?: return@Button
            onTransfer(selectedWordIds, target, moveWords)
            selectedWordIds = emptySet(); selecting = false; showTransfer = false
        }, enabled = targetDeckId != null && selectedWordIds.isNotEmpty() && !busy) {
            Text(if (moveWords) "Verschieben" else "Kopieren")
        } },
        dismissButton = { TextButton(onClick = { showTransfer = false }) { Text("Abbrechen") } },
    )
    shownScan?.let { uri -> OriginalImageDialog(uri, onDismiss = { shownScan = null }) }
}

@Composable
private fun DirectionKnowledge(label: String, progress: StudyProgress?, reviews: List<ReviewLog>, now: Long, modifier: Modifier) {
    val level = WordKnowledge.level(reviews, progress)
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val (background, foreground) = when (level) {
        KnowledgeLevel.NEW -> colors.surfaceVariant to colors.onSurfaceVariant
        KnowledgeLevel.STRUGGLING -> if (dark) Color(0xFF5B292E) to Color(0xFFFFDADB)
            else Color(0xFFFFE0E0) to Color(0xFF8E2A2A)
        KnowledgeLevel.BUILDING -> if (dark) Color(0xFF59441C) to Color(0xFFFFE3A1)
            else Color(0xFFFFF0C7) to Color(0xFF6C4C00)
        KnowledgeLevel.GOOD -> if (dark) Color(0xFF234936) to Color(0xFFD2F5DF)
            else Color(0xFFD9F1E1) to Color(0xFF225E38)
        KnowledgeLevel.STRONG -> if (dark) Color(0xFF45345B) to Color(0xFFF0DDFF)
            else Color(0xFFEDE3FA) to Color(0xFF552577)
    }
    Surface(modifier, shape = RoundedCornerShape(9.dp), color = background) {
        val dueLabel = if (progress != null && progress.dueAt <= now) " · fällig" else ""
        Text("$label · ${level.label}" + (progress?.let { " ${it.reviews}×" } ?: "") + dueLabel,
            Modifier.padding(horizontal = 7.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall,
            color = foreground)
    }
}


@Composable
private fun FlashcardDeckPreview(theme: ThemeMode, empty: Boolean = false) = VocabTheme(theme) {
    val deck = Deck("d", "Fachbegriffe Recht", "", type = DeckType.LERNKARTEN)
    val data = DeckWithVocabulary(deck, if (empty) emptyList() else listOf(
        Vocabulary("w", deck.id, "Vertrag", "Zwei übereinstimmende Willenserklärungen.")))
    Surface { DeckScreen(data, listOf(deck), emptyList(), busy = false, scanPages = emptyList(),
        scanFile = { Uri.EMPTY }, onEdit = {}, onFavorite = {}, onShareQr = {}, onShareCsv = {},
        onScan = {}, onPhoto = {}, onStudy = {}, onPractice = {}, onPracticeAll = {}, onStudyScan = {},
        onTransfer = { _, _, _ -> }) }
}
@Preview(name = "Lernkartenliste · Hell", showBackground = true)
@Composable private fun FlashcardDeckLightPreview() = FlashcardDeckPreview(ThemeMode.LIGHT)
@Preview(name = "Lernkartenliste · Dunkel", showBackground = true)
@Composable private fun FlashcardDeckDarkPreview() = FlashcardDeckPreview(ThemeMode.DARK)
@Preview(name = "Lernkartenliste · Leer", showBackground = true)
@Composable private fun FlashcardDeckEmptyPreview() = FlashcardDeckPreview(ThemeMode.LIGHT, true)
