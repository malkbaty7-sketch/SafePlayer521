package com.example.security.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "protected_items",
    primaryKeys = ["itemType", "itemId"],
    indices = [Index("itemType"), Index("itemId")]
)
data class ProtectedItem(
    val itemType: String, // MEDIA, PROJECT, PLAYLIST, FOLDER
    val itemId: Int,
    val protectedAt: Long = System.currentTimeMillis()
)
