package com.example.storage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageLocationDao {
    @Query("SELECT * FROM storage_locations")
    fun getAllLocations(): Flow<List<StorageLocation>>

    @Query("SELECT * FROM storage_locations WHERE id = :id")
    suspend fun getLocationById(id: Int): StorageLocation?

    @Query("SELECT * FROM storage_locations WHERE path = :path LIMIT 1")
    suspend fun getLocationByPath(path: String): StorageLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: StorageLocation): Long

    @Update
    suspend fun updateLocation(location: StorageLocation)
}
