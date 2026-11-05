package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_tasks")
data class ExportTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val sourceMediaId: Int,
    val status: String, // QUEUED, PREPARING, PROCESSING, VALIDATING, FINALIZING, COMPLETED, CANCELLED, FAILED, INTERRUPTED
    val progress: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val outputFormat: String = "MP4",
    val videoCodec: String = "H.264",
    val quality: String = "High",
    val tempFilePath: String? = null,
    val finalFilePath: String? = null,
    val errorMessage: String? = null
)
