package de.vocabscan.scan

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import kotlin.math.roundToInt
import java.io.File
import java.util.UUID
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrImporter(private val context: Context) {
    data class RecognizedPage(val tokens: List<OcrToken>, val pairs: List<ImportPair>, val width: Int, val height: Int)

    suspend fun recognize(pages: List<Uri>): List<ImportPair> = recognizePages(pages).flatMap { it.pairs }

    suspend fun recognizePages(pages: List<Uri>): List<RecognizedPage> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            pages.mapIndexed { index, uri -> recognizePage(uri, index, recognizer) }
        } finally {
            recognizer.close()
        }
    }

    suspend fun recognizePage(uri: Uri, index: Int): RecognizedPage = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try { recognizePage(uri, index, recognizer) } finally { recognizer.close() }
    }

    private suspend fun recognizePage(uri: Uri, index: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer): RecognizedPage {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        val initialTokens = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
            element.boundingBox?.let { box -> OcrToken(element.text, box.left, box.top, box.right, box.bottom, element.confidence) }
        }
        val initialBoundary = PairParser.suggestedBoundary(initialTokens)
        var boundary = initialBoundary
        val tokens = try {
            val upright = decodeUpright(uri)
            try {
                val baseTokens = if (boundary == null) {
                    val refreshed = recognizeRegions(upright, listOf(0 to upright.width), image.width, image.height, recognizer)
                    if (ocrQuality(refreshed) >= ocrQuality(initialTokens) * 0.85f && refreshed.isNotEmpty()) refreshed
                    else initialTokens
                } else initialTokens
                boundary = boundary ?: PairParser.suggestedBoundary(baseTokens)
                if (boundary != null && upright.width > 1) {
                    // Read each column and tall page section separately, then map the
                    // boxes back to the coordinates shown in the image editor.
                    val split = (boundary!! * upright.width / image.width).roundToInt().coerceIn(1, upright.width - 1)
                    val separate = recognizeRegions(upright, listOf(0 to split, split to (upright.width - split)),
                        image.width, image.height, recognizer)
                    listOf(true, false).flatMap { left ->
                        val originalColumn = baseTokens.filter { (it.centerX < boundary!!) == left }
                        val croppedColumn = separate.filter { (it.centerX < boundary!!) == left }
                        if (ocrQuality(croppedColumn) >= ocrQuality(originalColumn) * 0.7f && croppedColumn.isNotEmpty()) {
                            croppedColumn
                        } else originalColumn
                    }
                } else baseTokens
            } finally { upright.recycle() }
        } catch (error: Exception) {
            // A content provider can reject a second read; the initial OCR is still usable.
            boundary = initialBoundary
            initialTokens
        }
        return RecognizedPage(tokens, PairParser.parse(tokens, index, boundary), image.width, image.height)
    }

    private fun ocrQuality(tokens: List<OcrToken>): Float = tokens.sumOf { token ->
        textQuality(token.text, token.confidence).toDouble()
    }.toFloat()

    private fun elementQuality(elements: List<com.google.mlkit.vision.text.Text.Element>): Float =
        elements.sumOf { textQuality(it.text, it.confidence).toDouble() }.toFloat()

    private fun textQuality(text: String, confidence: Float): Float =
        text.count { it.isLetterOrDigit() } *
            (confidence.takeIf { it in 0f..1f } ?: 0.5f).coerceAtLeast(0.25f)

    private suspend fun recognizeRegions(bitmap: Bitmap, regions: List<Pair<Int, Int>>, imageWidth: Int, imageHeight: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer): List<OcrToken> {
        val tokens = mutableListOf<OcrToken>()
        val sectionHeight = 1800
        for ((x, columnWidth) in regions) {
            var sectionTop = 0
            while (sectionTop < bitmap.height) {
                val sectionBottom = (sectionTop + sectionHeight).coerceAtMost(bitmap.height)
                val cropTop = (sectionTop - 40).coerceAtLeast(0)
                val cropBottom = (sectionBottom + 40).coerceAtMost(bitmap.height)
                val cropped = Bitmap.createBitmap(bitmap, x, cropTop, columnWidth, cropBottom - cropTop)
                val scale = (1000f / columnWidth).coerceIn(1f, 2f)
                val input = if (scale > 1f) Bitmap.createScaledBitmap(cropped,
                    (cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt(), true) else cropped
                try {
                    val plain = recognizer.process(InputImage.fromBitmap(input, 0)).await()
                    val plainElements = plain.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                    val validConfidence = plainElements.map { it.confidence }.filter { it in 0f..1f }
                    val recognized = if (validConfidence.isEmpty() || validConfidence.average() < 0.8) {
                        val enhanced = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
                        try {
                            val gray = ColorMatrix().apply { setSaturation(0f) }
                            val contrast = ColorMatrix(floatArrayOf(
                                1.5f, 0f, 0f, 0f, -60f,
                                0f, 1.5f, 0f, 0f, -60f,
                                0f, 0f, 1.5f, 0f, -60f,
                                0f, 0f, 0f, 1f, 0f,
                            ))
                            gray.postConcat(contrast)
                            Canvas(enhanced).drawBitmap(input, 0f, 0f,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(gray) })
                            val second = recognizer.process(InputImage.fromBitmap(enhanced, 0)).await()
                            if (elementQuality(second.textBlocks.flatMap { it.lines }.flatMap { it.elements }) >
                                elementQuality(plainElements) * 1.12f) second else plain
                        } finally { enhanced.recycle() }
                    } else plain
                    val scaleX = input.width.toFloat() / cropped.width
                    val scaleY = input.height.toFloat() / cropped.height
                    recognized.textBlocks.flatMap { it.lines }.flatMap { it.elements }.forEach { element ->
                        val box = element.boundingBox ?: return@forEach
                        val centerY = cropTop + (box.top + box.bottom) / (2f * scaleY)
                        if (centerY < sectionTop || centerY >= sectionBottom) return@forEach
                        tokens += OcrToken(element.text,
                            ((x + box.left / scaleX) * imageWidth / bitmap.width).roundToInt(),
                            ((cropTop + box.top / scaleY) * imageHeight / bitmap.height).roundToInt(),
                            ((x + box.right / scaleX) * imageWidth / bitmap.width).roundToInt(),
                            ((cropTop + box.bottom / scaleY) * imageHeight / bitmap.height).roundToInt(),
                            element.confidence)
                    }
                } finally {
                    if (input !== cropped) input.recycle()
                    cropped.recycle()
                }
                sectionTop = sectionBottom
            }
        }
        return tokens
    }

    private fun decodeUpright(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = context.contentResolver.openInputStream(uri) ?: error("Bild konnte nicht geöffnet werden")
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val sample = generateSequence(1) { it * 2 }.first { size ->
            maxOf(bounds.outWidth, bounds.outHeight) / size <= 5000
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Bild konnte nicht gelesen werden")
        val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
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
        if (matrix.isIdentity) return decoded
        return try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
        finally { decoded.recycle() }
    }

    /** Creates a bounded private copy and reruns OCR on that exact image. */
    suspend fun transform(uri: Uri, rotation: Int = 0, crop: Float = 0f, area: ScanCrop? = null): Uri = withContext(Dispatchers.IO) {
        require(rotation in listOf(0, 90, 180, 270) && crop in 0f..0.2f)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = context.contentResolver.openInputStream(uri) ?: error("Bild konnte nicht geöffnet werden")
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val sample = generateSequence(1) { it * 2 }.first { size ->
            maxOf(bounds.outWidth, bounds.outHeight) / size <= 5000
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Bild konnte nicht gelesen werden")
        val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
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
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val region = area ?: ScanCrop(crop, crop, 1f - crop, 1f - crop)
        val left = (upright.width * region.left).toInt().coerceIn(0, upright.width - 1)
        val top = (upright.height * region.top).toInt().coerceIn(0, upright.height - 1)
        val right = (upright.width * region.right).toInt().coerceIn(left + 1, upright.width)
        val bottom = (upright.height * region.bottom).toInt().coerceIn(top + 1, upright.height)
        val clipped = Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
        val rotated = if (rotation == 0) clipped else Bitmap.createBitmap(clipped, 0, 0,
            clipped.width, clipped.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val file = File(context.cacheDir, "scan-edit-${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { require(rotated.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            Uri.fromFile(file)
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            if (rotated !== clipped) rotated.recycle()
            if (clipped !== upright) clipped.recycle()
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }
}
