package com.example.ai

import android.graphics.Bitmap

data class ModelResult(
    val category: String,
    val confidence: Float
)

interface VisualModel {
    val modelId: String
    val modelVersion: String
    
    suspend fun prepare()
    suspend fun analyzeFrame(bitmap: Bitmap): List<ModelResult>
    fun release()
}
