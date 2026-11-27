package de.vocabscan.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ApkDownloadTest {
    @Test fun completeDownloadReportsProgressAndVerifiesHash() = runBlocking {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val target = File.createTempFile("vocabscan-test", ".apk")
        val progress = mutableListOf<Float>()
        try {
            copyApk(data.inputStream(), target, data.size.toLong(), hash, progress::add)
            assertArrayEquals(data, target.readBytes())
            assertEquals(1f, progress.last())
            assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        } finally { target.delete() }
    }

    @Test fun truncatedOversizedAndTamperedDownloadsAreRejected() = runBlocking {
        val target = File.createTempFile("vocabscan-test", ".apk")
        try {
            for ((data, size, hash) in listOf(
                Triple(byteArrayOf(1, 2), 3L, null),
                Triple(byteArrayOf(1, 2, 3), 2L, null),
                Triple(byteArrayOf(1, 2), 2L, "0".repeat(64)),
            )) {
                assertTrue(runCatching { copyApk(data.inputStream(), target, size, hash) {} }.exceptionOrNull() is IOException)
            }
        } finally { target.delete() }
    }

    @Test fun cancelledDownloadDoesNotBecomeComplete() = runBlocking {
        val target = File.createTempFile("vocabscan-test", ".apk")
        try {
            val result = runCatching {
                copyApk(ByteArray(100_000).inputStream(), target, 100_000, null) { throw CancellationException() }
            }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(target.length() < 100_000)
        } finally { target.delete() }
    }
}
