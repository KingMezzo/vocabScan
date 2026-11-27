package de.vocabscan.update

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ApkInstallationTest {
    @Test fun installerReceivesPrivateApkThroughReadOnlyContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File.createTempFile("installer-test-", ".apk", directory)
        try {
            apk.writeBytes(byteArrayOf(1, 2, 3))
            val intent = ApkDownloader(context).installationIntent(apk)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.fileprovider", intent.data?.authority)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
            context.contentResolver.openInputStream(requireNotNull(intent.data)).use {
                assertArrayEquals(apk.readBytes(), requireNotNull(it).readBytes())
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.REQUEST_INSTALL_PACKAGES))
        } finally { apk.delete() }
    }

    @Test fun filesOutsideUpdateDirectoryCannotBeInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("unshared-test-", ".apk", context.cacheDir)
        try {
            assertTrue(runCatching { ApkDownloader(context).installationIntent(file) }.isFailure)
        } finally { file.delete() }
    }
}
