package de.vocabscan.learn

import java.text.Normalizer
import java.util.Locale

enum class AnswerKind { MATCH, ARTICLE, ACCENT, TYPO, DIFFERENT, EMPTY }
data class AnswerCheck(val kind: AnswerKind, val closest: String? = null) {
    val accepted get() = kind == AnswerKind.MATCH || kind == AnswerKind.ARTICLE
}

object AnswerChecker {
    fun alternatives(primary: String, extra: String): List<String> =
        (listOf(primary) + extra.split('\n', ';')).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy(::normalize)

    fun check(answer: String, primary: String, extra: String, language: String): AnswerCheck {
        val input = normalize(answer)
        if (input.isEmpty()) return AnswerCheck(AnswerKind.EMPTY)
        val allowed = alternatives(primary, extra)
        allowed.firstOrNull { normalize(it) == input }?.let { return AnswerCheck(AnswerKind.MATCH, it) }
        val withoutArticle = stripArticle(input, language)
        allowed.firstOrNull {
            val target = normalize(it)
            input == stripArticle(target, language) || withoutArticle == target
        }
            ?.let { return AnswerCheck(AnswerKind.ARTICLE, it) }
        allowed.firstOrNull {
            val target = stripArticle(normalize(it), language)
            withoutAccents(withoutArticle) == withoutAccents(target)
        }?.let { return AnswerCheck(AnswerKind.ACCENT, it) }
        val closest = allowed.minByOrNull { distance(withoutArticle, stripArticle(normalize(it), language)) }
        if (closest != null) {
            val target = stripArticle(normalize(closest), language)
            val threshold = if (target.length >= 8) 2 else if (target.length >= 4) 1 else 0
            if (distance(withoutArticle, target) <= threshold) return AnswerCheck(AnswerKind.TYPO, closest)
        }
        return AnswerCheck(AnswerKind.DIFFERENT, closest)
    }

    private fun withoutAccents(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace('’', '\'').replace(Regex("\\s+"), " ")

    private fun stripArticle(value: String, language: String): String {
        val articles = when (language) {
            "de" -> listOf("der ", "die ", "das ", "ein ", "eine ")
            "en" -> listOf("the ", "an ", "a ")
            "fr" -> listOf("les ", "des ", "une ", "un ", "le ", "la ", "l'")
            else -> emptyList()
        }
        val prefix = articles.firstOrNull { value.startsWith(it) && value.length > it.length }
        return if (prefix == null) value else value.removePrefix(prefix)
    }

    // Damerau-Levenshtein: adjacent transposed letters count as one typo.
    private fun distance(a: String, b: String): Int {
        if (a.length > 200 || b.length > 200) return maxOf(a.length, b.length)
        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) matrix[i][0] = i
        for (j in 0..b.length) matrix[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            matrix[i][j] = minOf(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1,
                matrix[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                matrix[i][j] = minOf(matrix[i][j], matrix[i - 2][j - 2] + 1)
        }
        return matrix[a.length][b.length]
    }
}
