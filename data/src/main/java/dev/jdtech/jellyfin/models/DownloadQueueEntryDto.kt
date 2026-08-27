package dev.jdtech.jellyfin.models

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "downloadqueue", primaryKeys = ["itemId", "sourceId"])
data class DownloadQueueEntryDto(
    val itemId: UUID,
    val sourceId: String,
    val name: String,
    val storageIndex: Int,
    val state: DownloadState,
    val queuedAt: Long,
    val downloadId: Long? = null,
    val attempt: Int = 0,
    val nextAttemptAt: Long? = null,
    val errorMessage: String? = null,
)

enum class DownloadState {
    QUEUED,
    RUNNING,
    FAILED,
}
