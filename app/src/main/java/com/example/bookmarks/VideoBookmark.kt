package com.example.bookmarks

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.MediaItem

@Entity(
    tableName = "video_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"])]
)
data class VideoBookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val timestampMs: Long,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
