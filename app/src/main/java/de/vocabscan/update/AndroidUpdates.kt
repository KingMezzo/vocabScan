package de.vocabscan.update

import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import de.vocabscan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.io.IOException

class AndroidUpdates(context: Context) {
    val enabled = !BuildConfig.DEBUG
    private val context = context.applicationContext
    @Suppress("DEPRECATION")
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val installedName: String = packageInfo.versionName.orEmpty()
    val downloader = ApkDownloader(this.context)
    private val store = PreferencesUpdateStore(this.context)
    var automaticEnabled: Boolean
        get() = enabled && store.automaticEnabled
        set(value) { if (enabled) store.automaticEnabled = value }
    val checker = UpdateChecker(store, installedCode, installedName = installedName, enabled = enabled, fetch = {
        withContext(Dispatchers.IO) { fetchUpdate() }
    })

    suspend fun whatsNew(): ReleaseNotes? = withContext(Dispatchers.IO) {
        downloader.removeExpiredDownloads()
        if (!checker.needsWhatsNew(packageInfo.firstInstallTime, packageInfo.lastUpdateTime)) {
            return@withContext null
        }
        val notes = runCatching {
            context.assets.open("changelog.json").bufferedReader(Charsets.UTF_8).use {
                parseNotes(JSONObject(it.readText()))
            }
        }.getOrNull()?.takeIf { it.versionCode == installedCode }
        if (notes == null) checker.acknowledgeWhatsNew()
        notes
    }

    private fun fetchUpdate(): AvailableUpdate? {
        val connection = URL(RELEASE_URL).openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "VocabScan/$installedName")
            if (connection.responseCode == HttpsURLConnection.HTTP_NOT_FOUND) return null
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytesAtMost(MAX_METADATA_BYTES) }
                ?: throw IOException("Release-Antwort ist zu groß")
            parseUpdate(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASE_URL = "https://api.github.com/repos/KingMezzo/vocabScan/releases/latest"
        const val APK_URL_PREFIX = "https://github.com/KingMezzo/vocabScan/releases/download/"
        private const val MAX_METADATA_BYTES = 128 * 1024

        fun parseNotes(json: JSONObject): ReleaseNotes {
            val code = json.get("versionCode")
            require(code is Int || code is Long)
            val versionCode = (code as Number).toLong()
            require(versionCode > 0)
            val name = json.get("versionName")
            require(name is String && name.isNotBlank())
            val entries = json.getJSONArray("changelog")
            val changelog = List(entries.length()) { index ->
                val entry = entries.get(index)
                require(entry is String && entry.isNotBlank())
                entry
            }
            return ReleaseNotes(versionCode, name, changelog)
        }

        fun parseUpdate(text: String): AvailableUpdate? {
            val json = JSONObject(text)
            if (json.getBoolean("draft") || json.getBoolean("prerelease")) return null
            val tag = json.getString("tag_name")
            require(ReleaseVersion.compare(tag, tag) != null) { "Ungültiger Release-Tag" }
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .singleOrNull { it.optString("name") == "vocabscan.apk" && it.optString("state") == "uploaded" }
                ?: return null
            val url = apk.getString("browser_download_url")
            val uri = URI(url)
            require(url.startsWith(APK_URL_PREFIX) && uri.userInfo == null && uri.fragment == null)
            val size = apk.getLong("size")
            require(size in 1..ApkDownloader.MAX_APK_BYTES)
            val digest = apk.optString("digest").takeIf { it.isNotBlank() && it != "null" }
            require(digest == null || Regex("sha256:[a-fA-F0-9]{64}").matches(digest))
            val changelog = json.optString("body", "").takeUnless { it == "null" }.orEmpty()
                .takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
            // GitHub has no Android versionCode; the downloaded APK is checked before installation.
            val notes = ReleaseNotes(0, tag.removePrefix("v").removePrefix("V"), changelog)
            return AvailableUpdate(notes, url, size, digest?.substringAfter(':')?.lowercase())
        }
    }
}

private fun java.io.InputStream.readBytesAtMost(limit: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) return output.toByteArray()
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
}

private class PreferencesUpdateStore(context: Context) : UpdateStore {
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    override var automaticEnabled: Boolean
        get() = preferences.getBoolean("automatic_enabled", true)
        set(value) { preferences.edit { putBoolean("automatic_enabled", value) } }
    override var lastAttempt: Long
        get() = preferences.getLong("last_attempt", 0L)
        set(value) { preferences.edit(commit = true) { putLong("last_attempt", value) } }
    override var postponedAt: Long
        get() = preferences.getLong("postponed_at", 0L)
        set(value) { preferences.edit(commit = true) { putLong("postponed_at", value) } }
    override var seenVersion: Long
        get() = preferences.getLong("seen_version", 0L)
        set(value) { preferences.edit(commit = true) { putLong("seen_version", value) } }
}
