package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.DownloadQueueState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.DownloadQueueWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class DownloadQueue
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) {
    fun getQueue(): Flow<List<DownloadQueueEntryDto>> = database.getDownloadQueue()

    fun getQueueSize(): Flow<Int> = database.getDownloadQueueSize()

    suspend fun enqueue(items: List<FindroidItem>, storageIndex: Int) =
        withContext(Dispatchers.IO) {
            val queuedAt = System.currentTimeMillis()
            for (item in items) {
                if (item.isDownloaded()) {
                    continue
                }
                val sourceId = item.sources.firstOrNull()?.id ?: continue
                database.insertDownloadQueueEntry(
                    DownloadQueueEntryDto(
                        itemId = item.id,
                        sourceId = sourceId,
                        name = item.name,
                        storageIndex = storageIndex,
                        state = DownloadQueueState.QUEUED,
                        queuedAt = queuedAt,
                    )
                )
            }
            start()
        }

    suspend fun cancel(item: FindroidItem) =
        withContext(Dispatchers.IO) {
            val entry = database.getDownloadQueueEntry(item.id)
            database.deleteDownloadQueueEntries(item.id)
            entry?.downloadId?.let { downloadId ->
                downloader.cancelDownload(item = item, downloadId = downloadId)
            }
            start()
        }

    suspend fun retry(itemId: UUID) =
        withContext(Dispatchers.IO) {
            val entry = database.getDownloadQueueEntry(itemId) ?: return@withContext
            database.updateDownloadQueueEntry(
                entry.copy(
                    state = DownloadQueueState.QUEUED,
                    attempt = 0,
                    nextAttemptAt = null,
                    errorMessage = null,
                    downloadId = null,
                )
            )
            start()
        }

    suspend fun retryFailed() =
        withContext(Dispatchers.IO) {
            database.retryFailedDownloadQueueEntries()
            start()
        }

    suspend fun clearFailed() =
        withContext(Dispatchers.IO) { database.deleteFailedDownloadQueueEntries() }

    suspend fun onDownloadStarted(entry: DownloadQueueEntryDto, downloadId: Long) =
        withContext(Dispatchers.IO) {
            database.updateDownloadQueueEntry(
                entry.copy(state = DownloadQueueState.RUNNING, downloadId = downloadId)
            )
        }

    suspend fun onDownloadFinished(downloadId: Long, status: Int, reason: Int) =
        withContext(Dispatchers.IO) {
            val entry = database.getDownloadQueueEntryByDownloadId(downloadId) ?: return@withContext
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                database.deleteDownloadQueueEntries(entry.itemId)
                start()
            } else {
                fail(entry, errorMessage(reason), isRetryable(reason))
            }
        }

    suspend fun onDownloadFailedToStart(entry: DownloadQueueEntryDto, error: UiText?) =
        withContext(Dispatchers.IO) {
            val message = error?.asString(context.resources)
            fail(entry, message, isRetryableError(error))
        }

    fun start(delayMillis: Long = 0) {
        val networkType =
            if (appPreferences.getValue(appPreferences.downloadOverMobileData)) {
                NetworkType.CONNECTED
            } else {
                NetworkType.UNMETERED
            }
        val request =
            OneTimeWorkRequestBuilder<DownloadQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = WORK_NAME,
            existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            request = request,
        )
    }

    private fun fail(entry: DownloadQueueEntryDto, message: String?, retryable: Boolean) {
        val attempt = entry.attempt + 1
        val retryDelay = if (retryable) RETRY_DELAYS.getOrNull(attempt - 1) else null

        database.updateDownloadQueueEntry(
            entry.copy(
                state = DownloadQueueState.FAILED,
                downloadId = null,
                attempt = attempt,
                nextAttemptAt = retryDelay?.let { System.currentTimeMillis() + it },
                errorMessage = message,
            )
        )
        start(retryDelay ?: 0)
    }

    private fun isRetryable(reason: Int): Boolean {
        return reason in RETRYABLE_REASONS || reason in 500..599
    }

    // Failing to start is retryable unless the storage itself is the problem.
    private fun isRetryableError(error: UiText?): Boolean {
        val resId = (error as? UiText.StringResource)?.resId
        return resId != CoreR.string.not_enough_storage && resId != CoreR.string.storage_unavailable
    }

    private fun errorMessage(reason: Int): String {
        val resId =
            when (reason) {
                DownloadManager.ERROR_INSUFFICIENT_SPACE -> CoreR.string.download_error_storage
                DownloadManager.ERROR_DEVICE_NOT_FOUND -> CoreR.string.storage_unavailable
                in RETRYABLE_REASONS -> CoreR.string.download_error_network
                else -> CoreR.string.unknown_error
            }
        return context.getString(resId)
    }

    companion object {
        const val WORK_NAME = "downloadQueue"

        private val RETRY_DELAYS = listOf(30_000L, 120_000L, 480_000L)

        private val RETRYABLE_REASONS =
            listOf(
                DownloadManager.ERROR_UNKNOWN,
                DownloadManager.ERROR_HTTP_DATA_ERROR,
                DownloadManager.ERROR_TOO_MANY_REDIRECTS,
                DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
                DownloadManager.ERROR_CANNOT_RESUME,
            )
    }
}
