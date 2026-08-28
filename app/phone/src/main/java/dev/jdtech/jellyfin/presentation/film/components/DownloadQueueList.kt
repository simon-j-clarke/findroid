package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueItem
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

@Composable
fun DownloadQueueList(
    items: List<DownloadQueueItem>,
    onAction: (DownloadQueueAction) -> Unit,
    onItemClick: (itemId: UUID, isEpisode: Boolean) -> Unit,
) {
    if (items.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        for (item in items) {
            val seasonNumber = item.parentIndexNumber
            val episodeNumber = item.indexNumber
            val episodeLabel =
                if (seasonNumber != null && episodeNumber != null) {
                    stringResource(
                        CoreR.string.episode_name_extended,
                        seasonNumber,
                        episodeNumber,
                        item.name,
                    )
                } else {
                    item.name
                }

            DownloaderCard(
                state = item.downloaderState,
                onCancelClick = { onAction(DownloadQueueAction.Cancel(item.itemId)) },
                onRetryClick = { onAction(DownloadQueueAction.Retry(item.itemId)) },
                title = if (item.isEpisode) item.seriesName else item.name,
                subtitle = if (item.isEpisode) episodeLabel else null,
                onClick = { onItemClick(item.itemId, item.isEpisode) },
            )
        }
    }
}
