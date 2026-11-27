package de.vocabscan.ui

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import de.vocabscan.data.DeckType
import de.vocabscan.data.DeckWithVocabulary
import de.vocabscan.data.DeckQrCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val QR_FRAME_MILLIS = 3500L

@Composable
fun DeckQrDialog(deck: DeckWithVocabulary, onDismiss: () -> Unit) {
    val frames by produceState<Result<List<String>>?>(null, deck) {
        value = withContext(Dispatchers.Default) { runCatching { DeckQrCodec.encode(deck) } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val keptAwake = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { if (!keptAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Card(Modifier.padding(16.dp).widthIn(max = 820.dp).fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Liste teilen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(deck.deck.name)
                when {
                    frames == null -> CircularProgressIndicator()
                    frames!!.isFailure -> Text(frames!!.exceptionOrNull()?.message ?: "QR-Code kann nicht erstellt werden.")
                    else -> QrCarousel(frames!!.getOrThrow(), deck.words.size, deck.deck.type)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Schließen") }
            }
        }
    }
}

@Composable
internal fun QrCarousel(codes: List<String>, wordCount: Int, type: DeckType = DeckType.SPRACHE) {
    val context = LocalContext.current
    val touchExploration = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    var index by rememberSaveable(codes) { mutableIntStateOf(0) }
    var automatic by rememberSaveable(codes) { mutableStateOf(!touchExploration) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(automatic, index, lifecycle, codes) {
        if (automatic && codes.size > 1 && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            delay(QR_FRAME_MILLIS)
            index = (index + 1) % codes.size
        }
    }
    val bitmap by produceState<Bitmap?>(null, codes, index) {
        value = withContext(Dispatchers.Default) { qrBitmap(codes[index]) }
    }
    // Keep the image sharp while it is scanned; only the controls animate.
    val image: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "QR-Code ${index + 1} von ${codes.size}",
                modifier = Modifier.fillMaxSize()) }
        }
    }
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Code ${index + 1} von ${codes.size}", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { (index + 1f) / codes.size }, modifier = Modifier.fillMaxWidth())
            Text("$wordCount " + if (type.isFlashcards) "Karten" else "Vokabeln", style = MaterialTheme.typography.bodySmall)
            if (codes.size > 1) {
                Text(if (automatic) "Wechselt alle 3,5 Sekunden und beginnt danach von vorn."
                    else "Automatischer Wechsel pausiert. Blättere in deinem Tempo.")
                FilledTonalButton(onClick = { automatic = !automatic }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (automatic) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (automatic) "Pausieren" else "Automatisch wechseln")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { automatic = false; index = (index - 1 + codes.size) % codes.size }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vorheriger Code")
                    }
                    IconButton(onClick = { automatic = false; index = (index + 1) % codes.size }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Nächster Code")
                    }
                }
            }
            Text("Auf dem anderen Gerät Bibliothek → + → „Per QR-Code importieren“ öffnen. Dort siehst du, welche Codes noch fehlen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth >= 600.dp && fontScale < 1.5f) Row(horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { image() }
            Box(Modifier.weight(1f)) { controls() }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { image(); controls() }
    }
}

@Composable
internal fun QrImportProgress(received: Set<Int>, expected: Int, scanning: Boolean, automatic: Boolean,
    status: String? = null, onAutomaticChange: (Boolean) -> Unit, onScan: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("QR-Import") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (expected > 0) "${received.size} von $expected Codes erfasst" else "Bereit zum Scannen",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (expected > 0) {
                    LinearProgressIndicator(progress = { received.size.toFloat() / expected }, modifier = Modifier.fillMaxWidth())
                    val missing = (1..expected).filterNot { it in received }
                    Text("Noch offen: ${missing.take(12).joinToString(", ")}" + if (missing.size > 12) " …" else "")
                }
                Text(if (scanning) "Scanner geöffnet …" else if (automatic && received.isNotEmpty())
                    "Der nächste Scan öffnet automatisch. Halte die Kamera auf die wechselnden Codes."
                    else "Deine erfassten Codes bleiben erhalten. Du kannst jederzeit weiterscannen.")
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatisch weiterscannen", Modifier.weight(1f))
                    Switch(checked = automatic, onCheckedChange = onAutomaticChange,
                        modifier = Modifier.semantics { contentDescription = "Automatisch weiterscannen" })
                }
            }
        },
        confirmButton = { TextButton(onClick = onScan, enabled = !scanning) { Text("Scannen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun qrBitmap(payload: String): Bitmap {
    val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
