package de.vocabscan.update

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateMetadataTest {
    private fun metadata() = JSONObject()
        .put("draft", false).put("prerelease", false).put("tag_name", "v1.10.0")
        .put("body", "- Neue Funktionen\n- Fehler behoben")
        .put("assets", JSONArray().put(asset()))

    private fun asset() = JSONObject().put("name", "vocabscan.apk").put("state", "uploaded")
        .put("size", 1234).put("digest", "sha256:" + "a".repeat(64))
        .put("browser_download_url", "${AndroidUpdates.APK_URL_PREFIX}v1.10.0/vocabscan.apk")

    @Test fun parsesGitHubReleaseAndAllowsEmptyNotes() {
        val json = metadata()
        val update = requireNotNull(AndroidUpdates.parseUpdate(json.toString()))
        assertEquals("1.10.0", update.notes.versionName)
        assertEquals(listOf("- Neue Funktionen\n- Fehler behoben"), update.notes.changelog)
        assertEquals(1234L, update.sizeBytes)
        assertEquals("a".repeat(64), update.sha256)
        assertTrue(requireNotNull(AndroidUpdates.parseUpdate(json.put("body", "").toString())).notes.changelog.isEmpty())
    }

    @Test fun markdownBodyKeepsBlankLinesIndentationAndDelimiters() {
        val markdown = "## Neu\n\n- **Ordner**\n  - Unterordner\n\n```kotlin\nval x = 1\n```"
        val update = requireNotNull(AndroidUpdates.parseUpdate(metadata().put("body", markdown).toString()))
        assertEquals(listOf(markdown), update.notes.changelog)
    }

    @Test fun draftsPrereleasesAndMissingApksAreIgnored() {
        assertNull(AndroidUpdates.parseUpdate(metadata().put("draft", true).toString()))
        assertNull(AndroidUpdates.parseUpdate(metadata().put("prerelease", true).toString()))
        assertNull(AndroidUpdates.parseUpdate(metadata().put("assets", JSONArray()).toString()))
        assertNull(AndroidUpdates.parseUpdate(metadata().put("assets", JSONArray().put(asset().put("state", "new"))).toString()))
    }

    @Test fun malformedReleaseAndUntrustedDownloadsAreRejected() {
        val invalid = listOf("not json", "{}", metadata().put("tag_name", "latest").toString()) +
            listOf("intent://install", "http://github.com/app.apk", "https://example.org/app.apk",
                "https://user:password@github.com/KingMezzo/vocabScan/releases/download/v1/a.apk").map { url ->
                metadata().put("assets", JSONArray().put(asset().put("browser_download_url", url))).toString()
            } + listOf(
                metadata().put("assets", JSONArray().put(asset().put("size", 0))).toString(),
                metadata().put("assets", JSONArray().put(asset().put("digest", "sha256:broken"))).toString(),
            )
        invalid.forEach { text -> assertTrue(runCatching { AndroidUpdates.parseUpdate(text) }.isFailure) }
    }

    @Test fun bundledChangelogMatchesInstalledApk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val updates = AndroidUpdates(context)
        val notes = context.assets.open("changelog.json").bufferedReader().use {
            AndroidUpdates.parseNotes(JSONObject(it.readText()))
        }
        assertEquals(updates.installedCode, notes.versionCode)
        assertEquals(updates.installedName, notes.versionName)
    }
}
