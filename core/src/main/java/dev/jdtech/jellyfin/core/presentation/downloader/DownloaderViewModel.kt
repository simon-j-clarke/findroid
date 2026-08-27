package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.utils.DownloadQueue
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel
@Inject
constructor(private val downloader: Downloader, private val downloadQueue: DownloadQueue) :
    ViewModel() {
    private val trackedItems = MutableStateFlow<List<FindroidItem>>(emptyList())

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    val state: StateFlow<DownloaderState> =
        combine(trackedItems, downloadQueue.getQueue(), progressTicker()) { items, queue, _ ->
                toState(queue.filter { entry -> items.any { it.id == entry.itemId } })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DownloaderState())

    init {
        viewModelScope.launch {
            combine(trackedItems, downloadQueue.getQueue()) { items, queue ->
                    queue.count { entry -> items.any { it.id == entry.itemId } }
                }
                .distinctUntilChanged()
                .drop(1)
                .collect { remaining ->
                    if (remaining == 0) {
                        eventsChannel.send(DownloaderEvent.Successful)
                    }
                }
        }
    }

    fun track(items: List<FindroidItem>) {
        trackedItems.value = items
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.items, action.storageIndex)
            is DownloaderAction.CancelDownload -> cancelDownload(action.items)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.items)
        }
    }

    private fun download(items: List<FindroidItem>, storageIndex: Int) {
        viewModelScope.launch { downloadQueue.enqueue(items, storageIndex) }
    }

    private fun cancelDownload(items: List<FindroidItem>) {
        viewModelScope.launch {
            for (item in items) {
                downloadQueue.cancel(item)
            }
            eventsChannel.send(DownloaderEvent.Successful)
        }
    }

    private fun deleteDownload(items: List<FindroidItem>) {
        viewModelScope.launch {
            for (item in items.filter { it.isDownloaded() }) {
                downloader.deleteItem(
                    item = item,
                    source = item.sources.first { it.type == FindroidSourceType.LOCAL },
                )
            }
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private suspend fun toState(entries: List<DownloadQueueEntryDto>): DownloaderState {
        val entry =
            entries.firstOrNull { it.state == DownloadState.RUNNING }
                ?: entries.firstOrNull { it.state == DownloadState.QUEUED }
                ?: entries.firstOrNull { it.state == DownloadState.FAILED }
                ?: return DownloaderState()
        return entry.toDownloaderState(downloader)
    }
}
