package de.vocabscan.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private class Store : UpdateStore {
        override var automaticEnabled = true
        override var lastAttempt = 0L
        override var postponedAt = 0L
        override var seenVersion = 0L
    }
    private val release = AvailableUpdate(ReleaseNotes(0, "16", listOf("Neu")), "https://example.org/app.apk")

    @Test fun automaticCheckPersistsAttemptsAcrossCheckerInstances() = runBlocking {
        val store = Store()
        var now = UpdateChecker.DAY_MILLIS
        var requests = 0
        fun checker() = UpdateChecker(store, 15, { now }) { requests++; release }
        assertEquals(release, checker().check())
        now += UpdateChecker.DAY_MILLIS - 1
        assertNull(checker().check())
        now++
        assertEquals(release, checker().check())
        assertEquals(2, requests)
    }

    @Test fun failedAttemptAlsoThrottlesAndManualCheckOverridesIt() = runBlocking {
        val store = Store()
        var requests = 0
        val checker = UpdateChecker(store, 15, { UpdateChecker.DAY_MILLIS }) {
            requests++
            throw java.io.IOException("offline / 404")
        }
        assertNull(checker.check())
        assertNull(checker.check())
        assertEquals(1, requests)
        assertTrue(runCatching { checker.check(manual = true) }.exceptionOrNull() is java.io.IOException)
        assertEquals(2, requests)
    }

    @Test fun postponementStartsANew24HourWindow() = runBlocking {
        val store = Store()
        var now = UpdateChecker.DAY_MILLIS
        val checker = UpdateChecker(store, 15, { now }) { release }
        checker.check()
        now += 60_000
        checker.postpone()
        now += UpdateChecker.DAY_MILLIS - 1
        assertNull(checker.check())
        now++
        assertEquals(release, checker.check())
    }

    @Test fun equalOrOlderVersionsDoNotPrompt() = runBlocking {
        for (code in listOf(14L, 15L)) {
            val checker = UpdateChecker(Store(), 15) { release.copy(notes = release.notes.copy(versionName = code.toString())) }
            assertNull(checker.check())
        }
    }

    @Test fun simultaneousAutomaticChecksMakeOneRequest() = runBlocking {
        var requests = 0
        val checker = UpdateChecker(Store(), 15) { requests++; delay(10); release }
        val first = async { checker.check() }
        val second = async { checker.check() }
        assertEquals(release, first.await())
        assertNull(second.await())
        assertEquals(1, requests)
    }

    @Test fun disabledAutomaticCheckMakesNoRequestButManualCheckStillWorks() = runBlocking {
        val store = Store().apply { automaticEnabled = false }
        var requests = 0
        val checker = UpdateChecker(store, 15) { requests++; release }
        assertNull(checker.check())
        assertEquals(0, requests)
        assertEquals(0L, store.lastAttempt)
        assertEquals(release, checker.check(manual = true))
        assertEquals(1, requests)
    }

    @Test fun releaseTagIsComparedToInstalledNameInsteadOfAndroidCode() = runBlocking {
        val checker = UpdateChecker(Store(), 900, installedName = "1.9.0") {
            release.copy(notes = ReleaseNotes(0, "1.10.0", emptyList()))
        }
        assertNotNull(checker.check())
    }

    @Test fun disabledCheckerSkipsAutomaticAndManualRequestsWithoutChangingStoredState() = runBlocking {
        val store = Store().apply {
            lastAttempt = 123L
            postponedAt = 456L
            seenVersion = 15L
        }
        val checker = UpdateChecker(store, 15, enabled = false) {
            fail("Disabled builds must never request release updates")
            release
        }
        assertNull(checker.check())
        assertNull(checker.check(manual = true))
        assertTrue(store.automaticEnabled)
        assertEquals(123L, store.lastAttempt)
        assertEquals(456L, store.postponedAt)
        assertEquals(15L, store.seenVersion)
    }

    @Test fun freshInstallSkipsNotesButNextUpgradeShowsOnce() {
        val store = Store()
        val first = UpdateChecker(store, 15) { null }
        assertFalse(first.needsWhatsNew(100, 100))
        assertEquals(15L, store.seenVersion)
        val upgrade = UpdateChecker(store, 16) { null }
        assertTrue(upgrade.needsWhatsNew(100, 200))
        // Do not lose the dialog on process death before acknowledgement.
        assertTrue(upgrade.needsWhatsNew(100, 200))
        upgrade.acknowledgeWhatsNew()
        assertFalse(upgrade.needsWhatsNew(100, 200))
    }

    @Test fun legacyUpgradeWithoutMarkerShowsNotesAndDowngradeDoesNot() {
        val store = Store()
        val upgrade = UpdateChecker(store, 15) { null }
        assertTrue(upgrade.needsWhatsNew(100, 200))
        upgrade.acknowledgeWhatsNew()
        assertFalse(UpdateChecker(store, 14) { null }.needsWhatsNew(100, 300))
    }

    @Test fun clockMovingBackDoesNotCauseExtraAutomaticRequest() = runBlocking {
        val store = Store().apply { lastAttempt = 100_000 }
        val checker = UpdateChecker(store, 15, { 90_000 }) { fail("Unexpected request"); null }
        assertNull(checker.check())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagated() = runBlocking {
        UpdateChecker(Store(), 15) { throw CancellationException() }.check()
        Unit
    }
}
