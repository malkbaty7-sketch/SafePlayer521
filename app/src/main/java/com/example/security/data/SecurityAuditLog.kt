package com.example.security.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "security_audit_logs",
    indices = [Index("eventType"), Index("timestamp")]
)
data class SecurityAuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: String, // APP_UNLOCKED, FAILED_UNLOCK, PROTECTED_ITEM_OPENED, SETTINGS_CHANGED, PARENTAL_CONTROL_CHANGED, ACTION_BLOCKED, APP_LOCKED
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = "",
    val userRole: String = "OWNER"
)
