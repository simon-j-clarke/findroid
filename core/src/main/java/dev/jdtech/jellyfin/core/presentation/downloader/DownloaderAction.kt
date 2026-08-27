package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface DownloaderAction {
    data class Download(val items: List<FindroidItem>, val storageIndex: Int = 0) : DownloaderAction

    data class CancelDownload(val items: List<FindroidItem>) : DownloaderAction

    data class DeleteDownload(val items: List<FindroidItem>) : DownloaderAction
}
