package com.example.scandata

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScanDataPackage(
    val formatVersion: String,
    val mediaIdentity: MediaIdentity,
    val aiModelInfo: AiModelInfo,
    val analysisSettings: AnalysisSettings,
    val scenes: List<SceneData>,
    val detections: List<DetectionData>
)

@JsonClass(generateAdapter = true)
data class MediaIdentity(
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val lastModified: Long
)

@JsonClass(generateAdapter = true)
data class AiModelInfo(
    val modelId: String,
    val modelVersion: String,
    val runtimeVersion: String
)

@JsonClass(generateAdapter = true)
data class AnalysisSettings(
    val sensitivity: String,
    val modality: String,
    val categories: List<String>
)

@JsonClass(generateAdapter = true)
data class SceneData(
    val sceneId: Int, // Logical ID within package
    val startTimeMs: Long,
    val endTimeMs: Long,
    val sceneIndex: Int
)

@JsonClass(generateAdapter = true)
data class DetectionData(
    val sceneId: Int,
    val category: String,
    val modality: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float,
    val status: String
)
