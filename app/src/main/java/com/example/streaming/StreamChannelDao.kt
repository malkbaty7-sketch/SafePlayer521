package com.example.streaming

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamChannelDao {
    @Query("SELECT * FROM stream_channels ORDER BY isFavorite DESC, id DESC")
    fun getAllChannelsFlow(): Flow<List<StreamChannel>>

    @Query("SELECT * FROM stream_channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: Int): StreamChannel?

    @Query("SELECT DISTINCT groupTitle FROM stream_channels ORDER BY groupTitle ASC")
    fun getAllGroupsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: StreamChannel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<StreamChannel>)

    @Update
    suspend fun updateChannel(channel: StreamChannel)

    @Delete
    suspend fun deleteChannel(channel: StreamChannel)

    @Query("DELETE FROM stream_channels WHERE id = :id")
    suspend fun deleteChannelById(id: Int)

    @Query("DELETE FROM stream_channels")
    suspend fun deleteAllChannels()

    @Query("UPDATE stream_channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Int, isFavorite: Boolean)
}
