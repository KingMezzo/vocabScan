package de.vocabscan.scan

import java.util.Locale

/** Only reports a language when several distinctive clues agree. */
object LanguageHint {
    private val latinWords = setOf("amicus", "amica", "aqua", "bellum", "dominus", "equus", "magister",
        "puella", "puer", "servus", "urbs", "videre", "audire", "dicere", "facere", "legere", "venire", "amare")

    fun suggest(pairs: List<ImportPair>, translationSide: Boolean = false): String? {
        val terms = pairs.map { (if (translationSide) it.translation else it.term).trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        val french = terms.count { term ->
            term.any { it in "àâçéèêëîïôùûœ" } ||
                listOf("le ", "la ", "les ", "un ", "une ", "l'", "je ", "nous ", "vous ").any(term::startsWith)
        }
        val english = terms.count { term ->
            listOf("the ", "a ", "an ", "to ", "i ", "you ", "we ", "they ").any(term::startsWith) ||
                term in setOf("the", "and", "with", "because", "where", "when")
        }
        val german = terms.count { term ->
            term.any { it in "äöß" } ||
                listOf("der ", "die ", "das ", "ein ", "eine ", "ich ", "wir ", "sie ").any(term::startsWith)
        }
        val latin = terms.count { term ->
            term.any { it in "āēīōūȳ" } || term.substringBefore(',').substringBefore(' ') in latinWords
        }
        return when {
            french >= 2 && french >= maxOf(english, german, latin) * 2 -> "fr"
            english >= 2 && english >= maxOf(french, german, latin) * 2 -> "en"
            german >= 2 && german >= maxOf(french, english, latin) * 2 -> "de"
            latin >= 2 && latin >= maxOf(french, english, german) * 2 -> "la"
            else -> null
        }
    }
}
