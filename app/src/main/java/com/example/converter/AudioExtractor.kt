package com.example.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioExtractor {

    data class ExtractionResult(
        val isSuccess: Boolean,
        val outputPath: String? = null,
        val outputSizeBytes: Long = 0L,
        val errorMessage: String? = null
    )

    /**
     * Lossless audio extraction from video using native MediaExtractor and MediaMuxer.
     * Copies compressed audio samples directly to an M4A/AAC container in milliseconds.
     */
    suspend fun extractAudio(
        context: Context,
        repository: MediaRepository,
        videoItem: MediaItem,
        onProgress: (Float) -> Unit = {}
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            val inputFile = File(videoItem.path)
            if (!inputFile.exists()) {
                return@withContext ExtractionResult(false, errorMessage = "الملف غير موجود")
            }

            extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
            }

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext ExtractionResult(false, errorMessage = "لم يتم العثور على مسار صوتي داخل هذا الفيديو")
            }

            extractor.selectTrack(audioTrackIndex)

            // Setup output file
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val cleanName = videoItem.name.substringBeforeLast('.')
            val outputFile = File(musicDir, "${cleanName}_Audio_${System.currentTimeMillis()}.m4a")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            // Buffer copy
            val maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 512)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                videoItem.durationMs * 1000
            }

            var sawEOS = false
            var sampleCount = 0

            while (!sawEOS) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)

                if (bufferInfo.size < 0) {
                    sawEOS = true
                    bufferInfo.size = 0
                } else {
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                    sampleCount++

                    if (sampleCount % 50 == 0 && durationUs > 0) {
                        val progress = (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }

            muxer.stop()
            muxer.release()
            muxer = null

            extractor.release()
            extractor = null

            onProgress(1f)

            // Insert new audio item into database
            val audioItem = MediaItem(
                name = "${cleanName} (صوت مستخرج).m4a",
                path = outputFile.absolutePath,
                folder = outputFile.parentFile?.name ?: "Music",
                mediaType = MediaType.AUDIO,
                sizeBytes = outputFile.length(),
                dateModified = System.currentTimeMillis() / 1000,
                durationMs = videoItem.durationMs,
                isNew = true,
                mimeType = "audio/mp4",
                sourceMediaId = videoItem.id
            )
            repository.insertOrUpdate(audioItem)

            ExtractionResult(
                isSuccess = true,
                outputPath = outputFile.absolutePath,
                outputSizeBytes = outputFile.length()
            )
        } catch (e: Exception) {
            ExtractionResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "حدث خطأ أثناء استخراج الصوت"
            )
        } finally {
            try { extractor?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }
}
