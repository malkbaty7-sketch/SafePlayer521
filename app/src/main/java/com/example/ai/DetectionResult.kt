package com.example.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.MediaItem

@Entity(
    tableName = "detection_results",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId")]
)
data class DetectionResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val sceneId: Int, // We'll store it but won't strictly constrain by FK to avoid complexity if scenes are deleted but we want to keep detection
    val category: String, // e.g. "Sensitive", "Violence", etc.
    val modality: String, // "VISUAL", "AUDIO"
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float,
    val modelId: String,
    val modelVersion: String,
    val sensitivity: String, // "LOW", "MEDIUM", "HIGH"
    val status: String, // "PENDING_REVIEW", "ACCEPTED", "REJECTED"
    val createdAt: Long = System.currentTimeMillis()
)
