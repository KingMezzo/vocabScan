package de.vocabscan.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class ApkDownloader(context: Context) {
    private val context = context.applicationContext

    fun removeExpiredDownloads() {
        val cutoff = System.currentTimeMillis() - UpdateChecker.DAY_MILLIS
        File(context.cacheDir, "updates").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(update.apkUrl.startsWith(AndroidUpdates.APK_URL_PREFIX))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File.createTempFile("update-", ".part", directory)
        val apk = File(directory, partial.nameWithoutExtension + ".apk")
        var complete = false
        try {
            val connection = openDownload(update.apkUrl)
            try {
                val length = connection.contentLengthLong
                if (length > 0 && length != update.sizeBytes) throw IOException("Unerwartete Dateigröße")
                connection.inputStream.use { input ->
                    copyApk(input, partial, update.sizeBytes, update.sha256, onProgress)
                }
            } finally {
                connection.disconnect()
            }
            currentCoroutineContext().ensureActive()
            validateApk(partial, update)
            if (!partial.renameTo(apk)) throw IOException("Update konnte nicht gespeichert werden")
            complete = true
            apk
        } finally {
            partial.delete()
            if (!complete) apk.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, update: AvailableUpdate) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Die Datei ist keine gültige APK")
        val installed = manager.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed) ||
            ReleaseVersion.compare(candidate.versionName.orEmpty(), update.notes.versionName) != 0
        ) throw IOException("Die APK passt nicht zu diesem Update")
        val candidateSignatures = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo?.apkContentsSigners else candidate.signatures
        val installedSignatures = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        if (candidateSignatures.isNullOrEmpty() || installedSignatures.isNullOrEmpty() ||
            candidateSignatures.toSet() != installedSignatures.toSet()
        ) throw IOException("Die APK wurde mit einem anderen Schlüssel signiert")
    }

    fun installationIntent(file: File): Intent {
        require(file.isFile && file.parentFile?.canonicalFile == File(context.cacheDir, "updates").canonicalFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private suspend fun openDownload(address: String): HttpsURLConnection {
        var url = URL(address)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443))
            require(url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "VocabScan")
                when (connection.responseCode) {
                    HttpsURLConnection.HTTP_OK -> return connection
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: throw IOException("Downloadziel fehlt")
                        url = URL(url, location)
                    }
                    else -> throw IOException("Download fehlgeschlagen (HTTP ${connection.responseCode})")
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw IOException("Zu viele Download-Weiterleitungen")
    }

    companion object {
        const val MAX_APK_BYTES = 512L * 1024 * 1024
    }
}

/** Only a complete, size-checked and (when available) hash-checked download is installable. */
internal suspend fun copyApk(input: InputStream, target: File, size: Long, sha256: String?, onProgress: (Float) -> Unit) {
    require(size in 1..ApkDownloader.MAX_APK_BYTES)
    val digest = MessageDigest.getInstance("SHA-256")
    var received = 0L
    var lastPercent = -1
    target.outputStream().use { output ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            currentCoroutineContext().ensureActive()
            if (count < 0) break
            received += count
            if (received > size) throw IOException("Unerwartete Dateigröße")
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            val percent = (received * 100 / size).toInt()
            if (percent != lastPercent) {
                onProgress(received.toFloat() / size)
                lastPercent = percent
            }
        }
    }
    if (received != size) throw IOException("Download ist unvollständig")
    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
    if (sha256 != null && !actualHash.equals(sha256, ignoreCase = true)) throw IOException("Prüfsumme stimmt nicht überein")
}
