package de.vocabscan.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReleaseNotes(val versionCode: Long, val versionName: String, val changelog: List<String>)
data class AvailableUpdate(
    val notes: ReleaseNotes,
    val apkUrl: String,
    val sizeBytes: Long = 0,
    val sha256: String? = null,
)

interface UpdateStore {
    var automaticEnabled: Boolean
    var lastAttempt: Long
    var postponedAt: Long
    var seenVersion: Long
}

/** One shared instance serializes startup and explicit checks, including failed attempts. */
class UpdateChecker(
    private val store: UpdateStore,
    private val installedCode: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val installedName: String = installedCode.toString(),
    private val enabled: Boolean = true,
    private val fetch: suspend () -> AvailableUpdate?,
) {
    private val mutex = Mutex()

    suspend fun check(manual: Boolean = false): AvailableUpdate? = mutex.withLock {
        if (!enabled) return@withLock null
        if (!manual && !store.automaticEnabled) return@withLock null
        val now = clock()
        if (!manual && (withinDay(store.lastAttempt, now) || withinDay(store.postponedAt, now))) {
            return@withLock null
        }
        // Persist before networking: offline and 404 also count as an attempt.
        store.lastAttempt = now
        try {
            fetch()?.takeIf { (ReleaseVersion.compare(it.notes.versionName, installedName) ?: 0) > 0 }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (manual) throw error
            null
        }
    }

    fun postpone() { store.postponedAt = clock() }

    fun needsWhatsNew(firstInstallTime: Long, lastUpdateTime: Long): Boolean {
        val previous = store.seenVersion
        if (previous == 0L && firstInstallTime >= lastUpdateTime) {
            store.seenVersion = installedCode
            return false
        }
        return previous < installedCode
    }

    fun acknowledgeWhatsNew() { store.seenVersion = installedCode }

    private fun withinDay(timestamp: Long, now: Long): Boolean =
        timestamp != 0L && now - timestamp < DAY_MILLIS

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
