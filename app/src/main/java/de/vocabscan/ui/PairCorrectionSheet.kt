package de.vocabscan.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Deck
import de.vocabscan.scan.ImportPair
import de.vocabscan.scan.ScanEdits
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairCorrectionSheet(
    pair: ImportPair, language: String, busy: Boolean, duplicate: Boolean,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit, onRemove: () -> Unit,
    onMerge: () -> Unit, onSplit: (String, String, Int, Int) -> Unit,
    onOriginal: (() -> Unit)? = null,
    targetLanguage: String = "de",
) {
    val deck = Deck("", "", language, targetLanguage = targetLanguage)
    var term by rememberSaveable(pair.id) { mutableStateOf(pair.term) }
    var translation by rememberSaveable(pair.id) { mutableStateOf(pair.translation) }
    var mode by rememberSaveable(pair.id) { mutableIntStateOf(0) }
    val leftWords = ScanEdits.words(term)
    val rightWords = ScanEdits.words(translation)
    var leftAt by rememberSaveable(term) { mutableIntStateOf((leftWords.size + 1) / 2) }
    var rightAt by rememberSaveable(translation) { mutableIntStateOf((rightWords.size + 1) / 2) }
    val canSplit = leftAt + rightAt > 0 && leftAt + rightAt < leftWords.size + rightWords.size
    var leftBounds by remember { mutableStateOf(Rect.Zero) }
    var rightBounds by remember { mutableStateOf(Rect.Zero) }
    var targetSide by remember { mutableStateOf<Boolean?>(null) }
    var moving by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset.Zero) }
    fun move(fromTerm: Boolean, index: Int) {
        val updated = ScanEdits.moveWord(listOf(pair.copy(term = term, translation = translation)), pair.id, fromTerm, index).single()
        term = updated.term; translation = updated.translation
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Wortpaar korrigieren", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seite ${pair.page + 1}" + (pair.confidence?.let { " · Erkennung ${(it * 100).toInt()} %" } ?: ""),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onOriginal != null) IconButton(onClick = onOriginal) {
                        Icon(Icons.Outlined.Image, "Original zum Wortpaar ansehen")
                    }
                }
                if (duplicate) Text("Dieses Wortpaar ist doppelt. Beim Import wird es nur einmal übernommen.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (!pair.reviewed) pair.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Text", "Verschieben", "Trennen").forEachIndexed { index, title ->
                        FilterChip(selected = mode == index, onClick = { mode = index }, label = { Text(title) })
                    }
                }
                when (mode) {
                    0 -> {
                        OutlinedTextField(term, { term = it }, label = { Text(deck.termLabel) },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy, minLines = 2)
                        OutlinedTextField(translation, { translation = it }, label = { Text(deck.translationLabel) },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy, minLines = 2)
                        Text("Auch unvollständige Paare kannst du zwischenspeichern und später ergänzen.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> {
                        Text("Ein Wort antippen oder halten und in die andere Spalte ziehen. Es wird dort hinten angefügt.",
                            style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(true, false).forEach { fromTerm ->
                                Surface(Modifier.weight(1f).heightIn(min = 160.dp).onGloballyPositioned {
                                    if (fromTerm) leftBounds = it.boundsInRoot() else rightBounds = it.boundsInRoot()
                                }, shape = MaterialTheme.shapes.medium, color = if (targetSide == fromTerm)
                                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (fromTerm) deck.termLabel else deck.translationLabel,
                                            style = MaterialTheme.typography.labelLarge)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val tokens = if (fromTerm) leftWords else rightWords
                                            if (tokens.isEmpty()) Text("Hier ablegen", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 24.dp))
                                            tokens.forEachIndexed { index, word ->
                                                var origin by remember { mutableStateOf(Offset.Zero) }
                                                InputChip(selected = false, enabled = !busy,
                                                    onClick = { if (!moving) move(fromTerm, index) }, label = { Text(word) },
                                                    modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() }
                                                        .semantics { contentDescription = "$word in die andere Spalte verschieben" }
                                                        .pointerInput(word, index, fromTerm, busy) {
                                                            if (!busy) detectDragGesturesAfterLongPress(
                                                                onDragStart = { moving = true; point = origin + it },
                                                                onDrag = { change, amount ->
                                                                    change.consume(); point += amount
                                                                    targetSide = if (fromTerm && rightBounds.contains(point)) false
                                                                        else if (!fromTerm && leftBounds.contains(point)) true else null
                                                                }, onDragEnd = {
                                                                    if (targetSide == !fromTerm) move(fromTerm, index)
                                                                    moving = false; targetSide = null
                                                                }, onDragCancel = { moving = false; targetSide = null })
                                                        })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Ziehe die Regler an die Wortgrenzen. Die Vorschau zeigt beide neuen Zeilen.")
                        SplitControl(deck.termLabel, leftWords, leftAt) { leftAt = it }
                        SplitControl(deck.translationLabel, rightWords, rightAt) { rightAt = it }
                        listOf(leftWords.take(leftAt) to rightWords.take(rightAt),
                            leftWords.drop(leftAt) to rightWords.drop(rightAt)).forEachIndexed { index, parts ->
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text("ZEILE ${index + 1}", style = MaterialTheme.typography.labelSmall)
                                    Text("${parts.first.joinToString(" ").ifBlank { "Wort fehlt" }}  ↔  ${parts.second.joinToString(" ").ifBlank { "Übersetzung fehlt" }}")
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                TextButton(enabled = !busy, onClick = { onSave(term, translation); onMerge() }) {
                    Icon(Icons.AutoMirrored.Outlined.MergeType, null); Spacer(Modifier.width(8.dp)); Text("Mit anderer Zeile zusammenführen")
                }
                TextButton(enabled = !busy, onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Zeile entfernen")
                }
            }
            Button(enabled = !busy && (mode != 2 || canSplit), onClick = {
                if (mode == 2) onSplit(term, translation, leftAt, rightAt) else onSave(term, translation)
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text(if (mode == 2) "In zwei Zeilen trennen" else "Änderungen übernehmen")
            }
        }
    }
}

@Composable
private fun SplitControl(label: String, words: List<String>, at: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label · ${words.take(at).joinToString(" ")} | ${words.drop(at).joinToString(" ")}", style = MaterialTheme.typography.bodySmall)
        Slider(value = at.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..words.size.coerceAtLeast(1).toFloat(), steps = (words.size - 1).coerceAtLeast(0),
            enabled = words.isNotEmpty(), modifier = Modifier.semantics { contentDescription = "Trennstelle $label" })
    }
}
