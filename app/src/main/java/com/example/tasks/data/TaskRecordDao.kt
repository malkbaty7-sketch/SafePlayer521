package com.example.tasks.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskRecord): Long

    @Update
    suspend fun update(task: TaskRecord)

    @Delete
    suspend fun delete(task: TaskRecord)

    @Query("SELECT * FROM task_records WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskRecord?

    @Query("SELECT * FROM task_records ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM task_records WHERE status IN ('PENDING', 'QUEUED', 'PREPARING', 'RUNNING') ORDER BY CASE priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END, createdAt ASC")
    fun getActiveAndQueuedTasksFlow(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM task_records WHERE status IN ('RUNNING', 'PREPARING')")
    suspend fun getRunningTasks(): List<TaskRecord>

    @Query("SELECT * FROM task_records WHERE status IN ('PENDING', 'QUEUED') ORDER BY CASE priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END, createdAt ASC")
    suspend fun getQueuedTasks(): List<TaskRecord>

    @Query("SELECT * FROM task_records WHERE status = :status ORDER BY createdAt DESC")
    fun getTasksByStatusFlow(status: String): Flow<List<TaskRecord>>

    @Query("SELECT * FROM task_records WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getTasksForProjectFlow(projectId: Int): Flow<List<TaskRecord>>

    @Query("SELECT * FROM task_records WHERE taskType = :taskType AND mediaId = :mediaId AND status IN ('PENDING', 'QUEUED', 'RUNNING') LIMIT 1")
    suspend fun findExistingPendingOrRunning(taskType: String, mediaId: Int): TaskRecord?

    @Query("DELETE FROM task_records WHERE status IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearCompleted()

    @Query("UPDATE task_records SET status = 'INTERRUPTED' WHERE status IN ('RUNNING', 'PREPARING')")
    suspend fun markRunningAsInterrupted()
}
