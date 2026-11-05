package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.SafePlayerApp
import com.example.ai.DetectionResult
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class AiReviewViewModel(
    private val repository: MediaRepository,
    private val mediaId: Int
) : ViewModel() {
    val results = repository.getDetectionResultsForMedia(mediaId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val mediaItem: StateFlow<MediaItem?> = repository.getMediaById(mediaId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    fun updateResultStatus(resultId: Int, status: String) {
        viewModelScope.launch {
            repository.updateDetectionResultStatus(resultId, status)
        }
    }
}

class AiReviewViewModelFactory(
    private val app: SafePlayerApp,
    private val mediaId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AiReviewViewModel(app.repository, mediaId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReviewScreen(
    app: SafePlayerApp,
    mediaId: Int,
    onNavigateBack: () -> Unit,
    onPlayScene: (Int, Long) -> Unit
) {
    val viewModel: AiReviewViewModel = viewModel(factory = AiReviewViewModelFactory(app, mediaId))
    val results by viewModel.results.collectAsState()
    val media by viewModel.mediaItem.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Detections Review") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(media?.name ?: "Loading...", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
            }
            
            val pending = results.filter { it.status == "PENDING_REVIEW" }
            val accepted = results.filter { it.status == "ACCEPTED" }
            val rejected = results.filter { it.status == "REJECTED" }
            
            if (pending.isNotEmpty()) {
                item { Text("Pending Review (${pending.size})", fontWeight = FontWeight.Bold) }
                items(pending) { result ->
                    DetectionCard(result, onPlayScene, viewModel::updateResultStatus)
                }
            }
            if (accepted.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)); Text("Accepted (${accepted.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                items(accepted) { result ->
                    DetectionCard(result, onPlayScene, viewModel::updateResultStatus)
                }
            }
            if (rejected.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)); Text("Rejected (${rejected.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(rejected) { result ->
                    DetectionCard(result, onPlayScene, viewModel::updateResultStatus)
                }
            }
        }
    }
}

@Composable
fun DetectionCard(
    result: DetectionResult,
    onPlayScene: (Int, Long) -> Unit,
    onUpdateStatus: (Int, String) -> Unit
) {
    val color = when (result.status) {
        "ACCEPTED" -> MaterialTheme.colorScheme.primaryContainer
        "REJECTED" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(result.category, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${(result.confidence * 100).toInt()}% Confidence")
            }
            Text("Model: ${result.modelId} (${result.modelVersion})", style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPlayScene(result.mediaId, result.startTimeMs) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Segment")
                }
                Text("${formatTime(result.startTimeMs)} → ${formatTime(result.endTimeMs)}")
                Spacer(Modifier.weight(1f))
                
                if (result.status != "ACCEPTED") {
                    IconButton(onClick = { onUpdateStatus(result.id, "ACCEPTED") }) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (result.status != "REJECTED") {
                    IconButton(onClick = { onUpdateStatus(result.id, "REJECTED") }) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (result.status != "PENDING_REVIEW") {
                    TextButton(onClick = { onUpdateStatus(result.id, "PENDING_REVIEW") }) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
