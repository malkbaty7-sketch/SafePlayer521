package com.example.security

import android.content.Context
import com.example.security.data.ProtectedItem
import com.example.security.data.SecurityAuditLog
import com.example.security.data.SecurityConfig
import com.example.security.data.SecurityDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.SecureRandom

enum class SecurityAction {
    PLAY,
    EDIT,
    EXPORT,
    DELETE,
    SHARE,
    ADD_SOURCE,
    MODIFY_SETTINGS
}

enum class VaultAuthResult {
    REAL_UNLOCKED,
    DECOY_UNLOCKED,
    INVALID
}

class SecurityManager(
    private val context: Context,
    private val securityDao: SecurityDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val vaultPrefs = context.getSharedPreferences("safe_vault_pro_prefs", Context.MODE_PRIVATE)

    val securityConfig: Flow<SecurityConfig?> = securityDao.getSecurityConfigFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isParentAuthorized = MutableStateFlow(false)
    val isParentAuthorized: StateFlow<Boolean> = _isParentAuthorized.asStateFlow()

    // Safe Vault Pro states
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _isDecoyModeActive = MutableStateFlow(false)
    val isDecoyModeActive: StateFlow<Boolean> = _isDecoyModeActive.asStateFlow()

    private val _isDecoyConfigured = MutableStateFlow(vaultPrefs.getString("decoy_pin_hash", null) != null)
    val isDecoyConfigured: StateFlow<Boolean> = _isDecoyConfigured.asStateFlow()

    private var lastActivityTime: Long = System.currentTimeMillis()

    val protectedMediaIds: Flow<Set<Int>> = securityDao.getProtectedItemIdsFlow("MEDIA")
        .map { it.toSet() }

    val protectedProjectIds: Flow<Set<Int>> = securityDao.getProtectedItemIdsFlow("PROJECT")
        .map { it.toSet() }

    val protectedPlaylistIds: Flow<Set<Int>> = securityDao.getProtectedItemIdsFlow("PLAYLIST")
        .map { it.toSet() }

    init {
        scope.launch {
            val config = securityDao.getSecurityConfig()
            if (config == null) {
                securityDao.saveSecurityConfig(SecurityConfig())
            } else {
                if (!config.isAppLockEnabled) {
                    _isUnlocked.value = true
                }
            }
        }
    }

    fun recordUserActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun checkAutoLock() {
        scope.launch {
            val config = securityDao.getSecurityConfig() ?: return@launch
            if (config.isAppLockEnabled && _isUnlocked.value) {
                val timeoutMs = config.autoLockTimeoutSeconds * 1000L
                if (System.currentTimeMillis() - lastActivityTime > timeoutMs) {
                    lockApp("Auto-lock timeout reached")
                }
            }
        }
    }

    fun lockApp(reason: String = "Manual lock") {
        _isUnlocked.value = false
        _isParentAuthorized.value = false
        _isVaultUnlocked.value = false
        _isDecoyModeActive.value = false
        scope.launch {
            logAudit("APP_LOCKED", "App locked: $reason")
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        _isDecoyModeActive.value = false
    }

    suspend fun setupAppLock(pin: String, enableBiometric: Boolean, autoLockSeconds: Int) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        val current = securityDao.getSecurityConfig() ?: SecurityConfig()
        val updated = current.copy(
            isAppLockEnabled = true,
            pinHash = hash,
            salt = salt,
            isBiometricEnabled = enableBiometric,
            autoLockTimeoutSeconds = autoLockSeconds
        )
        securityDao.saveSecurityConfig(updated)
        _isUnlocked.value = true
        _isVaultUnlocked.value = true
        _isDecoyModeActive.value = false
        logAudit("SETTINGS_CHANGED", "App & Vault lock enabled with PIN")
    }

    fun setupDecoyPin(decoyPin: String?) {
        if (decoyPin.isNullOrBlank()) {
            vaultPrefs.edit().remove("decoy_pin_hash").remove("decoy_salt").apply()
            _isDecoyConfigured.value = false
        } else {
            val salt = generateSalt()
            val hash = hashPin(decoyPin, salt)
            vaultPrefs.edit()
                .putString("decoy_pin_hash", hash)
                .putString("decoy_salt", salt)
                .apply()
            _isDecoyConfigured.value = true
        }
        scope.launch {
            logAudit("SETTINGS_CHANGED", "Decoy PIN configuration updated")
        }
    }

    suspend fun verifyVaultOrAppPin(enteredPin: String): VaultAuthResult {
        val config = securityDao.getSecurityConfig()
        if (config == null || !config.isAppLockEnabled || config.pinHash == null || config.salt == null) {
            _isUnlocked.value = true
            _isVaultUnlocked.value = true
            _isDecoyModeActive.value = false
            return VaultAuthResult.REAL_UNLOCKED
        }

        if (System.currentTimeMillis() < lockoutUntilTimestamp) {
            return VaultAuthResult.INVALID
        }

        // Check Real PIN first
        val isRealMatch = PinHasher.verify(enteredPin, config.salt, config.pinHash)
        if (isRealMatch) {
            if (PinHasher.needsUpgrade(config.pinHash)) {
                securityDao.saveSecurityConfig(config.copy(pinHash = hashPin(enteredPin, config.salt)))
            }
            failedAttempts = 0
            lockoutUntilTimestamp = 0L
            _isUnlocked.value = true
            _isVaultUnlocked.value = true
            _isDecoyModeActive.value = false
            recordUserActivity()
            logAudit("VAULT_UNLOCKED", "Real Vault unlocked via PIN")
            return VaultAuthResult.REAL_UNLOCKED
        }

        // Check Decoy PIN if configured
        val decoyHash = vaultPrefs.getString("decoy_pin_hash", null)
        val decoySalt = vaultPrefs.getString("decoy_salt", null)
        if (decoyHash != null && decoySalt != null) {
            val isDecoyMatch = PinHasher.verify(enteredPin, decoySalt, decoyHash)
            if (isDecoyMatch) {
                if (PinHasher.needsUpgrade(decoyHash)) {
                    vaultPrefs.edit().putString("decoy_pin_hash", hashPin(enteredPin, decoySalt)).apply()
                }
                failedAttempts = 0
                lockoutUntilTimestamp = 0L
                _isUnlocked.value = true
                _isVaultUnlocked.value = true
                _isDecoyModeActive.value = true
                recordUserActivity()
                logAudit("DECOY_VAULT_OPENED", "Decoy Vault triggered via Fake PIN")
                return VaultAuthResult.DECOY_UNLOCKED
            }
        }

        failedAttempts++
        if (failedAttempts >= 5) {
            lockoutUntilTimestamp = System.currentTimeMillis() + 30_000L
            logAudit("FAILED_UNLOCK", "5 consecutive failed PIN attempts. Cooldown 30s.")
        }
        return VaultAuthResult.INVALID
    }

    suspend fun disableAppLock(currentPin: String): Boolean {
        val current = securityDao.getSecurityConfig() ?: return false
        if (currentPin.isBlank() || current.pinHash == null || current.salt == null ||
            !PinHasher.verify(currentPin, current.salt, current.pinHash)
        ) return false
        val updated = current.copy(
            isAppLockEnabled = false,
            pinHash = null,
            salt = null
        )
        securityDao.saveSecurityConfig(updated)
        _isUnlocked.value = true
        _isVaultUnlocked.value = false
        logAudit("SETTINGS_CHANGED", "App lock disabled")
        return true
    }

    private var failedAttempts: Int = 0
    private var lockoutUntilTimestamp: Long = 0L

    fun getLockoutRemainingSeconds(): Long {
        val remaining = lockoutUntilTimestamp - System.currentTimeMillis()
        return if (remaining > 0) (remaining + 999) / 1000 else 0L
    }

    suspend fun verifyAppPin(enteredPin: String): Boolean {
        val res = verifyVaultOrAppPin(enteredPin)
        return res == VaultAuthResult.REAL_UNLOCKED || res == VaultAuthResult.DECOY_UNLOCKED
    }

    fun unlockWithBiometrics() {
        failedAttempts = 0
        lockoutUntilTimestamp = 0L
        _isUnlocked.value = true
        _isVaultUnlocked.value = true
        _isDecoyModeActive.value = false
        recordUserActivity()
        scope.launch {
            logAudit("APP_UNLOCKED", "Successful Biometric unlock")
        }
    }

    // Parental Controls
    suspend fun setupParentalControl(
        parentPin: String,
        blockDelete: Boolean,
        blockExport: Boolean,
        blockShare: Boolean,
        blockEdit: Boolean,
        blockAddSource: Boolean,
        blockSettings: Boolean
    ) {
        val salt = generateSalt()
        val hash = hashPin(parentPin, salt)
        val current = securityDao.getSecurityConfig() ?: SecurityConfig()
        val updated = current.copy(
            isParentalControlEnabled = true,
            parentPinHash = hash,
            parentPinSalt = salt,
            userRole = "RESTRICTED",
            blockDelete = blockDelete,
            blockExport = blockExport,
            blockShare = blockShare,
            blockEdit = blockEdit,
            blockAddSource = blockAddSource,
            blockSettings = blockSettings
        )
        securityDao.saveSecurityConfig(updated)
        _isParentAuthorized.value = true
        logAudit("PARENTAL_CONTROL_CHANGED", "Parental controls configured and activated")
    }

    suspend fun verifyParentPin(enteredPin: String): Boolean {
        val config = securityDao.getSecurityConfig() ?: return true
        if (!config.isParentalControlEnabled || config.parentPinHash == null || config.parentPinSalt == null) {
            _isParentAuthorized.value = true
            return true
        }

        if (System.currentTimeMillis() < lockoutUntilTimestamp) {
            logAudit("FAILED_UNLOCK", "Parent authorization attempted during lockout cooldown")
            return false
        }

        val matches = PinHasher.verify(enteredPin, config.parentPinSalt, config.parentPinHash)

        if (matches) {
            if (PinHasher.needsUpgrade(config.parentPinHash)) {
                securityDao.saveSecurityConfig(config.copy(parentPinHash = hashPin(enteredPin, config.parentPinSalt)))
            }
            failedAttempts = 0
            lockoutUntilTimestamp = 0L
            _isParentAuthorized.value = true
            logAudit("APP_UNLOCKED", "Parent authorization verified")
        } else {
            failedAttempts++
            if (failedAttempts >= 5) {
                lockoutUntilTimestamp = System.currentTimeMillis() + 30_000L
                logAudit("FAILED_UNLOCK", "5 consecutive failed Parent PIN attempts. Cooldown activated for 30s.")
            } else {
                logAudit("FAILED_UNLOCK", "Parent authorization failed ($failedAttempts/5)")
            }
        }
        return matches
    }

    fun clearParentAuthorization() {
        _isParentAuthorized.value = false
    }

    suspend fun canPerformAction(action: SecurityAction): Boolean {
        val config = securityDao.getSecurityConfig() ?: return true
        if (!config.isParentalControlEnabled || _isParentAuthorized.value) {
            return true
        }
        val isBlocked = when (action) {
            SecurityAction.DELETE -> config.blockDelete
            SecurityAction.EXPORT -> config.blockExport
            SecurityAction.SHARE -> config.blockShare
            SecurityAction.EDIT -> config.blockEdit
            SecurityAction.ADD_SOURCE -> config.blockAddSource
            SecurityAction.MODIFY_SETTINGS -> config.blockSettings
            SecurityAction.PLAY -> false
        }
        if (isBlocked) {
            logAudit("ACTION_BLOCKED", "Blocked action: ${action.name} by Parental Controls")
        }
        return !isBlocked
    }

    // Protected Items (Safe Vault items)
    suspend fun toggleItemProtection(itemType: String, itemId: Int): Boolean {
        val isProtected = securityDao.isItemProtected(itemType, itemId)
        if (isProtected) {
            securityDao.removeProtection(itemType, itemId)
            logAudit("VAULT_ITEM_REMOVED", "Removed protection for $itemType $itemId")
            return false
        } else {
            securityDao.protectItem(ProtectedItem(itemType, itemId))
            logAudit("VAULT_ITEM_ADDED", "Added protection for $itemType $itemId")
            return true
        }
    }

    suspend fun isMediaProtected(mediaId: Int): Boolean {
        return securityDao.isItemProtected("MEDIA", mediaId)
    }

    suspend fun setScreenshotProtection(enabled: Boolean) {
        val current = securityDao.getSecurityConfig() ?: SecurityConfig()
        securityDao.saveSecurityConfig(current.copy(isScreenshotProtectionEnabled = enabled))
        logAudit("SETTINGS_CHANGED", "Screenshot protection set to $enabled")
    }

    suspend fun logAudit(eventType: String, details: String) {
        val config = securityDao.getSecurityConfig()
        securityDao.insertAuditLog(
            SecurityAuditLog(
                eventType = eventType,
                timestamp = System.currentTimeMillis(),
                details = details,
                userRole = if (_isParentAuthorized.value) "PARENT" else (config?.userRole ?: "OWNER")
            )
        )
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String = PinHasher.hash(pin, salt)
}
