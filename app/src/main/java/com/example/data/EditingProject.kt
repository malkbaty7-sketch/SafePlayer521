package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "editing_projects",
    indices = [Index(value = ["mediaId"])]
)
data class EditingProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val name: String = "Untitled Project",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
