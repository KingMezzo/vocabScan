package de.vocabscan.scan

/** OCR coordinates refer to the upright image, before it is scaled for display. */
data class ScanSource(
    val pageId: String,
    val width: Int,
    val height: Int,
    val term: OcrBounds?,
    val translation: OcrBounds?,
) {
    fun encode(): String = listOf(pageId, width, height, term.encode(), translation.encode()).joinToString(";")

    companion object {
        fun decode(value: String?): ScanSource? = runCatching {
            val parts = value?.split(';') ?: return null
            if (parts.size != 5 || parts[0].isBlank()) return null
            val width = parts[1].toInt().takeIf { it > 0 } ?: return null
            val height = parts[2].toInt().takeIf { it > 0 } ?: return null
            val term = parseBounds(parts[3])
            val translation = parseBounds(parts[4])
            if ((parts[3].isNotEmpty() && term == null) || (parts[4].isNotEmpty() && translation == null)) return null
            ScanSource(parts[0], width, height, term, translation)
        }.getOrNull()

        private fun parseBounds(value: String): OcrBounds? {
            val n = value.split(',').mapNotNull(String::toIntOrNull)
            return if (n.size == 4 && n[2] > n[0] && n[3] > n[1]) OcrBounds(n[0], n[1], n[2], n[3]) else null
        }
    }
}

private fun OcrBounds?.encode(): String = this?.let { "${it.left},${it.top},${it.right},${it.bottom}" }.orEmpty()
