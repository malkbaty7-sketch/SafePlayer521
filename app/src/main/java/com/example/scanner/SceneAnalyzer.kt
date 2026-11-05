package com.example.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.Scene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

data class SceneScanProgress(
    val mediaId: Int = -1,
    val isScanning: Boolean = false,
    val totalDurationMs: Long = 0,
    val processedMs: Long = 0,
    val scenesFound: Int = 0,
    val status: String = "IDLE", // "PREPARING", "SCANNING", "SAVING", "COMPLETED", "FAILED", "CANCELLED"
    val errorMessage: String? = null
)

class SceneAnalyzer(
    private val context: Context,
    private val repository: MediaRepository
) {
    private val _progress = MutableStateFlow(SceneScanProgress())
    val progress: StateFlow<SceneScanProgress> = _progress.asStateFlow()

    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    suspend fun analyzeScenes(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        if (_progress.value.isScanning) return@withContext
        
        isCancelled = false
        _progress.value = SceneScanProgress(
            mediaId = mediaItem.id,
            isScanning = true,
            totalDurationMs = mediaItem.durationMs,
            status = "PREPARING"
        )
        repository.updateAnalysisStatus(mediaItem.id, "IN_PROGRESS", mediaItem.sizeBytes, mediaItem.dateModified)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(mediaItem.path))
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: mediaItem.durationMs
            
            _progress.value = _progress.value.copy(totalDurationMs = durationMs, status = "SCANNING")

            // Clear old scenes
            repository.clearScenesForMedia(mediaItem.id)

            // Ensure cache directory exists
            val thumbDir = File(context.cacheDir, "scene_thumbs")
            if (!thumbDir.exists()) thumbDir.mkdirs()

            // We sample every 5 seconds
            val intervalMs = 5000L
            var currentMs = 0L
            var sceneCount = 0
            
            var sceneStartMs = 0L
            var lastBitmap: Bitmap? = null

            while (currentMs < durationMs) {
                if (isCancelled) {
                    _progress.value = _progress.value.copy(isScanning = false, status = "CANCELLED")
                    repository.updateAnalysisStatus(mediaItem.id, "NONE", 0, 0)
                    return@withContext
                }

                // Extract scaled frame (reduce resolution for faster processing)
                // We use getFrameAtTime in microseconds
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 128, 128)
                } else {
                    retriever.getFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                        Bitmap.createScaledBitmap(it, 128, 128, false)
                    }
                }

                if (bitmap != null) {
                    val isNewScene = if (lastBitmap == null) {
                        true
                    } else {
                        // Compare difference
                        compareBitmaps(lastBitmap, bitmap) > 0.15f // 15% difference threshold
                    }

                    if (isNewScene) {
                        // Save previous scene
                        if (sceneCount > 0) {
                            val previousScene = Scene(
                                mediaId = mediaItem.id,
                                startTimeMs = sceneStartMs,
                                endTimeMs = currentMs,
                                thumbnailPath = saveThumbnail(lastBitmap!!, thumbDir, mediaItem.id, sceneCount),
                                sceneIndex = sceneCount
                            )
                            repository.saveScene(previousScene)
                        }
                        
                        sceneStartMs = currentMs
                        sceneCount++
                        _progress.value = _progress.value.copy(scenesFound = sceneCount)
                        lastBitmap?.recycle()
                        lastBitmap = bitmap
                    } else {
                        bitmap.recycle()
                    }
                }

                currentMs += intervalMs
                _progress.value = _progress.value.copy(processedMs = currentMs.coerceAtMost(durationMs))
            }

            // Save the last scene
            if (lastBitmap != null) {
                val previousScene = Scene(
                    mediaId = mediaItem.id,
                    startTimeMs = sceneStartMs,
                    endTimeMs = durationMs,
                    thumbnailPath = saveThumbnail(lastBitmap, thumbDir, mediaItem.id, sceneCount),
                    sceneIndex = sceneCount
                )
                repository.saveScene(previousScene)
                lastBitmap.recycle()
            }

            _progress.value = _progress.value.copy(
                isScanning = false, 
                processedMs = durationMs,
                status = "COMPLETED"
            )
            repository.updateAnalysisStatus(mediaItem.id, "COMPLETED", mediaItem.sizeBytes, mediaItem.dateModified)
            
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(
                isScanning = false,
                status = "FAILED",
                errorMessage = e.message
            )
            repository.updateAnalysisStatus(mediaItem.id, "NONE", 0, 0)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    private fun saveThumbnail(bitmap: Bitmap, dir: File, mediaId: Int, sceneIndex: Int): String {
        val file = File(dir, "media_${mediaId}_scene_${sceneIndex}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    private fun compareBitmaps(b1: Bitmap, b2: Bitmap): Float {
        // Very basic frame comparison
        val pixels1 = IntArray(b1.width * b1.height)
        val pixels2 = IntArray(b2.width * b2.height)
        b1.getPixels(pixels1, 0, b1.width, 0, 0, b1.width, b1.height)
        b2.getPixels(pixels2, 0, b2.width, 0, 0, b2.width, b2.height)
        
        var diff = 0L
        for (i in pixels1.indices) {
            val c1 = pixels1[i]
            val c2 = pixels2[i]
            val rDiff = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
            val gDiff = abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
            val bDiff = abs((c1 and 0xFF) - (c2 and 0xFF))
            diff += rDiff + gDiff + bDiff
        }
        
        val maxDiff = pixels1.size * 3L * 255L
        return diff.toFloat() / maxDiff
    }
}
