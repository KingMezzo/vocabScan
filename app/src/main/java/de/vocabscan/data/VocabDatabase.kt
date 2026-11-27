package de.vocabscan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE, id")
    fun observeFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE, id")
    suspend fun allFolders(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun folderById(id: String): Folder?

    @Insert
    suspend fun insertFolder(folder: Folder)

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()

    @Transaction
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun observeDecks(): Flow<List<DeckWithVocabulary>>

    @Transaction
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    suspend fun allDecksWithWords(): List<DeckWithVocabulary>

    @Query("SELECT * FROM study_progress")
    suspend fun allProgress(): List<StudyProgress>

    @Query("SELECT * FROM review_log ORDER BY id")
    suspend fun allLogs(): List<ReviewLog>

    @Query("SELECT * FROM scan_pages ORDER BY createdAt, id")
    suspend fun allScanPages(): List<ScanPage>

    @Query("SELECT * FROM scan_pages ORDER BY createdAt, id")
    fun observeScanPages(): Flow<List<ScanPage>>

    @Insert
    suspend fun insertScanPages(pages: List<ScanPage>)

    @Query("DELETE FROM decks")
    suspend fun deleteAllDecks()

    @Insert
    suspend fun insertProgress(progress: List<StudyProgress>)

    @Insert
    suspend fun insertLogs(logs: List<ReviewLog>)

    @Query("SELECT * FROM vocabulary WHERE deckId = :deckId ORDER BY createdAt, id")
    suspend fun wordsForDeck(deckId: String): List<Vocabulary>

    @Query("SELECT * FROM vocabulary WHERE id = :wordId")
    suspend fun wordById(wordId: String): Vocabulary?

    @Query("SELECT * FROM study_progress WHERE wordId = :wordId")
    suspend fun progressForWord(wordId: String): List<StudyProgress>

    @Query("SELECT * FROM review_log WHERE wordId = :wordId")
    suspend fun logsForWord(wordId: String): List<ReviewLog>

    @Query("SELECT * FROM scan_pages WHERE deckId = :deckId")
    suspend fun pagesForDeck(deckId: String): List<ScanPage>

    @Query("SELECT * FROM decks ORDER BY createdAt, id")
    suspend fun allDecks(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun deckById(deckId: String): Deck?

    @Query("UPDATE scan_pages SET deckId = :targetDeckId WHERE deckId = :sourceDeckId")
    suspend fun moveScanPages(sourceDeckId: String, targetDeckId: String)

    @Query("SELECT * FROM study_progress")
    fun observeProgress(): Flow<List<StudyProgress>>

    @Query("SELECT * FROM study_progress WHERE wordId = :wordId AND direction = :direction")
    suspend fun progressFor(wordId: String, direction: Int): StudyProgress?

    @Query("DELETE FROM study_progress WHERE wordId = :wordId")
    suspend fun deleteProgressForWord(wordId: String)

    @Query("DELETE FROM review_log WHERE wordId = :wordId")
    suspend fun deleteLogsForWord(wordId: String)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: StudyProgress)

    @Insert
    suspend fun insertLog(log: ReviewLog)

    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :since")
    fun observeReviewsSince(since: Long): Flow<Int>

    @Query("SELECT * FROM review_log ORDER BY reviewedAt DESC")
    fun observeReviewLogs(): Flow<List<ReviewLog>>

    @Query("SELECT COUNT(*) FROM vocabulary v INNER JOIN decks d ON d.id = v.deckId LEFT JOIN study_progress p ON p.wordId = v.id AND p.direction = :direction WHERE d.isArchived = 0 AND (p.dueAt IS NULL OR p.dueAt <= :now)")
    suspend fun countDue(now: Long, direction: Int): Int

    @Insert
    suspend fun insertDeck(deck: Deck)

    @Update
    suspend fun updateDeck(deck: Deck)

    @Delete
    suspend fun deleteDeck(deck: Deck)

    @Insert
    suspend fun insertWords(words: List<Vocabulary>)

    @Update
    suspend fun updateWord(word: Vocabulary)

    @Delete
    suspend fun deleteWord(word: Vocabulary)
}

@Database(entities = [Folder::class, Deck::class, Vocabulary::class, StudyProgress::class, ReviewLog::class, ScanPage::class], version = 12, exportSchema = true)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    companion object {
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId TEXT REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("DROP INDEX index_folders_name")
                db.execSQL("CREATE INDEX index_folders_parentId ON folders (parentId)")
                db.execSQL("CREATE UNIQUE INDEX index_folders_isArchived_parentId_name ON folders (isArchived, parentId, name)")
                // Clone shared legacy folders before redirecting archived lists. Learning data is untouched.
                db.execSQL("""INSERT INTO folders (id, name, createdAt, isArchived, parentId)
                    SELECT 'archive:' || id, name, createdAt, 1, NULL FROM folders
                    WHERE id IN (SELECT folderId FROM decks WHERE isArchived = 1)""")
                db.execSQL("UPDATE decks SET folderId = 'archive:' || folderId WHERE isArchived = 1 AND folderId IS NOT NULL")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decks ADD COLUMN targetLanguage TEXT NOT NULL DEFAULT 'de'")
                db.execSQL("UPDATE decks SET targetLanguage = '' WHERE type = 'LERNKARTEN'")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decks ADD COLUMN type TEXT NOT NULL DEFAULT 'SPRACHE'")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS folders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_folders_name ON folders (name)")
                // Stable IDs preserve exact legacy names, including case and non-ASCII text.
                db.execSQL("""INSERT INTO folders (id, name, createdAt)
                    SELECT 'legacy:' || hex(folder), folder, MIN(createdAt)
                    FROM decks WHERE folder != '' GROUP BY folder""")
                db.execSQL("ALTER TABLE decks ADD COLUMN folderId TEXT REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decks_folderId ON decks (folderId)")
                db.execSQL("UPDATE decks SET folderId = 'legacy:' || hex(folder) WHERE folder != ''")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decks ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE decks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vocabulary ADD COLUMN scanSource TEXT")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vocabulary ADD COLUMN scanGroupId TEXT")
                db.execSQL("ALTER TABLE scan_pages ADD COLUMN scanGroupId TEXT")
                // Older imports created their words just before saving the page rows.
                // Link only close timestamps, so unrelated manually entered words stay ungrouped.
                db.execSQL("""UPDATE scan_pages SET scanGroupId = (
                    SELECT firstPage.id FROM scan_pages AS firstPage
                    WHERE firstPage.deckId = scan_pages.deckId
                    AND firstPage.createdAt BETWEEN scan_pages.createdAt - 2000 AND scan_pages.createdAt
                    ORDER BY firstPage.createdAt, firstPage.id LIMIT 1
                )""")
                db.execSQL("""UPDATE vocabulary SET scanGroupId = (
                    SELECT page.scanGroupId FROM scan_pages AS page
                    WHERE page.deckId = vocabulary.deckId
                    AND page.createdAt BETWEEN vocabulary.createdAt AND vocabulary.createdAt + 2000
                    ORDER BY page.createdAt, page.id LIMIT 1
                ) WHERE EXISTS (
                    SELECT 1 FROM scan_pages AS page WHERE page.deckId = vocabulary.deckId
                    AND page.createdAt BETWEEN vocabulary.createdAt AND vocabulary.createdAt + 2000
                )""")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vocabulary ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `scan_pages` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_pages_deckId` ON `scan_pages` (`deckId`)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("termAlternatives", "translationAlternatives", "chapter", "tags").forEach { column ->
                    db.execSQL("ALTER TABLE vocabulary ADD COLUMN `$column` TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `study_progress` (`wordId` TEXT NOT NULL, `direction` INTEGER NOT NULL, `stability` REAL NOT NULL, `difficulty` REAL NOT NULL, `lastReviewedAt` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `reviews` INTEGER NOT NULL, `lapses` INTEGER NOT NULL, PRIMARY KEY(`wordId`, `direction`), FOREIGN KEY(`wordId`) REFERENCES `vocabulary`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_progress_wordId` ON `study_progress` (`wordId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `review_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wordId` TEXT NOT NULL, `direction` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL, FOREIGN KEY(`wordId`) REFERENCES `vocabulary`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_log_wordId` ON `review_log` (`wordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_log_reviewedAt` ON `review_log` (`reviewedAt`)")
            }
        }

        fun open(context: Context): VocabDatabase = Room.databaseBuilder(
            context.applicationContext, VocabDatabase::class.java, "vocabscan.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build()
    }
}
