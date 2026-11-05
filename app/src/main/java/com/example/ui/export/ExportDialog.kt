package com.example.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.EditingOperation
import com.example.data.EditingProject
import com.example.data.ExportTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    project: EditingProject,
    operations: List<EditingOperation>,
    viewModel: ExportViewModel,
    onDismiss: () -> Unit
) {
    val tasks by viewModel.getTasksForProject(project.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedQuality by remember { mutableStateOf("High") }
    
    val activeTask = tasks.firstOrNull { it.status == "PROCESSING" || it.status == "PREPARING" }
    
    AlertDialog(
        onDismissRequest = {
            if (activeTask == null) onDismiss()
        },
        title = { Text("Export Project") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activeTask != null) {
                    Text("Exporting... ${activeTask.progress}%")
                    LinearProgressIndicator(
                        progress = { activeTask.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("Status: ${activeTask.status}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Ready to export.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Quality:")
                    Row {
                        FilterChip(
                            selected = selectedQuality == "High",
                            onClick = { selectedQuality = "High" },
                            label = { Text("High") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedQuality == "Medium",
                            onClick = { selectedQuality = "Medium" },
                            label = { Text("Medium") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedQuality == "Low",
                            onClick = { selectedQuality = "Low" },
                            label = { Text("Low") }
                        )
                    }
                    
                    if (tasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Recent Exports", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.height(150.dp)) {
                            items(tasks) { task ->
                                ListItem(
                                    headlineContent = { Text("Status: ${task.status}") },
                                    supportingContent = { Text("Quality: ${task.quality}") },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (activeTask != null) {
                TextButton(onClick = { viewModel.cancelExport() }) {
                    Text("Cancel Export")
                }
            } else {
                Button(onClick = { viewModel.startExport(project, operations, selectedQuality) }) {
                    Text("Start Export")
                }
            }
        },
        dismissButton = {
            if (activeTask == null) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
