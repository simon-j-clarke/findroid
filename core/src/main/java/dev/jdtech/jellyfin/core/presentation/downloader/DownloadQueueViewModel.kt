package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.utils.DownloadQueue
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadQueueViewModel
@Inject
constructor(private val downloadQueue: DownloadQueue) : ViewModel() {
    val state: StateFlow<DownloadQueueState> =
        combine(downloadQueue.getQueue(), progressTicker()) { queue, _ ->
                DownloadQueueState(items = queue.map { entry -> entry.toItem() })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DownloadQueueState())

    fun onAction(action: DownloadQueueAction) {
        when (action) {
            is DownloadQueueAction.Cancel -> cancel(action.itemId)
            is DownloadQueueAction.Retry -> retry(action.itemId)
            is DownloadQueueAction.RetryAllFailed -> retryAllFailed()
            is DownloadQueueAction.ClearFailed -> clearFailed()
        }
    }

    private fun cancel(itemId: UUID) {
        viewModelScope.launch { downloadQueue.cancel(itemId) }
    }

    private fun retry(itemId: UUID) {
        viewModelScope.launch { downloadQueue.retry(itemId) }
    }

    private fun retryAllFailed() {
        viewModelScope.launch { downloadQueue.retryFailed() }
    }

    private fun clearFailed() {
        viewModelScope.launch { downloadQueue.clearFailed() }
    }

    private fun DownloadQueueEntryDto.toItem(): DownloadQueueItem {
        return DownloadQueueItem(
            itemId = itemId,
            name = name,
            seriesName = seriesName,
            parentIndexNumber = parentIndexNumber,
            indexNumber = indexNumber,
            downloaderState = toDownloaderState(),
        )
    }
}
