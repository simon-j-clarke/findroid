package dev.jdtech.jellyfin.work

import android.app.DownloadManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.DownloadQueue
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadQueueWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val repository: JellyfinRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            if (hasRunningDownload()) {
                return@withContext Result.success()
            }

            val entry =
                database.getNextDownloadQueueEntry(System.currentTimeMillis())
                    ?: return@withContext Result.success()

            startDownload(entry)
            Result.success()
        }

    // Downloads outlive the app, so an entry left running by a previous process is only really
    // running when the download manager still knows about it.
    private suspend fun hasRunningDownload(): Boolean {
        for (entry in database.getDownloadQueueEntriesByState(DownloadState.RUNNING)) {
            val downloadId = entry.downloadId
            if (downloadId == null) {
                database.updateDownloadQueueEntry(entry.copy(state = DownloadState.QUEUED))
                continue
            }

            val (status, reason) = downloader.getDownloadStatus(downloadId)
            when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> return true
                else -> downloadQueue.onDownloadFinished(downloadId, status, reason)
            }
        }
        return false
    }

    private suspend fun startDownload(entry: DownloadQueueEntryDto) {
        val item = repository.getItem(entry.itemId)
        if (item == null) {
            downloadQueue.onDownloadFailedToStart(
                entry,
                UiText.StringResource(CoreR.string.unknown_error),
            )
            return
        }

        val (downloadId, error) =
            downloader.downloadItem(
                item = item,
                sourceId = entry.sourceId,
                storageIndex = entry.storageIndex,
            )

        if (downloadId == -1L) {
            downloadQueue.onDownloadFailedToStart(entry, error)
        } else {
            downloadQueue.onDownloadStarted(entry, downloadId)
        }
    }
}
