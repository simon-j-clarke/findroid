package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.UiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun progressTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1000L)
    }
}

internal fun DownloadQueueEntryDto.toDownloaderState() =
    DownloaderState(
        state = state,
        progress = if (bytesTotal > 0) bytesDownloaded.toFloat().div(bytesTotal) else 0f,
        bytesDownloaded = bytesDownloaded,
        bytesTotal = bytesTotal,
        willRetry = nextAttemptAt != null,
        errorText = errorMessage?.let { UiText.DynamicString(it) },
    )

internal fun List<DownloadQueueEntryDto>.toDownloaderState(itemsTotal: Int = 0): DownloaderState {
    val entry =
        firstOrNull { it.state == DownloadState.RUNNING }
            ?: firstOrNull { it.state == DownloadState.QUEUED }
            ?: firstOrNull { it.state == DownloadState.FAILED }
            ?: return DownloaderState()

    // A download of several items is followed by how many are left, not by the bytes of whichever
    // one happens to be downloading.
    return entry.toDownloaderState().copy(itemsRemaining = size, itemsTotal = itemsTotal)
}
