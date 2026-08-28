package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.models.DownloadState
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlin.math.roundToInt

@Composable
fun DownloaderCard(
    state: DownloaderState,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    val animatedProgress by
        animateFloatAsState(
            targetValue = state.progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )

    val failed = state.state == DownloadState.FAILED

    val textColor =
        when {
            failed && !state.willRetry -> MaterialTheme.colorScheme.error
            failed -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        }

    val statusText =
        when {
            state.state == DownloadState.QUEUED -> stringResource(CoreR.string.download_pending)
            failed && state.willRetry -> stringResource(CoreR.string.download_paused_retry)
            failed -> stringResource(CoreR.string.download_failed)
            else -> stringResource(CoreR.string.download_downloading)
        }

    val progressIndicatorColor =
        when {
            failed && !state.willRetry -> MaterialTheme.colorScheme.error
            failed -> MaterialTheme.colorScheme.tertiary
            else -> ProgressIndicatorDefaults.linearColor
        }

    val progressTrackColor =
        when {
            failed && !state.willRetry -> MaterialTheme.colorScheme.errorContainer
            else -> ProgressIndicatorDefaults.linearTrackColor
        }

    val cardModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    OutlinedCard(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (title != null || subtitle != null) {
                    Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text =
                            when {
                                state.itemsTotal > 1 ->
                                    "${state.itemsTotal - state.itemsRemaining}/${state.itemsTotal}"
                                state.itemsRemaining > 1 ->
                                    stringResource(
                                        CoreR.string.download_items_remaining,
                                        state.itemsRemaining,
                                    )
                                state.bytesTotal > 0 ->
                                    animatedProgress.times(100).roundToInt().toString() + "%"
                                else -> Formatter.formatShortFileSize(context, state.bytesDownloaded)
                            },
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                // A server that reports no size leaves the download manager without a total, so
                // there is no percentage to show.
                when {
                    // A queued item is not doing anything yet, so its bar does not animate.
                    state.state == DownloadState.QUEUED -> {
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                    state.itemsTotal > 1 -> {
                        LinearProgressIndicator(
                            progress = {
                                (state.itemsTotal - state.itemsRemaining).toFloat() /
                                    state.itemsTotal
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                    state.bytesTotal == 0L -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                if (state.errorText != null) {
                    Text(
                        text = state.errorText!!.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                when {
                    state.state == DownloadState.QUEUED ||
                        state.state == DownloadState.RUNNING -> {
                        FilledTonalIconButton(onClick = onCancelClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                        }
                    }
                    state.state == DownloadState.FAILED -> {
                        FilledTonalIconButton(onClick = onRetryClick) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun DownloaderCardPendingPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(state = DownloadState.QUEUED),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardDownloadingPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    state = DownloadState.RUNNING,
                    progress = 0.5f,
                    bytesDownloaded = 500,
                    bytesTotal = 1000,
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardFailedPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    state = DownloadState.FAILED,
                    errorText = UiText.DynamicString("Not enough storage space"),
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
