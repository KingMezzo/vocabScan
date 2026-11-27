package de.vocabscan.scan

/** Normalized coordinates in the upright image shown to the user. */
data class ScanCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(right > left && bottom > top)
    }
}

object ScanEdits {
    fun words(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    fun merge(pairs: List<ImportPair>, firstId: String, secondId: String): List<ImportPair> {
        if (firstId == secondId) return pairs
        val selected = pairs.filter { it.id == firstId || it.id == secondId }
        if (selected.size != 2 || selected[0].page != selected[1].page) return pairs
        val first = selected[0]
        val second = selected[1]
        return pairs.mapNotNull {
            when (it.id) {
                first.id -> first.copy(term = listOf(first.term, second.term).filter(String::isNotBlank).joinToString(" "),
                    translation = listOf(first.translation, second.translation).filter(String::isNotBlank).joinToString(" "),
                    warning = null, confidence = null, reviewed = true,
                    termBounds = combine(first.termBounds, second.termBounds),
                    translationBounds = combine(first.translationBounds, second.translationBounds))
                second.id -> null
                else -> it
            }
        }
    }

    fun split(pairs: List<ImportPair>, id: String, termAt: Int, translationAt: Int): List<ImportPair> {
        val pair = pairs.firstOrNull { it.id == id } ?: return pairs
        val left = words(pair.term)
        val right = words(pair.translation)
        if (termAt !in 0..left.size || translationAt !in 0..right.size) return pairs
        if (termAt + translationAt == 0 || termAt + translationAt == left.size + right.size) return pairs
        return pairs.flatMap {
            if (it.id != id) listOf(it) else listOf(
                pair.copy(term = left.take(termAt).joinToString(" "), translation = right.take(translationAt).joinToString(" "),
                    warning = null, confidence = null, reviewed = true),
                ImportPair(term = left.drop(termAt).joinToString(" "), translation = right.drop(translationAt).joinToString(" "),
                    page = pair.page, reviewed = true, termBounds = pair.termBounds,
                    translationBounds = pair.translationBounds),
            )
        }
    }

    fun moveWord(pairs: List<ImportPair>, id: String, fromTerm: Boolean, index: Int): List<ImportPair> = pairs.map { pair ->
        if (pair.id != id) pair else {
            val source = words(if (fromTerm) pair.term else pair.translation).toMutableList()
            if (index !in source.indices) pair else {
                val moved = source.removeAt(index)
                val destination = listOf(if (fromTerm) pair.translation else pair.term, moved)
                    .filter(String::isNotBlank).joinToString(" ")
                pair.copy(term = if (fromTerm) source.joinToString(" ") else destination,
                    translation = if (fromTerm) destination else source.joinToString(" "),
                    warning = null, confidence = null, reviewed = true)
            }
        }
    }

    private fun combine(first: OcrBounds?, second: OcrBounds?): OcrBounds? = when {
        first == null -> second
        second == null -> first
        else -> OcrBounds(minOf(first.left, second.left), minOf(first.top, second.top),
            maxOf(first.right, second.right), maxOf(first.bottom, second.bottom))
    }
}
