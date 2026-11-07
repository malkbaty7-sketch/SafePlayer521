package com.example.streaming

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_channels")
data class StreamChannel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val groupTitle: String = "عام",
    val logoUrl: String = "",
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
