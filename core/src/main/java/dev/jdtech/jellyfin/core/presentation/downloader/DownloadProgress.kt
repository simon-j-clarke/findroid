package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun progressTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1000L)
    }
}

// The queue knows which item is downloading, the progress of that download is owned by the
// download manager.
internal suspend fun DownloadQueueEntryDto.toDownloaderState(downloader: Downloader) =
    when (state) {
        DownloadState.RUNNING -> {
            val (status, progress) = downloader.getProgress(downloadId)
            DownloaderState(status = status, progress = progress.coerceAtLeast(0).div(100f))
        }
        DownloadState.QUEUED -> DownloaderState(status = DownloadManager.STATUS_PENDING)
        DownloadState.FAILED ->
            DownloaderState(
                status = DownloadManager.STATUS_FAILED,
                errorText = errorMessage?.let { UiText.DynamicString(it) },
            )
    }
