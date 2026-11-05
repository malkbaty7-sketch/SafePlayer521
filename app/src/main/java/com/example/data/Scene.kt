package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scenes",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId")]
)
data class Scene(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val thumbnailPath: String,
    val sceneIndex: Int = 0
)
