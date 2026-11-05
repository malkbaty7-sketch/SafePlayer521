package com.example.security.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_configs")
data class SecurityConfig(
    @PrimaryKey
    val id: Int = 1,
    val isAppLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val salt: String? = null,
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeoutSeconds: Int = 60, // 0 = immediate, 60, 300
    val parentPinHash: String? = null,
    val parentPinSalt: String? = null,
    val isParentalControlEnabled: Boolean = false,
    val userRole: String = "OWNER", // OWNER, PARENT, RESTRICTED
    val blockDelete: Boolean = false,
    val blockExport: Boolean = false,
    val blockShare: Boolean = false,
    val blockEdit: Boolean = false,
    val blockAddSource: Boolean = false,
    val blockSettings: Boolean = false,
    val isScreenshotProtectionEnabled: Boolean = false
)
