package de.vocabscan.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.vocabscan.learn.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LanguagePairRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: VocabDatabase
    private lateinit var repository: VocabRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).build()
        repository = VocabRepository(database)
    }
    @After fun close() = database.close()

    @Test fun allLanguagePairsSurviveCsvQrAndFullBackupWithBothReviewDirections() = runTest {
        for (source in StudyLanguage.entries) for (target in StudyLanguage.entries) {
            val id = repository.createDeck("${source.label} → ${target.label}", source, targetLanguage = target)
            val word = repository.saveWord(id, null, "term-${source.code}", "answer-${target.code}", "Notiz")
            StudyDirection.entries.forEach { repository.recordReview(word.id, it, ReviewRating.GOOD, 1000) }
            val deck = database.vocabDao().allDecksWithWords().first { it.deck.id == id }
            val collector = DeckQrCodec.Collector()
            val frames = DeckQrCodec.encode(deck)
            assertTrue(frames.all { it.startsWith("VS2:") })
            frames.reversed().forEach { collector.accept(it) }
            val shared = collector.completed!!
            assertEquals(source.code, shared.language)
            assertEquals(target.code, shared.targetLanguage)
            val copy = repository.importSharedDeck(shared.name, shared.language, shared.words, type = shared.type,
                targetLanguage = shared.targetLanguage)
            assertTrue(deck.deck.compatibleWith(database.vocabDao().deckById(copy)!!))
            repository.deleteDeck(database.vocabDao().deckById(copy)!!)
        }
        val original = repository.fullSnapshot()
        val csv = repository.exportCsv()
        val archive = File(context.cacheDir, "language-pairs.zip")
        try {
            FullBackup(context, repository).export(Uri.fromFile(archive))
            database.vocabDao().deleteAllDecks()
            val pairCount = StudyLanguage.entries.size * StudyLanguage.entries.size
            assertEquals(pairCount to pairCount, repository.importCsv(csv))
            assertEquals(original.decks.map { it.id to (it.language to it.targetLanguage) }.toSet(),
                repository.fullSnapshot().decks.map { it.id to (it.language to it.targetLanguage) }.toSet())
            FullBackup(context, repository).restore(Uri.fromFile(archive))
            val restored = repository.fullSnapshot()
            assertEquals(original.decks.toSet(), restored.decks.toSet())
            assertEquals(original.words.toSet(), restored.words.toSet())
            assertEquals(original.progress.toSet(), restored.progress.toSet())
            assertEquals(original.logs, restored.logs)
        } finally { archive.delete() }
    }

    @Test fun mergeTransferAndCsvRejectDifferentTargetLanguagesWithoutChangingData() = runTest {
        val en = repository.createDeck("Definitions", StudyLanguage.ENGLISH, targetLanguage = StudyLanguage.ENGLISH)
        val de = repository.createDeck("Translations", StudyLanguage.ENGLISH)
        val word = repository.saveWord(en, null, "enormous", "very large", "")
        val original = repository.fullSnapshot()
        suspend fun rejects(action: suspend () -> Unit) {
            try { action(); fail("Conflicting language pair accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(original, repository.fullSnapshot())
        }
        rejects { repository.mergeDecks(en, de) }
        rejects { repository.transferWords(en, de, setOf(word.id), true) }
        rejects { repository.importCsv(CsvCodec.encode(listOf(CsvRow(en, "Conflict", "en", "new", "neu", "")))) }
        val compatible = repository.createDeck("More definitions", StudyLanguage.ENGLISH, targetLanguage = StudyLanguage.ENGLISH)
        repository.recordReview(word.id, StudyDirection.FOREIGN_TO_GERMAN, ReviewRating.GOOD, 1000)
        assertEquals(1, repository.mergeDecks(en, compatible))
        assertEquals(1, repository.fullSnapshot().progress.single().reviews)
    }

    @Test fun versionTenMigrationPreservesAllDataAndKeepsFlashcardsLanguageFree() = runTest {
        val name = "migration-v10-language-pairs.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("de.vocabscan.data.VocabDatabase/10.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            schema.getJSONArray("setupQueries").let { queries -> for (i in 0 until queries.length()) old.execSQL(queries.getString(i)) }
            old.execSQL("INSERT INTO decks (id,name,language,createdAt,type) VALUES ('en','English','en',1000,'SPRACHE'),('fr','French','fr',1000,'SPRACHE'),('cards','Cards','',1000,'LERNKARTEN')")
            old.execSQL("INSERT INTO vocabulary (id,deckId,term,translation,note,createdAt) VALUES ('w','en','house','Haus','note',1000)")
            old.execSQL("INSERT INTO study_progress VALUES ('w',0,8.0,4.0,1000,2000,7,1),('w',1,9.0,3.0,1000,3000,5,0)")
            old.execSQL("INSERT INTO review_log VALUES (1,'w',0,3,1000),(2,'w',1,4,1000)")
            old.execSQL("INSERT INTO scan_pages VALUES ('p','en','page.img',1000,'group')")
            old.version = 10
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            val snapshot = VocabRepository(migrated).fullSnapshot()
            assertEquals("de", snapshot.decks.first { it.id == "en" }.targetLanguage)
            assertEquals("de", snapshot.decks.first { it.id == "fr" }.targetLanguage)
            assertEquals("", snapshot.decks.first { it.id == "cards" }.targetLanguage)
            assertEquals(setOf(7, 5), snapshot.progress.map { it.reviews }.toSet())
            assertEquals(setOf(0, 1), snapshot.logs.map { it.direction }.toSet())
            assertEquals("note", snapshot.words.single().note)
            assertEquals("page.img", snapshot.pages.single().fileName)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun readsLegacyQrAndVersionOneBackupWithGermanAsTarget() = runTest {
        val json = """{"name":"Legacy","language":"fr","words":[{"term":"maison","translation":"Haus"}]}"""
        val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        val hash = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        assertEquals("de", DeckQrCodec.Collector().accept("VS1:$hash:1:1:$encoded")!!.targetLanguage)
        repository.createDeck("Legacy", StudyLanguage.FRENCH)
        val original = repository.fullSnapshot()
        val modern = File(context.cacheDir, "modern-language-pairs.zip")
        val legacy = File(context.cacheDir, "legacy-language-pairs.zip")
        try {
            FullBackup(context, repository).export(Uri.fromFile(modern))
            ZipInputStream(modern.inputStream()).use { zip ->
                assertEquals("manifest.json", zip.nextEntry.name)
                val root = JSONObject(zip.readBytes().toString(Charsets.UTF_8)).put("version", 1)
                val decks = root.getJSONArray("decks")
                for (i in 0 until decks.length()) decks.getJSONObject(i).remove("targetLanguage")
                ZipOutputStream(legacy.outputStream()).use { out ->
                    out.putNextEntry(ZipEntry("manifest.json")); out.write(root.toString().toByteArray()); out.closeEntry()
                }
            }
            FullBackup(context, repository).restore(Uri.fromFile(legacy))
            assertEquals(original, repository.fullSnapshot())
        } finally { modern.delete(); legacy.delete() }
    }
}
