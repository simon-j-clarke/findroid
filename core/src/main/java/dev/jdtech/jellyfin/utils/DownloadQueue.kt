package dev.jdtech.jellyfin.utils

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
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.DownloadQueueWorker
import java.io.IOException
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
                val existing = database.getDownloadQueueEntry(item.id)
                if (existing != null) {
                    if (existing.state == DownloadState.FAILED) {
                        database.updateDownloadQueueEntry(existing.toQueued())
                    }
                    continue
                }
                val sourceId = item.sources.firstOrNull()?.id ?: continue
                database.insertDownloadQueueEntry(
                    DownloadQueueEntryDto(
                        itemId = item.id,
                        sourceId = sourceId,
                        name = item.name,
                        seriesName = (item as? FindroidEpisode)?.seriesName,
                        parentIndexNumber = (item as? FindroidEpisode)?.parentIndexNumber,
                        indexNumber = (item as? FindroidEpisode)?.indexNumber,
                        storageIndex = storageIndex,
                        state = DownloadState.QUEUED,
                        queuedAt = queuedAt,
                    )
                )
            }
            start()
        }

    suspend fun cancel(item: FindroidItem) = cancel(item.id)

    suspend fun cancel(itemId: UUID) =
        withContext(Dispatchers.IO) {
            database.deleteDownloadQueueEntries(itemId)
            downloader.deleteDownload(itemId)
            start()
        }

    suspend fun retry(itemId: UUID) =
        withContext(Dispatchers.IO) {
            val entry = database.getDownloadQueueEntry(itemId) ?: return@withContext
            database.updateDownloadQueueEntry(entry.toQueued())
            start()
        }

    suspend fun retryFailed() =
        withContext(Dispatchers.IO) {
            database.retryFailedDownloadQueueEntries()
            start()
        }

    suspend fun clearFailed() =
        withContext(Dispatchers.IO) {
            for (entry in database.getDownloadQueueEntriesByState(DownloadState.FAILED)) {
                downloader.deleteDownload(entry.itemId)
            }
            database.deleteFailedDownloadQueueEntries()
        }

    suspend fun onDownloadStarted(entry: DownloadQueueEntryDto) =
        withContext(Dispatchers.IO) {
            database.updateDownloadQueueEntry(entry.copy(state = DownloadState.RUNNING))
        }

    suspend fun onProgress(itemId: UUID, bytesDownloaded: Long, bytesTotal: Long) =
        withContext(Dispatchers.IO) {
            database.setDownloadQueueProgress(itemId, bytesDownloaded, bytesTotal)
        }

    suspend fun onDownloadFinished(entry: DownloadQueueEntryDto) =
        withContext(Dispatchers.IO) {
            database.deleteDownloadQueueEntries(entry.itemId)
            start()
        }

    suspend fun onDownloadFailed(entry: DownloadQueueEntryDto, error: Throwable) =
        withContext(Dispatchers.IO) { fail(entry, errorMessage(error), isRetryable(error)) }

    suspend fun onDownloadFailedToStart(entry: DownloadQueueEntryDto, error: UiText?) =
        withContext(Dispatchers.IO) {
            fail(entry, error?.asString(context.resources), isRetryableError(error))
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
                state = DownloadState.FAILED,
                attempt = attempt,
                nextAttemptAt = retryDelay?.let { System.currentTimeMillis() + it },
                errorMessage = message,
            )
        )
        start(retryDelay ?: 0)
    }

    private fun DownloadQueueEntryDto.toQueued(): DownloadQueueEntryDto {
        return copy(
            state = DownloadState.QUEUED,
            attempt = 0,
            nextAttemptAt = null,
            errorMessage = null,
        )
    }

    // A server that is unreachable or having trouble is worth another attempt, one that rejects
    // the request is not.
    private fun isRetryable(error: Throwable): Boolean {
        val code = (error as? HttpStatusException)?.code ?: return error is IOException
        return code >= 500
    }

    private fun isRetryableError(error: UiText?): Boolean {
        val resId = (error as? UiText.StringResource)?.resId
        return resId != CoreR.string.not_enough_storage && resId != CoreR.string.storage_unavailable
    }

    private fun errorMessage(error: Throwable): String {
        return when {
            error is HttpStatusException -> context.getString(CoreR.string.download_error_server)
            error is IOException -> context.getString(CoreR.string.download_error_network)
            error.message != null -> error.message!!
            else -> context.getString(CoreR.string.unknown_error)
        }
    }

    companion object {
        const val WORK_NAME = "downloadQueue"

        private val RETRY_DELAYS = listOf(30_000L, 120_000L, 480_000L)
    }
}
