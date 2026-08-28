package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.UiText

data class DownloaderState(
    val state: DownloadState? = null,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val itemsRemaining: Int = 0,
    val itemsTotal: Int = 0,
    val willRetry: Boolean = false,
    val errorText: UiText? = null,
) {
    val isDownloading: Boolean
        get() = state != null
}
