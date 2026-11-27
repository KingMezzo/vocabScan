package de.vocabscan.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.vocabscan.scan.OcrBounds
import de.vocabscan.scan.ScanSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class Excerpt(val bitmap: Bitmap, val crop: Rect, val fullWidth: Int, val fullHeight: Int)

@Composable
fun ScanExcerpt(file: File, source: ScanSource) {
    val excerpt by produceState<Excerpt?>(null, file.absolutePath, source) {
        value = withContext(Dispatchers.IO) { runCatching { loadExcerpt(file, source) }.getOrNull() }
    }
    val shown = excerpt ?: return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Originalausschnitt", style = MaterialTheme.typography.titleMedium)
            Text("Markiert: Begriff und Übersetzung / Erklärung", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).aspectRatio(
                shown.bitmap.width.toFloat() / shown.bitmap.height.coerceAtLeast(1))) {
                Image(shown.bitmap.asImageBitmap(), "Ausschnitt des gescannten Originals",
                    modifier = Modifier.fillMaxWidth())
                Canvas(Modifier.matchParentSize()) {
                    fun mark(bounds: OcrBounds?, color: androidx.compose.ui.graphics.Color) {
                        if (bounds == null) return
                        val x = (bounds.left.toFloat() / source.width * shown.fullWidth - shown.crop.left) * size.width / shown.crop.width()
                        val y = (bounds.top.toFloat() / source.height * shown.fullHeight - shown.crop.top) * size.height / shown.crop.height()
                        val right = (bounds.right.toFloat() / source.width * shown.fullWidth - shown.crop.left) * size.width / shown.crop.width()
                        val bottom = (bounds.bottom.toFloat() / source.height * shown.fullHeight - shown.crop.top) * size.height / shown.crop.height()
                        val width = (right - x).coerceAtLeast(1f)
                        val height = (bottom - y).coerceAtLeast(1f)
                        drawRect(color.copy(alpha = 0.2f), Offset(x, y), Size(width, height))
                        drawRect(color, Offset(x, y), Size(width, height), style = Stroke(2.dp.toPx()))
                    }
                    mark(source.term, primary)
                    mark(source.translation, secondary)
                }
            }
        }
    }
}

private fun loadExcerpt(file: File, source: ScanSource): Excerpt {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / it <= 2400 }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Scanbild nicht lesbar")
    val orientation = ExifInterface(file.absolutePath)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0,
        decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
    val regions = listOfNotNull(source.term, source.translation)
    val crop = if (regions.isEmpty()) Rect(0, 0, upright.width, upright.height) else {
        val left = regions.minOf { it.left }.toFloat() / source.width * upright.width
        val top = regions.minOf { it.top }.toFloat() / source.height * upright.height
        val right = regions.maxOf { it.right }.toFloat() / source.width * upright.width
        val bottom = regions.maxOf { it.bottom }.toFloat() / source.height * upright.height
        val padX = ((right - left) * 0.12f).coerceAtLeast(24f)
        val padY = ((bottom - top) * 1.5f).coerceAtLeast(24f)
        Rect((left - padX).roundToInt().coerceIn(0, upright.width - 1),
            (top - padY).roundToInt().coerceIn(0, upright.height - 1),
            (right + padX).roundToInt().coerceIn(1, upright.width),
            (bottom + padY).roundToInt().coerceIn(1, upright.height))
    }
    require(crop.width() > 0 && crop.height() > 0)
    return Excerpt(Bitmap.createBitmap(upright, crop.left, crop.top, crop.width(), crop.height()).also {
        if (it !== upright) upright.recycle()
    }, crop, upright.width, upright.height)
}
