package de.vocabscan.data

import android.content.Context
import android.net.Uri
import de.vocabscan.learn.ReviewReminder
import de.vocabscan.learn.LearningGoal
import de.vocabscan.ui.ThemeMode
import de.vocabscan.ui.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable, versioned ZIP backup. Import replaces the complete local collection. */
class FullBackup(private val context: Context, private val repository: VocabRepository) {
    private val scans get() = File(context.filesDir, "scans").apply { mkdirs() }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = repository.fullSnapshot()
        val imageInfo = snapshot.pages.associate { page ->
            val file = safeScanFile(page.fileName)
            require(file.isFile) { "Ein gespeichertes Scanbild fehlt: ${page.id}" }
            require(file.length() <= MAX_IMAGE) { "Ein Scanbild ist zu groß" }
            page.id to (file.length() to sha256(file.inputStream()))
        }
        require(imageInfo.values.sumOf { it.first } <= MAX_IMAGES_TOTAL) { "Scanbilder sind zusammen zu groß" }
        val manifest = encode(snapshot, imageInfo, ThemePreference.read(context), ReviewReminder.isEnabled(context), LearningGoal.read(context))
            .toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST) { "Zu viele Daten für dieses Sicherungsformat" }
        val stream = context.contentResolver.openOutputStream(uri) ?: error("Datei kann nicht geöffnet werden")
        ZipOutputStream(stream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            snapshot.pages.forEach { page ->
                zip.putNextEntry(ZipEntry("images/${page.id}"))
                safeScanFile(page.fileName).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val newFiles = mutableListOf<File>()
        var committed = false
        try {
            var manifest: ByteArray? = null
            val staged = mutableMapOf<String, File>()
            var totalBytes = 0L
            val input = context.contentResolver.openInputStream(uri) ?: error("Datei kann nicht geöffnet werden")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name == "manifest.json" || IMAGE_PATH.matches(name))) { "Unbekannter Archiveintrag" }
                    if (name == "manifest.json") {
                        require(manifest == null) { "Manifest mehrfach vorhanden" }
                        manifest = zip.readLimited(MAX_MANIFEST)
                    } else {
                        require(name !in staged) { "Bild mehrfach vorhanden" }
                        val file = File(stage, name.substringAfterLast('/'))
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var imageSize = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                imageSize += count
                                totalBytes += count
                                require(imageSize <= MAX_IMAGE && totalBytes <= MAX_IMAGES_TOTAL) { "Sicherung ist zu groß" }
                                output.write(buffer, 0, count)
                            }
                        }
                        staged[name] = file
                    }
                    zip.closeEntry()
                }
            }
            val root = JSONObject(String(manifest ?: error("Manifest fehlt"), Charsets.UTF_8))
            require(root.getInt("version") in 1..3) { "Diese Sicherungsversion wird nicht unterstützt" }
            val decoded = decode(root)
            val settings = root.getJSONObject("settings")
            val theme = ThemeMode.valueOf(settings.getString("theme"))
            val reminders = settings.getBoolean("reminders")
            val dailyGoal = settings.optInt("dailyGoal", 10).coerceIn(1, 100)
            require(staged.keys == decoded.pages.map { "images/${it.id}" }.toSet()) { "Bilddateien passen nicht zum Manifest" }
            val pageMetadata = root.getJSONArray("pages")
            val restoredPages = decoded.pages.mapIndexed { index, page ->
                val meta = pageMetadata.getJSONObject(index)
                val source = staged.getValue("images/${page.id}")
                require(source.length() == meta.getLong("size") && sha256(source.inputStream()) == meta.getString("sha256")) {
                    "Scanbild ist beschädigt"
                }
                val target = safeScanFile("${UUID.randomUUID()}.img")
                source.copyTo(target)
                newFiles += target
                page.copy(fileName = target.name)
            }
            val oldFiles = repository.fullSnapshot().pages.map { it.fileName }.toSet()
            repository.restoreFull(decoded.copy(pages = restoredPages))
            committed = true
            ThemePreference.write(context, theme)
            ReviewReminder.setEnabled(context, reminders)
            LearningGoal.write(context, dailyGoal)
            oldFiles.forEach { safeScanFile(it).delete() }
        } catch (error: Exception) {
            if (!committed) newFiles.forEach { it.delete() }
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun safeScanFile(name: String): File {
        require(FILE_NAME.matches(name)) { "Ungültiger Bilddateiname" }
        return File(scans, name)
    }

    companion object {
        private const val MAX_MANIFEST = 20 * 1024 * 1024
        private const val MAX_IMAGE = 50L * 1024 * 1024
        private const val MAX_IMAGES_TOTAL = 300L * 1024 * 1024
        private val FILE_NAME = Regex("[A-Za-z0-9_-]{1,100}\\.(?:img|jpg|jpeg|png)")
        private val IMAGE_PATH = Regex("images/[A-Za-z0-9_-]{1,100}")

        private fun sha256(input: InputStream): String = input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun ZipInputStream.readLimited(max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                require(out.size() + count <= max) { "Manifest ist zu groß" }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }

        private fun encode(data: FullSnapshot, images: Map<String, Pair<Long, String>>,
            theme: ThemeMode, reminders: Boolean, dailyGoal: Int) = JSONObject().apply {
            put("version", 3)
            put("settings", JSONObject().put("theme", theme.name).put("reminders", reminders)
                .put("dailyGoal", dailyGoal))
            put("folders", JSONArray().apply { data.folders.forEach { f -> put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("createdAt", f.createdAt)
                put("isArchived", f.isArchived); put("parentId", f.parentId)
            }) } })
            put("decks", JSONArray().apply { data.decks.forEach { d -> put(JSONObject().apply {
                put("id", d.id); put("name", d.name); put("language", d.language); put("type", d.type.name); put("createdAt", d.createdAt)
                put("targetLanguage", d.targetLanguage)
                put("folder", d.folder); put("isArchived", d.isArchived)
                put("folderId", d.folderId)
            }) } })
            put("words", JSONArray().apply { data.words.forEach { w -> put(JSONObject().apply {
                put("id", w.id); put("deckId", w.deckId); put("term", w.term); put("translation", w.translation)
                put("note", w.note); put("createdAt", w.createdAt); put("termAlternatives", w.termAlternatives)
                put("translationAlternatives", w.translationAlternatives); put("chapter", w.chapter); put("tags", w.tags)
                put("isFavorite", w.isFavorite); put("scanGroupId", w.scanGroupId); put("scanSource", w.scanSource)
            }) } })
            put("progress", JSONArray().apply { data.progress.forEach { p -> put(JSONObject().apply {
                put("wordId", p.wordId); put("direction", p.direction); put("stability", p.stability)
                put("difficulty", p.difficulty); put("lastReviewedAt", p.lastReviewedAt); put("dueAt", p.dueAt)
                put("reviews", p.reviews); put("lapses", p.lapses)
            }) } })
            put("logs", JSONArray().apply { data.logs.forEach { log -> put(JSONObject().apply {
                put("id", log.id); put("wordId", log.wordId); put("direction", log.direction)
                put("rating", log.rating); put("reviewedAt", log.reviewedAt)
            }) } })
            put("pages", JSONArray().apply { data.pages.forEach { page -> put(JSONObject().apply {
                put("id", page.id); put("deckId", page.deckId); put("createdAt", page.createdAt)
                put("scanGroupId", page.scanGroupId)
                put("size", images.getValue(page.id).first); put("sha256", images.getValue(page.id).second)
            }) } })
        }

        private fun decode(root: JSONObject): FullSnapshot {
            fun <T> array(key: String, read: (JSONObject) -> T): List<T> = root.getJSONArray(key).let { a ->
                (0 until a.length()).map { read(a.getJSONObject(it)) }
            }
            val folders = if (root.has("folders")) array("folders") {
                Folder(it.getString("id"), it.getString("name").also { name -> require(name.isNotBlank() && name.length <= 80) }, it.getLong("createdAt"),
                    it.optBoolean("isArchived", false),
                    it.optString("parentId").takeUnless { value -> value.isBlank() || value == "null" })
            } else emptyList()
            val decks = array("decks") { Deck(it.getString("id"), it.getString("name"), it.getString("language"), it.getLong("createdAt"),
                it.optString("folder", "").also { folder -> require(folder.length <= 2048) }, it.optBoolean("isArchived", false),
                it.optString("folderId").takeUnless { value -> value.isBlank() || value == "null" },
                DeckType.valueOf(it.optString("type", "SPRACHE")),
                it.optString("targetLanguage", if (it.optString("type") == "LERNKARTEN") "" else "de")) }
            val words = array("words") { Vocabulary(it.getString("id"), it.getString("deckId"), it.getString("term"),
                it.getString("translation"), it.getString("note"), it.getLong("createdAt"),
                it.getString("termAlternatives"), it.getString("translationAlternatives"), it.getString("chapter"), it.getString("tags"),
                it.optBoolean("isFavorite", false), it.optString("scanGroupId").takeUnless { value -> value.isBlank() || value == "null" },
                it.optString("scanSource").takeUnless { value -> value.isBlank() || value == "null" }) }
            val progress = array("progress") { StudyProgress(it.getString("wordId"), it.getInt("direction"),
                it.getDouble("stability"), it.getDouble("difficulty"), it.getLong("lastReviewedAt"), it.getLong("dueAt"),
                it.getInt("reviews"), it.getInt("lapses")) }
            val logs = array("logs") { ReviewLog(it.getLong("id"), it.getString("wordId"), it.getInt("direction"),
                it.getInt("rating"), it.getLong("reviewedAt")) }
            val pages = array("pages") { ScanPage(it.getString("id"), it.getString("deckId"), "", it.getLong("createdAt"),
                it.optString("scanGroupId").takeUnless { value -> value.isBlank() || value == "null" }) }
            require(decks.map { it.id }.toSet().size == decks.size && words.map { it.id }.toSet().size == words.size &&
                pages.map { it.id }.toSet().size == pages.size && logs.map { it.id }.toSet().size == logs.size) { "Doppelte IDs im Archiv" }
            val deckIds = decks.map { it.id }.toSet()
            val folderIds = folders.map { it.id }.toSet()
            require(folderIds.size == folders.size && folders.map { Triple(it.isArchived, it.parentId, it.name) }.distinct().size == folders.size &&
                folders.all { folder ->
                    (folder.parentId == null || folders.any { it.id == folder.parentId && it.isArchived == folder.isArchived }) &&
                        folderPath(folders, folder.id).firstOrNull()?.parentId == null
                } && decks.all { it.folderId == null || it.folderId in folderIds }) { "Ungültige Ordner im Archiv" }
            require(decks.all { it.name.isNotBlank() && it.type.acceptsLanguages(it.language, it.targetLanguage) }) { "Ungültiger Listentyp oder Sprache" }
            val wordIds = words.map { it.id }.toSet()
            require(words.all { it.deckId in deckIds && it.term.isNotBlank() && it.translation.isNotBlank() } &&
                pages.all { it.deckId in deckIds && IMAGE_PATH.matches("images/${it.id}") } &&
                progress.all { it.wordId in wordIds && it.direction in 0..1 } &&
                logs.all { it.wordId in wordIds && it.direction in 0..1 && it.rating in 1..4 } &&
                progress.map { it.wordId to it.direction }.toSet().size == progress.size) { "Ungültige Verweise im Archiv" }
            return FullSnapshot(decks, words, progress, logs, pages, folders)
        }
    }
}
