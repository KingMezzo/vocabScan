package de.vocabscan.scan

import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class OcrToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f,
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val height get() = max(1, bottom - top)
}

data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        fun around(tokens: List<OcrToken>): OcrBounds? = tokens.takeIf { it.isNotEmpty() }?.let {
            OcrBounds(it.minOf(OcrToken::left), it.minOf(OcrToken::top),
                it.maxOf(OcrToken::right), it.maxOf(OcrToken::bottom))
        }
    }
}

data class ImportPair(
    val id: String = UUID.randomUUID().toString(),
    val term: String,
    val translation: String,
    val warning: String? = null,
    val page: Int = 0,
    val confidence: Float? = null,
    val reviewed: Boolean = false,
    val termBounds: OcrBounds? = null,
    val translationBounds: OcrBounds? = null,
)

/** Conservative parser for printed, two-column lists. Never invents missing translations. */
object PairParser {
    private val delimiter = Regex("\\s+(?:[-–—=]|→)\\s+|\\s*[|;\\t]\\s*")
    private val numberingPrefix = Regex("^\\d+[.)]\\s+")
    private val numberingToken = Regex("^\\d+[.)]$")
    private val headers = setOf(
        "english", "englisch", "french", "français", "französisch", "deutsch", "german",
        "allemand", "vokabel", "vokabeln", "übersetzung", "translation", "vocabulary", "wort",
        "bedeutung", "latein", "latin", "lateinisch", "lingua latina",
    )

    /** The same automatically detected boundary used by [parse], for an accurate image overlay. */
    fun suggestedBoundary(tokens: List<OcrToken>): Float? {
        val usable = tokens.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (usable.isEmpty()) return null
        val typicalHeight = medianInts(usable.map { it.height }).coerceAtLeast(1f)
        val coarseRows = clusterRows(usable, typicalHeight, slope = 0f)
            .map(::withoutLeadingNumberToken).filter { it.isNotEmpty() }
        return findGlobalCorridor(usable, typicalHeight) ?: findColumnBoundary(coarseRows, typicalHeight)
    }

    fun parse(tokens: List<OcrToken>, page: Int = 0, columnBoundary: Float? = null): List<ImportPair> {
        val usable = tokens.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (usable.isEmpty()) return emptyList()

        val typicalHeight = medianInts(usable.map { it.height }).coerceAtLeast(1f)
        val coarseRows = clusterRows(usable, typicalHeight, slope = 0f)
            .map(::withoutLeadingNumberToken)
            .filter { it.isNotEmpty() }
        val boundary = columnBoundary ?: findGlobalCorridor(usable, typicalHeight) ?: findColumnBoundary(coarseRows, typicalHeight)

        // A small residual perspective/skew shifts the right cell vertically. Estimate and remove it
        // before the final row grouping so that both cells still land in the same vocabulary row.
        val slope = boundary?.let { estimateSlope(coarseRows, it) } ?: 0f
        val rows = clusterRows(usable, typicalHeight, slope)
            .map(::withoutLeadingNumberToken)
            .filter { it.isNotEmpty() }

        // Once a table corridor is known, group each column on its own. OCR boxes in
        // opposite cells can have different heights or baselines; clustering them
        // together can silently attach a translation to the neighbouring word.
        if (boundary != null) {
            val leftRows = clusterRows(usable.filter { it.centerX < boundary }, typicalHeight, slope)
                .map(::withoutLeadingNumberToken).filter { it.isNotEmpty() }
            val rightRows = clusterRows(usable.filter { it.centerX >= boundary }, typicalHeight, slope)
                .filter { it.isNotEmpty() }
            if (leftRows.isNotEmpty() && rightRows.isNotEmpty()) {
                return alignColumns(leftRows, rightRows, boundary, typicalHeight, slope, page)
            }
        }
        return rows.mapNotNull { row -> parseRow(row, boundary, typicalHeight, page) }
    }

    private fun alignColumns(
        leftRows: List<List<OcrToken>>, rightRows: List<List<OcrToken>>,
        boundary: Float, typicalHeight: Float, slope: Float, page: Int,
    ): List<ImportPair> {
        val originX = (leftRows + rightRows).flatten().minOf { it.left }
        fun center(row: List<OcrToken>) = row.map {
            it.centerY - slope * (it.centerX - originX)
        }.average().toFloat()
        val left = leftRows.sortedBy(::center)
        val right = rightRows.sortedBy(::center)
        val matched = mutableListOf<List<OcrToken>>()
        var i = 0
        var j = 0
        while (i < left.size || j < right.size) {
            if (i == left.size) { matched += right[j++]; continue }
            if (j == right.size) { matched += left[i++]; continue }
            val delta = center(left[i]) - center(right[j])
            val tolerance = max(typicalHeight * 0.95f,
                max(left[i].maxOf { it.height }, right[j].maxOf { it.height }) * 0.55f)
            when {
                abs(delta) <= tolerance -> matched += left[i++] + right[j++]
                delta < 0f -> matched += left[i++]
                else -> matched += right[j++]
            }
        }
        // A single complete token may itself contain an explicit separator.
        return matched.mapNotNull { row -> parseRow(row, boundary, typicalHeight, page) }
    }

    private fun parseRow(
        row: List<OcrToken>,
        boundary: Float?,
        typicalHeight: Float,
        page: Int,
    ): ImportPair? {
        val ordered = row.sortedBy { it.left }
        val completeText = ordered.joinToString(" ") { it.text }.trim()
        if (completeText.isBlank()) return null

        val explicitParts = delimiter.split(completeText, limit = 2)
        val split = when {
            boundary != null -> {
                val left = ordered.filter { it.centerX < boundary }
                val right = ordered.filter { it.centerX >= boundary }
                listOf(
                    cleanLeft(left.joinToString(" ") { it.text }),
                    right.joinToString(" ") { it.text }.trim(),
                )
            }
            explicitParts.size == 2 -> listOf(cleanLeft(explicitParts[0]), explicitParts[1].trim())
            else -> splitAtLargestGap(ordered, typicalHeight)
        }

        val term = split[0]
        val translation = split[1]
        if (term.isBlank() && translation.isBlank()) return null
        if (listOf(term, translation).all { it.isBlank() || normalize(it) in headers }) return null

        val confidence = ordered.map { it.confidence }.filter { it in 0f..1f }.minOrNull()
        val warning = when {
            term.isBlank() -> "Keine eindeutige erste Spalte. Bitte ergänzen oder entfernen."
            translation.isBlank() -> "Keine eindeutige zweite Spalte. Bitte ergänzen oder entfernen."
            confidence != null && confidence < 0.8f -> "Unsichere Texterkennung. Bitte mit dem Bild vergleichen."
            else -> null
        }
        val leftTokens: List<OcrToken>
        val rightTokens: List<OcrToken>
        if (boundary != null) {
            leftTokens = ordered.filter { it.centerX < boundary }
            rightTokens = ordered.filter { it.centerX >= boundary }
        } else {
            val gapIndex = (0 until ordered.lastIndex).maxByOrNull { ordered[it + 1].left - ordered[it].right }
            val gap = gapIndex?.let { ordered[it + 1].left - ordered[it].right } ?: 0
            if (gapIndex != null && gap > typicalHeight * 1.35f) {
                leftTokens = ordered.take(gapIndex + 1)
                rightTokens = ordered.drop(gapIndex + 1)
            } else {
                leftTokens = ordered
                rightTokens = emptyList()
            }
        }
        return ImportPair(term = term, translation = translation, warning = warning, page = page,
            confidence = confidence, termBounds = OcrBounds.around(leftTokens),
            translationBounds = OcrBounds.around(rightTokens))
    }

    private fun splitAtLargestGap(tokens: List<OcrToken>, typicalHeight: Float): List<String> {
        if (tokens.size < 2) return listOf(cleanLeft(tokens.joinToString(" ") { it.text }), "")
        val gapIndex = (0 until tokens.lastIndex).maxByOrNull { tokens[it + 1].left - tokens[it].right }
        val gap = gapIndex?.let { tokens[it + 1].left - tokens[it].right } ?: 0
        return if (gapIndex != null && gap > typicalHeight * 1.35f) {
            listOf(
                cleanLeft(tokens.take(gapIndex + 1).joinToString(" ") { it.text }),
                tokens.drop(gapIndex + 1).joinToString(" ") { it.text }.trim(),
            )
        } else listOf(cleanLeft(tokens.joinToString(" ") { it.text }), "")
    }

    /** Find whitespace across the whole page, independent of row alignment and OCR reading order. */
    private fun findGlobalCorridor(tokens: List<OcrToken>, typicalHeight: Float): Float? {
        if (tokens.size < 4) return null
        val sorted = tokens.sortedBy { it.left }
        val contentLeft = sorted.first().left
        val contentRight = sorted.maxOf { it.right }
        val width = contentRight - contentLeft
        var end = sorted.first().right
        val gaps = mutableListOf<Pair<Int, Int>>()
        sorted.drop(1).forEach { token ->
            if (token.left > end) gaps += end to token.left
            end = max(end, token.right)
        }
        return gaps.mapNotNull { (gapLeft, gapRight) ->
            val gap = gapRight - gapLeft
            val x = (gapLeft + gapRight) / 2f
            if (gap < typicalHeight * 1.5f || x < contentLeft + width * 0.18f ||
                x > contentRight - width * 0.18f) return@mapNotNull null
            val left = tokens.filter { it.centerX < x }
            val right = tokens.filter { it.centerX >= x }
            val leftRows = clusterRows(left, typicalHeight, 0f).size
            val rightRows = clusterRows(right, typicalHeight, 0f).size
            if (leftRows < 2 || rightRows < 2) null else {
                val balance = min(leftRows, rightRows).toFloat() / max(leftRows, rightRows)
                x to (gap * balance)
            }
        }.maxByOrNull { it.second }?.first
    }

    /** A real table separator repeats in many rows; a gap inside a phrase normally does not. */
    private fun findColumnBoundary(rows: List<List<OcrToken>>, typicalHeight: Float): Float? {
        val candidates = rows.flatMap { row ->
            row.sortedBy { it.left }.zipWithNext().mapNotNull { (left, right) ->
                val gap = right.left - left.right
                if (gap >= typicalHeight * 0.75f) (left.right + right.left) / 2f else null
            }
        }
        if (candidates.isEmpty()) return null

        val all = rows.flatten()
        val contentLeft = all.minOf { it.left }.toFloat()
        val contentRight = all.maxOf { it.right }.toFloat()
        val contentWidth = contentRight - contentLeft
        if (contentWidth <= typicalHeight * 3f) return null

        data class BoundaryScore(val x: Float, val supportingRows: Int, val score: Float)
        val scored = candidates.distinct().mapNotNull { candidate ->
            // Reject margins and list-number columns. Both vocabulary columns need useful width.
            if (candidate < contentLeft + contentWidth * 0.18f || candidate > contentRight - contentWidth * 0.18f) {
                return@mapNotNull null
            }
            var support = 0
            var gapScore = 0f
            rows.forEach { row ->
                val crossingGap = row.sortedBy { it.left }.zipWithNext().firstOrNull { (left, right) ->
                    left.right < candidate && right.left > candidate
                }
                if (crossingGap != null) {
                    val gap = crossingGap.second.left - crossingGap.first.right
                    if (gap >= typicalHeight * 0.75f) {
                        support++
                        gapScore += min(gap / typicalHeight, 12f)
                    }
                }
            }
            BoundaryScore(candidate, support, support * 100f + gapScore)
        }
        val best = scored.maxWithOrNull(compareBy<BoundaryScore> { it.score }.thenBy { it.x }) ?: return null
        val rowsWithPotentialSplit = rows.count { it.size >= 2 }
        val requiredSupport = if (rowsWithPotentialSplit <= 1) 1 else {
            max(2, ceil(rowsWithPotentialSplit * 0.4).toInt())
        }
        return best.x.takeIf { best.supportingRows >= requiredSupport }
    }

    private fun estimateSlope(rows: List<List<OcrToken>>, boundary: Float): Float {
        val slopes = rows.mapNotNull { row ->
            val left = row.filter { it.centerX < boundary }
            val right = row.filter { it.centerX >= boundary }
            if (left.isEmpty() || right.isEmpty()) return@mapNotNull null
            val leftX = left.map { it.centerX }.average().toFloat()
            val rightX = right.map { it.centerX }.average().toFloat()
            val run = rightX - leftX
            if (run <= 0f) null else {
                val leftY = left.map { it.centerY }.average().toFloat()
                val rightY = right.map { it.centerY }.average().toFloat()
                ((rightY - leftY) / run).takeIf { abs(it) <= 0.15f }
            }
        }
        return if (slopes.size >= 2) median(slopes) else 0f
    }

    private fun clusterRows(tokens: List<OcrToken>, typicalHeight: Float, slope: Float): List<List<OcrToken>> {
        data class Positioned(val token: OcrToken, val y: Float)
        data class Row(val entries: MutableList<Positioned>) {
            val center get() = entries.map { it.y }.average().toFloat()
        }

        val originX = tokens.minOf { it.left }
        val positioned = tokens.map { token ->
            Positioned(token, token.centerY - slope * (token.centerX - originX))
        }.sortedBy { it.y }
        val rows = mutableListOf<Row>()

        positioned.forEach { entry ->
            val tolerance = max(typicalHeight * 0.72f, entry.token.height * 0.55f)
            val target = rows.asReversed().take(3)
                .filter { abs(it.center - entry.y) <= tolerance }
                .minByOrNull { abs(it.center - entry.y) }
            if (target == null) rows.add(Row(mutableListOf(entry))) else target.entries.add(entry)
        }
        return rows.sortedBy { it.center }.map { row -> row.entries.map { it.token }.sortedBy { it.left } }
    }

    private fun withoutLeadingNumberToken(row: List<OcrToken>): List<OcrToken> {
        val sorted = row.sortedBy { it.left }
        return if (sorted.size > 1 && numberingToken.matches(sorted.first().text.trim())) sorted.drop(1) else sorted
    }

    private fun cleanLeft(text: String) = text.trim().replace(numberingPrefix, "").trim()
    private fun normalize(text: String) = text.trim().lowercase(Locale.ROOT).removeSuffix(":")

    private fun medianInts(values: List<Int>): Float = median(values.map(Int::toFloat))
    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
    }
}
