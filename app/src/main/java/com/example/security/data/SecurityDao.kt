package com.example.security.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityDao {
    @Query("SELECT * FROM security_configs WHERE id = 1 LIMIT 1")
    fun getSecurityConfigFlow(): Flow<SecurityConfig?>

    @Query("SELECT * FROM security_configs WHERE id = 1 LIMIT 1")
    suspend fun getSecurityConfig(): SecurityConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSecurityConfig(config: SecurityConfig)

    // Protected Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun protectItem(item: ProtectedItem)

    @Delete
    suspend fun unprotectItem(item: ProtectedItem)

    @Query("DELETE FROM protected_items WHERE itemType = :itemType AND itemId = :itemId")
    suspend fun removeProtection(itemType: String, itemId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM protected_items WHERE itemType = :itemType AND itemId = :itemId)")
    suspend fun isItemProtected(itemType: String, itemId: Int): Boolean

    @Query("SELECT * FROM protected_items")
    fun getAllProtectedItemsFlow(): Flow<List<ProtectedItem>>

    @Query("SELECT itemId FROM protected_items WHERE itemType = :itemType")
    fun getProtectedItemIdsFlow(itemType: String): Flow<List<Int>>

    @Query("SELECT itemId FROM protected_items WHERE itemType = :itemType")
    suspend fun getProtectedItemIds(itemType: String): List<Int>

    // Audit Logs
    @Insert
    suspend fun insertAuditLog(log: SecurityAuditLog): Long

    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditLogsFlow(): Flow<List<SecurityAuditLog>>

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearAuditLogs()
}
