package dev.jdtech.jellyfin.presentation.film

import android.app.DownloadManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueItem
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueViewModel
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.presentation.film.components.DownloaderCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.util.UUID

@Composable
fun DownloadQueueScreen(viewModel: DownloadQueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DownloadQueueScreenLayout(state = state, onAction = { action -> viewModel.onAction(action) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadQueueScreenLayout(
    state: DownloadQueueState,
    onAction: (DownloadQueueAction) -> Unit,
) {
    val safePadding = rememberSafePadding()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_downloading)) },
                actions = {
                    if (state.hasFailed) {
                        IconButton(onClick = { onAction(DownloadQueueAction.RetryAllFailed) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription =
                                    stringResource(CoreR.string.download_retry_all),
                            )
                        }
                        IconButton(onClick = { onAction(DownloadQueueAction.ClearFailed) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription =
                                    stringResource(CoreR.string.download_clear_failed),
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = safePadding.start + MaterialTheme.spacings.default,
                    top = innerPadding.calculateTopPadding() + MaterialTheme.spacings.default,
                    end = safePadding.end + MaterialTheme.spacings.default,
                    bottom = safePadding.bottom + MaterialTheme.spacings.default,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            items(state.items, key = { item -> item.itemId }) { item ->
                DownloaderCard(
                    state = item.downloaderState,
                    onCancelClick = { onAction(DownloadQueueAction.Cancel(item.itemId)) },
                    onRetryClick = { onAction(DownloadQueueAction.Retry(item.itemId)) },
                    title = item.name,
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadQueueScreenLayoutPreview() {
    FindroidTheme {
        DownloadQueueScreenLayout(
            state =
                DownloadQueueState(
                    items =
                        listOf(
                            DownloadQueueItem(
                                itemId = UUID.randomUUID(),
                                name = "Pilot",
                                downloaderState =
                                    DownloaderState(
                                        status = DownloadManager.STATUS_RUNNING,
                                        progress = 0.4f,
                                    ),
                            )
                        )
                ),
            onAction = {},
        )
    }
}
