package app.allfun.data.update

import android.content.Context
import app.allfun.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface DownloadEvent {
    data class Running(val bytes: Long, val total: Long) : DownloadEvent
    data class Completed(val file: File) : DownloadEvent
}

class MissingChecksumException :
    Exception("This release does not publish a SHA-256 checksum, so the download cannot be verified.")

class ChecksumMismatchException(expected: String, actual: String) :
    Exception("The file's SHA-256 is $actual, but the release says $expected. The download was discarded.")

/**
 * Streams the release APK into the cache and hashes it in the same pass. Nothing is kept
 * unless the digest matches the one published with the release.
 */
class UpdateDownloader(
    private val context: Context,
    private val http: Http
) {

    fun download(release: ReleaseInfo): Flow<DownloadEvent> = flow {
        val expected = release.checksumSha256
            ?.lowercase()
            ?.takeIf { it.length == 64 }
            ?: throw MissingChecksumException()

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "AllFun-${release.versionName}.apk")
        val partial = File(dir, "${target.name}.part")
        partial.delete()

        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "AllFun-Android")
            .build()
        val call = http.client.newCall(request)
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} while downloading the update.")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                var lastEmit = 0L

                FileOutputStream(partial).use { output ->
                    body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 100) {
                                emit(DownloadEvent.Running(bytes, total))
                                lastEmit = now
                            }
                        }
                    }
                }
                emit(DownloadEvent.Running(bytes, total))

                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    partial.delete()
                    throw ChecksumMismatchException(expected, actual)
                }

                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    throw IOException("Could not finish writing the downloaded update.")
                }
                emit(DownloadEvent.Completed(target))
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }.flowOn(Dispatchers.IO)
}
