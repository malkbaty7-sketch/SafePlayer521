package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportTaskDao {
    @Insert
    suspend fun insert(task: ExportTask): Long

    @Update
    suspend fun update(task: ExportTask)

    @Query("SELECT * FROM export_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): ExportTask?

    @Query("SELECT * FROM export_tasks ORDER BY startTime DESC")
    fun getAllTasks(): Flow<List<ExportTask>>

    @Query("SELECT * FROM export_tasks WHERE projectId = :projectId ORDER BY startTime DESC")
    fun getTasksForProject(projectId: Int): Flow<List<ExportTask>>
    
    @Query("DELETE FROM export_tasks WHERE id = :id")
    suspend fun delete(id: Int)
}
