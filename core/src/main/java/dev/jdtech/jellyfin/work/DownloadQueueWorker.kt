package dev.jdtech.jellyfin.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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
import dev.jdtech.jellyfin.utils.MediaDownloader
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class DownloadQueueWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val mediaDownloader: MediaDownloader,
    private val repository: JellyfinRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            // An entry left running by a process that died never finished, so it is picked up
            // again from the part that was written.
            for (entry in database.getDownloadQueueEntriesByState(DownloadState.RUNNING)) {
                database.updateDownloadQueueEntry(entry.copy(state = DownloadState.QUEUED))
            }

            while (true) {
                val entry = database.getNextDownloadQueueEntry(System.currentTimeMillis()) ?: break
                download(entry)
            }

            Result.success()
        }

    private suspend fun download(entry: DownloadQueueEntryDto) {
        val item = repository.getItem(entry.itemId)
        if (item == null) {
            downloadQueue.onDownloadFailedToStart(
                entry,
                UiText.StringResource(CoreR.string.unknown_error),
            )
            return
        }

        val (prepared, error) =
            downloader.prepareDownload(
                item = item,
                sourceId = entry.sourceId,
                storageIndex = entry.storageIndex,
            )
        if (prepared == null) {
            downloadQueue.onDownloadFailedToStart(entry, error)
            return
        }

        downloadQueue.onDownloadStarted(entry)
        setForeground(foregroundInfo(entry.seriesName ?: entry.name))

        try {
            var lastUpdate = 0L
            mediaDownloader.download(prepared.url, File(prepared.path), prepared.resumable) {
                downloaded,
                total ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= PROGRESS_INTERVAL) {
                    lastUpdate = now
                    downloadQueue.onProgress(entry.itemId, downloaded, total)
                }
            }
            downloader.completeDownload(entry.sourceId, prepared.path)
            downloadQueue.onDownloadFinished(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            downloadQueue.onDownloadFailed(entry, e, retryable = prepared.resumable)
        }
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(CoreR.string.title_downloading),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(appContext.getString(CoreR.string.title_downloading))
                .setContentText(title)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setOngoing(true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1
        const val PROGRESS_INTERVAL = 1000L
    }
}
