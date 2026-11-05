package com.example.tasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_records",
    indices = [
        Index("status"),
        Index("priority"),
        Index("taskType"),
        Index("projectId"),
        Index("mediaId")
    ]
)
data class TaskRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskType: String, // EXPORT, MEDIA_PROCESSING, SCAN, THUMBNAIL_GEN, PREVIEW_RENDER, METADATA_EXTRACT, STORAGE_CLEANUP, RECOVERY
    val status: String,   // PENDING, QUEUED, PREPARING, RUNNING, PAUSED, CANCELLING, CANCELLED, COMPLETED, FAILED, INTERRUPTED, RECOVERING
    val priority: String, // CRITICAL, HIGH, NORMAL, LOW, BACKGROUND
    val title: String,
    val progress: Int = 0, // 0..100
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val projectId: Int? = null,
    val mediaId: Int? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val resourceRequirements: String = "NORMAL", // HEAVY_VIDEO, LIGHT_IO, CPU_ONLY, NORMAL
    val pipelineStage: String? = null,
    val payloadJson: String? = null
)
