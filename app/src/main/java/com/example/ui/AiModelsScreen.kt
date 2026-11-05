package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import com.example.modelmanager.AiModelEntity
import com.example.modelmanager.ModelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiModelsViewModel(private val modelManager: ModelManager) : ViewModel() {
    val models = modelManager.allModels
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val downloadProgress = modelManager.downloadProgress
    
    fun downloadModel(model: AiModelEntity) {
        viewModelScope.launch {
            modelManager.downloadModel(model)
        }
    }
    
    fun deleteModel(model: AiModelEntity) {
        viewModelScope.launch {
            modelManager.deleteModel(model)
        }
    }
}

class AiModelsViewModelFactory(private val modelManager: ModelManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AiModelsViewModel(modelManager) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelsScreen(
    app: SafePlayerApp,
    onNavigateBack: () -> Unit
) {
    val viewModel: AiModelsViewModel = viewModel(factory = AiModelsViewModelFactory(app.modelManager))
    val models by viewModel.models.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Models & Engine") },
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
                Text("Installed Models", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
            }
            
            items(models) { model ->
                ModelCard(
                    model = model,
                    isDownloading = progress.isDownloading && progress.modelId == model.modelId,
                    downloadProgress = progress.progress,
                    onDownload = { viewModel.downloadModel(model) },
                    onDelete = { viewModel.deleteModel(model) }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: AiModelEntity,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(model.version, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("Type: ${model.analysisType} | Size: ${model.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
            Text("Status: ${model.status}", style = MaterialTheme.typography.bodyMedium, color = if (model.status == "INSTALLED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth())
                Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (model.status == "AVAILABLE" || model.status == "FAILED") {
                    Button(onClick = onDownload, enabled = !isDownloading) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download")
                    }
                } else if (model.status == "INSTALLED" && model.modelId != "mlkit-image-labeling-default") {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
