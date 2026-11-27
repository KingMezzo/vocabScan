package de.vocabscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrImporterTest {
    @Test fun cropUsesDisplayedCoordinatesOfExifRotatedPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "ocr-exif-crop.jpg")
        val bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).drawRect(200f, 180f, 900f, 480f, Paint().apply { color = Color.RED })
        val importer = OcrImporter(context)
        try {
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            android.media.ExifInterface(source.path).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val original = importer.recognizePage(Uri.fromFile(source), 0)
            assertEquals(600, original.width)
            assertEquals(1000, original.height)
            val cropped = importer.transform(Uri.fromFile(source), area = ScanCrop(0.2f, 0.2f, 0.7f, 0.9f))
            try {
                val result = android.graphics.BitmapFactory.decodeFile(cropped.path)
                assertEquals(300, result.width)
                assertEquals(700, result.height)
                assertTrue(Color.red(result.getPixel(150, 350)) > 240)
                assertTrue(Color.green(result.getPixel(150, 350)) < 20)
                result.recycle()
            } finally { File(cropped.path!!).delete() }
        } finally { bitmap.recycle(); source.delete() }
    }

    @Test fun freeCropSelectsAnOffsetTableRegion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "ocr-free-crop.png")
        val bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).drawRect(200f, 180f, 900f, 480f, Paint().apply { color = Color.RED })
        try {
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val cropped = OcrImporter(context).transform(Uri.fromFile(source), area = ScanCrop(0.2f, 0.3f, 0.9f, 0.8f))
            try {
                val result = android.graphics.BitmapFactory.decodeFile(cropped.path)
                assertEquals(700, result.width)
                assertEquals(300, result.height)
                assertTrue(Color.red(result.getPixel(350, 150)) > 240)
                assertTrue(Color.green(result.getPixel(350, 150)) < 20)
                result.recycle()
            } finally { File(cropped.path!!).delete() }
        } finally { bitmap.recycle(); source.delete() }
    }

    @Test fun rotatesAndCropsBeforeRerunningRecognition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "ocr-transform-source.png")
        val bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val importer = OcrImporter(context)
        try {
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val transformed = importer.transform(Uri.fromFile(source), rotation = 90, crop = 0.1f)
            try {
                val result = importer.recognizePage(transformed, 0)
                assertEquals(480, result.width)
                assertEquals(800, result.height)
            } finally { transformed.path?.let { File(it).delete() } }
        } finally { bitmap.recycle(); source.delete() }
    }

    @Test fun recognizesPrintedFrenchPairsOnDevice() = runBlocking {
        recognizePrintedPairs(listOf("Français" to "Deutsch", "la maison" to "das Haus",
            "une école" to "eine Schule", "le jardin" to "der Garten"), "ocr-french-test.png")
    }

    @Test fun recognizesPrintedLatinPairsOnDevice() = runBlocking {
        recognizePrintedPairs(listOf("Latein" to "Deutsch", "aqua" to "Wasser",
            "puella" to "Mädchen", "amicus" to "Freund"), "ocr-latin-test.png")
    }

    private suspend fun recognizePrintedPairs(rows: List<Pair<String, String>>, fileName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1400, 650, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        rows.forEachIndexed { index, (term, translation) ->
            canvas.drawText(term, 80f, 100f + index * 130f, paint)
            canvas.drawText(translation, 780f, 100f + index * 130f, paint)
        }
        val file = File(context.cacheDir, fileName)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = OcrImporter(context).recognize(listOf(Uri.fromFile(file)))
            assertEquals(rows.size - 1, result.size)
            // OCR can vary letter case; answer checking already ignores it.
            assertEquals(rows.drop(1).map { it.first.lowercase(Locale.ROOT) }, result.map { it.term.lowercase(Locale.ROOT) })
            assertEquals(rows.drop(1).map { it.second }, result.map { it.translation })
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test fun recognizesCompactRuledTwoColumnPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1200, 2100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val rule = Paint().apply { color = Color.rgb(225, 225, 225); strokeWidth = 1f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 31f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val words = listOf(
            "le texte" to "der Text", "le roman" to "der Roman", "le chapitre" to "das Kapitel",
            "la pluie" to "der Regen", "ensuite" to "danach", "au début" to "am Anfang",
            "pendant" to "während", "soudain" to "plötzlich",
        )
        repeat(24) { index ->
            val y = 100f + index * 78f
            canvas.drawLine(30f, y + 9f, 1170f, y + 9f, rule)
            val (term, translation) = words[index % words.size]
            canvas.drawText(term, 55f, y, text)
            canvas.drawText(translation, 680f, y, text)
        }
        val file = File(context.cacheDir, "ocr-compact-ruled.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val page = OcrImporter(context).recognizePage(Uri.fromFile(file), 0)
            assertEquals(1200, page.width)
            assertTrue("Expected most printed rows, got ${page.pairs.size}", page.pairs.size >= 22)
            assertEquals("le texte", page.pairs.first().term)
            assertEquals("der Text", page.pairs.first().translation)
            assertEquals("plötzlich", page.pairs.last().translation)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
