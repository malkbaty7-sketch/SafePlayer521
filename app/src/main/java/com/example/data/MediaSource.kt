package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_sources")
data class MediaSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val displayName: String,
    val isScanEnabled: Boolean = true,
    val lastScanTime: Long = 0L
)
