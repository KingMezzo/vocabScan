package de.vocabscan.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SharedDeck(val name: String, val language: String, val words: List<Vocabulary>, val type: DeckType = DeckType.SPRACHE,
    val targetLanguage: String = if (type.isFlashcards) "" else "de")

/** QR frames carry only the list and its words. Learning history and scan images stay private. */
object DeckQrCodec {
    private const val CHUNK = 800
    private const val MAX_FRAMES = 80
    private const val MAX_JSON = 1_000_000

    fun encode(deck: DeckWithVocabulary): List<String> {
        val root = JSONObject().put("name", deck.deck.name).put("language", deck.deck.language).put("type", deck.deck.type.name)
            .put("targetLanguage", deck.deck.targetLanguage)
            .put("words", JSONArray().apply {
                deck.words.forEach { word -> put(JSONObject()
                    .put("term", word.term).put("translation", word.translation).put("note", word.note)
                    .put("termAlternatives", word.termAlternatives)
                    .put("translationAlternatives", word.translationAlternatives)
                    .put("chapter", word.chapter).put("tags", word.tags)
                    .put("isFavorite", word.isFavorite)) }
            })
        val json = root.toString().toByteArray(Charsets.UTF_8)
        require(json.size <= MAX_JSON) { "Diese Liste ist für QR-Codes zu groß. Nutze den CSV-Export." }
        val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json) } }.toByteArray()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        val id = sha256(encoded).take(16)
        val chunks = encoded.chunked(CHUNK)
        require(chunks.size <= MAX_FRAMES) { "Diese Liste braucht zu viele QR-Codes. Nutze den CSV-Export." }
        return chunks.mapIndexed { index, chunk -> "VS2:$id:${index + 1}:${chunks.size}:$chunk" }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }

    class Collector {
        private var transferId: String? = null
        private var protocol: String? = null
        private var total = 0
        private val parts = mutableMapOf<Int, String>()
        var completed: SharedDeck? = null
            private set
        val received get() = parts.size
        val expected get() = total
        val receivedIndices: Set<Int> get() = parts.keys.toSet()
        val missingIndices: List<Int> get() = (1..total).filterNot { it in parts }
        val savedFrames: ArrayList<String> get() = ArrayList(parts.toSortedMap().map { (index, chunk) ->
            "$protocol:$transferId:$index:$total:$chunk"
        })

        fun accept(frame: String): SharedDeck? {
            val values = frame.split(':', limit = 5)
            require(values.size == 5 && values[0] in listOf("VS1", "VS2") && values[1].matches(Regex("[a-f0-9]{16}"))) {
                "Das ist kein VocabScan-QR-Code."
            }
            val index = values[2].toIntOrNull() ?: error("Ungültige QR-Nummer")
            val count = values[3].toIntOrNull() ?: error("Ungültige QR-Anzahl")
            require(count in 1..MAX_FRAMES && index in 1..count && values[4].length <= CHUNK) { "Ungültiger QR-Code" }
            if (transferId == null) { transferId = values[1]; protocol = values[0]; total = count }
            require(transferId == values[1] && protocol == values[0] && total == count) { "Dieser Code gehört zu einer anderen Liste." }
            require(parts[index] == null || parts[index] == values[4]) { "Widersprüchlicher QR-Code" }
            parts[index] = values[4]
            if (parts.size != total) return null
            val encoded = (1..total).joinToString("") { parts.getValue(it) }
            if (sha256(encoded).take(16) != transferId) {
                transferId = null; total = 0; parts.clear()
                error("Die QR-Codes sind unvollständig oder beschädigt. Bitte erneut beginnen.")
            }
            val packed = Base64.getUrlDecoder().decode(encoded)
            val json = GZIPInputStream(ByteArrayInputStream(packed)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= MAX_JSON) { "QR-Liste ist zu groß" }
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
            val root = JSONObject(json)
            val name = root.getString("name").trim()
            val language = root.getString("language")
            val type = DeckType.valueOf(root.optString("type", "SPRACHE"))
            val targetLanguage = if (protocol == "VS2") root.getString("targetLanguage")
                else root.optString("targetLanguage", if (type.isFlashcards) "" else "de")
            require(name.isNotEmpty() && type.acceptsLanguages(language, targetLanguage)) { "Ungültige Liste" }
            val array = root.getJSONArray("words")
            require(array.length() in 1..5000) { "Die Liste enthält keine oder zu viele Karten." }
            val words = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val term = obj.getString("term").trim()
                val translation = obj.getString("translation").trim()
                require(term.isNotEmpty() && translation.isNotEmpty()) {
                    "Unvollständige Karte"
                }
                Vocabulary(UUID.randomUUID().toString(), "", term, translation,
                    note = obj.optString("note"),
                    termAlternatives = obj.optString("termAlternatives"),
                    translationAlternatives = obj.optString("translationAlternatives"),
                    chapter = obj.optString("chapter"), tags = obj.optString("tags"),
                    isFavorite = obj.optBoolean("isFavorite", false))
            }
            return SharedDeck(name, language, words, type, targetLanguage).also { completed = it }
        }
    }
}
