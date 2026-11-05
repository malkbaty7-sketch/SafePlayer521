package com.example.processing

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processing_jobs",
    indices = [Index(value = ["originalMediaId"])]
)
data class ProcessingJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalMediaId: Int,
    val operationType: String,
    val status: String, // QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
    val progress: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val tempFilePath: String? = null,
    val finalFilePath: String? = null,
    val errorMessage: String? = null
)
