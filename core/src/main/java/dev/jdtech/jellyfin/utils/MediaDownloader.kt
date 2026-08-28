package dev.jdtech.jellyfin.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpStatusException(val code: Int) : IOException("Server returned $code")

@Singleton
class MediaDownloader @Inject constructor() {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    suspend fun download(url: String, target: File, onProgress: suspend (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()

            // A partial file from an earlier attempt is continued instead of fetched again.
            val alreadyDownloaded = if (target.exists()) target.length() else 0L
            val request =
                Request.Builder()
                    .url(url)
                    .apply {
                        if (alreadyDownloaded > 0) {
                            header("Range", "bytes=$alreadyDownloaded-")
                        }
                    }
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException(response.code)
                }

                val resuming = response.code == 206 && alreadyDownloaded > 0
                val bodyLength = response.body.contentLength()
                val bytesTotal =
                    if (bodyLength > 0) bodyLength + (if (resuming) alreadyDownloaded else 0) else 0

                var bytesDownloaded = if (resuming) alreadyDownloaded else 0
                onProgress(bytesDownloaded, bytesTotal)

                response.body.byteStream().use { input ->
                    FileOutputStream(target, resuming).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            onProgress(bytesDownloaded, bytesTotal)
                        }
                        output.flush()
                    }
                }
            }
        }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
