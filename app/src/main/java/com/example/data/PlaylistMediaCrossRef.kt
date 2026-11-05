package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_media_cross_ref",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index("mediaId")]
)
data class PlaylistMediaCrossRef(
    val playlistId: Int,
    val mediaId: Int,
    val orderIndex: Int
)
