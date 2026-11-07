package com.example.bookmarks

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoBookmarkDao {
    @Query("SELECT * FROM video_bookmarks WHERE mediaId = :mediaId ORDER BY timestampMs ASC")
    fun getBookmarksForMediaFlow(mediaId: Int): Flow<List<VideoBookmark>>

    @Query("SELECT * FROM video_bookmarks WHERE mediaId = :mediaId ORDER BY timestampMs ASC")
    suspend fun getBookmarksForMediaSync(mediaId: Int): List<VideoBookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: VideoBookmark): Long

    @Delete
    suspend fun deleteBookmark(bookmark: VideoBookmark)

    @Query("DELETE FROM video_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Int)

    @Query("DELETE FROM video_bookmarks WHERE mediaId = :mediaId")
    suspend fun deleteAllBookmarksForMedia(mediaId: Int)
}
