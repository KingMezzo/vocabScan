package de.vocabscan.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import de.vocabscan.scan.PairParser
import de.vocabscan.scan.ScanCrop
import kotlin.math.min

@Composable
fun ScanImageEditor(draft: ImportDraft, busy: Boolean, onSplit: (Float?) -> Unit,
    onPageEdit: (Int, Int, Float) -> Unit, onCrop: (Int, ScanCrop) -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var split by rememberSaveable { mutableFloatStateOf(0.5f) }
    var showCrop by rememberSaveable { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val dimensions = draft.dimensions.getOrNull(page) ?: (1 to 1)
    val tokens = draft.tokens.getOrNull(page).orEmpty()
    LaunchedEffect(page, draft.tokens, draft.manualSplit) {
        split = draft.manualSplit ?: (PairParser.suggestedBoundary(tokens)?.div(dimensions.first.coerceAtLeast(1)) ?: 0.5f).coerceIn(0.05f, 0.95f)
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.pages.isEmpty()) Text("Für diesen Import ist kein Bild vorhanden.")
        if (draft.pages.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.pages.indices.forEach { index -> FilterChip(selected = page == index, onClick = { page = index }, label = { Text("Seite ${index + 1}") }) }
        }
        draft.pages.getOrNull(page)?.let { uri ->
            Box(Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(dimensions, busy) {
                    if (!busy) detectDragGestures { change, _ ->
                        val image = fitImage(size.width.toFloat(), size.height.toFloat(), dimensions)
                        split = ((change.position.x - image.left) / image.width).coerceIn(0.05f, 0.95f)
                        change.consume()
                    }
                }) {
                AsyncImage(uri, "Originalseite ${page + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    val image = fitImage(size.width, size.height, dimensions)
                    val scale = image.width / dimensions.first.coerceAtLeast(1)
                    tokens.forEach { token ->
                        val color = if (token.confidence in 0f..0.79f) Color(0xFFE45E36)
                            else if (token.centerX < dimensions.first * split) Color(0xFF2D8867) else Color(0xFF5665D8)
                        val origin = Offset(image.left + token.left * scale, image.top + token.top * scale)
                        val boxSize = Size((token.right - token.left) * scale, (token.bottom - token.top) * scale)
                        drawRect(color.copy(alpha = 0.13f), origin, boxSize)
                        drawRect(color, origin, boxSize, style = Stroke(1.dp.toPx()))
                    }
                    val x = image.left + image.width * split
                    drawLine(Color(0xFFFFB300), Offset(x, image.top), Offset(x, image.bottom), 3.dp.toPx())
                }
            }
            Text("Grün: links · Blau: rechts · Orange: unsicher", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { showCrop = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Crop, null); Spacer(Modifier.width(8.dp)); Text("Tabelle auswählen")
                }
                IconButton(onClick = { onPageEdit(page, 270, 0f) }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.RotateLeft, "Nach links drehen") }
                IconButton(onClick = { onPageEdit(page, 90, 0f) }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.RotateRight, "Nach rechts drehen") }
                IconButton(onClick = { showOriginal = true }) { Icon(Icons.Outlined.ZoomIn, "Original vergrößern") }
            }
            Text("Spaltengrenze", style = MaterialTheme.typography.titleMedium)
            Text("Ziehe die gelbe Linie zwischen die Spalten und ordne die Wortpaare neu zu.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = split, onValueChange = { split = it }, valueRange = 0.05f..0.95f, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Spaltengrenze" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSplit(null) }, enabled = !busy) { Text("Automatisch") }
                Button(onClick = { onSplit(split) }, enabled = !busy) { Text("Neu zuordnen") }
            }
            Text("Drehen und Zuschneiden erkennen diese Seite neu. Neu zuordnen ersetzt Korrekturen auf allen Seiten. Über ↶ kannst du Änderungen rückgängig machen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showOriginal) OriginalImageDialog(uri, onDismiss = { showOriginal = false })
            if (showCrop) CropScanDialog(uri, dimensions, onDismiss = { showCrop = false }, onApply = {
                onCrop(page, it); showCrop = false
            })
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun fitImage(width: Float, height: Float, dimensions: Pair<Int, Int>): Rect {
    val scale = min(width / dimensions.first.coerceAtLeast(1), height / dimensions.second.coerceAtLeast(1))
    val w = dimensions.first.coerceAtLeast(1) * scale
    val h = dimensions.second.coerceAtLeast(1) * scale
    return Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}

@Composable
private fun CropScanDialog(uri: Uri, dimensions: Pair<Int, Int>, onDismiss: () -> Unit, onApply: (ScanCrop) -> Unit) {
    var left by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.05f) }
    var top by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.05f) }
    var right by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.95f) }
    var bottom by rememberSaveable(uri.toString()) { mutableFloatStateOf(0.95f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nur deine Tabelle.", style = MaterialTheme.typography.headlineSmall)
                Text("Ziehe die Ecken oder verschiebe den Rahmen. Alles außerhalb wird abgeschnitten.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainer)
                    .semantics { contentDescription = "Tabellenbereich mit verschiebbaren Ecken" }
                    .pointerInput(dimensions) {
                        var corner = -1
                        detectDragGestures(onDragStart = { point ->
                            val image = fitImage(size.width.toFloat(), size.height.toFloat(), dimensions)
                            val corners = listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
                                .map { Offset(image.left + it.x * image.width, image.top + it.y * image.height) }
                            val nearest = corners.indices.minBy { (corners[it] - point).getDistance() }
                            val inside = Rect(corners[0], corners[3]).contains(point)
                            corner = if (inside && (corners[nearest] - point).getDistance() > 40.dp.toPx()) -1 else nearest
                        }, onDrag = { change, amount ->
                            change.consume()
                            val image = fitImage(size.width.toFloat(), size.height.toFloat(), dimensions)
                            val x = amount.x / image.width
                            val y = amount.y / image.height
                            if (corner == -1) {
                                val dx = x.coerceIn(-left, 1f - right)
                                val dy = y.coerceIn(-top, 1f - bottom)
                                left += dx; right += dx; top += dy; bottom += dy
                            } else {
                                if (corner == 0 || corner == 2) left = (left + x).coerceIn(0f, right - 0.05f)
                                else right = (right + x).coerceIn(left + 0.05f, 1f)
                                if (corner == 0 || corner == 1) top = (top + y).coerceIn(0f, bottom - 0.05f)
                                else bottom = (bottom + y).coerceIn(top + 0.05f, 1f)
                            }
                        })
                    }) {
                    AsyncImage(uri, "Ausschnitt-Vorschau", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val image = fitImage(size.width, size.height, dimensions)
                        val box = Rect(image.left + left * image.width, image.top + top * image.height,
                            image.left + right * image.width, image.top + bottom * image.height)
                        val shade = Color.Black.copy(alpha = 0.55f)
                        drawRect(shade, Offset.Zero, Size(size.width, box.top))
                        drawRect(shade, Offset(0f, box.bottom), Size(size.width, size.height - box.bottom))
                        drawRect(shade, Offset(0f, box.top), Size(box.left, box.height))
                        drawRect(shade, Offset(box.right, box.top), Size(size.width - box.right, box.height))
                        drawRect(Color.White, box.topLeft, box.size, style = Stroke(2.dp.toPx()))
                        listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).forEach { drawCircle(Color.White, 7.dp.toPx(), it) }
                    }
                }
                Text("Breite", style = MaterialTheme.typography.labelMedium)
                RangeSlider(value = left..right, onValueChange = {
                    if (it.endInclusive - it.start >= 0.05f) { left = it.start; right = it.endInclusive }
                }, modifier = Modifier.semantics { contentDescription = "Horizontaler Tabellenbereich" })
                Text("Höhe", style = MaterialTheme.typography.labelMedium)
                RangeSlider(value = top..bottom, onValueChange = {
                    if (it.endInclusive - it.start >= 0.05f) { top = it.start; bottom = it.endInclusive }
                }, modifier = Modifier.semantics { contentDescription = "Vertikaler Tabellenbereich" })
                Text("Die Seite wird neu erkannt. Bisherige Zeilenkorrekturen dieser Seite werden ersetzt und können rückgängig gemacht werden.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
                    Button(onClick = { onApply(ScanCrop(left, top, right, bottom)) }, modifier = Modifier.weight(1f)) { Text("Zuschneiden") }
                }
            }
        }
    }
}
