package com.example.ai

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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

data class AiAnalysisProgress(
    val mediaId: Int = -1,
    val isAnalyzing: Boolean = false,
    val totalDurationMs: Long = 0,
    val processedMs: Long = 0,
    val detectionsFound: Int = 0,
    val status: String = "IDLE", // "PREPARING", "ANALYZING", "SAVING", "COMPLETED", "FAILED", "CANCELLED"
    val errorMessage: String? = null,
    val strategy: AiStrategy = AiStrategy.BALANCED
)

class AiAnalyzer(
    private val context: Context,
    private val repository: MediaRepository
) {
    private val _progress = MutableStateFlow(AiAnalysisProgress())
    val progress: StateFlow<AiAnalysisProgress> = _progress.asStateFlow()

    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    suspend fun analyzeMedia(mediaItem: MediaItem, sensitivity: String = "MEDIUM") = withContext(Dispatchers.IO) {
        if (_progress.value.isAnalyzing) return@withContext
        
        isCancelled = false
        val capabilities = getDeviceCapabilities(context)
        val strategy = determineAiStrategy(capabilities)
        
        _progress.value = AiAnalysisProgress(
            mediaId = mediaItem.id,
            isAnalyzing = true,
            totalDurationMs = mediaItem.durationMs,
            status = "PREPARING",
            strategy = strategy
        )
        
        repository.updateAiAnalysisStatus(mediaItem.id, "IN_PROGRESS", mediaItem.sizeBytes, mediaItem.dateModified)

        val retriever = MediaMetadataRetriever()
        val visualModel: VisualModel = MlKitVisualModel()
        
        try {
            retriever.setDataSource(context, Uri.parse(mediaItem.path))
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: mediaItem.durationMs
            
            _progress.value = _progress.value.copy(totalDurationMs = durationMs, status = "ANALYZING")

            // Clear old AI results
            repository.clearDetectionResultsForMedia(mediaItem.id)

            visualModel.prepare()

            // Fetch scenes to use as a guide (Stage 3 -> Stage 4)
            val scenes = repository.getScenesForMedia(mediaItem.id).firstOrNull() ?: emptyList()
            
            // Determine sampling interval based on strategy and sensitivity
            val baseIntervalMs = when (strategy) {
                AiStrategy.LIGHT -> 5000L
                AiStrategy.BALANCED -> 3000L
                AiStrategy.ADVANCED -> 1000L
            }
            
            val intervalMs = when (sensitivity) {
                "LOW" -> (baseIntervalMs * 1.5).toLong()
                "HIGH" -> (baseIntervalMs * 0.5).toLong().coerceAtLeast(500L)
                else -> baseIntervalMs
            }

            var detectionsCount = 0
            
            // Temporal grouping variables
            var currentDetectionStartMs: Long? = null
            var lastDetectionTimeMs: Long? = null
            var currentDetectedCategories = mutableMapOf<String, Float>()
            var currentSceneId = -1

            suspend fun commitTemporalSegment() {
                if (currentDetectionStartMs != null && lastDetectionTimeMs != null && currentDetectedCategories.isNotEmpty()) {
                    // Pick the highest confidence category for simplicity, or we could save multiple
                    val topCategory = currentDetectedCategories.maxByOrNull { it.value }!!
                    
                    val result = DetectionResult(
                        mediaId = mediaItem.id,
                        sceneId = currentSceneId,
                        category = topCategory.key,
                        modality = "VISUAL",
                        startTimeMs = currentDetectionStartMs!!,
                        endTimeMs = lastDetectionTimeMs!!,
                        confidence = topCategory.value,
                        modelId = visualModel.modelId,
                        modelVersion = visualModel.modelVersion,
                        sensitivity = sensitivity,
                        status = "PENDING_REVIEW"
                    )
                    repository.saveDetectionResult(result)
                    detectionsCount++
                    _progress.value = _progress.value.copy(detectionsFound = detectionsCount)
                }
                currentDetectionStartMs = null
                lastDetectionTimeMs = null
                currentDetectedCategories.clear()
            }

            val sensitiveKeywords = setOf("Flesh", "Violence", "Blood", "Weapon", "Skin", "Undergarment", "Swimwear")

            for (scene in scenes) {
                var currentMs = scene.startTimeMs
                currentSceneId = scene.id
                
                while (currentMs < scene.endTimeMs) {
                    if (isCancelled) {
                        commitTemporalSegment()
                        _progress.value = _progress.value.copy(isAnalyzing = false, status = "CANCELLED")
                        repository.updateAiAnalysisStatus(mediaItem.id, "NONE", 0, 0)
                        return@withContext
                    }

                    // Extract frame
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 224, 224)
                    } else {
                        retriever.getFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                            Bitmap.createScaledBitmap(it, 224, 224, false)
                        }
                    }

                    if (bitmap != null) {
                        val results = visualModel.analyzeFrame(bitmap)
                        bitmap.recycle()
                        
                        val sensitiveResults = results.filter { 
                            // In a real scenario, this would use model's specific sensitive output.
                            // Here we map standard ML Kit labels to simulate.
                            it.confidence > 0.6f && sensitiveKeywords.any { kw -> it.category.contains(kw, ignoreCase = true) }
                        }

                        if (sensitiveResults.isNotEmpty()) {
                            if (currentDetectionStartMs == null) {
                                currentDetectionStartMs = currentMs
                            }
                            lastDetectionTimeMs = currentMs
                            sensitiveResults.forEach { 
                                currentDetectedCategories[it.category] = maxOf(it.confidence, currentDetectedCategories[it.category] ?: 0f)
                            }
                        } else {
                            // Gap in detection, commit segment if gap is too large. 
                            // For simplicity, we commit immediately on gap.
                            commitTemporalSegment()
                        }
                    }

                    currentMs += intervalMs
                    _progress.value = _progress.value.copy(processedMs = currentMs.coerceAtMost(durationMs))
                }
                // Commit at end of scene
                commitTemporalSegment()
            }

            _progress.value = _progress.value.copy(
                isAnalyzing = false, 
                processedMs = durationMs,
                status = "COMPLETED"
            )
            repository.updateAiAnalysisStatus(mediaItem.id, "COMPLETED", mediaItem.sizeBytes, mediaItem.dateModified)
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _progress.value = _progress.value.copy(
                isAnalyzing = false,
                status = "FAILED",
                errorMessage = e.message
            )
            repository.updateAiAnalysisStatus(mediaItem.id, "NONE", 0, 0)
        } finally {
            try {
                retriever.release()
                visualModel.release()
            } catch (e: Exception) {}
        }
    }
}
