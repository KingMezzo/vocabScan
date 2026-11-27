package de.vocabscan.scan

import org.junit.Assert.*
import org.junit.Test

class ScanEditsTest {
    @Test fun mergeKeepsPageOrderRegardlessOfDragDirectionAndLeavesOtherPagesAlone() {
        val first = ImportPair(term = "to look", translation = "sich", page = 1)
        val second = ImportPair(term = "after", translation = "kümmern", page = 1)
        val other = ImportPair(term = "house", translation = "Haus", page = 2)
        val pairs = listOf(first, second, other)
        val merged = ScanEdits.merge(pairs, second.id, first.id)
        assertEquals(listOf("to look after", "house"), merged.map { it.term })
        assertEquals("sich kümmern", merged.first().translation)
        assertEquals(first.id, merged.first().id)
        assertEquals(pairs, ScanEdits.merge(pairs, first.id, other.id))
    }

    @Test fun splitPreservesEveryWordAndAllowsARecognizedMissingCell() {
        val pair = ImportPair(term = "la maison le jardin", translation = "das Haus der Garten", page = 3)
        val split = ScanEdits.split(listOf(pair), pair.id, 2, 2)
        assertEquals(listOf("la maison", "le jardin"), split.map { it.term })
        assertEquals(listOf("das Haus", "der Garten"), split.map { it.translation })
        assertTrue(split.all { it.page == 3 })
        assertNotEquals(split[0].id, split[1].id)
        val missing = ScanEdits.split(listOf(pair), pair.id, 0, 2)
        assertEquals("", missing.first().term)
        assertEquals(pair.term, missing.last().term)
        assertEquals(listOf(pair), ScanEdits.split(listOf(pair), pair.id, 0, 0))
    }

    @Test fun movingRepeatedWordsRemovesOnlyTheSelectedOccurrence() {
        val pair = ImportPair(term = "had had", translation = "hatte")
        val moved = ScanEdits.moveWord(listOf(pair), pair.id, true, 1).single()
        assertEquals("had", moved.term)
        assertEquals("hatte had", moved.translation)
        assertEquals(listOf(pair), ScanEdits.moveWord(listOf(pair), pair.id, true, 4))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidCropCannotBeApplied() {
        ScanCrop(0.8f, 0f, 0.2f, 1f)
    }
}
