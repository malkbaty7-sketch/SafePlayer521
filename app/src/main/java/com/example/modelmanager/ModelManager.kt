package com.example.modelmanager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DownloadProgress(
    val modelId: String = "",
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
)

class ModelManager(
    private val context: Context,
    private val aiModelDao: AiModelDao
) {
    val allModels: Flow<List<AiModelEntity>> = aiModelDao.getAllModels()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    suspend fun initializeDefaultModels() = withContext(Dispatchers.IO) {
        // Base model included via ML Kit
        if (aiModelDao.getModelById("mlkit-image-labeling-default") == null) {
            aiModelDao.insertOrUpdate(
                AiModelEntity(
                    modelId = "mlkit-image-labeling-default",
                    name = "SafeVision Base",
                    version = "17.0.9",
                    sizeBytes = 184_000_000L, // Simulated size for UI
                    analysisType = "VISUAL",
                    minApiLevel = 24,
                    minRamGb = 2,
                    status = "INSTALLED",
                    updatedAt = System.currentTimeMillis(),
                    checksum = "builtin-mlkit"
                )
            )
        }
        
        // Advanced model - available for download
        if (aiModelDao.getModelById("safevision-advanced-v1") == null) {
            aiModelDao.insertOrUpdate(
                AiModelEntity(
                    modelId = "safevision-advanced-v1",
                    name = "SafeVision Advanced",
                    version = "1.0.0",
                    sizeBytes = 540_000_000L,
                    analysisType = "VISUAL_AND_AUDIO",
                    minApiLevel = 28,
                    minRamGb = 4,
                    status = "AVAILABLE",
                    updatedAt = System.currentTimeMillis(),
                    checksum = "remote-hash"
                )
            )
        }
    }

    suspend fun downloadModel(model: AiModelEntity) = withContext(Dispatchers.IO) {
        if (_downloadProgress.value.isDownloading) return@withContext
        
        try {
            aiModelDao.update(model.copy(status = "DOWNLOADING"))
            
            val total = model.sizeBytes
            var downloaded = 0L
            val chunkSize = total / 20 // Simulate 20 chunks
            
            _downloadProgress.value = DownloadProgress(
                modelId = model.modelId,
                isDownloading = true,
                totalBytes = total,
                progress = 0f
            )

            // Simulate download process
            while (downloaded < total) {
                delay(300) // fake network delay
                downloaded += chunkSize
                if (downloaded > total) downloaded = total
                
                _downloadProgress.value = _downloadProgress.value.copy(
                    progress = downloaded.toFloat() / total,
                    downloadedBytes = downloaded
                )
            }
            
            // Verification step
            aiModelDao.update(model.copy(status = "VERIFYING"))
            delay(1000)
            
            aiModelDao.update(model.copy(status = "INSTALLED", updatedAt = System.currentTimeMillis()))
            
        } catch (e: Exception) {
            aiModelDao.update(model.copy(status = "FAILED"))
            _downloadProgress.value = _downloadProgress.value.copy(error = e.message)
        } finally {
            _downloadProgress.value = DownloadProgress() // Reset
        }
    }

    suspend fun deleteModel(model: AiModelEntity) = withContext(Dispatchers.IO) {
        // Base model cannot be deleted in this simulation
        if (model.modelId == "mlkit-image-labeling-default") return@withContext
        aiModelDao.update(model.copy(status = "AVAILABLE"))
    }
}
