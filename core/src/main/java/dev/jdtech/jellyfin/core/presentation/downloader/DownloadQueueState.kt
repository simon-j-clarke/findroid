package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.DownloadState
import java.util.UUID

data class DownloadQueueState(val items: List<DownloadQueueItem> = emptyList()) {
    val hasFailed: Boolean
        get() = items.any { it.downloaderState.state == DownloadState.FAILED }
}

data class DownloadQueueItem(
    val itemId: UUID,
    val name: String,
    val seriesName: String?,
    val parentIndexNumber: Int?,
    val indexNumber: Int?,
    val downloaderState: DownloaderState,
) {
    val isEpisode: Boolean
        get() = seriesName != null
}
