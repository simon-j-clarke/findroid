package dev.jdtech.jellyfin.core.presentation.downloader

import java.util.UUID

sealed interface DownloadQueueAction {
    data class Cancel(val itemId: UUID) : DownloadQueueAction

    data class Retry(val itemId: UUID) : DownloadQueueAction

    data object RetryAllFailed : DownloadQueueAction

    data object ClearFailed : DownloadQueueAction
}
