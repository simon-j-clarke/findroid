package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueueViewModel
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.CollectionGrid
import dev.jdtech.jellyfin.presentation.film.components.DownloadQueueList
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.UUID

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onQueuedItemClick: (itemId: UUID, isEpisode: Boolean) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
    queueViewModel: DownloadQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueState by queueViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadItems() }

    // Items leave the queue as they finish, which is when they belong in the list below.
    LaunchedEffect(queueState.items.size) { viewModel.loadItems() }

    DownloadsScreenLayout(
        state = state,
        queueState = queueState,
        onAction = { action ->
            when (action) {
                is CollectionAction.OnItemClick -> onItemClick(action.item)
                is CollectionAction.OnBackClick -> Unit
            }
        },
        onQueueAction = { action -> queueViewModel.onAction(action) },
        onQueuedItemClick = onQueuedItemClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: CollectionState,
    queueState: DownloadQueueState,
    onAction: (CollectionAction) -> Unit,
    onQueueAction: (DownloadQueueAction) -> Unit,
    onQueuedItemClick: (itemId: UUID, isEpisode: Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_download)) },
                actions = {
                    if (queueState.hasFailed) {
                        IconButton(onClick = { onQueueAction(DownloadQueueAction.RetryAllFailed) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription =
                                    stringResource(CoreR.string.download_retry_all),
                            )
                        }
                        IconButton(onClick = { onQueueAction(DownloadQueueAction.ClearFailed) }) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.sections.isEmpty() && queueState.items.isEmpty()) {
                Text(
                    text = stringResource(CoreR.string.no_downloads),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        CollectionGrid(
            sections = state.sections,
            innerPadding = innerPadding,
            onAction = onAction,
            header = {
                DownloadQueueList(
                    items = queueState.items,
                    onAction = onQueueAction,
                    onItemClick = onQueuedItemClick,
                )
            },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                CollectionState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = 0,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            )
                        )
                ),
            queueState = DownloadQueueState(),
            onAction = {},
            onQueueAction = {},
            onQueuedItemClick = { _, _ -> },
        )
    }
}
