package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import java.util.UUID

data class DownloadQueueState(val items: List<DownloadQueueItem> = emptyList()) {
    val hasFailed: Boolean
        get() = items.any { it.downloaderState.status == DownloadManager.STATUS_FAILED }
}

data class DownloadQueueItem(
    val itemId: UUID,
    val name: String,
    val downloaderState: DownloaderState,
)
