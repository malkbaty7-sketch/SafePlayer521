package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.WatchedState
import com.example.scanner.MediaScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MediaRepository,
    private val scanner: MediaScanner
) : ViewModel() {

    val scanProgress = scanner.progress

    val allMedia: StateFlow<List<MediaItem>> = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val videos: StateFlow<List<MediaItem>> = repository.videos
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val audios: StateFlow<List<MediaItem>> = repository.audios
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val continueWatching: StateFlow<List<MediaItem>> = repository.continueWatching
        .map { list -> list.filter { !it.isMissing }.sortedByDescending { it.lastPlayedAt } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val recentlyPlayed: StateFlow<List<MediaItem>> = repository.allMedia
        .map { list -> list.filter { it.lastPlayedAt > 0 && !it.isMissing }.sortedByDescending { it.lastPlayedAt }.take(10) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favorites: StateFlow<List<MediaItem>> = repository.favorites
        .map { list -> list.filter { !it.isMissing } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lastScanJob = repository.lastScanJob
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun startScan() {
        if (!scanProgress.value.isScanning) {
            viewModelScope.launch {
                scanner.scanMedia()
            }
        }
    }
    
    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }
    
    fun markAsNotNew(id: Int) {
        viewModelScope.launch {
            repository.markAsNotNew(id)
        }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            repository.deleteByPath(item.path)
        }
    }
}

class MainViewModelFactory(
    private val repository: MediaRepository,
    private val scanner: MediaScanner
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, scanner) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
