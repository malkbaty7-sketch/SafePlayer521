package com.example.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {
    val allMedia: Flow<List<MediaItem>> = mediaDao.getAllMedia()
    val videos: Flow<List<MediaItem>> = mediaDao.getMediaByType(MediaType.VIDEO)
    val audios: Flow<List<MediaItem>> = mediaDao.getMediaByType(MediaType.AUDIO)
    val favorites: Flow<List<MediaItem>> = mediaDao.getFavorites()
    val continueWatching: Flow<List<MediaItem>> = mediaDao.getByWatchedState(WatchedState.IN_PROGRESS)

    fun getMediaById(id: Int): Flow<MediaItem?> = mediaDao.getMediaById(id)

    suspend fun insertOrUpdate(media: MediaItem) {
        val existing = mediaDao.getMediaByPath(media.path)
        if (existing != null) {
            val updated = existing.copy(
                name = media.name,
                folder = media.folder,
                sizeBytes = media.sizeBytes,
                dateModified = media.dateModified,
                durationMs = media.durationMs
            )
            mediaDao.updateMedia(updated)
        } else {
            mediaDao.insertMedia(media)
        }
    }

    suspend fun deleteMissing(currentPaths: Set<String>) {
        // Find existing paths in DB
        // (In a real app, you might want to mark them as missing instead of deleting to keep history,
        // but for Phase 1, detecting missing can be deleting them or just marking).
        // For simplicity and to save memory, we can delete them, or keep them with a "missing" flag.
        // Let's implement a missing flag later if needed, but for now we will just use a specific DAO query.
    }
    
    suspend fun getMediaByPath(path: String) = mediaDao.getMediaByPath(path)

    suspend fun updatePlaybackState(id: Int, state: WatchedState, position: Long) {
        mediaDao.updatePlaybackState(id, state, position)
    }

    suspend fun markAsNotNew(id: Int) {
        mediaDao.markAsNotNew(id)
    }
    
    suspend fun toggleFavorite(media: MediaItem) {
        mediaDao.updateMedia(media.copy(isFavorite = !media.isFavorite))
    }
    
    suspend fun deleteByPath(path: String) {
        mediaDao.deleteByPath(path)
    }

    // --- Media Source ---
    val allSources: Flow<List<MediaSource>> = mediaDao.getAllSources()
    
    suspend fun addSource(source: MediaSource) = mediaDao.insertSource(source)
    suspend fun removeSource(id: Int) = mediaDao.deleteSource(id)

    // --- Playlists ---
    val allPlaylists: Flow<List<Playlist>> = mediaDao.getAllPlaylists()
    suspend fun createPlaylist(name: String): Long = mediaDao.insertPlaylist(Playlist(name = name, createdAt = System.currentTimeMillis()))
    suspend fun deletePlaylist(playlist: Playlist) = mediaDao.deletePlaylist(playlist)
    suspend fun addMediaToPlaylist(playlistId: Int, mediaId: Int, orderIndex: Int = 0) = mediaDao.addMediaToPlaylist(PlaylistMediaCrossRef(playlistId, mediaId, orderIndex))
    suspend fun removeMediaFromPlaylist(playlistId: Int, mediaId: Int) = mediaDao.removeMediaFromPlaylist(playlistId, mediaId)
    fun getMediaForPlaylist(playlistId: Int): Flow<List<MediaItem>> = mediaDao.getMediaForPlaylist(playlistId)

    // --- Media Integrity & Relink ---
    val missingMedia: Flow<List<MediaItem>> = mediaDao.getMissingMedia()
    suspend fun relinkMedia(mediaId: Int, newPath: String) = mediaDao.relinkMedia(mediaId, newPath)

    // --- Scan Job ---
    val lastScanJob: Flow<ScanJob?> = mediaDao.getLastScanJob()
    
    suspend fun insertScanJob(job: ScanJob): Long = mediaDao.insertScanJob(job)
    suspend fun updateScanJob(job: ScanJob) = mediaDao.updateScanJob(job)

    // --- Scene Analysis ---
    fun getScenesForMedia(mediaId: Int): Flow<List<Scene>> = mediaDao.getScenesForMedia(mediaId)
    
    suspend fun saveScene(scene: Scene): Long = mediaDao.insertScene(scene)
    
    suspend fun clearScenesForMedia(mediaId: Int) = mediaDao.deleteScenesForMedia(mediaId)
    
    suspend fun updateAnalysisStatus(mediaId: Int, status: String, size: Long, date: Long) {
        mediaDao.updateAnalysisStatus(mediaId, status, size, date)
    }

    // --- Local AI Analysis ---
    fun getDetectionResultsForMedia(mediaId: Int): Flow<List<com.example.ai.DetectionResult>> = mediaDao.getDetectionResultsForMedia(mediaId)
    
    suspend fun saveDetectionResult(result: com.example.ai.DetectionResult) = mediaDao.insertDetectionResult(result)
    
    suspend fun clearDetectionResultsForMedia(mediaId: Int) = mediaDao.deleteDetectionResultsForMedia(mediaId)
    
    suspend fun updateAiAnalysisStatus(mediaId: Int, status: String, size: Long, date: Long) {
        mediaDao.updateAiAnalysisStatus(mediaId, status, size, date)
    }
    
    suspend fun updateDetectionResultStatus(resultId: Int, status: String) {
        mediaDao.updateDetectionResultStatus(resultId, status)
    }
    
    // --- Processing Jobs ---
    val allProcessingJobs: Flow<List<com.example.processing.ProcessingJob>> = mediaDao.getAllProcessingJobs()
    
    suspend fun getActiveProcessingJobs() = mediaDao.getActiveProcessingJobs()
    
    suspend fun insertProcessingJob(job: com.example.processing.ProcessingJob): Long = mediaDao.insertProcessingJob(job)
    
    suspend fun updateProcessingJob(job: com.example.processing.ProcessingJob) = mediaDao.updateProcessingJob(job)
    
    suspend fun getProcessingJobById(id: Int) = mediaDao.getProcessingJobById(id)

    // --- Timeline Markers ---
    fun getMarkersForMedia(mediaId: Int): Flow<List<TimelineMarker>> = mediaDao.getMarkersForMedia(mediaId)
    
    suspend fun insertMarker(marker: TimelineMarker): Long = mediaDao.insertMarker(marker)
    
    suspend fun updateMarker(marker: TimelineMarker) = mediaDao.updateMarker(marker)
    
    suspend fun deleteMarker(markerId: Int) = mediaDao.deleteMarker(markerId)

    // --- Editing Projects ---
    fun getProjectsForMedia(mediaId: Int): Flow<List<EditingProject>> = mediaDao.getProjectsForMedia(mediaId)
    
    suspend fun getProjectById(projectId: Int) = mediaDao.getProjectById(projectId)
    
    suspend fun insertProject(project: EditingProject): Long = mediaDao.insertProject(project)
    
    suspend fun updateProject(project: EditingProject) = mediaDao.updateProject(project)
    
    // --- Editing Operations ---
    fun getOperationsForProject(projectId: Int): Flow<List<EditingOperation>> = mediaDao.getOperationsForProject(projectId)
    
    suspend fun insertOperation(operation: EditingOperation): Long = mediaDao.insertOperation(operation)
    
    suspend fun updateOperation(operation: EditingOperation) = mediaDao.updateOperation(operation)
    
    suspend fun deleteOperation(operationId: Int) = mediaDao.deleteOperation(operationId)
}
