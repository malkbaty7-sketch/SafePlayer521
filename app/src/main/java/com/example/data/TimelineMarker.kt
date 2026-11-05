package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_markers",
    indices = [Index(value = ["mediaId"])]
)
data class TimelineMarker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val timeMs: Long,
    val name: String? = null,
    val description: String? = null,
    val type: String = "STANDARD", // STANDARD, START_RANGE, END_RANGE
    val createdAt: Long = System.currentTimeMillis()
)
