package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "editing_operations",
    indices = [Index(value = ["projectId"])]
)
data class EditingOperation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val type: String, // CENSOR_BLACK, CENSOR_BLUR, CENSOR_PIXELATE, DELETE, MUTE
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isEnabled: Boolean = true,
    val propertiesJson: String = "{}"
)
