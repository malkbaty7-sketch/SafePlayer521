package com.example.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.data.EditingOperation
import com.example.data.EditingProject
import com.example.data.ExportTask
import com.example.data.ExportTaskDao
import com.example.data.MediaDao
import com.example.data.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

import kotlinx.coroutines.delay
import androidx.media3.transformer.ProgressHolder

@UnstableApi
class ExportEngine(
    private val context: Context,
    private val exportTaskDao: ExportTaskDao,
    private val mediaDao: MediaDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentTransformer: Transformer? = null
    
    // Track the active export ID
    private val _currentExportId = MutableStateFlow<Int?>(null)
    val currentExportId: StateFlow<Int?> = _currentExportId

    suspend fun startExport(
        project: EditingProject,
        operations: List<EditingOperation>,
        outputFormat: String = "MP4",
        videoCodec: String = "H.264",
        quality: String = "High"
    ): Int {
        val sourceMedia = mediaDao.getMediaByIdSync(project.mediaId) ?: throw IllegalArgumentException("Source media not found")
        
        val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        val exportDir = (context.getExternalFilesDir("exported_videos") ?: File(context.filesDir, "exported_videos")).apply { mkdirs() }
        val safeProjectName = project.name.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim().ifEmpty { "project" }
        val finalFile = File(exportDir, "${safeProjectName}_exported_${System.currentTimeMillis()}.mp4")
        
        val exportTask = ExportTask(
            projectId = project.id,
            sourceMediaId = project.mediaId,
            status = "PREPARING",
            outputFormat = outputFormat,
            videoCodec = videoCodec,
            quality = quality,
            tempFilePath = tempFile.absolutePath,
            finalFilePath = finalFile.absolutePath
        )
        
        val taskId = exportTaskDao.insert(exportTask).toInt()
        
        scope.launch {
            try {
                _currentExportId.value = taskId
                exportTaskDao.update(exportTask.copy(id = taskId, status = "PROCESSING"))
                
                val composition = buildComposition(sourceMedia.path, operations)
                
                val transformerBuilder = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            scope.launch {
                                finalizeExport(taskId, tempFile, finalFile, project, sourceMedia.path)
                            }
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            scope.launch {
                                exportTaskDao.update(exportTask.copy(id = taskId, status = "FAILED", errorMessage = exportException.message))
                                _currentExportId.value = null
                                tempFile.delete()
                            }
                        }
                    })
                
                // Set output MIME type
                if (outputFormat == "MP4") {
                    // Not setting mime type will default to mp4
                }
                
                val transformer = transformerBuilder.build()
                currentTransformer = transformer
                transformer.start(composition, tempFile.absolutePath)
                
                // Progress polling loop
                val progressHolder = ProgressHolder()
                while (_currentExportId.value == taskId) {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val currentTask = exportTaskDao.getTaskById(taskId)
                        if (currentTask != null && currentTask.status == "PROCESSING") {
                            exportTaskDao.update(currentTask.copy(progress = progressHolder.progress))
                        }
                    } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                        // wait
                    } else {
                        break // UNAVAILABLE OR FINISHED
                    }
                    delay(500)
                }
                
            } catch (e: Exception) {
                exportTaskDao.update(exportTask.copy(id = taskId, status = "FAILED", errorMessage = e.message))
                _currentExportId.value = null
                tempFile.delete()
            }
        }
        
        return taskId
    }
    
    private suspend fun finalizeExport(taskId: Int, tempFile: File, finalFile: File, project: EditingProject, sourcePath: String) {
        try {
            exportTaskDao.update(exportTaskDao.getTaskById(taskId)!!.copy(status = "VALIDATING"))
            
            // Validate file
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Output file is empty or does not exist")
            }
            
            finalFile.parentFile?.mkdirs()
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
            
            // Save to Library
            val newMedia = com.example.data.MediaItem(
                path = finalFile.absolutePath,
                name = "${project.name} (Edited)",
                folder = finalFile.parent ?: "Exported",
                sizeBytes = finalFile.length(),
                dateModified = System.currentTimeMillis(),
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                durationMs = 0L // Optional: Use MediaMetadataRetriever to set true duration
            )
            mediaDao.insertMedia(newMedia)
            
            exportTaskDao.update(exportTaskDao.getTaskById(taskId)!!.copy(
                status = "COMPLETED", 
                progress = 100, 
                endTime = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            exportTaskDao.update(exportTaskDao.getTaskById(taskId)!!.copy(
                status = "FAILED", 
                errorMessage = "Validation failed: ${e.message}"
            ))
            tempFile.delete()
            finalFile.delete()
        } finally {
            _currentExportId.value = null
        }
    }
    
    fun cancelCurrentExport() {
        currentTransformer?.cancel()
        currentTransformer = null
        _currentExportId.value?.let { taskId ->
            scope.launch {
                exportTaskDao.update(exportTaskDao.getTaskById(taskId)!!.copy(status = "CANCELLED", endTime = System.currentTimeMillis()))
                _currentExportId.value = null
            }
        }
    }

    private fun buildComposition(sourcePath: String, operations: List<EditingOperation>): Composition {
        val activeOps = operations.filter { it.isEnabled }
        val deletes = activeOps.filter { it.type == "DELETE" }.sortedBy { it.startTimeMs }
        val mutes = activeOps.filter { it.type == "MUTE" }
        val censors = activeOps.filter { it.type.startsWith("CENSOR") }
        
        // 1. Calculate retained segments (inverse of deletes)
        val retainedSegments = mutableListOf<Pair<Long, Long>>() // start, end
        var currentStart = 0L
        for (del in deletes) {
            if (del.startTimeMs > currentStart) {
                retainedSegments.add(Pair(currentStart, del.startTimeMs))
            }
            currentStart = max(currentStart, del.endTimeMs)
        }
        retainedSegments.add(Pair(currentStart, Long.MAX_VALUE))
        
        // 2. Build EditedMediaItems
        val editedMediaItems = mutableListOf<EditedMediaItem>()
        
        for (segment in retainedSegments) {
            val segmentStart = segment.first
            val segmentEnd = segment.second
            
            // Check if this segment needs to be split further by mutes
            val segmentMutes = mutes.filter { it.startTimeMs < segmentEnd && it.endTimeMs > segmentStart }
            
            if (segmentMutes.isEmpty()) {
                editedMediaItems.add(createEditedMediaItem(sourcePath, segmentStart, segmentEnd, false, censors))
            } else {
                // Split by mutes
                val mutePoints = mutableSetOf(segmentStart, segmentEnd)
                for (mute in segmentMutes) {
                    if (mute.startTimeMs > segmentStart) mutePoints.add(mute.startTimeMs)
                    if (mute.endTimeMs < segmentEnd) mutePoints.add(mute.endTimeMs)
                }
                val sortedPoints = mutePoints.sorted()
                
                for (i in 0 until sortedPoints.size - 1) {
                    val pStart = sortedPoints[i]
                    val pEnd = sortedPoints[i+1]
                    if (pStart == pEnd) continue
                    
                    val isMuted = segmentMutes.any { it.startTimeMs <= pStart && it.endTimeMs >= pEnd }
                    editedMediaItems.add(createEditedMediaItem(sourcePath, pStart, pEnd, isMuted, censors))
                }
            }
        }
        
        val sequence = EditedMediaItemSequence(editedMediaItems)
        return Composition.Builder(listOf(sequence)).build()
    }
    
    private fun createEditedMediaItem(
        path: String, 
        startMs: Long, 
        endMs: Long, 
        isMuted: Boolean, 
        censors: List<EditingOperation>
    ): EditedMediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(path))
        
        // Apply clipping
        if (startMs > 0 || endMs < Long.MAX_VALUE) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(if (endMs == Long.MAX_VALUE) C.TIME_END_OF_SOURCE else endMs)
                    .build()
            )
        }
        
        val mediaItem = mediaItemBuilder.build()
        val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItem)
        
        if (isMuted) {
            editedMediaItemBuilder.setRemoveAudio(true)
        }
        
        // Add censor effects if applicable to this segment
        val segmentCensors = censors.filter { it.startTimeMs < endMs && it.endTimeMs > startMs }
        if (segmentCensors.isNotEmpty()) {
            val censorEffect = CensorEffect(segmentCensors)
            editedMediaItemBuilder.setEffects(
                androidx.media3.transformer.Effects(emptyList(), listOf(censorEffect))
            )
        }
        
        return editedMediaItemBuilder.build()
    }
}
