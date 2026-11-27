package de.vocabscan.learn

import org.junit.Assert.*
import org.junit.Test

class FsrsSchedulerTest {
    private val now = 1_700_000_000_000L

    @Test fun newCardGetsDifferentIntervalsByRating() {
        val again = FsrsScheduler.review(null, ReviewRating.AGAIN, now)
        val hard = FsrsScheduler.review(null, ReviewRating.HARD, now)
        val good = FsrsScheduler.review(null, ReviewRating.GOOD, now)
        val easy = FsrsScheduler.review(null, ReviewRating.EASY, now)
        assertEquals(now + 60_000L, again.dueAt)
        assertTrue(hard.dueAt < good.dueAt)
        assertTrue(good.dueAt < easy.dueAt)
        assertEquals(1, good.memory.reviews)
    }

    @Test fun lapseRepeatsSoonAndChangesMemory() {
        val first = FsrsScheduler.review(null, ReviewRating.GOOD, now)
        val lapse = FsrsScheduler.review(first.memory, ReviewRating.AGAIN, now + 3 * FsrsScheduler.DAY_MS)
        assertEquals(1, lapse.memory.lapses)
        assertEquals(2, lapse.memory.reviews)
        assertEquals(now + 3 * FsrsScheduler.DAY_MS + 60_000L, lapse.dueAt)
        assertTrue(lapse.memory.stability > 0.0)
    }

    @Test fun dueCheckIncludesNeverStudiedAndExactDueTime() {
        assertTrue(FsrsScheduler.isDue(null, now))
        assertTrue(FsrsScheduler.isDue(now, now))
        assertFalse(FsrsScheduler.isDue(now + 1, now))
    }
}
