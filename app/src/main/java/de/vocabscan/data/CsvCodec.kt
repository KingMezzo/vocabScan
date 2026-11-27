package de.vocabscan.data

data class CsvRow(
    val deckId: String,
    val deck: String,
    val language: String,
    val term: String,
    val translation: String,
    val note: String,
    val termAlternatives: String = "",
    val translationAlternatives: String = "",
    val chapter: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val scanGroupId: String = "",
    val folder: String = "",
    val isArchived: Boolean = false,
    val type: DeckType = DeckType.SPRACHE,
    val targetLanguage: String = if (type.isFlashcards) "" else "de",
)

/** UTF-8 CSV backup format. Quotes and embedded newlines are preserved. */
object CsvCodec {
    private val oldHeader = listOf("deckId", "deck", "language", "term", "translation", "note")
    private val previousHeader = oldHeader + listOf("termAlternatives", "translationAlternatives", "chapter", "tags")
    private val favoriteHeader = previousHeader + "isFavorite"
    private val scanHeader = favoriteHeader + "scanGroupId"
    private val folderHeader = scanHeader + listOf("folder", "isArchived")
    private val typeHeader = folderHeader + "type"
    private val header = typeHeader + "targetLanguage"

    fun encode(rows: List<CsvRow>): String = buildString {
        appendLine(header.joinToString(","))
        rows.forEach { row ->
            appendLine(listOf(row.deckId, row.deck, row.language, row.term, row.translation, row.note,
                row.termAlternatives, row.translationAlternatives, row.chapter, row.tags, row.isFavorite.toString(), row.scanGroupId,
                row.folder, row.isArchived.toString(), row.type.name, row.targetLanguage)
                .joinToString(",") { quote(it) })
        }
    }

    fun decode(csv: String): List<CsvRow> {
        val records = parse(csv.removePrefix("\uFEFF"))
        val columns = records.firstOrNull() ?: throw IllegalArgumentException("Leere CSV-Datei")
        require(columns == header || columns == typeHeader || columns == folderHeader || columns == scanHeader || columns == favoriteHeader || columns == previousHeader || columns == oldHeader) { "Unbekannte CSV-Spalten" }
        return records.drop(1).filterNot { it.size == 1 && it[0].isEmpty() }.map { fields ->
            require(fields.size == columns.size) { "Ungültige Spaltenzahl" }
            CsvRow(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                fields.getOrElse(6) { "" }, fields.getOrElse(7) { "" }, fields.getOrElse(8) { "" },
                fields.getOrElse(9) { "" }, fields.getOrElse(10) { "false" }.toBooleanStrict(),
                fields.getOrElse(11) { "" }, fields.getOrElse(12) { "" }, fields.getOrElse(13) { "false" }.toBooleanStrict(),
                DeckType.valueOf(fields.getOrElse(14) { "SPRACHE" }),
                fields.getOrElse(15) { if (fields.getOrNull(14) == "LERNKARTEN") "" else "de" }).also {
                require(it.deck.isNotBlank() && it.type.acceptsLanguages(it.language, it.targetLanguage)) { "Ungültige Liste" }
                require(it.term.isBlank() == it.translation.isBlank()) { "Unvollständige Karte" }
                require(it.folder.length <= 2048 && it.folder.split('/').all { part -> part.length <= 80 }) { "Ordnername ist zu lang" }
            }
        }
    }

    private fun quote(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
        "\"${value.replace("\"", "\"\"")}\"" else value

    /** Columns: front, back, optional note and tags. Supports quoted multiline CSV. */
    fun decodeCards(input: String, delimiter: Char): List<List<String>> {
        require(delimiter in listOf(';', ',', '\t', '|')) { "Ungültiges Trennzeichen" }
        val rows = parse(input.removePrefix("\uFEFF"), delimiter)
            .filterNot { it.size == 1 && it[0].isBlank() }
        require(rows.isNotEmpty()) { "Noch keine Karten. Füge Begriff und Erklärung ein." }
        return rows.mapIndexed { index, fields ->
            require(fields.size in 2..4 && fields[0].isNotBlank() && fields[1].isNotBlank()) {
                "Zeile ${index + 1}: Vorderseite und Rückseite mit dem gewählten Trennzeichen trennen."
            }
            fields.map { it.trim() }
        }
    }

    private fun parse(input: String, delimiter: Char = ','): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        while (i < input.length) {
            when (val ch = input[i]) {
                '"' -> {
                    if (quoted && i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"'); i++
                    } else if (quoted) { quoted = false; closed = true }
                    else if (field.isEmpty() && !closed) quoted = true
                    else throw IllegalArgumentException("Ungültiges Anführungszeichen")
                }
                delimiter -> if (quoted) field.append(ch) else {
                    fields.add(field.toString()); field.clear(); closed = false
                }
                '\n', '\r' -> if (quoted) field.append(ch) else {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    fields.add(field.toString()); field.clear(); closed = false
                    records.add(fields.toList()); fields.clear()
                }
                else -> {
                    require(!closed) { "Zeichen nach Anführungszeichen" }
                    field.append(ch)
                }
            }
            i++
        }
        require(!quoted) { "Nicht geschlossenes Anführungszeichen" }
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) {
            fields.add(field.toString()); records.add(fields.toList())
        }
        return records
    }
}
