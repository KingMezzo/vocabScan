package de.vocabscan.scan

import org.junit.Assert.*
import org.junit.Test

class PairParserTest {
    private fun token(text: String, left: Int, top: Int = 0, width: Int = 50, confidence: Float = 1f) =
        OcrToken(text, left, top, left + width, top + 20, confidence)

    private fun sizedToken(
        text: String,
        left: Int,
        top: Int,
        width: Int = 50,
        height: Int = 20,
        confidence: Float = 1f,
    ) = OcrToken(text, left, top, left + width, top + height, confidence)

    @Test fun `pairs columns despite column-first OCR reading order`() {
        val result = PairParser.parse(listOf(
            token("apple", 0), token("house", 0, 40),
            token("Apfel", 240, 1), token("Haus", 240, 41),
        ))
        assertEquals(listOf("apple", "house"), result.map { it.term })
        assertEquals(listOf("Apfel", "Haus"), result.map { it.translation })
    }

    @Test fun `keeps phrases articles accents and apostrophes`() {
        val result = PairParser.parse(listOf(
            token("l'école", 0, width = 70), token("die", 240, width = 24), token("Schule", 271),
            token("to", 0, 40, 20), token("look", 28, 40, 40), token("after", 76, 40, 40), token("aufpassen", 240, 40, 100),
        ))
        assertEquals("l'école", result[0].term)
        assertEquals("die Schule", result[0].translation)
        assertEquals("to look after", result[1].term)
    }

    @Test fun `preserves unmatched rows instead of shifting following translations`() {
        val result = PairParser.parse(listOf(token("apple", 0), token("house", 0, 40), token("Haus", 240, 40)))
        assertEquals("", result[0].translation)
        assertNotNull(result[0].warning)
        assertEquals("house", result[1].term)
        assertEquals("Haus", result[1].translation)
    }

    @Test fun `does not split ordinary spaces inside a phrase`() {
        val result = PairParser.parse(listOf(token("to", 0, width = 20), token("take", 26, width = 35), token("care", 68)))
        assertEquals("to take care", result.single().term)
        assertEquals("", result.single().translation)
    }

    @Test fun `handles explicit separators and numbered rows`() {
        val result = PairParser.parse(listOf(token("1. apple – Apfel", 0), token("2) maison = Haus", 0, 40)))
        assertEquals("apple", result[0].term)
        assertEquals("Apfel", result[0].translation)
        assertEquals("maison", result[1].term)
        assertEquals("Haus", result[1].translation)
    }

    @Test fun `does not split a hyphenated word`() {
        val result = PairParser.parse(listOf(token("well-known", 0, width = 110), token("bekannt", 240)))
        assertEquals("well-known", result.single().term)
    }

    @Test fun `skips known column headings`() {
        assertTrue(PairParser.parse(listOf(token("Français", 0), token("Deutsch", 240))).isEmpty())
    }

    @Test fun `skips Latin headings and preserves dictionary forms and macrons`() {
        val result = PairParser.parse(listOf(
            token("Latein", 0, width = 100), token("Deutsch", 240, width = 100),
            token("puella, -ae f.", 0, 40, width = 140), token("Mädchen", 240, 40, width = 100),
            token("amāre", 0, 80, width = 100), token("lieben", 240, 80, width = 100),
        ))
        assertEquals(listOf("puella, -ae f.", "amāre"), result.map { it.term })
        assertEquals(listOf("Mädchen", "lieben"), result.map { it.translation })
        assertTrue(PairParser.parse(listOf(token("Deutsch", 0), token("Latin", 240))).isEmpty())
    }

    @Test fun `marks low confidence and retains original text`() {
        val result = PairParser.parse(listOf(token("école", 0, confidence = 0.4f), token("Schule", 240)), page = 2)
        assertEquals("école", result.single().term)
        assertEquals(2, result.single().page)
        assertNotNull(result.single().warning)
    }

    @Test fun `empty or whitespace OCR produces no cards`() {
        assertTrue(PairParser.parse(emptyList()).isEmpty())
        assertTrue(PairParser.parse(listOf(token("   ", 0))).isEmpty())
    }

    @Test fun `preserves ordinal numbers in translations`() {
        val result = PairParser.parse(listOf(token("first floor", 0, width = 120), token("1.", 240, width = 20), token("Stock", 270)))
        assertEquals("1. Stock", result.single().translation)
    }

    @Test fun `numbering token is not mistaken for a vocabulary column`() {
        val result = PairParser.parse(listOf(token("1.", 0, width = 15), token("apple", 90), token("Apfel", 300)))
        assertEquals("apple", result.single().term)
        assertEquals("Apfel", result.single().translation)
    }

    @Test fun `pairs cells with different OCR box heights and vertical centers`() {
        val result = PairParser.parse(listOf(
            sizedToken("apple", 20, 10, width = 70, height = 20),
            sizedToken("Apfel", 420, 3, width = 80, height = 36),
            sizedToken("garden", 20, 62, width = 90, height = 21),
            sizedToken("Garten", 420, 55, width = 90, height = 35),
            sizedToken("beautiful", 20, 115, width = 110, height = 22),
            sizedToken("schön", 420, 107, width = 80, height = 38),
        ))

        assertEquals(listOf("apple", "garden", "beautiful"), result.map { it.term })
        assertEquals(listOf("Apfel", "Garten", "schön"), result.map { it.translation })
    }

    @Test fun `uses the repeated table corridor instead of large gaps inside phrases`() {
        val result = PairParser.parse(listOf(
            token("to", 20, 10, width = 25), token("look", 62, 10, width = 45),
            token("after", 170, 10, width = 55), token("aufpassen", 430, 10, width = 100),
            token("in", 20, 55, width = 20), token("front", 58, 55, width = 50),
            token("of", 180, 55, width = 22), token("vor", 430, 55, width = 35),
            token("as", 20, 100, width = 25), token("soon", 62, 100, width = 50),
            token("as", 185, 100, width = 25), token("sobald", 430, 100, width = 70),
        ))

        assertEquals(listOf("to look after", "in front of", "as soon as"), result.map { it.term })
        assertEquals(listOf("aufpassen", "vor", "sobald"), result.map { it.translation })
    }

    @Test fun `deskews a slightly slanted table before grouping rows`() {
        val result = PairParser.parse(listOf(
            token("one", 20, 10), token("eins", 420, 20),
            token("two", 20, 48), token("zwei", 420, 58),
            token("three", 20, 86), token("drei", 420, 96),
            token("four", 20, 124), token("vier", 420, 134),
        ))

        assertEquals(listOf("one", "two", "three", "four"), result.map { it.term })
        assertEquals(listOf("eins", "zwei", "drei", "vier"), result.map { it.translation })
    }

    @Test fun `keeps a missing right cell on its own visual row`() {
        val result = PairParser.parse(listOf(
            token("apple", 20, 10), token("Apfel", 420, 10),
            token("garden", 20, 50),
            token("house", 20, 90), token("Haus", 420, 90),
        ))

        assertEquals(listOf("apple", "garden", "house"), result.map { it.term })
        assertEquals(listOf("Apfel", "", "Haus"), result.map { it.translation })
        assertNotNull(result[1].warning)
    }

    @Test fun `ignores a leading row number without dropping ordinal translation`() {
        val result = PairParser.parse(listOf(
            token("12.", 0, 10, width = 25), token("first floor", 70, 10, width = 120),
            token("1.", 430, 10, width = 20), token("Stock", 460, 10),
            token("13.", 0, 50, width = 25), token("second floor", 70, 50, width = 140),
            token("2.", 430, 50, width = 20), token("Stock", 460, 50),
        ))

        assertEquals(listOf("first floor", "second floor"), result.map { it.term })
        assertEquals(listOf("1. Stock", "2. Stock"), result.map { it.translation })
    }

    @Test fun `matches columns whose OCR baselines do not share coarse rows`() {
        val result = PairParser.parse(listOf(
            token("apple", 20, 10, 80), token("garden", 20, 60, 90), token("house", 20, 110, 80),
            token("Apfel", 420, 25, 80), token("Garten", 420, 75, 90), token("Haus", 420, 125, 80),
        ))
        assertEquals(listOf("apple", "garden", "house"), result.map { it.term })
        assertEquals(listOf("Apfel", "Garten", "Haus"), result.map { it.translation })
    }

    @Test fun `does not shift a missing cell when the next row has a different baseline`() {
        val result = PairParser.parse(listOf(
            token("apple", 20, 10), token("garden", 20, 60), token("house", 20, 110),
            token("Apfel", 420, 25), token("Haus", 420, 125),
        ))
        assertEquals(listOf("apple", "garden", "house"), result.map { it.term })
        assertEquals(listOf("Apfel", "", "Haus"), result.map { it.translation })
        assertNotNull(result[1].warning)
    }

    @Test fun `manual column boundary reparses the same OCR tokens`() {
        val tokens = listOf(
            token("to", 20, 10, 25), token("look", 65, 10, 45), token("after", 145, 10, 55),
            token("aufpassen", 400, 10, 100),
            token("in", 20, 55, 20), token("front", 65, 55, 50), token("of", 150, 55, 20),
            token("vor", 400, 55, 50),
        )
        val result = PairParser.parse(tokens, columnBoundary = 300f)
        assertEquals(listOf("to look after", "in front of"), result.map { it.term })
        assertEquals(listOf("aufpassen", "vor"), result.map { it.translation })
    }

    @Test fun `keeps a separator inside a cell when a spatial column boundary exists`() {
        val result = PairParser.parse(listOf(
            token("to - look after", 20, 10, 160), token("sich kümmern", 400, 10, 110),
            token("well - known", 20, 55, 150), token("bekannt", 400, 55, 90),
        ), columnBoundary = 300f)
        assertEquals(listOf("to - look after", "well - known"), result.map { it.term })
        assertEquals(listOf("sich kümmern", "bekannt"), result.map { it.translation })
    }

    @Test fun `finds page wide gutter despite staggered rows and word gaps`() {
        val tokens = listOf(
            token("le", 25, 10, 25), token("texte", 60, 10, 75),
            token("la", 25, 60, 25), token("maison", 60, 60, 95),
            token("un", 25, 110, 25), token("jour", 60, 110, 70),
            token("der", 340, 24, 40), token("Text", 390, 24, 65),
            token("das", 340, 74, 40), token("Haus", 390, 74, 65),
            token("ein", 340, 124, 40), token("Tag", 390, 124, 65),
        )
        val boundary = PairParser.suggestedBoundary(tokens)
        assertNotNull(boundary)
        assertTrue(boundary!! > 155f && boundary < 340f)
        val parsed = PairParser.parse(tokens)
        assertEquals(listOf("le texte", "la maison", "un jour"), parsed.map { it.term })
        assertEquals(listOf("der Text", "das Haus", "ein Tag"), parsed.map { it.translation })
    }

    @Test fun `suggested boundary matches the parsed table corridor`() {
        val tokens = listOf(
            token("apple", 15, 10, 80), token("Apfel", 660, 10, 80),
            token("house", 15, 55, 80), token("Haus", 660, 55, 80),
        )
        val boundary = PairParser.suggestedBoundary(tokens)
        assertNotNull(boundary)
        assertTrue(boundary!! > 95f && boundary < 660f)
        assertEquals(listOf("Apfel", "Haus"), PairParser.parse(tokens, columnBoundary = boundary).map { it.translation })
    }
}
