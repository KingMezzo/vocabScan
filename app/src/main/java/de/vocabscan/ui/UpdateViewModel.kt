package de.vocabscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.vocabscan.update.AndroidUpdates
import de.vocabscan.update.AvailableUpdate
import de.vocabscan.update.ReleaseNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateUiState(
    val checking: Boolean = true,
    val update: AvailableUpdate? = null,
    val whatsNew: ReleaseNotes? = null,
    val automaticEnabled: Boolean = true,
    val checkMessage: String? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApk: File? = null,
    val downloadError: String? = null,
)

class UpdateViewModel(private val updates: AndroidUpdates) : ViewModel() {
    val updatesEnabled = updates.enabled
    val installedVersion = "${updates.installedName} (${updates.installedCode})"
    private val mutableState = MutableStateFlow(UpdateUiState(checking = updatesEnabled, automaticEnabled = updates.automaticEnabled))
    private var downloadJob: Job? = null
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val notes = updates.whatsNew()
            mutableState.update { it.copy(whatsNew = notes) }
            check(manual = false)
        }
    }

    fun checkManually() {
        if (!updatesEnabled || mutableState.value.checking || mutableState.value.downloading) return
        mutableState.update { it.copy(checking = true, checkMessage = null) }
        viewModelScope.launch { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        // Persistence as well as the request run off the main thread.
        try {
            val update = withContext(Dispatchers.IO) { updates.checker.check(manual) }
            mutableState.update { state -> state.copy(checking = false,
                update = update.takeIf { manual || state.automaticEnabled },
                checkMessage = if (manual && update == null) "Keine neuere Version mit APK verfügbar." else null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(checking = false,
                checkMessage = "Updateprüfung fehlgeschlagen. Prüfe deine Internetverbindung und versuche es erneut.") }
        }
    }

    fun setAutomaticEnabled(enabled: Boolean) {
        mutableState.update { it.copy(automaticEnabled = enabled) }
        updates.automaticEnabled = enabled
    }

    fun download() {
        val update = mutableState.value.update ?: return
        if (mutableState.value.downloading || mutableState.value.downloadedApk != null) return
        mutableState.update { it.copy(downloading = true, downloadProgress = 0f, downloadError = null) }
        downloadJob = viewModelScope.launch {
            try {
                val file = updates.downloader.download(update) { progress ->
                    mutableState.update { it.copy(downloadProgress = progress) }
                }
                mutableState.update { it.copy(downloading = false, downloadedApk = file) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(downloading = false,
                    downloadError = "Update konnte nicht heruntergeladen oder geprüft werden. Bitte versuche es erneut. Das Update muss mit deiner installierten App kompatibel sein.") }
            }
        }
    }

    fun installationIntent() = updates.downloader.installationIntent(requireNotNull(mutableState.value.downloadedApk))

    fun installationError(message: String) {
        mutableState.update { it.copy(downloadError = message) }
    }

    fun postpone() {
        downloadJob?.cancel()
        val file = mutableState.value.downloadedApk
        mutableState.update { it.copy(update = null, downloading = false, downloadedApk = null,
            downloadProgress = 0f, downloadError = null) }
        viewModelScope.launch(Dispatchers.IO) { file?.delete(); updates.checker.postpone() }
    }

    fun acknowledgeWhatsNew() {
        mutableState.update { it.copy(whatsNew = null) }
        viewModelScope.launch(Dispatchers.IO) { updates.checker.acknowledgeWhatsNew() }
    }
}
