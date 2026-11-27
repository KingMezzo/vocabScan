package de.vocabscan.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanSourceTest {
    @Test fun parserKeepsThePositionOfEachColumn() {
        val pair = PairParser.parse(listOf(
            OcrToken("house", 20, 100, 90, 125),
            OcrToken("Haus", 240, 102, 295, 127),
        )).single()
        assertEquals(OcrBounds(20, 100, 90, 125), pair.termBounds)
        assertEquals(OcrBounds(240, 102, 295, 127), pair.translationBounds)
    }

    @Test fun preservesPageAndBothHighlightedWords() {
        val source = ScanSource("page-2", 1200, 1800,
            OcrBounds(100, 200, 280, 235), OcrBounds(650, 198, 830, 238))
        assertEquals(source, ScanSource.decode(source.encode()))
    }

    @Test fun rejectsMissingOrMalformedLocation() {
        assertNull(ScanSource.decode(null))
        assertNull(ScanSource.decode("page;0;10;1,2,3,4;"))
        assertNull(ScanSource.decode("page;10;10;1,2,3;"))
    }
}
