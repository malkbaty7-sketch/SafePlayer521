package com.example.storage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage_locations")
data class StorageLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val displayName: String,
    val platform: String = "ANDROID", // or "IOS"
    val path: String,
    val uri: String,
    val type: String, // e.g., "APP_STORAGE", "DEVICE_STORAGE", "USER_FOLDER"
    val availableSpace: Long = 0L,
    val totalSpace: Long = 0L,
    val usedSpace: Long = 0L,
    val permissionState: String = "GRANTED",
    val isAccessible: Boolean = true,
    val isReadOnly: Boolean = false,
    val lastCheckedAt: Long = 0L
)
