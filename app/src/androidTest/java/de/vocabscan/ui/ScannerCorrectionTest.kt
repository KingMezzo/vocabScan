package de.vocabscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vocabscan.data.StudyLanguage
import de.vocabscan.data.VocabDatabase
import de.vocabscan.data.VocabRepository
import de.vocabscan.scan.OcrImporter
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScannerCorrectionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: VocabDatabase
    private lateinit var model: VocabViewModel
    private lateinit var source: File
    private val store = ViewModelStore()

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).build()
        val repository = VocabRepository(database)
        val id = runBlocking { repository.createDeck("Scanner-Test", StudyLanguage.ENGLISH) }
        source = File(context.cacheDir, "scanner-correction-fixture.png")
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        listOf("apple" to "Apfel", "house" to "Haus", "garden" to "Garten").forEachIndexed { index, pair ->
            canvas.drawText(pair.first, 100f, 150f + index * 140f, paint)
            canvas.drawText(pair.second, 700f, 150f + index * 140f, paint)
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.runOnUiThread {
            model = VocabViewModel(repository, OcrImporter(context), SavedStateHandle(), context)
            store.put("scanner", model)
            model.startImport(id, listOf(Uri.fromFile(source)))
        }
        compose.waitUntil(20_000) { model.state.value.draft != null }
        assertEquals(3, model.state.value.draft!!.pairs.size)
        compose.setContent {
            val state by model.state.collectAsState()
            VocabTheme(ThemeMode.LIGHT) { Surface {
                state.draft?.let { draft -> ImportScreen(draft, emptyList(), "en", state.busy,
                    model::updatePair, model::removePair, model::addPair, model::swapColumns,
                    model::setColumnSplit, { model.removeDuplicates(emptyList()) }, model::editScanPage,
                    {}, state.canUndoImport, model::undoImport, model::cropScanPage,
                    model::mergePairs, model::splitPair,
                    onRemoveMany = model::removePairs, onReviewMany = model::reviewPairs)
                }
            } }
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { model.discardImport(); store.clear() }
        database.close()
        source.delete()
    }

    @Test fun dragMergesRowsAndUndoRestoresOriginalPairs() {
        val handle = compose.onNodeWithContentDescription("Zeile 1 zum Verbinden ziehen", useUnmergedTree = true)
        val sourceCenter = handle.fetchSemanticsNode().boundsInRoot.center
        val targetCenter = compose.onNodeWithText("house").fetchSemanticsNode().boundsInRoot.center
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(center + Offset(0f, targetCenter.y - sourceCenter.y), delayMillis = 400)
            up()
        }
        compose.onNodeWithText("Zeilen verbinden?").assertIsDisplayed()
        compose.onNodeWithText("Verbinden", substring = false).performClick()
        compose.runOnIdle { assertEquals("apple house", model.state.value.draft!!.pairs.first().term) }
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.runOnIdle { assertEquals(listOf("apple", "house", "garden"), model.state.value.draft!!.pairs.map { it.term }) }
    }

    @Test fun movesAWordBetweenColumnsAndSplitsAtSelectedBoundary() {
        compose.onNodeWithContentDescription("Zeile 1 bearbeiten").performClick()
        compose.onNodeWithText("Verschieben", substring = false).performClick()
        compose.onNodeWithContentDescription("apple in die andere Spalte verschieben").performClick()
        compose.onNodeWithText("Änderungen übernehmen").performClick()
        compose.runOnIdle {
            assertEquals("", model.state.value.draft!!.pairs.first().term)
            assertEquals("Apfel apple", model.state.value.draft!!.pairs.first().translation)
        }
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.onNodeWithContentDescription("Zeile 1 bearbeiten").performClick()
        compose.onNodeWithText("Trennen", substring = false).performClick()
        compose.onNodeWithContentDescription("Trennstelle Begriff · Englisch").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("In zwei Zeilen trennen").performClick()
        compose.runOnIdle {
            val pairs = model.state.value.draft!!.pairs
            assertEquals(4, pairs.size)
            assertEquals("Apfel", pairs[0].translation)
            assertEquals("apple", pairs[1].term)
        }
    }

    @Test fun cropRerunsRecognitionAndUndoRestoresTheImageAndPairs() {
        val before = model.state.value.draft!!
        compose.onNodeWithText("1 · Bild & Ausschnitt").performClick()
        compose.onNodeWithText("Tabelle auswählen").performScrollTo().performClick()
        compose.onNodeWithText("Zuschneiden").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.draft!!.pages != before.pages }
        assertTrue(model.state.value.draft!!.dimensions.first().first < before.dimensions.first().first)
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.runOnIdle { assertEquals(before, model.state.value.draft) }
    }

    @Test fun inlineEditsAndBatchDeleteCanEachBeUndoneAsOneAction() {
        val before = model.state.value.draft!!
        compose.onNodeWithContentDescription("Begriff · Englisch · Zeile 1").performTextReplacement("p")
        compose.onNodeWithContentDescription("Begriff · Englisch · Zeile 1").performTextReplacement("pear")
        compose.runOnIdle { assertEquals("pear", model.state.value.draft!!.pairs.first().term) }
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.runOnIdle { assertEquals(before, model.state.value.draft) }
        compose.onNodeWithText("Auswählen").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Zeile 1 auswählen"))
        compose.onNodeWithContentDescription("Zeile 1 auswählen").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Zeile 2 auswählen"))
        compose.onNodeWithContentDescription("Zeile 2 auswählen").performClick()
        compose.onNodeWithText("Auswahl löschen").performClick()
        compose.onNodeWithText("Löschen", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("garden"), model.state.value.draft!!.pairs.map { it.term }) }
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.runOnIdle { assertEquals(before, model.state.value.draft) }
    }

    @Test fun draggingColumnHeaderSwapsTextAndOriginalBounds() {
        val before = model.state.value.draft!!
        val left = compose.onNodeWithContentDescription("Begriff · Englisch-Spalte zum Tauschen ziehen")
        val start = left.fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithContentDescription("Übersetzung · Deutsch-Spalte zum Tauschen ziehen").fetchSemanticsNode().boundsInRoot.center
        left.performTouchInput {
            down(center); advanceEventTime(700)
            moveTo(center + end - start, delayMillis = 400); up()
        }
        compose.runOnIdle {
            val swapped = model.state.value.draft!!.pairs.first()
            assertEquals("Apfel", swapped.term)
            assertEquals("apple", swapped.translation)
            assertEquals(before.pairs.first().termBounds, swapped.translationBounds)
            assertEquals(before.pairs.first().translationBounds, swapped.termBounds)
        }
        compose.onNodeWithContentDescription("Korrektur rückgängig").performClick()
        compose.runOnIdle { assertEquals(before, model.state.value.draft) }
    }
}
