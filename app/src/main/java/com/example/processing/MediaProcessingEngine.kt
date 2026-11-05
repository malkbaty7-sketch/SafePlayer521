package com.example.processing

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class ExportState {
    data class Progress(val progress: Int) : ExportState()
    data class Completed(val file: File) : ExportState()
    data class Error(val exception: Exception) : ExportState()
}

class MediaProcessingEngine(private val context: Context) {

    fun executeTrim(
        sourceUri: String,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Flow<ExportState> = callbackFlow {
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(ExportState.Completed(outputFile))
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    trySend(ExportState.Error(exportException))
                    close(exportException)
                }
            })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        transformer.start(editedMediaItem, outputFile.absolutePath)

        // Polling progress could be implemented with a Coroutine timer 
        // querying transformer.getProgress(progressHolder)

        awaitClose {
            transformer.cancel()
        }
    }
    
    // Future expansion: Multi-segment delete (Composition with multiple EditedMediaItems),
    // Audio Mute (Effects), Filter Graph, etc.
}
