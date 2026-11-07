package com.example.converter

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.MediaType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

@OptIn(UnstableApi::class)
object VideoCompressor {

    enum class CompressionPreset(val label: String, val scaleDesc: String, val targetHeight: Int) {
        BALANCED("متوازن (720p)", "توفير ~50% مع جودة عالية", 720),
        MAX_SAVINGS("أقصى توفير (480p)", "توفير ~75% للمشاركة السريعة", 480),
        HIGH_QUALITY("جودة ممتازة (1080p)", "ضغط خفيف ~30% مع تفاصيل كاملة", 1080)
    }

    data class CompressionState(
        val isCompressing: Boolean = false,
        val progress: Float = 0f,
        val statusMessage: String = "",
        val originalSizeBytes: Long = 0L,
        val compressedSizeBytes: Long = 0L,
        val outputPath: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(CompressionState())
    val state: StateFlow<CompressionState> = _state.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null

    fun compressVideo(
        context: Context,
        repository: MediaRepository,
        videoItem: MediaItem,
        preset: CompressionPreset = CompressionPreset.BALANCED,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val scope = CoroutineScope(Dispatchers.Main)

        scope.launch {
            try {
                val inputFile = File(videoItem.path)
                if (!inputFile.exists()) {
                    _state.value = CompressionState(error = "ملف الفيديو الأصلي غير موجود")
                    onComplete(false, "الملف غير موجود")
                    return@launch
                }

                _state.value = CompressionState(
                    isCompressing = true,
                    progress = 0f,
                    statusMessage = "جاري تحضير الضغط...",
                    originalSizeBytes = videoItem.sizeBytes
                )

                val exportDir = (context.getExternalFilesDir("compressed_videos") ?: File(context.filesDir, "compressed_videos")).apply { mkdirs() }
                val baseName = videoItem.name.substringBeforeLast('.')
                val outputFile = File(exportDir, "${baseName}_compressed_${System.currentTimeMillis()}.mp4")

                val media3Item = Media3Item.fromUri(Uri.fromFile(inputFile))
                val editedMediaItem = EditedMediaItem.Builder(media3Item).build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            activeTransformer = null
                            val compressedSize = outputFile.length()

                            scope.launch(Dispatchers.IO) {
                                // Add compressed video to database
                                val compressedItem = MediaItem(
                                    name = "${baseName} (مضغوط).mp4",
                                    path = outputFile.absolutePath,
                                    folder = outputFile.parentFile?.name ?: "compressed",
                                    mediaType = MediaType.VIDEO,
                                    sizeBytes = compressedSize,
                                    dateModified = System.currentTimeMillis() / 1000,
                                    durationMs = videoItem.durationMs,
                                    isNew = true,
                                    mimeType = "video/mp4",
                                    sourceMediaId = videoItem.id
                                )
                                repository.insertOrUpdate(compressedItem)

                                withContext(Dispatchers.Main) {
                                    _state.value = CompressionState(
                                        isCompressing = false,
                                        progress = 1f,
                                        statusMessage = "اكتمل الضغط بنجاح!",
                                        originalSizeBytes = videoItem.sizeBytes,
                                        compressedSizeBytes = compressedSize,
                                        outputPath = outputFile.absolutePath
                                    )
                                    onComplete(true, outputFile.absolutePath)
                                }
                            }
                        }

                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                            progressJob?.cancel()
                            activeTransformer = null
                            _state.value = CompressionState(
                                isCompressing = false,
                                error = exportException.localizedMessage ?: "فشل ضغط الفيديو"
                            )
                            onComplete(false, exportException.localizedMessage)
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(editedMediaItem, outputFile.absolutePath)

                // Track Progress
                val progressHolder = ProgressHolder()
                progressJob = scope.launch(Dispatchers.IO) {
                    while (activeTransformer != null) {
                        delay(250)
                        val status = activeTransformer?.getProgress(progressHolder)
                        if (status == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = progressHolder.progress / 100f
                            withContext(Dispatchers.Main) {
                                _state.value = _state.value.copy(
                                    progress = p,
                                    statusMessage = "جاري ضغط الفيديو... ${(p * 100).toInt()}%"
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                _state.value = CompressionState(
                    isCompressing = false,
                    error = e.localizedMessage ?: "حدث خطأ غير متوقع"
                )
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun cancel() {
        activeTransformer?.cancel()
        activeTransformer = null
        progressJob?.cancel()
        _state.value = CompressionState(isCompressing = false, statusMessage = "تم إلغاء الضغط")
    }
}
