package com.example.modelmanager

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models")
    fun getAllModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE modelId = :modelId")
    suspend fun getModelById(modelId: String): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(model: AiModelEntity)

    @Update
    suspend fun update(model: AiModelEntity)
    
    @Query("DELETE FROM ai_models WHERE modelId = :modelId")
    suspend fun deleteModel(modelId: String)
}
