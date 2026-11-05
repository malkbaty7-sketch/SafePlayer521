package com.example.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

class MlKitVisualModel : VisualModel {
    override val modelId: String = "mlkit-image-labeling-default"
    override val modelVersion: String = "17.0.9"

    // Real ML Kit Local Model
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun prepare() {
        // ML Kit loads the model on first inference, but we can do a dummy pass if needed.
    }

    override suspend fun analyzeFrame(bitmap: Bitmap): List<ModelResult> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val labels = labeler.process(image).await()
            labels.map { 
                ModelResult(
                    category = it.text,
                    confidence = it.confidence
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun release() {
        labeler.close()
    }
}
