package de.vocabscan.ui

import android.net.Uri

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Vocabulary
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.Deck
import de.vocabscan.data.pairKey
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.LanguageHint
import de.vocabscan.scan.ScanCrop

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    draft: ImportDraft, existing: List<Vocabulary>, language: String, busy: Boolean,
    onUpdate: (String, String, String) -> Unit, onRemove: (String) -> Unit,
    onAdd: () -> Unit, onSwap: () -> Unit, onSplit: (Float?) -> Unit,
    onDeduplicate: () -> Unit, onPageEdit: (Int, Int, Float) -> Unit, onConfirm: () -> Unit,
    canUndo: Boolean, onUndo: () -> Unit, onCrop: (Int, ScanCrop) -> Unit,
    onMerge: (String, String) -> Unit, onSeparate: (String, Int, Int) -> Unit,
    onRemoveMany: (Set<String>) -> Unit = { ids -> ids.forEach(onRemove) },
    onReviewMany: (Set<String>) -> Unit = {},
    availableDecks: List<Deck> = emptyList(), onTargetChange: (String) -> Unit = {},
    targetLanguage: String = "de",
) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var problemsOnly by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var mergeSource by remember { mutableStateOf<String?>(null) }
    var pendingMerge by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Uri?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var showMapping by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun editRow(id: String) { focus.clearFocus(); keyboard?.hide(); editingId = id }
    fun selectTab(value: Int) { focus.clearFocus(); keyboard?.hide(); tab = value }
    fun swapColumns() { focus.clearFocus(); keyboard?.hide(); onSwap() }
    val pairIds = draft.pairs.map { it.id }.toSet()
    LaunchedEffect(pairIds) { selectedIds = selectedIds.filter { it in pairIds } }
    fun toggleSelection(id: String) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    LaunchedEffect(draft) { checked = false }
    val existingKeys = remember(existing) { existing.map { pairKey(it.term, it.translation) }.toSet() }
    val counts = remember(draft.pairs) { draft.pairs.groupingBy { pairKey(it.term, it.translation) }.eachCount() }
    fun duplicate(pair: ImportPair) = pair.term.isNotBlank() && pair.translation.isNotBlank() &&
        (pairKey(pair.term, pair.translation) in existingKeys || (counts[pairKey(pair.term, pair.translation)] ?: 0) > 1)
    fun problem(pair: ImportPair) = pair.term.isBlank() || pair.translation.isBlank() ||
        (!pair.reviewed && pair.warning != null) || duplicate(pair)
    val problemCount = draft.pairs.count(::problem)
    val shown = if (problemsOnly) draft.pairs.filter(::problem) else draft.pairs
    val complete = draft.pairs.isNotEmpty() && draft.pairs.all { it.term.isNotBlank() && it.translation.isNotBlank() }
    val languageHint = remember(draft.pairs) { LanguageHint.suggest(draft.pairs) }
    val targetHint = remember(draft.pairs) { LanguageHint.suggest(draft.pairs, translationSide = true) }
    val listState = rememberLazyListState()
    val rowBounds = remember { mutableMapOf<String, Rect>() }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<String?>(null) }
    val currentPairs by rememberUpdatedState(draft.pairs)
    fun targetAt(point: Offset): String? {
        val page = currentPairs.firstOrNull { it.id == dragging }?.page
        return rowBounds.entries.firstOrNull { (id, rect) -> id != dragging && rect.contains(point) &&
            currentPairs.any { it.id == id && it.page == page } }?.key
    }
    LaunchedEffect(dragging) {
        if (dragging != null) while (true) {
            withFrameNanos { }
            val delta = when {
                dragPoint.y < listBounds.top + 90f -> -12f
                dragPoint.y > listBounds.bottom - 90f -> 12f
                else -> 0f
            }
            if (delta != 0f) { listState.scrollBy(delta); dropTarget = targetAt(dragPoint) }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fast in deiner Liste.", style = MaterialTheme.typography.headlineSmall)
                Text("${draft.pairs.size} Wortpaare · $problemCount prüfen", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUndo, enabled = canUndo && !busy) { Icon(Icons.AutoMirrored.Outlined.Undo, "Korrektur rückgängig") }
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreVert, "Importaktionen") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Spalten tauschen") }, onClick = { menu = false; swapColumns() })
                    DropdownMenuItem(text = { Text("Mehrere Zeilen auswählen") }, onClick = {
                        focus.clearFocus(); keyboard?.hide(); menu = false; selecting = true
                    })
                    DropdownMenuItem(text = { Text("Doppelte zusammenfassen") }, enabled = draft.pairs.any(::duplicate),
                        onClick = { menu = false; onDeduplicate() })
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { selectTab(0) }, label = { Text("1 · Bild & Ausschnitt") })
            FilterChip(selected = tab == 1, onClick = { selectTab(1) }, label = { Text("2 · Wortpaare") })
        }
        if (tab == 0) {
            ScanImageEditor(draft, busy, onSplit, onPageEdit, onCrop, Modifier.weight(1f))
            Button(onClick = { selectTab(1) }, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Wortpaare prüfen") }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).onGloballyPositioned { listBounds = it.boundsInRoot() },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = problemsOnly, onClick = { problemsOnly = !problemsOnly }, label = { Text("Nur prüfen ($problemCount)") })
                        FilterChip(selected = selecting, onClick = {
                            focus.clearFocus(); keyboard?.hide(); selecting = !selecting; selectedIds = emptyList()
                        }, label = { Text("Auswählen") })
                        TextButton(enabled = !busy, onClick = { problemsOnly = false; onAdd() }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("Wortpaar")
                        }
                    }
                    if (languageHint != null && languageHint != language) Text(
                        "Links erkannt: ${StudyLanguage.fromCode(languageHint).label}. Erwartet: ${StudyLanguage.fromCode(language).label}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (targetHint != null && targetHint != targetLanguage) Text(
                        "Rechts erkannt: ${StudyLanguage.fromCode(targetHint).label}. Erwartet: ${StudyLanguage.fromCode(targetLanguage).label}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { showMapping = !showMapping }, enabled = !busy) { Text("Ziel & Spalten prüfen") }
                    if (showMapping) {
                        Text("Sprachhinweise: " + (languageHint?.let { StudyLanguage.fromCode(it).label } ?: "links unklar") + " → " +
                            (targetHint?.let { StudyLanguage.fromCode(it).label } ?: "rechts unklar"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (availableDecks.isNotEmpty()) ChoiceDropdown("Ziel der Wortpaare",
                            availableDecks.firstOrNull { it.id == draft.deckId }?.let { "${it.name} · ${it.subjectLabel}" } ?: "Liste wählen",
                            availableDecks.map { "${it.name} · ${it.subjectLabel}" }) { onTargetChange(availableDecks[it].id) }
                        Text("Import: ${StudyLanguage.fromCode(language).label} → ${StudyLanguage.fromCode(targetLanguage).label}. Links: Begriff. Rechts: Übersetzung / Erklärung. Ziehe einen Spaltenkopf auf den anderen oder nutze die Tauschen-Schaltfläche.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (dragging == null) "Text direkt in der Tabelle ändern. Über ⋮ stehen Verbinden, Trennen und Originalvergleich bereit."
                        else if (dropTarget != null) "Loslassen, um diese Zeilen zu verbinden." else "Auf eine andere Zeile derselben Seite ziehen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                    ImportColumnHeader(language, busy, ::swapColumns, targetLanguage)
                }
                if (shown.isEmpty()) item {
                    Text(if (problemsOnly) "Keine offenen Hinweise. Prüfe trotzdem den Text im Original."
                        else "Noch keine Wortpaare. Wähle den Tabellenbereich im Bild oder ergänze ein Wortpaar.",
                        Modifier.padding(vertical = 24.dp))
                }
                itemsIndexed(shown, key = { _, pair -> pair.id }) { index, pair ->
                    var handleOrigin by remember { mutableStateOf(Offset.Zero) }
                    DisposableEffect(pair.id) { onDispose { rowBounds.remove(pair.id) } }
                    val row = draft.pairs.indexOfFirst { it.id == pair.id } + 1
                    val empty = pair.term.isBlank() || pair.translation.isBlank()
                    val status = when {
                        empty -> "Text fehlt"
                        duplicate(pair) -> "Doppelt"
                        !pair.reviewed && pair.warning != null -> "Unsicher" + (pair.confidence?.let { " ${(it * 100).toInt()} %" } ?: "")
                        pair.reviewed -> "Geprüft"
                        else -> "Erkannt"
                    }
                    Card(onClick = { if (selecting) toggleSelection(pair.id) else editRow(pair.id) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().animateItem().onGloballyPositioned { rowBounds[pair.id] = it.boundsInRoot() },
                        colors = CardDefaults.cardColors(containerColor = when (pair.id) {
                            dropTarget -> MaterialTheme.colorScheme.primaryContainer
                            dragging -> MaterialTheme.colorScheme.secondaryContainer
                            else -> when {
                                pair.id in selectedIds -> MaterialTheme.colorScheme.primaryContainer
                                empty -> MaterialTheme.colorScheme.errorContainer
                                problem(pair) -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        })) {
                        Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selecting) Checkbox(checked = pair.id in selectedIds, onCheckedChange = { toggleSelection(pair.id) }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Zeile $row auswählen" })
                            else Icon(Icons.Outlined.DragIndicator, "Zeile $row zum Verbinden ziehen",
                                Modifier.size(44.dp).padding(10.dp).onGloballyPositioned { handleOrigin = it.positionInRoot() }
                                    .pointerInput(pair.id, busy) {
                                        if (!busy) detectDragGesturesAfterLongPress(
                                            onDragStart = { dragging = pair.id; dragPoint = handleOrigin + it },
                                            onDrag = { change, amount -> change.consume(); dragPoint += amount; dropTarget = targetAt(dragPoint) },
                                            onDragEnd = {
                                                dropTarget?.let { pendingMerge = pair.id to it }
                                                dragging = null; dropTarget = null
                                            }, onDragCancel = { dragging = null; dropTarget = null })
                                    }, tint = MaterialTheme.colorScheme.outline)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$row · SEITE ${pair.page + 1} · $status",
                                    style = MaterialTheme.typography.labelSmall, color = if (problem(pair))
                                        MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selecting) WordPairText(pair.term.ifBlank { "Wort fehlt" }, pair.translation.ifBlank { "Übersetzung fehlt" })
                                else ImportPairCells(pair, row, language, busy, onUpdate, targetLanguage)
                            }
                            if (!selecting) IconButton(onClick = { editRow(pair.id) }, enabled = !busy) {
                                Icon(Icons.Outlined.MoreVert, "Zeile $row bearbeiten")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (selecting) Surface(color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedIds.size} Zeilen ausgewählt", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                        TextButton(onClick = { selecting = false; selectedIds = emptyList() }) { Text("Auswahl beenden") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedIds = (selectedIds + shown.map { it.id }).distinct() }, enabled = !busy) { Text("Alle sichtbaren") }
                        TextButton(onClick = { onReviewMany(selectedIds.toSet()); selectedIds = emptyList() }, enabled = !busy && selectedIds.isNotEmpty()) { Text("Als geprüft markieren") }
                        TextButton(onClick = { confirmDeleteSelection = true }, enabled = !busy && selectedIds.isNotEmpty()) { Text("Auswahl löschen") }
                    }
                }
            } else Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Mit dem Original verglichen" })
                        Text("Mit dem Original verglichen", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { focus.clearFocus(); keyboard?.hide(); onConfirm() }, enabled = complete && checked && !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${draft.pairs.size} Wortpaare übernehmen")
                    }
                    if (!complete) Text("Leere Felder ergänzen oder betroffene Zeilen entfernen.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirmDeleteSelection) ConfirmDialog("Ausgewählte Zeilen löschen?",
        "${selectedIds.size} Zeilen werden aus diesem Import entfernt. Du kannst die Aktion anschließend rückgängig machen.", "Löschen",
        onDismiss = { confirmDeleteSelection = false }, onConfirm = {
            onRemoveMany(selectedIds.toSet()); selectedIds = emptyList(); confirmDeleteSelection = false
        })
    editingId?.let { id -> draft.pairs.firstOrNull { it.id == id }?.let { pair ->
        PairCorrectionSheet(pair, language, busy, duplicate(pair), targetLanguage = targetLanguage, onDismiss = { editingId = null },
            onSave = { term, translation -> onUpdate(id, term, translation); editingId = null },
            onRemove = { onRemove(id); editingId = null }, onMerge = { editingId = null; mergeSource = id },
            onSplit = { term, translation, left, right ->
                onUpdate(id, term, translation); onSeparate(id, left, right); editingId = null
            }, onOriginal = draft.pages.getOrNull(pair.page)?.let { uri -> { original = uri } })
    } }
    mergeSource?.let { id ->
        val source = draft.pairs.firstOrNull { it.id == id }
        AlertDialog(onDismissRequest = { mergeSource = null }, title = { Text("Mit welcher Zeile verbinden?") },
            text = { LazyColumn(Modifier.heightIn(max = 320.dp)) {
                val candidates = draft.pairs.filter { it.id != id && it.page == source?.page }
                if (candidates.isEmpty()) item { Text("Keine weitere Zeile auf dieser Seite.") }
                itemsIndexed(candidates) { _, pair -> TextButton(onClick = { pendingMerge = id to pair.id; mergeSource = null }) {
                    Text("${pair.term}  ·  ${pair.translation}")
                } }
            } }, confirmButton = { TextButton(onClick = { mergeSource = null }) { Text("Abbrechen") } })
    }
    pendingMerge?.let { ids ->
        val pairs = draft.pairs.filter { it.id == ids.first || it.id == ids.second }
        AlertDialog(onDismissRequest = { pendingMerge = null }, title = { Text("Zeilen verbinden?") },
            text = { Text(pairs.joinToString("\n+\n") { "${it.term}  ↔  ${it.translation}" } + "\n\nDie Reihenfolge auf der Seite bleibt erhalten.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onMerge(ids.first, ids.second); pendingMerge = null }) { Text("Verbinden") } },
            dismissButton = { TextButton(onClick = { pendingMerge = null }) { Text("Abbrechen") } })
    }
    original?.let { OriginalImageDialog(it, onDismiss = { original = null }) }
}
