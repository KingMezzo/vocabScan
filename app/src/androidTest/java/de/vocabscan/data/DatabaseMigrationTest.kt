package de.vocabscan.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun versionEightFoldersPreserveArchivedListsAndAllLearningData() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v8-folders.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("de.vocabscan.data.VocabDatabase/8.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            schema.getJSONArray("setupQueries").let { queries ->
                for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            }
            old.execSQL("INSERT INTO decks VALUES ('a', 'Aktiv', 'fr', 1000, 'École', 0)")
            old.execSQL("INSERT INTO decks VALUES ('b', 'Archiv', 'fr', 2000, 'École', 1)")
            old.execSQL("INSERT INTO decks VALUES ('c', 'Lose', 'en', 3000, '', 0)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt, isFavorite) VALUES ('w', 'b', 'école', 'Schule', 'Notiz', 1000, 1)")
            old.execSQL("INSERT INTO study_progress VALUES ('w', 0, 8.0, 4.0, 1000, 2000, 7, 1)")
            old.execSQL("INSERT INTO review_log VALUES (1, 'w', 0, 3, 1000)")
            old.execSQL("INSERT INTO scan_pages VALUES ('p', 'b', 'one.img', 1000, 'group')")
            old.version = 8
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_8_9, VocabDatabase.MIGRATION_9_10, VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            val dao = migrated.vocabDao()
            val folder = dao.allFolders().single { !it.isArchived }
            val archiveFolder = dao.allFolders().single { it.isArchived }
            assertEquals(folder.name, archiveFolder.name)
            assertEquals("École", folder.name)
            assertEquals(listOf(folder.id, archiveFolder.id, null), dao.allDecks().map { it.folderId })
            assertEquals(true, dao.deckById("b")!!.isArchived)
            assertEquals(true, dao.wordById("w")!!.isFavorite)
            assertEquals("Notiz", dao.wordById("w")!!.note)
            assertEquals(7, dao.allProgress().single().reviews)
            assertEquals(3, dao.allLogs().single().rating)
            assertEquals("one.img", dao.allScanPages().single().fileName)
            VocabRepository(migrated).deleteFolder(folder.id)
            assertEquals(3, dao.allDecks().size)
            assertEquals(archiveFolder.id, dao.deckById("b")!!.folderId)
            assertEquals(1, dao.allLogs().size)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun versionNineDecksBecomeLanguageListsWithoutLosingAnyData() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v9-types.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("de.vocabscan.data.VocabDatabase/9.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            schema.getJSONArray("setupQueries").let { queries ->
                for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            }
            old.execSQL("INSERT INTO folders VALUES ('f', 'École', 1000)")
            old.execSQL("INSERT INTO decks VALUES ('a', 'Aktiv', 'fr', 1000, 'École', 0, 'f')")
            old.execSQL("INSERT INTO decks VALUES ('b', 'Archiv', 'fr', 2000, 'École', 1, 'f')")
            old.execSQL("INSERT INTO decks VALUES ('c', 'Lose', 'en', 3000, '', 0, NULL)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt, isFavorite) VALUES ('w', 'b', 'école', 'Schule', 'Notiz', 1000, 1)")
            old.execSQL("INSERT INTO study_progress VALUES ('w', 0, 8.0, 4.0, 1000, 2000, 7, 1)")
            old.execSQL("INSERT INTO review_log VALUES (1, 'w', 0, 3, 1000)")
            old.execSQL("INSERT INTO scan_pages VALUES ('p', 'b', 'one.img', 1000, 'group')")
            old.version = 9
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_8_9, VocabDatabase.MIGRATION_9_10, VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            val dao = migrated.vocabDao()
            val folder = dao.allFolders().single { !it.isArchived }
            val archiveFolder = dao.allFolders().single { it.isArchived }
            assertEquals(folder.name, archiveFolder.name)
            assertEquals("École", folder.name)
            assertTrue(dao.allDecks().all { it.type == DeckType.SPRACHE })
            val flashId = VocabRepository(migrated).createDeck("Recht", StudyLanguage.ENGLISH, type = DeckType.LERNKARTEN)
            assertEquals("", dao.deckById(flashId)!!.language)
            assertEquals(DeckType.LERNKARTEN, dao.deckById(flashId)!!.type)
            dao.deleteDeck(dao.deckById(flashId)!!)
            assertEquals(listOf(folder.id, archiveFolder.id, null), dao.allDecks().map { it.folderId })
            assertEquals(true, dao.deckById("b")!!.isArchived)
            assertEquals(true, dao.wordById("w")!!.isFavorite)
            assertEquals("Notiz", dao.wordById("w")!!.note)
            assertEquals(7, dao.allProgress().single().reviews)
            assertEquals(3, dao.allLogs().single().rating)
            assertEquals("one.img", dao.allScanPages().single().fileName)
            VocabRepository(migrated).deleteFolder(folder.id)
            assertEquals(3, dao.allDecks().size)
            assertEquals(archiveFolder.id, dao.deckById("b")!!.folderId)
            assertEquals(1, dao.allLogs().size)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun oldScanWordsAreGroupedWithoutTouchingManualWords() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v5-scans-test.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("de.vocabscan.data.VocabDatabase/5.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            schema.getJSONArray("setupQueries").let { queries ->
                for (index in 0 until queries.length()) old.execSQL(queries.getString(index))
            }
            old.execSQL("INSERT INTO decks (id, name, language, createdAt) VALUES ('d', 'Unit 1', 'en', 1000)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt) VALUES ('manual', 'd', 'manual', 'manuell', '', 5000)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt) VALUES ('scan1', 'd', 'apple', 'Apfel', '', 10000)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt) VALUES ('scan2', 'd', 'house', 'Haus', '', 10001)")
            old.execSQL("INSERT INTO scan_pages (id, deckId, fileName, createdAt) VALUES ('page1', 'd', 'one.img', 10005)")
            old.execSQL("INSERT INTO scan_pages (id, deckId, fileName, createdAt) VALUES ('page2', 'd', 'two.img', 10006)")
            old.version = 5
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_5_6, VocabDatabase.MIGRATION_6_7, VocabDatabase.MIGRATION_7_8, VocabDatabase.MIGRATION_8_9, VocabDatabase.MIGRATION_9_10, VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            val words = migrated.vocabDao().wordsForDeck("d").associateBy { it.id }
            assertEquals(null, words.getValue("manual").scanGroupId)
            assertEquals(null, words.getValue("scan1").scanSource)
            assertEquals("page1", words.getValue("scan1").scanGroupId)
            assertEquals("page1", words.getValue("scan2").scanGroupId)
            assertEquals(setOf("page1"), migrated.vocabDao().allScanPages().map { it.scanGroupId }.toSet())
            assertEquals("", migrated.vocabDao().allDecks().single().folder)
            assertEquals(false, migrated.vocabDao().allDecks().single().isArchived)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun versionTwoProgressAndReviewsSurviveMetadataMigration() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("de.vocabscan.data.VocabDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val queries = schema.getJSONArray("setupQueries")
            for (index in 0 until queries.length()) old.execSQL(queries.getString(index))
            old.execSQL("INSERT INTO decks VALUES ('d', 'Unit 1', 'en', 1000)")
            old.execSQL("INSERT INTO vocabulary VALUES ('w', 'd', 'house', 'Haus', '', 1000)")
            old.execSQL("INSERT INTO study_progress VALUES ('w', 0, 8.0, 4.0, 1000, 2000, 7, 1)")
            old.execSQL("INSERT INTO review_log VALUES (1, 'w', 0, 3, 1000)")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_1_2, VocabDatabase.MIGRATION_2_3, VocabDatabase.MIGRATION_3_4,
                VocabDatabase.MIGRATION_4_5, VocabDatabase.MIGRATION_5_6, VocabDatabase.MIGRATION_6_7, VocabDatabase.MIGRATION_7_8, VocabDatabase.MIGRATION_8_9, VocabDatabase.MIGRATION_9_10, VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            assertEquals(7, migrated.vocabDao().allProgress().single().reviews)
            assertEquals(0, migrated.vocabDao().allScanPages().size)
            assertEquals(3, migrated.vocabDao().observeReviewLogs().first().single().rating)
            val word = migrated.vocabDao().wordsForDeck("d").single()
            assertEquals("", word.tags)
            assertEquals(false, word.isFavorite)
            VocabRepository(migrated).saveWord("d", word, word.term, word.translation, word.note,
                translationAlternatives = "Gebäude", chapter = "Unit 1", tags = "Wohnen")
            assertEquals("Gebäude", migrated.vocabDao().wordsForDeck("d").single().translationAlternatives)
            assertEquals(7, migrated.vocabDao().allProgress().single().reviews)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun oldDeckAndWordSurviveMigration() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE decks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, language TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE vocabulary (id TEXT NOT NULL PRIMARY KEY, deckId TEXT NOT NULL, term TEXT NOT NULL, translation TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(deckId) REFERENCES decks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_vocabulary_deckId ON vocabulary(deckId)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'd7b7f2a86c24748b6c79a7a763453317')")
            old.execSQL("INSERT INTO decks (id, name, language, createdAt) VALUES ('d1', 'Unit 1', 'en', 1000)")
            old.execSQL("INSERT INTO vocabulary (id, deckId, term, translation, note, createdAt) VALUES ('w1', 'd1', 'apple', 'Apfel', 'fruit', 1000)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name)
            .addMigrations(VocabDatabase.MIGRATION_1_2, VocabDatabase.MIGRATION_2_3, VocabDatabase.MIGRATION_3_4,
                VocabDatabase.MIGRATION_4_5, VocabDatabase.MIGRATION_5_6, VocabDatabase.MIGRATION_6_7, VocabDatabase.MIGRATION_7_8, VocabDatabase.MIGRATION_8_9, VocabDatabase.MIGRATION_9_10, VocabDatabase.MIGRATION_10_11, VocabDatabase.MIGRATION_11_12).build()
        try {
            val deck = migrated.vocabDao().allDecks().single()
            val word = migrated.vocabDao().wordsForDeck(deck.id).single()
            assertEquals("Unit 1", deck.name)
            assertEquals("apple", word.term)
            assertEquals("Apfel", word.translation)
            assertEquals("fruit", word.note)
            assertEquals("", word.chapter)
            assertEquals("", word.tags)
            assertEquals("", word.translationAlternatives)
            assertEquals(false, word.isFavorite)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
