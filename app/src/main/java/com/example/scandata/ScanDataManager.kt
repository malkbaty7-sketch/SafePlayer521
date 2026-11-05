package com.example.scandata

import android.content.Context
import android.net.Uri
import com.example.ai.DetectionResult
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.Scene
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.InputStreamReader

class ScanDataManager(
    private val context: Context,
    private val repository: MediaRepository
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ScanDataPackage::class.java)

    suspend fun exportScanData(mediaItem: MediaItem, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val scenes = repository.getScenesForMedia(mediaItem.id).firstOrNull() ?: emptyList()
            val detections = repository.getDetectionResultsForMedia(mediaItem.id).firstOrNull() ?: emptyList()
            
            if (scenes.isEmpty() && detections.isEmpty()) {
                return@withContext Result.failure(Exception("No scan data available to export"))
            }

            val pkg = ScanDataPackage(
                formatVersion = "1.0",
                mediaIdentity = MediaIdentity(
                    name = mediaItem.name,
                    sizeBytes = mediaItem.sizeBytes,
                    durationMs = mediaItem.durationMs,
                    lastModified = mediaItem.dateModified
                ),
                aiModelInfo = AiModelInfo(
                    modelId = detections.firstOrNull()?.modelId ?: "unknown",
                    modelVersion = detections.firstOrNull()?.modelVersion ?: "unknown",
                    runtimeVersion = "android-1.0"
                ),
                analysisSettings = AnalysisSettings(
                    sensitivity = detections.firstOrNull()?.sensitivity ?: "MEDIUM",
                    modality = "VISUAL",
                    categories = detections.map { it.category }.distinct()
                ),
                scenes = scenes.map { 
                    SceneData(it.id, it.startTimeMs, it.endTimeMs, it.sceneIndex)
                },
                detections = detections.map {
                    DetectionData(it.sceneId, it.category, it.modality, it.startTimeMs, it.endTimeMs, it.confidence, it.status)
                }
            )

            context.contentResolver.openOutputStream(destUri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(adapter.toJson(pkg))
                }
            } ?: return@withContext Result.failure(Exception("Could not open destination for writing"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importScanData(mediaItem: MediaItem, sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                InputStreamReader(input).readText()
            } ?: return@withContext Result.failure(Exception("Could not read source file"))

            val pkg = adapter.fromJson(json) ?: return@withContext Result.failure(Exception("Invalid scan data format"))

            // Verify Source Identity
            val id = pkg.mediaIdentity
            if (id.sizeBytes != mediaItem.sizeBytes || id.durationMs != mediaItem.durationMs) {
                return@withContext Result.failure(Exception("Source Verification Failed: The scan data does not match this media file."))
            }

            // Clear old data
            repository.clearScenesForMedia(mediaItem.id)
            repository.clearDetectionResultsForMedia(mediaItem.id)

            // Import Scenes
            val sceneIdMap = mutableMapOf<Int, Int>() // Old ID to New ID
            for (sceneData in pkg.scenes) {
                val newScene = Scene(
                    mediaId = mediaItem.id,
                    startTimeMs = sceneData.startTimeMs,
                    endTimeMs = sceneData.endTimeMs,
                    thumbnailPath = "", // Thumbnails need regeneration, or we store them in package (too big). Better to regen on demand.
                    sceneIndex = sceneData.sceneIndex
                )
                val newId = repository.saveScene(newScene).toInt().toInt()
                sceneIdMap[sceneData.sceneId] = newId
            }

            // Import Detections
            for (det in pkg.detections) {
                val newDet = DetectionResult(
                    mediaId = mediaItem.id,
                    sceneId = sceneIdMap[det.sceneId] ?: -1,
                    category = det.category,
                    modality = det.modality,
                    startTimeMs = det.startTimeMs,
                    endTimeMs = det.endTimeMs,
                    confidence = det.confidence,
                    modelId = pkg.aiModelInfo.modelId,
                    modelVersion = pkg.aiModelInfo.modelVersion,
                    sensitivity = pkg.analysisSettings.sensitivity,
                    status = det.status,
                    createdAt = System.currentTimeMillis()
                )
                repository.saveDetectionResult(newDet)
            }

            repository.updateAnalysisStatus(mediaItem.id, "COMPLETED", mediaItem.sizeBytes, mediaItem.dateModified)
            repository.updateAiAnalysisStatus(mediaItem.id, "COMPLETED", mediaItem.sizeBytes, mediaItem.dateModified)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
