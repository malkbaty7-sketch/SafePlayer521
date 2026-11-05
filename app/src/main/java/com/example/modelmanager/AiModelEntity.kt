package com.example.modelmanager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val modelId: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val analysisType: String,
    val minApiLevel: Int,
    val minRamGb: Int,
    val status: String, // AVAILABLE, DOWNLOADING, INSTALLED, CORRUPTED
    val updatedAt: Long,
    val checksum: String
)
