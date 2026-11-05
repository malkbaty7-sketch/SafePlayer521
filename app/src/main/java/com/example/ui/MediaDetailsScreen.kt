package com.example.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.SafePlayerApp
import com.example.ai.AiAnalysisProgress
import com.example.ai.AiAnalyzer
import com.example.ai.DetectionResult
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.Scene
import com.example.scandata.ScanDataManager
import com.example.scanner.SceneAnalyzer
import com.example.scanner.SceneScanProgress
import com.example.security.SecurityAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaDetailsViewModel(
    private val repository: MediaRepository,
    private val sceneAnalyzer: SceneAnalyzer,
    private val aiAnalyzer: AiAnalyzer,
    private val scanDataManager: ScanDataManager,
    private val mediaId: Int
) : ViewModel() {

    private val _mediaItem = MutableStateFlow<MediaItem?>(null)
    val mediaItem: StateFlow<MediaItem?> = _mediaItem.asStateFlow()

    val scenes: StateFlow<List<Scene>> = repository.getScenesForMedia(mediaId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val aiResults: StateFlow<List<DetectionResult>> = repository.getDetectionResultsForMedia(mediaId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val scanProgress = sceneAnalyzer.progress
    val aiProgress = aiAnalyzer.progress

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _mediaItem.value = repository.getMediaById(mediaId).first()
        }
    }

    fun startSceneScan() {
        _mediaItem.value?.let {
            viewModelScope.launch {
                sceneAnalyzer.analyzeScenes(it)
                loadMedia()
            }
        }
    }

    fun cancelScan() {
        sceneAnalyzer.cancel()
    }

    fun startAiAnalysis(sensitivity: String = "MEDIUM") {
        _mediaItem.value?.let {
            viewModelScope.launch {
                aiAnalyzer.analyzeMedia(it, sensitivity)
                loadMedia()
            }
        }
    }

    fun cancelAiAnalysis() {
        aiAnalyzer.cancel()
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun exportScanData(uri: Uri) {
        _mediaItem.value?.let {
            viewModelScope.launch {
                val result = scanDataManager.exportScanData(it, uri)
                if (result.isSuccess) {
                    _message.value = "تم تصدير بيانات الفحص بنجاح"
                } else {
                    _message.value = "فشل التصدير: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun importScanData(uri: Uri) {
        _mediaItem.value?.let {
            viewModelScope.launch {
                val result = scanDataManager.importScanData(it, uri)
                if (result.isSuccess) {
                    _message.value = "تم استيراد بيانات الفحص بنجاح"
                    loadMedia()
                } else {
                    _message.value = "فشل الاستيراد: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun renameMedia(app: SafePlayerApp, newName: String) {
        viewModelScope.launch {
            val result = app.storageManager.renameMedia(mediaId, newName)
            if (result.isSuccess) {
                _message.value = "تمت إعادة تسمية الوسيط بنجاح"
                loadMedia()
            } else {
                _message.value = result.exceptionOrNull()?.message ?: "فشلت إعادة التسمية"
            }
        }
    }

    fun deleteMedia(app: SafePlayerApp, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = app.storageManager.deleteMedia(mediaId)
            if (result.isSuccess) {
                _message.value = "تم حذف الوسيط بنجاح"
                onDeleted()
            } else {
                _message.value = result.exceptionOrNull()?.message ?: "فشل الحذف"
            }
        }
    }
}

class MediaDetailsViewModelFactory(
    private val app: SafePlayerApp,
    private val mediaId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MediaDetailsViewModel(app.repository, app.sceneAnalyzer, app.aiAnalyzer, app.scanDataManager, mediaId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsScreen(
    app: SafePlayerApp,
    mediaId: Int,
    onNavigateBack: () -> Unit,
    onPlayMedia: (Int) -> Unit,
    onPlayScene: (Int, Long) -> Unit,
    onNavigateToAiReview: (Int) -> Unit,
    onNavigateToEditing: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: MediaDetailsViewModel = viewModel(
        factory = MediaDetailsViewModelFactory(app, mediaId)
    )
    val mediaItem by viewModel.mediaItem.collectAsState()
    val scenes by viewModel.scenes.collectAsState()
    val aiResults by viewModel.aiResults.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val aiProgress by viewModel.aiProgress.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val protectedMediaIds by app.securityManager.protectedMediaIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val isProtected = mediaItem?.let { it.id in protectedMediaIds } ?: false

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportScanData(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importScanData(it) }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistInputName by remember { mutableStateOf("") }
    val playlists by app.repository.allPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }

    if (showRenameDialog && mediaItem != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.title_rename_media), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.label_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameMedia(app, newName)
                    showRenameDialog = false
                }) {
                    Text(stringResource(R.string.action_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel_dialog))
                }
            }
        )
    }

    if (showDeleteDialog && mediaItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.title_delete_media), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.desc_delete_media, mediaItem!!.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMedia(app, onDeleted = { onNavigateBack() })
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel_dialog))
                }
            }
        )
    }

    if (showAddToPlaylistDialog && mediaItem != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("إضافة لقائمة التشغيل", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (playlists.isEmpty()) {
                        Text("لا توجد قوائم تشغيل حالياً. أنشئ قائمة جديدة أدناه:", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("اختر قائمة تشغيل:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        app.repository.addMediaToPlaylist(pl.id, mediaItem!!.id)
                                        snackbarHostState.showSnackbar("تمت الإضافة إلى: ${pl.name}")
                                        showAddToPlaylistDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("أو أنشئ قائمة جديدة:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newPlaylistInputName,
                        onValueChange = { newPlaylistInputName = it },
                        label = { Text("اسم القائمة الجديدة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                if (newPlaylistInputName.isNotBlank()) {
                    Button(onClick = {
                        scope.launch {
                            val newId = app.repository.createPlaylist(newPlaylistInputName.trim())
                            app.repository.addMediaToPlaylist(newId.toInt(), mediaItem!!.id)
                            snackbarHostState.showSnackbar("تم إنشاء القائمة وإضافة الوسيط")
                            newPlaylistInputName = ""
                            showAddToPlaylistDialog = false
                        }
                    }) {
                        Text("إنشاء وإضافة")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = mediaItem?.name ?: stringResource(R.string.title_media_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // Lock / Unlock toggle
                    IconButton(onClick = {
                        scope.launch {
                            mediaItem?.let {
                                app.securityManager.toggleItemProtection("MEDIA", it.id)
                            }
                        }
                    }) {
                        Icon(
                            if (isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "حماية الوسيط",
                            tint = if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Import metadata
                    IconButton(onClick = {
                        scope.launch {
                            if (app.securityManager.canPerformAction(SecurityAction.ADD_SOURCE)) {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            } else {
                                snackbarHostState.showSnackbar("إضافة المصادر مقيدة بأدوات الرقابة الأبوية")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "استيراد البيانات")
                    }

                    // Export metadata
                    if (scenes.isNotEmpty() || aiResults.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                if (app.securityManager.canPerformAction(SecurityAction.SHARE)) {
                                    exportLauncher.launch("SafePlayer_ScanData_${mediaItem?.id}.json")
                                } else {
                                    snackbarHostState.showSnackbar("المشاركة والتصدير مقيد بأدوات الرقابة الأبوية")
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "تصدير البيانات")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (mediaItem == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val media = mediaItem!!
            val isVideo = media.mediaType == com.example.data.MediaType.VIDEO ||
                    media.mimeType?.startsWith("video") == true ||
                    listOf("mp4", "mkv", "webm", "avi", "mov", "3gp").any { media.path.endsWith(it, ignoreCase = true) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Cinematic Backdrop Hero Section
                item {
                    CinematicHeroBackdrop(
                        media = media,
                        isVideo = isVideo,
                        isProtected = isProtected,
                        onPlayClick = { onPlayMedia(media.id) }
                    )
                }

                // 2. Quick Action Pills Row
                item {
                    QuickActionPillsRow(
                        media = media,
                        isProtected = isProtected,
                        onPlayClick = { onPlayMedia(media.id) },
                        onEditClick = if (onNavigateToEditing != null) {
                            {
                                scope.launch {
                                    if (app.securityManager.canPerformAction(SecurityAction.EDIT)) {
                                        onNavigateToEditing(media.id)
                                    } else {
                                        snackbarHostState.showSnackbar("التعديل مقيد بواسطة أدوات الرقابة الأبوية")
                                    }
                                }
                            }
                        } else null,
                        onToggleProtection = {
                            scope.launch {
                                app.securityManager.toggleItemProtection("MEDIA", media.id)
                            }
                        },
                        onAddToPlaylist = {
                            newPlaylistInputName = ""
                            showAddToPlaylistDialog = true
                        },
                        onRename = {
                            scope.launch {
                                if (app.securityManager.canPerformAction(SecurityAction.EDIT)) {
                                    newName = media.name
                                    showRenameDialog = true
                                } else {
                                    snackbarHostState.showSnackbar("إعادة التسمية مقيدة بواسطة أدوات الرقابة الأبوية")
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                if (app.securityManager.canPerformAction(SecurityAction.DELETE)) {
                                    showDeleteDialog = true
                                } else {
                                    snackbarHostState.showSnackbar("الحذف مقيد بواسطة أدوات الرقابة الأبوية")
                                }
                            }
                        }
                    )
                }

                // 3. Technical Specs Card
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TechnicalSpecsCard(media = media, context = context, sceneCount = scenes.size)
                    }
                }

                // 4. Visual Scenes Strip (1-Tap Jump Carousel)
                if (scenes.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.MovieFilter,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "المشاهد المكتشفة (${scenes.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "انقر للقفز المباشر",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(scenes) { scene ->
                                    VisualSceneCard(
                                        scene = scene,
                                        onClick = { onPlayScene(media.id, scene.startTimeMs) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Scene Analyzer Center
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SceneAnalyzerSection(
                            media = media,
                            scanProgress = scanProgress,
                            onStartScan = { viewModel.startSceneScan() },
                            onCancelScan = { viewModel.cancelScan() }
                        )
                    }
                }

                // 6. AI Safety & Smart Engine Section
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AiEngineSection(
                            media = media,
                            aiProgress = aiProgress,
                            aiResults = aiResults,
                            onStartAi = { viewModel.startAiAnalysis() },
                            onCancelAi = { viewModel.cancelAiAnalysis() },
                            onReviewDetections = { onNavigateToAiReview(media.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cinematic 16:9 Hero Backdrop with Gradient Overlay, Play FAB, and Meta Badges
 */
@Composable
fun CinematicHeroBackdrop(
    media: MediaItem,
    isVideo: Boolean,
    isProtected: Boolean,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    val extension = media.path.substringAfterLast('.', "").uppercase()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(210.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail / Frame
            if (isVideo) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(media.path))
                        .videoFrameMillis(2000)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Cinematic Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // Protected Badge Top-End
            if (isProtected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("الخزنة السرية", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Big Center Glowing Play Button
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .shadow(12.dp, CircleShape),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "تشغيل",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Bottom Badges Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatDuration(media.durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Extension Badge
                if (extension.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = extension,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Watch Status Badge
                if (media.lastPositionMs > 0 && media.durationMs > 0) {
                    val percent = (media.lastPositionMs.toFloat() / media.durationMs * 100).toInt().coerceIn(1, 100)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (percent >= 90 || media.watchedState == com.example.data.WatchedState.WATCHED) Color(0xFF2E7D32).copy(alpha = 0.9f) else Color(0xFFE65100).copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = if (percent >= 90 || media.watchedState == com.example.data.WatchedState.WATCHED) "تمت المشاهدة ✅" else "متبقي ${100 - percent}% ⏳",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Quick Action Pills Row
 */
@Composable
fun QuickActionPillsRow(
    media: MediaItem,
    isProtected: Boolean,
    onPlayClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onToggleProtection: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = onPlayClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("تشغيل الآن", fontWeight = FontWeight.Bold)
            }
        }

        if (onEditClick != null) {
            item {
                FilledTonalButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استوديو التعديل")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onToggleProtection,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isProtected) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isProtected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isProtected) "إلغاء القفل" else "قفل بالخزنة")
            }
        }

        item {
            OutlinedButton(
                onClick = onAddToPlaylist,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("قائمة تشغيل")
            }
        }

        item {
            OutlinedButton(
                onClick = onRename,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("تسمية")
            }
        }

        item {
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("حذف")
            }
        }
    }
}

/**
 * Detailed Technical Specifications Card with Modern Badges
 */
@Composable
fun TechnicalSpecsCard(media: MediaItem, context: android.content.Context, sceneCount: Int) {
    val folderName = File(media.path).parentFile?.name ?: "الرئيسي"
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(media.dateModified) { sdf.format(Date(media.dateModified * 1000)) }
    val formattedSize = remember(media.sizeBytes) { Formatter.formatFileSize(context, media.sizeBytes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "المواصفات الفنية للملف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecRow(icon = Icons.Default.Folder, label = "المجلد", value = folderName)
                SpecRow(icon = Icons.Default.Storage, label = "الحجم", value = formattedSize)
                SpecRow(icon = Icons.Default.Timer, label = "المدة الزمنية", value = formatDuration(media.durationMs))
                SpecRow(icon = Icons.Default.CalendarToday, label = "تاريخ التعديل", value = formattedDate)
                SpecRow(
                    icon = Icons.Default.Psychology,
                    label = "حالة الفحص الذكي",
                    value = when (media.analysisStatus) {
                        "COMPLETED" -> "مفحوص بالكامل ($sceneCount مشهد) ✅"
                        "ANALYZING" -> "قيد الفحص الآن ⏳"
                        else -> "غير مفحوص بعد ⚪"
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = media.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SpecRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Visual Scene Card for the Horizontal 1-Tap Jump Strip
 */
@Composable
fun VisualSceneCard(scene: Scene, onClick: () -> Unit) {
    val duration = scene.endTimeMs - scene.startTimeMs
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = File(scene.thumbnailPath),
                    contentDescription = "Scene Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Timestamp range pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = formatDuration(scene.startTimeMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "تشغيل",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "مشهد #${scene.sceneIndex + 1}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = "${formatDuration(scene.startTimeMs)} - ${formatDuration(scene.endTimeMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Scene Analyzer Management Card
 */
@Composable
fun SceneAnalyzerSection(
    media: MediaItem,
    scanProgress: SceneScanProgress,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    val isStale = media.analysisStatus == "COMPLETED" && (media.analyzedSize != media.sizeBytes || media.analyzedDate != media.dateModified)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.title_scene_analysis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            if (isStale) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.status_analysis_stale), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.desc_analysis_stale), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (scanProgress.isScanning && scanProgress.mediaId == media.id) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(stringResource(R.string.status_analysis_progress, scanProgress.status), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val progress = if (scanProgress.totalDurationMs > 0) {
                            (scanProgress.processedMs.toFloat() / scanProgress.totalDurationMs).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.format_processed, formatDuration(scanProgress.processedMs), formatDuration(scanProgress.totalDurationMs)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.format_scenes_found, scanProgress.scenesFound), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_cancel_scan))
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isStale) "إعادة فحص المشاهد" else if (media.analysisStatus == "COMPLETED") "إعادة تحليل المشاهد 🎬" else "بدء فحص المشاهد 🎬")
                }
                if (scanProgress.status == "FAILED") {
                    Text(stringResource(R.string.status_scan_failed, scanProgress.errorMessage ?: ""), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (scanProgress.status == "CANCELLED") {
                    Text(stringResource(R.string.status_scan_cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * AI Safety & Content Inspection Section
 */
@Composable
fun AiEngineSection(
    media: MediaItem,
    aiProgress: AiAnalysisProgress,
    aiResults: List<DetectionResult>,
    onStartAi: () -> Unit,
    onCancelAi: () -> Unit,
    onReviewDetections: () -> Unit
) {
    val aiStale = media.aiAnalysisStatus == "COMPLETED" && (media.aiAnalyzedSize != media.sizeBytes || media.aiAnalyzedDate != media.dateModified)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.title_ai_engine), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.desc_ai_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            if (aiStale) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.status_ai_stale), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.desc_ai_stale), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (aiProgress.isAnalyzing && aiProgress.mediaId == media.id) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(stringResource(R.string.status_ai_progress, aiProgress.status), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.format_ai_strategy, aiProgress.strategy.toString()), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        val progress = if (aiProgress.totalDurationMs > 0) {
                            (aiProgress.processedMs.toFloat() / aiProgress.totalDurationMs).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.format_processed, formatDuration(aiProgress.processedMs), formatDuration(aiProgress.totalDurationMs)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.format_ai_detections, aiProgress.detectionsFound), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onCancelAi, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_cancel_ai))
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartAi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = media.analysisStatus == "COMPLETED" && !aiStale
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiStale) "إعادة فحص الذكاء الاصطناعي 🛡️" else "بدء الفحص الأمني بالذكاء الاصطناعي 🛡️")
                }
                if (media.analysisStatus != "COMPLETED") {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.req_scene_analysis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (aiResults.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onReviewDetections,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_review_detections, aiResults.size), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
