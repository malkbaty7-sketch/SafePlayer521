package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScanStatus {
    QUEUED, RUNNING, COMPLETED, FAILED
}

@Entity(tableName = "scan_jobs")
data class ScanJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val status: ScanStatus = ScanStatus.QUEUED,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val itemsScanned: Int = 0,
    val newItems: Int = 0,
    val changedItems: Int = 0,
    val missingItems: Int = 0,
    val errorMessage: String? = null
)
