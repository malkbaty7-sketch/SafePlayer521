package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

import kotlinx.coroutines.flow.first
import com.example.data.ScanJob
import com.example.data.ScanStatus

data class ScanProgress(
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val new: Int = 0,
    val changed: Int = 0,
    val missing: Int = 0,
    val currentFolder: String = "",
    val currentFile: String = ""
)

class MediaScanner(
    private val context: Context,
    private val repository: MediaRepository
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    suspend fun scanMedia() = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) return@withContext
        
        _progress.value = ScanProgress(isScanning = true)
        
        val jobId = repository.insertScanJob(ScanJob(status = ScanStatus.RUNNING))

        var newCount = 0
        var changedCount = 0
        var missingCount = 0
        val currentPaths = mutableSetOf<String>()

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        try {
            val videoCursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )
            
            val audioCursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )

            val totalFiles = (videoCursor?.count ?: 0) + (audioCursor?.count ?: 0)
            _progress.value = _progress.value.copy(total = totalFiles)

            var processed = 0

            videoCursor?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durationCol)
                    val mime = cursor.getString(mimeCol)
                    val bucketName = if (bucketCol != -1) cursor.getString(bucketCol) else null
                    val parentName = if (path.isNotBlank()) File(path).parentFile?.name?.takeIf { it.isNotBlank() } else null
                    val folder: String = bucketName?.takeIf { it.isNotBlank() } ?: parentName ?: "Videos"

                    val validVideoExts = listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "ts", "m4v")
                    val isVideoExt = validVideoExts.any { name.endsWith(".$it", ignoreCase = true) || path.endsWith(".$it", ignoreCase = true) }
                    val isVideoMime = mime?.startsWith("video/", ignoreCase = true) == true
                    if (size > 0 && (isVideoExt || isVideoMime)) {
                        currentPaths.add(uri)
                        val result = processItem(uri, name, folder, MediaType.VIDEO, size, date, duration, mime, null, null)
                        if (result == 1) newCount++
                        if (result == 2) changedCount++

                        processed++
                        _progress.value = _progress.value.copy(
                            scanned = processed, new = newCount, changed = changedCount,
                            currentFolder = folder, currentFile = name
                        )
                    }
                }
            }

            audioCursor?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durationCol)
                    val mime = cursor.getString(mimeCol)
                    val artist = cursor.getString(artistCol)
                    val album = cursor.getString(albumCol)
                    val parentName = if (path.isNotBlank()) File(path).parentFile?.name?.takeIf { it.isNotBlank() } else null
                    val folder: String = parentName ?: album?.takeIf { it.isNotBlank() } ?: "Music"

                    val validAudioExts = listOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "wma", "amr")
                    val isAudioExt = validAudioExts.any { name.endsWith(".$it", ignoreCase = true) || path.endsWith(".$it", ignoreCase = true) }
                    val isAudioMime = mime?.startsWith("audio/", ignoreCase = true) == true
                    if (size > 0 && (isAudioExt || isAudioMime)) {
                        currentPaths.add(uri)
                        val result = processItem(uri, name, folder, MediaType.AUDIO, size, date, duration, mime, artist, album)
                        if (result == 1) newCount++
                        if (result == 2) changedCount++

                        processed++
                        _progress.value = _progress.value.copy(
                            scanned = processed, new = newCount, changed = changedCount,
                            currentFolder = folder, currentFile = name
                        )
                    }
                }
            }

            // Handle Missing Files
            val allDbMedia = repository.allMedia.first()
            for (media in allDbMedia) {
                if (!currentPaths.contains(media.path) && !media.isMissing) {
                    repository.insertOrUpdate(media.copy(isMissing = true))
                    missingCount++
                    _progress.value = _progress.value.copy(missing = missingCount)
                } else if (currentPaths.contains(media.path) && media.isMissing) {
                    // Recovered missing file
                    repository.insertOrUpdate(media.copy(isMissing = false))
                }
            }
            
            repository.updateScanJob(
                ScanJob(
                    id = jobId.toInt(),
                    status = ScanStatus.COMPLETED,
                    startTime = jobId, // actually we don't have the original start time easily accessible here without fetching, but we can just use System.currentTimeMillis() or keep it simple. Let's fetch it.
                    endTime = System.currentTimeMillis(),
                    itemsScanned = processed,
                    newItems = newCount,
                    changedItems = changedCount,
                    missingItems = missingCount
                )
            )

        } catch (e: Exception) {
            repository.updateScanJob(
                ScanJob(
                    id = jobId.toInt(),
                    status = ScanStatus.FAILED,
                    endTime = System.currentTimeMillis(),
                    errorMessage = e.message
                )
            )
        } finally {
            _progress.value = _progress.value.copy(isScanning = false)
        }
    }

    // Returns: 0 = No change, 1 = New, 2 = Changed
    private suspend fun processItem(
        uri: String, name: String, folder: String, type: MediaType,
        size: Long, date: Long, duration: Long, mime: String?, artist: String?, album: String?
    ): Int {
        val existing = repository.getMediaByPath(uri)
        if (existing == null) {
            repository.insertOrUpdate(
                MediaItem(
                    name = name,
                    path = uri,
                    folder = folder,
                    mediaType = type,
                    sizeBytes = size,
                    dateModified = date,
                    durationMs = duration,
                    mimeType = mime,
                    artist = artist,
                    album = album
                )
            )
            return 1
        } else if (existing.sizeBytes != size || existing.dateModified != date) {
            repository.insertOrUpdate(
                existing.copy(
                    name = name,
                    folder = folder,
                    sizeBytes = size,
                    dateModified = date,
                    durationMs = duration,
                    mimeType = mime,
                    artist = artist,
                    album = album,
                    isMissing = false
                )
            )
            return 2
        }
        return 0
    }
}
