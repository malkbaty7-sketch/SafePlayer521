package com.example.ui.editing

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.SafePlayerApp
import com.example.data.EditingOperation
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditingScreen(
    app: SafePlayerApp,
    mediaId: Int,
    onNavigateBack: () -> Unit
) {
    val viewModel: EditingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EditingViewModel(app, app.repository) as T
            }
        }
    )

    LaunchedEffect(mediaId) {
        viewModel.loadMediaAndProject(mediaId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.player?.pause()
        }
    }

    val isPlayerReady by viewModel.isPlayerReady.collectAsState()
    val operations by viewModel.operations.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val project by viewModel.project.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    
    var editingOp by remember { mutableStateOf<EditingOperation?>(null) }
    var viewingConflict by remember { mutableStateOf<String?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderScrubPos by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    LaunchedEffect(isPlayerReady) {
        if (isPlayerReady) {
            while (true) {
                viewModel.updatePosition()
                delay(100)
            }
        }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    
    // Add ExportDialog when showExportDialog is true
    if (showExportDialog && project != null) {
        val app = context.applicationContext as com.example.SafePlayerApp
        val exportViewModel = remember { 
            com.example.ui.export.ExportViewModel(app.exportEngine, app.exportTaskDao) 
        }
        com.example.ui.export.ExportDialog(
            project = project!!,
            operations = operations,
            viewModel = exportViewModel,
            onDismiss = { showExportDialog = false }
        )
    }

    Scaffold(
        topBar = {
            val canUndo by viewModel.canUndo.collectAsState()
            val canRedo by viewModel.canRedo.collectAsState()
            
            TopAppBar(
                title = { 
                    Column {
                        Text(project?.name ?: "Editing Studio", style = MaterialTheme.typography.titleMedium)
                        Text("SafePlayer Advanced Manual Censorship", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    TextButton(onClick = { showExportDialog = true }) {
                        Text("Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- 1. Video Preview Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isPlayerReady && viewModel.player != null) {
                    AndroidView(
                        factory = {
                            PlayerView(context).apply {
                                player = viewModel.player
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Live Mask & Censor Overlays (with Keyframe Interpolation & Z-Ordering)
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        operations
                            .filter { it.isEnabled && it.type.startsWith("CENSOR") && currentPosition in it.startTimeMs..it.endTimeMs }
                            .map { op -> op to OperationProperties.fromJson(op.propertiesJson) }
                            .filter { (_, props) -> props.isVisible }
                            .sortedBy { (_, props) -> props.zOrder }
                            .forEach { (op, baseProps) ->
                            
                            val props = baseProps.getInterpolated(currentPosition)
                            
                            val x = props.x.coerceIn(0f, 1f)
                            val y = props.y.coerceIn(0f, 1f)
                            val w = props.width.coerceIn(0.05f, 1f)
                            val h = props.height.coerceIn(0.05f, 1f)
                            
                            val maskColor = when (op.type) {
                                "CENSOR_BLACK" -> Color.Black
                                "CENSOR_COVER" -> Color.Black.copy(alpha = 0.95f)
                                "CENSOR_BLUR" -> Color(0xFF64B5F6).copy(alpha = 0.65f) // Frosted glass representation
                                "CENSOR_PIXELATE" -> Color(0xFF7E57C2).copy(alpha = 0.65f) // Pixelation representation
                                else -> Color.Black
                            }

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = maxWidth * x,
                                        y = maxHeight * y
                                    )
                                    .size(
                                        width = maxWidth * w,
                                        height = maxHeight * h
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(maskColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = op.type.removePrefix("CENSOR_"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            
            // --- 2. Timeline & Multi-Track Control Deck ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Playback Transport Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.seekBy(-1000) }) {
                            Icon(Icons.Default.Replay, contentDescription = "-1s")
                        }
                        IconButton(onClick = { viewModel.seekBy(-40) }) {
                            Text("-1f", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        FilledIconButton(onClick = { viewModel.playPause() }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        IconButton(onClick = { viewModel.seekBy(40) }) {
                            Text("+1f", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.seekBy(1000) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "+1s")
                        }
                    }
                }
                
                // Multi-Track Timeline Overview (Visual representation of operations along duration)
                if (duration > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        operations.filter { it.isEnabled }.forEach { op ->
                            val startFraction = (op.startTimeMs.toFloat() / duration).coerceIn(0f, 1f)
                            val endFraction = (op.endTimeMs.toFloat() / duration).coerceIn(startFraction, 1f)
                            val trackColor = when {
                                op.type.startsWith("CENSOR") -> Color(0xFF2196F3)
                                op.type == "MUTE" -> Color(0xFFFF9800)
                                op.type == "DELETE" -> Color(0xFFE91E63)
                                else -> Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = endFraction)
                                    .padding(start = 0.dp) // Offset is handled via drawing or layout
                            )
                        }

                        // Current playhead line in track overview
                        val playheadFraction = (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .offset(x = 0.dp) // Playhead indicator
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Interactive Timeline Scrubber Slider
                val sliderValue = if (isDraggingSlider) {
                    sliderScrubPos
                } else {
                    if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { percent ->
                        isDraggingSlider = true
                        sliderScrubPos = percent
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        if (duration > 0) {
                            viewModel.seekTo((sliderScrubPos * duration).toLong())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                
                // Editing Action Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showCensorMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        Button(
                            onClick = { showCensorMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Censor")
                        }
                        DropdownMenu(
                            expanded = showCensorMenu,
                            onDismissRequest = { showCensorMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Black Mask (قناع أسود)") },
                                onClick = { 
                                    viewModel.addOperation("CENSOR_BLACK", currentPosition, currentPosition + 4000, OperationProperties().toJson())
                                    showCensorMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Blur (تمويه)") },
                                onClick = { 
                                    viewModel.addOperation("CENSOR_BLUR", currentPosition, currentPosition + 4000, OperationProperties(blur = 20).toJson())
                                    showCensorMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pixelate (بكسلة)") },
                                onClick = { 
                                    viewModel.addOperation("CENSOR_PIXELATE", currentPosition, currentPosition + 4000, OperationProperties(pixelSize = 16).toJson())
                                    showCensorMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Full-Screen Cover (حجب كامل الشاشة)") },
                                onClick = { 
                                    viewModel.addOperation("CENSOR_COVER", currentPosition, currentPosition + 4000, OperationProperties(x = 0f, y = 0f, width = 1f, height = 1f).toJson())
                                    showCensorMenu = false
                                }
                            )
                        }
                    }

                    FilledTonalButton(onClick = { 
                        viewModel.addOperation("MUTE", currentPosition, currentPosition + 3000)
                    }) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mute")
                    }

                    FilledTonalButton(onClick = { 
                        viewModel.addOperation("DELETE", currentPosition, currentPosition + 3000)
                    }) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cut")
                    }
                }
                
                // Operations List Track (Layer Manager)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (operations.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No editing operations yet. Tap Censor, Mute, or Cut to add an edit at the current time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(operations, key = { it.id }) { op ->
                            val props = remember(op.propertiesJson) { OperationProperties.fromJson(op.propertiesJson) }
                            
                            val opColor = when {
                                !props.isVisible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                op.type.startsWith("CENSOR") -> MaterialTheme.colorScheme.primaryContainer
                                op.type == "MUTE" -> MaterialTheme.colorScheme.tertiaryContainer
                                op.type == "DELETE" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (op.isEnabled) opColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = op.type,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (!op.isEnabled) {
                                                Spacer(Modifier.width(6.dp))
                                                Text("(Disabled)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                            
                                            val opConflicts = conflicts[op.id]
                                            if (!opConflicts.isNullOrEmpty()) {
                                                Spacer(Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.Warning, 
                                                    contentDescription = "Conflict", 
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp).clickable { 
                                                        viewingConflict = opConflicts.joinToString("\n") 
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            "${formatTime(op.startTimeMs)} ➔ ${formatTime(op.endTimeMs)} (${(op.endTimeMs - op.startTimeMs) / 1000}s)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (op.type.startsWith("CENSOR")) {
                                            IconButton(onClick = { 
                                                viewModel.updateOperation(op.copy(propertiesJson = props.copy(isVisible = !props.isVisible).toJson()))
                                            }) {
                                                Icon(if (props.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Visibility")
                                            }
                                            IconButton(onClick = { 
                                                viewModel.updateOperation(op.copy(propertiesJson = props.copy(isLocked = !props.isLocked).toJson()))
                                            }) {
                                                Icon(if (props.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Toggle Lock")
                                            }
                                        }
                                        
                                        IconButton(onClick = { viewModel.seekTo(op.startTimeMs) }) {
                                            Icon(Icons.Default.PlayCircleOutline, contentDescription = "Go to Start")
                                        }
                                        IconButton(onClick = { editingOp = op }, enabled = !props.isLocked) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Properties")
                                        }
                                        IconButton(onClick = { viewModel.duplicateOperation(op) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                                        }
                                        IconButton(onClick = { viewModel.toggleOperation(op) }, enabled = !props.isLocked) {
                                            Icon(
                                                if (op.isEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = "Toggle"
                                            )
                                        }
                                        IconButton(onClick = { viewModel.removeOperation(op.id) }, enabled = !props.isLocked) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // --- 3. Property Editing Dialog ---
    editingOp?.let { op ->
        val initialProps = remember(op.propertiesJson) { OperationProperties.fromJson(op.propertiesJson) }
        
        var startMsStr by remember(op.id) { mutableStateOf(op.startTimeMs.toString()) }
        var endMsStr by remember(op.id) { mutableStateOf(op.endTimeMs.toString()) }
        var xStr by remember(op.id) { mutableStateOf(initialProps.x.toString()) }
        var yStr by remember(op.id) { mutableStateOf(initialProps.y.toString()) }
        var wStr by remember(op.id) { mutableStateOf(initialProps.width.toString()) }
        var hStr by remember(op.id) { mutableStateOf(initialProps.height.toString()) }
        var zOrderStr by remember(op.id) { mutableStateOf(initialProps.zOrder.toString()) }
        
        AlertDialog(
            onDismissRequest = { editingOp = null },
            title = { Text("Advanced Edit: ${op.type.removePrefix("CENSOR_")}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startMsStr,
                                onValueChange = { startMsStr = it },
                                label = { Text("Start (ms)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endMsStr,
                                onValueChange = { endMsStr = it },
                                label = { Text("End (ms)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    if (op.type.startsWith("CENSOR")) {
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Mask Geometry", style = MaterialTheme.typography.titleSmall)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = xStr,
                                    onValueChange = { xStr = it },
                                    label = { Text("X (0.0-1.0)") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = yStr,
                                    onValueChange = { yStr = it },
                                    label = { Text("Y (0.0-1.0)") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = wStr,
                                    onValueChange = { wStr = it },
                                    label = { Text("Width") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = hStr,
                                    onValueChange = { hStr = it },
                                    label = { Text("Height") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = zOrderStr,
                                onValueChange = { zOrderStr = it },
                                label = { Text("Z-Order") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Keyframes: ${initialProps.keyframes.size}", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { 
                                    // Add keyframe at current position with current typed values
                                    val newKf = Keyframe(
                                        timeMs = currentPosition,
                                        x = xStr.toFloatOrNull(),
                                        y = yStr.toFloatOrNull(),
                                        width = wStr.toFloatOrNull(),
                                        height = hStr.toFloatOrNull()
                                    )
                                    val newProps = initialProps.copy(keyframes = initialProps.keyframes + newKf)
                                    viewModel.updateOperation(op.copy(propertiesJson = newProps.toJson()))
                                    editingOp = null // Close dialog to refresh
                                }) {
                                    Text("+ Add at Playhead")
                                }
                            }
                        }
                        
                        items(initialProps.keyframes) { kf ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("@ ${formatTime(kf.timeMs)} (${kf.timeMs}ms)", style = MaterialTheme.typography.labelMedium)
                                        Text("x:${kf.x} y:${kf.y} w:${kf.width} h:${kf.height}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = {
                                        val newProps = initialProps.copy(keyframes = initialProps.keyframes.filter { it.timeMs != kf.timeMs })
                                        viewModel.updateOperation(op.copy(propertiesJson = newProps.toJson()))
                                        editingOp = null // Close to refresh
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Keyframe")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val start = startMsStr.toLongOrNull() ?: op.startTimeMs
                    val end = endMsStr.toLongOrNull() ?: op.endTimeMs
                    
                    val newProps = initialProps.copy(
                        x = xStr.toFloatOrNull() ?: initialProps.x,
                        y = yStr.toFloatOrNull() ?: initialProps.y,
                        width = wStr.toFloatOrNull() ?: initialProps.width,
                        height = hStr.toFloatOrNull() ?: initialProps.height,
                        zOrder = zOrderStr.toIntOrNull() ?: initialProps.zOrder
                    )

                    viewModel.updateOperation(
                        op.copy(
                            startTimeMs = start,
                            endTimeMs = end.coerceAtLeast(start + 100),
                            propertiesJson = newProps.toJson()
                        )
                    )
                    editingOp = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingOp = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // --- 4. Conflict Viewing Dialog ---
    viewingConflict?.let { conflictText ->
        AlertDialog(
            onDismissRequest = { viewingConflict = null },
            title = { Text("Operation Conflict Detected") },
            text = { Text(conflictText) },
            confirmButton = {
                TextButton(onClick = { viewingConflict = null }) { Text("OK") }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
