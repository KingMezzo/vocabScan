package de.vocabscan.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.vocabscan.data.Deck
import de.vocabscan.scan.ImportPair

@Composable
internal fun ImportColumnHeader(language: String, busy: Boolean, onSwap: () -> Unit, targetLanguage: String = "de") {
    var left by remember { mutableStateOf(Rect.Zero) }
    var right by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<Boolean?>(null) }
    var point by remember { mutableStateOf(Offset.Zero) }
    val deck = Deck("", "", language, targetLanguage = targetLanguage)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf(true, false).forEach { isLeft ->
            val label = if (isLeft) deck.termLabel else deck.translationLabel
            val hovered = dragging != null && dragging != isLeft && (if (isLeft) left else right).contains(point)
            Surface(Modifier.weight(1f).onGloballyPositioned { if (isLeft) left = it.boundsInRoot() else right = it.boundsInRoot() }
                .semantics(mergeDescendants = true) { contentDescription = "$label-Spalte zum Tauschen ziehen" }
                .pointerInput(left, right, busy) {
                    if (!busy) detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = isLeft; point = (if (isLeft) left else right).topLeft + it },
                        onDrag = { change, delta -> change.consume(); point += delta },
                        onDragEnd = {
                            if ((if (isLeft) right else left).contains(point)) onSwap()
                            dragging = null
                        }, onDragCancel = { dragging = null })
                }, color = if (hovered) MaterialTheme.colorScheme.tertiaryContainer else if (isLeft)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small) {
                Text(label, Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
            }
            if (isLeft) IconButton(onClick = onSwap, enabled = !busy) { Icon(Icons.Outlined.SwapHoriz, "Spalten tauschen") }
        }
    }
}

@Composable
internal fun ImportPairCells(pair: ImportPair, row: Int, language: String, busy: Boolean,
    onUpdate: (String, String, String) -> Unit, targetLanguage: String = "de") {
    val large = LocalDensity.current.fontScale >= 1.3f
    val deck = Deck("", "", language, targetLanguage = targetLanguage)
    val foreign = deck.termLabel
    val target = deck.translationLabel
    if (large) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ImportCell(pair.term, "$foreign · Zeile $row", true, busy, Modifier.fillMaxWidth()) { onUpdate(pair.id, it, pair.translation) }
        ImportCell(pair.translation, "$target · Zeile $row", true, busy, Modifier.fillMaxWidth()) { onUpdate(pair.id, pair.term, it) }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportCell(pair.term, "$foreign · Zeile $row", false, busy, Modifier.weight(1f)) { onUpdate(pair.id, it, pair.translation) }
        ImportCell(pair.translation, "$target · Zeile $row", false, busy, Modifier.weight(1f)) { onUpdate(pair.id, pair.term, it) }
    }
}

@Composable
private fun ImportCell(value: String, label: String, showLabel: Boolean, busy: Boolean, modifier: Modifier,
    onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, enabled = !busy, isError = value.isBlank(), maxLines = 3,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = if (showLabel) ({ Text(label) }) else null,
        placeholder = { Text("Text fehlt") }, shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = label })
}
