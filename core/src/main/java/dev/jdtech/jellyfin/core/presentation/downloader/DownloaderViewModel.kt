package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQueueEntryDto
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.DownloadQueue
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFields

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloaderViewModel
@Inject
constructor(
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val repository: JellyfinRepository,
) : ViewModel() {
    private val trackedItems = MutableStateFlow<List<FindroidItem>>(emptyList())
    private val trackedSeriesId = MutableStateFlow<UUID?>(null)

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    private val downloadedInSeries =
        trackedSeriesId.flatMapLatest { seriesId ->
            if (seriesId == null) flowOf(0) else downloadQueue.getDownloadedEpisodeCount(seriesId)
        }

    val state: StateFlow<DownloaderState> =
        combine(
                trackedItems,
                trackedSeriesId,
                downloadedInSeries,
                downloadQueue.getQueue(),
                progressTicker(),
            ) { items, seriesId, downloaded, queue, _ ->
                val entries = queue.filter { entry -> entry.isTracked(items, seriesId) }
                val alreadyDownloaded =
                    if (seriesId != null) downloaded else items.count { it.isDownloaded() }
                entries.toDownloaderState(itemsTotal = alreadyDownloaded + entries.size)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DownloaderState())

    init {
        viewModelScope.launch {
            combine(trackedItems, trackedSeriesId, downloadQueue.getQueue()) { items, seriesId, queue
                    ->
                    queue.count { entry -> entry.isTracked(items, seriesId) }
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

    fun trackSeries(seriesId: UUID) {
        trackedSeriesId.value = seriesId
    }

    private fun DownloadQueueEntryDto.isTracked(
        items: List<FindroidItem>,
        seriesId: UUID?,
    ): Boolean {
        return items.any { it.id == itemId } || (seriesId != null && this.seriesId == seriesId)
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.items, action.storageIndex)
            is DownloaderAction.DownloadShow -> downloadShow(action.seriesId, action.storageIndex)
            is DownloaderAction.CancelDownload -> cancelDownload(action.items)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.items)
        }
    }

    private fun download(items: List<FindroidItem>, storageIndex: Int) {
        viewModelScope.launch { downloadQueue.enqueue(items, storageIndex) }
    }

    private fun downloadShow(seriesId: UUID, storageIndex: Int) {
        viewModelScope.launch {
            val episodes =
                repository.getSeasons(seriesId).flatMap { season ->
                    repository.getEpisodes(
                        seriesId = seriesId,
                        seasonId = season.id,
                        fields = listOf(ItemFields.CAN_DOWNLOAD, ItemFields.MEDIA_SOURCES),
                    )
                }
            downloadQueue.enqueue(episodes.filter { it.canDownload }, storageIndex)
        }
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
}
