package com.example.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.SafePlayerApp
import com.example.tasks.data.TaskRecord
import com.example.tasks.resource.PerformancePreset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCenterScreen(
    app: SafePlayerApp,
    onNavigateBack: () -> Unit
) {
    val taskManager = app.taskManager
    val resourceMonitor = taskManager.resourceMonitor
    val scope = rememberCoroutineScope()

    val allTasks by taskManager.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val resourceSnapshot by resourceMonitor.currentSnapshot.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Active", "Queued", "Completed", "Failed")

    val filteredTasks = remember(allTasks, selectedTab) {
        when (selectedTab) {
            1 -> allTasks.filter { it.status == "RUNNING" || it.status == "PREPARING" }
            2 -> allTasks.filter { it.status == "QUEUED" || it.status == "PENDING" }
            3 -> allTasks.filter { it.status == "COMPLETED" }
            4 -> allTasks.filter { it.status == "FAILED" || it.status == "INTERRUPTED" || it.status == "CANCELLED" }
            else -> allTasks
        }
    }

    var selectedTaskForDetails by remember { mutableStateOf<TaskRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task & Resource Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            taskManager.enqueueTask(
                                taskType = "STORAGE_CLEANUP",
                                title = "Storage & Cache Purge",
                                priority = "LOW",
                                resourceRequirements = "LIGHT_IO"
                            )
                        }
                    }) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Clean Cache")
                    }
                    IconButton(onClick = { taskManager.clearCompletedTasks() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Completed")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Resource Monitor Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Resources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (resourceSnapshot.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (resourceSnapshot.batteryPercent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("${resourceSnapshot.batteryPercent}%", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(resourceSnapshot.thermalStatus) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // RAM info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("RAM Available: ${resourceSnapshot.availableRamMb} MB / ${resourceSnapshot.totalRamMb} MB", style = MaterialTheme.typography.bodySmall)
                        if (resourceSnapshot.isLowMemory) {
                            Text("LOW MEMORY", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Performance Preset Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preset:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = resourceSnapshot.performancePreset == PerformancePreset.BATTERY_SAVER,
                                onClick = { resourceMonitor.setPerformancePreset(PerformancePreset.BATTERY_SAVER) },
                                label = { Text("Saver", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = resourceSnapshot.performancePreset == PerformancePreset.BALANCED,
                                onClick = { resourceMonitor.setPerformancePreset(PerformancePreset.BALANCED) },
                                label = { Text("Balanced", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = resourceSnapshot.performancePreset == PerformancePreset.PERFORMANCE,
                                onClick = { resourceMonitor.setPerformancePreset(PerformancePreset.PERFORMANCE) },
                                label = { Text("Performance", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tasks List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks found in this category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onCancel = { taskManager.cancelTask(task.id) },
                            onRetry = { taskManager.retryTask(task.id) },
                            onClick = { selectedTaskForDetails = task }
                        )
                    }
                }
            }
        }
    }

    // Task Details Dialog
    selectedTaskForDetails?.let { task ->
        AlertDialog(
            onDismissRequest = { selectedTaskForDetails = null },
            title = { Text(task.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Task ID: ${task.id}", style = MaterialTheme.typography.bodySmall)
                    Text("Type: ${task.taskType}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.format_status, task.status.toString()), style = MaterialTheme.typography.bodySmall)
                    Text("Priority: ${task.priority}", style = MaterialTheme.typography.bodySmall)
                    Text("Pipeline Stage: ${task.pipelineStage ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.format_progress, task.progress), style = MaterialTheme.typography.bodySmall)
                    Text("Retries: ${task.retryCount} / ${task.maxRetries}", style = MaterialTheme.typography.bodySmall)
                    Text("Resource Requirement: ${task.resourceRequirements}", style = MaterialTheme.typography.bodySmall)
                    task.errorMessage?.let {
                        Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTaskForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TaskItemCard(
    task: TaskRecord,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        task.taskType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(status = task.status)
            }

            Spacer(Modifier.height(8.dp))

            if (task.status == "RUNNING" || task.status == "PREPARING") {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        task.pipelineStage ?: "Processing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("${task.progress}%", style = MaterialTheme.typography.labelSmall)
                }
            } else if (task.errorMessage != null) {
                Text(
                    stringResource(R.string.format_error, task.errorMessage ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    task.pipelineStage ?: stringResource(R.string.format_status, task.status.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriorityBadge(priority = task.priority)

                Row {
                    if (task.status == "RUNNING" || task.status == "QUEUED" || task.status == "PREPARING") {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (task.status == "FAILED" || task.status == "INTERRUPTED" || task.status == "CANCELLED") {
                        FilledTonalButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, text) = when (status) {
        "RUNNING" -> Pair(MaterialTheme.colorScheme.primary, "Running")
        "PREPARING" -> Pair(MaterialTheme.colorScheme.tertiary, "Preparing")
        "QUEUED" -> Pair(MaterialTheme.colorScheme.secondary, "Queued")
        "COMPLETED" -> Pair(Color(0xFF2E7D32), "Completed")
        "FAILED" -> Pair(MaterialTheme.colorScheme.error, "Failed")
        "INTERRUPTED" -> Pair(MaterialTheme.colorScheme.error, "Interrupted")
        "CANCELLED" -> Pair(MaterialTheme.colorScheme.outline, "Cancelled")
        else -> Pair(MaterialTheme.colorScheme.outline, status)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val color = when (priority) {
        "CRITICAL" -> MaterialTheme.colorScheme.error
        "HIGH" -> Color(0xFFE65100)
        "NORMAL" -> MaterialTheme.colorScheme.onSurfaceVariant
        "LOW" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = "Priority: $priority",
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium
    )
}
