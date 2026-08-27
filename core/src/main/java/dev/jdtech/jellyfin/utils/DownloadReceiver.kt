package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var database: ServerDatabaseDao

    @Inject lateinit var downloader: Downloader

    @Inject lateinit var repository: JellyfinRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.DOWNLOAD_COMPLETE") {
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onDownloadComplete(downloadId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // A completed download is not necessarily a successful one, renaming the partial file of a
    // failed download would mark it as fully downloaded.
    private suspend fun onDownloadComplete(downloadId: Long) {
        val (status, _) = downloader.getDownloadStatus(downloadId)
        val successful = status == DownloadManager.STATUS_SUCCESSFUL

        val source = database.getSourceByDownloadId(downloadId)
        if (source != null) {
            val path = source.path.replace(".download", "")
            if (successful && File(source.path).renameTo(File(path))) {
                database.setSourcePath(source.id, path)
            } else {
                val item = getDownloadedItem(source.itemId)
                if (item != null) {
                    downloader.deleteItem(item, source.toFindroidSource(database))
                } else {
                    File(source.path).delete()
                    database.deleteSource(source.id)
                }
            }
            return
        }

        val mediaStream = database.getMediaStreamByDownloadId(downloadId) ?: return
        val path = mediaStream.path.replace(".download", "")
        if (successful && File(mediaStream.path).renameTo(File(path))) {
            database.setMediaStreamPath(mediaStream.id, path)
        } else {
            File(mediaStream.path).delete()
            database.deleteMediaStream(mediaStream.id)
        }
    }

    private fun getDownloadedItem(itemId: UUID): FindroidItem? {
        val userId = repository.getUserId()
        val movie = database.getMovies().firstOrNull { it.id == itemId }
        if (movie != null) {
            return movie.toFindroidMovie(database, userId)
        }
        return database.getEpisodes()
            .firstOrNull { it.id == itemId }
            ?.toFindroidEpisode(database, userId)
    }
}
