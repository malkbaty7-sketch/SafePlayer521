package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class WatchedState {
    UNWATCHED,
    IN_PROGRESS,
    WATCHED
}

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // URI string
    val folder: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val dateModified: Long,
    val durationMs: Long,
    val isNew: Boolean = true,
    val watchedState: WatchedState = WatchedState.UNWATCHED,
    val lastPositionMs: Long = 0,
    val isFavorite: Boolean = false,
    val isMissing: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val mimeType: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val analysisStatus: String = "NONE",
    val analyzedSize: Long = 0L,
    val analyzedDate: Long = 0L,
    val aiAnalysisStatus: String = "NONE",
    val aiAnalyzedSize: Long = 0L,
    val aiAnalyzedDate: Long = 0L,
    val isOriginal: Boolean = true,
    val sourceMediaId: Int? = null
)
