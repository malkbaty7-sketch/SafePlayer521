package com.example.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.SafePlayerApp
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.Playlist
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    app: SafePlayerApp,
    onNavigateBack: () -> Unit,
    onPlayMedia: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val playlists by app.repository.allPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (selectedPlaylist != null) {
        // Detailed playlist contents view
        val current = selectedPlaylist!!
        val playlistItems by app.repository.getMediaForPlaylist(current.id).collectAsStateWithLifecycle(initialValue = emptyList())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current.name, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                app.repository.deletePlaylist(current)
                                selectedPlaylist = null
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("${playlistItems.size} Items in playlist", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }

                if (playlistItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlaylistRemove, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text("This playlist is currently empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Add media to this playlist from the Media Details screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    items(playlistItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayMedia(item.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("${item.folder} • ${formatDuration(item.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        app.repository.removeMediaFromPlaylist(current.id, item.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Main Playlists Overview
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlists", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newPlaylistName = ""
                    showCreateDialog = true
                },
                icon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                text = { Text("New Playlist") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("SafePlayer Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Organize and group your favorite videos and audio files into customized offline collections.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("No playlists created yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { showCreateDialog = true }) {
                                    Text("Create First Playlist")
                                }
                            }
                        }
                    }
                }
            } else {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylist = playlist },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FeaturedPlayList, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(playlist.createdAt))
                                Text("Created: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            scope.launch {
                                app.repository.createPlaylist(newPlaylistName.trim())
                                showCreateDialog = false
                            }
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
