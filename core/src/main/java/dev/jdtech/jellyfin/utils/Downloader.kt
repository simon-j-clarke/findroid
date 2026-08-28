package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import java.util.UUID

data class PreparedDownload(val url: String, val path: String)

interface Downloader {
    /**
     * Stores everything belonging to the item and returns where its media file has to be downloaded
     * from and to.
     */
    suspend fun prepareDownload(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<PreparedDownload?, UiText?>

    /** Turns the partial file of a finished download into the file the item plays from. */
    suspend fun completeDownload(sourceId: String, path: String): Boolean

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    suspend fun deleteDownload(itemId: UUID)

    suspend fun getDownloadedItem(itemId: UUID): FindroidItem?
}
