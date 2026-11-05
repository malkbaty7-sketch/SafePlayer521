package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY dateModified DESC")
    fun getAllMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY dateModified DESC")
    fun getMediaByType(type: MediaType): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY dateModified DESC")
    fun getFavorites(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE watchedState = :state ORDER BY dateModified DESC")
    fun getByWatchedState(state: WatchedState): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY dateModified DESC")
    suspend fun getAllMediaSync(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE path = :path LIMIT 1")
    suspend fun getMediaByPath(path: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun getMediaById(id: Int): Flow<MediaItem?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaByIdSync(id: Int): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItem): Long

    @Update
    suspend fun updateMedia(media: MediaItem)
    
    @androidx.room.Delete
    suspend fun deleteMedia(media: MediaItem)

    @Query("DELETE FROM media_items WHERE path = :path")
    suspend fun deleteByPath(path: String)
    
    @Query("UPDATE media_items SET watchedState = :state, lastPositionMs = :position WHERE id = :id")
    suspend fun updatePlaybackState(id: Int, state: WatchedState, position: Long)
    
    @Query("UPDATE media_items SET isNew = 0 WHERE id = :id")
    suspend fun markAsNotNew(id: Int)
    
    // --- Media Source ---
    @Query("SELECT * FROM media_sources")
    fun getAllSources(): Flow<List<MediaSource>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: MediaSource): Long
    
    @Query("DELETE FROM media_sources WHERE id = :id")
    suspend fun deleteSource(id: Int)
    
    // --- Scan Job ---
    @Query("SELECT * FROM scan_jobs ORDER BY startTime DESC")
    fun getAllScanJobs(): Flow<List<ScanJob>>
    
    @Query("SELECT * FROM scan_jobs ORDER BY startTime DESC LIMIT 1")
    fun getLastScanJob(): Flow<ScanJob?>
    
    @Insert
    suspend fun insertScanJob(job: ScanJob): Long
    
    @Update
    suspend fun updateScanJob(job: ScanJob)
    
    // --- Playlist ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeMediaFromPlaylist(playlistId: Int, mediaId: Int)

    @Query("SELECT m.* FROM media_items m INNER JOIN playlist_media_cross_ref r ON m.id = r.mediaId WHERE r.playlistId = :playlistId ORDER BY r.orderIndex ASC")
    fun getMediaForPlaylist(playlistId: Int): Flow<List<MediaItem>>

    @Query("UPDATE media_items SET path = :newPath, isMissing = 0 WHERE id = :mediaId")
    suspend fun relinkMedia(mediaId: Int, newPath: String)

    @Query("SELECT * FROM media_items WHERE isMissing = 1")
    fun getMissingMedia(): Flow<List<MediaItem>>
    // --- Scene Analysis ---
    @Query("SELECT * FROM scenes WHERE mediaId = :mediaId ORDER BY startTimeMs ASC")
    fun getScenesForMedia(mediaId: Int): Flow<List<Scene>>
    
    @Insert
    suspend fun insertScene(scene: Scene): Long
    
    @Query("DELETE FROM scenes WHERE mediaId = :mediaId")
    suspend fun deleteScenesForMedia(mediaId: Int)
    
    @Query("UPDATE media_items SET analysisStatus = :status, analyzedSize = :size, analyzedDate = :date WHERE id = :mediaId")
    suspend fun updateAnalysisStatus(mediaId: Int, status: String, size: Long, date: Long)

    // --- Local AI Analysis ---
    @Query("SELECT * FROM detection_results WHERE mediaId = :mediaId ORDER BY startTimeMs ASC")
    fun getDetectionResultsForMedia(mediaId: Int): kotlinx.coroutines.flow.Flow<List<com.example.ai.DetectionResult>>
    
    @Insert
    suspend fun insertDetectionResult(result: com.example.ai.DetectionResult)
    
    @Query("DELETE FROM detection_results WHERE mediaId = :mediaId")
    suspend fun deleteDetectionResultsForMedia(mediaId: Int)
    
    @Query("UPDATE media_items SET aiAnalysisStatus = :status, aiAnalyzedSize = :size, aiAnalyzedDate = :date WHERE id = :mediaId")
    suspend fun updateAiAnalysisStatus(mediaId: Int, status: String, size: Long, date: Long)
    
    @Query("UPDATE detection_results SET status = :status WHERE id = :resultId")
    suspend fun updateDetectionResultStatus(resultId: Int, status: String)
    
    // --- Processing Jobs ---
    @Query("SELECT * FROM processing_jobs ORDER BY startTime DESC")
    fun getAllProcessingJobs(): kotlinx.coroutines.flow.Flow<List<com.example.processing.ProcessingJob>>
    
    @Query("SELECT * FROM processing_jobs WHERE status IN ('QUEUED', 'PROCESSING')")
    suspend fun getActiveProcessingJobs(): List<com.example.processing.ProcessingJob>
    
    @Insert
    suspend fun insertProcessingJob(job: com.example.processing.ProcessingJob): Long
    
    @Update
    suspend fun updateProcessingJob(job: com.example.processing.ProcessingJob)
    
    @Query("SELECT * FROM processing_jobs WHERE id = :id")
    suspend fun getProcessingJobById(id: Int): com.example.processing.ProcessingJob?
    
    // --- Timeline Markers ---
    @Query("SELECT * FROM timeline_markers WHERE mediaId = :mediaId ORDER BY timeMs ASC")
    fun getMarkersForMedia(mediaId: Int): kotlinx.coroutines.flow.Flow<List<TimelineMarker>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: TimelineMarker): Long
    
    @Update
    suspend fun updateMarker(marker: TimelineMarker)
    
    @Query("DELETE FROM timeline_markers WHERE id = :markerId")
    suspend fun deleteMarker(markerId: Int)
    
    // --- Editing Projects ---
    @Query("SELECT * FROM editing_projects WHERE mediaId = :mediaId ORDER BY updatedAt DESC")
    fun getProjectsForMedia(mediaId: Int): kotlinx.coroutines.flow.Flow<List<EditingProject>>
    
    @Query("SELECT * FROM editing_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Int): EditingProject?

    @Insert
    suspend fun insertProject(project: EditingProject): Long

    @Update
    suspend fun updateProject(project: EditingProject)

    // --- Editing Operations ---
    @Query("SELECT * FROM editing_operations WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    fun getOperationsForProject(projectId: Int): kotlinx.coroutines.flow.Flow<List<EditingOperation>>

    @Insert
    suspend fun insertOperation(operation: EditingOperation): Long

    @Update
    suspend fun updateOperation(operation: EditingOperation)

    @Query("DELETE FROM editing_operations WHERE id = :operationId")
    suspend fun deleteOperation(operationId: Int)
}
