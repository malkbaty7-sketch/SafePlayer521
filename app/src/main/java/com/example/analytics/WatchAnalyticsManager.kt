package com.example.analytics

import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.MediaType
import com.example.data.WatchedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WatchAnalyticsSummary(
    val totalVideosCount: Int,
    val totalAudiosCount: Int,
    val inProgressCount: Int,
    val completedCount: Int,
    val favoritesCount: Int,
    val totalLibraryDurationMs: Long,
    val totalWatchProgressMs: Long,
    val mostRecentPlayed: List<MediaItem>
)

class WatchAnalyticsManager(
    private val repository: MediaRepository
) {
    val analyticsSummaryFlow: Flow<WatchAnalyticsSummary> = repository.allMedia.map { mediaList ->
        var videoCount = 0
        var audioCount = 0
        var inProgress = 0
        var completed = 0
        var favorites = 0
        var totalDur = 0L
        var totalProgress = 0L

        mediaList.forEach { item ->
            if (item.mediaType == MediaType.VIDEO) videoCount++ else audioCount++
            if (item.isFavorite) favorites++
            totalDur += item.durationMs
            totalProgress += item.lastPositionMs

            when (item.watchedState) {
                WatchedState.IN_PROGRESS -> inProgress++
                WatchedState.WATCHED -> completed++
                WatchedState.UNWATCHED -> {}
            }
        }

        val recent = mediaList
            .filter { it.lastPositionMs > 0 || it.lastPlayedAt > 0 }
            .sortedByDescending { if (it.lastPlayedAt > 0) it.lastPlayedAt else it.dateModified }
            .take(6)

        WatchAnalyticsSummary(
            totalVideosCount = videoCount,
            totalAudiosCount = audioCount,
            inProgressCount = inProgress,
            completedCount = completed,
            favoritesCount = favorites,
            totalLibraryDurationMs = totalDur,
            totalWatchProgressMs = totalProgress,
            mostRecentPlayed = recent
        )
    }
}
