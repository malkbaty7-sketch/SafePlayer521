package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.WatchedState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    playerViewModel: com.example.ui.player.PlayerViewModel? = null,
    protectedMediaIds: Set<Int> = emptySet(),
    onNavigateToPlayer: (MediaItem) -> Unit,
    onNavigateToLibrary: (MediaType) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTaskCenter: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToStorage: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToSources: () -> Unit = {},
    onNavigateToMediaDetails: (MediaItem) -> Unit = {},
    onNavigateToEditing: (Int) -> Unit = {},
    onToggleVaultProtection: (Int) -> Unit = {}
) {
    val scanProgress by viewModel.scanProgress.collectAsState()
    val rawContinueWatching by viewModel.continueWatching.collectAsState()
    val rawRecentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val rawFavorites by viewModel.favorites.collectAsState()
    val rawAllMedia by viewModel.allMedia.collectAsState()

    // Filter protected items from casual browsing
    val continueWatching = remember(rawContinueWatching, protectedMediaIds) {
        rawContinueWatching.filter { it.id !in protectedMediaIds }
    }
    val recentlyPlayed = remember(rawRecentlyPlayed, protectedMediaIds) {
        rawRecentlyPlayed.filter { it.id !in protectedMediaIds }
    }
    val favorites = remember(rawFavorites, protectedMediaIds) {
        rawFavorites.filter { it.id !in protectedMediaIds }
    }
    val allMedia = remember(rawAllMedia, protectedMediaIds) {
        rawAllMedia.filter { it.id !in protectedMediaIds }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    var currentTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedMediaForOptions by remember { mutableStateOf<MediaItem?>(null) }
    var showMediaOptionsSheet by remember { mutableStateOf(false) }

    // Screen 2.0 State Upgrades: Grid Toggle, Search & Smart Filter
    var isGridView by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilterChip by remember { mutableStateOf("ALL") } // ALL, CONTINUE, FAVORITES, NEW, UNWATCHED

    LaunchedEffect(currentTab) {
        selectedFolder = null
    }

    BackHandler(enabled = isSearchActive || selectedFolder != null) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (selectedFolder != null) {
            selectedFolder = null
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    val themeMode by com.example.util.ThemeManager.themeMode.collectAsStateWithLifecycle()
    val isDark = when(themeMode) {
        com.example.util.ThemeManager.MODE_LIGHT -> false
        com.example.util.ThemeManager.MODE_DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val context = LocalContext.current

    val activePlayingMedia = playerViewModel?.currentMedia?.collectAsState()?.value
    val isMiniPlaying = playerViewModel?.isPlaying?.collectAsState()?.value ?: false
    val miniPosition = playerViewModel?.currentPosition?.collectAsState()?.value ?: 0L
    val miniDuration = playerViewModel?.duration?.collectAsState()?.value ?: 0L

    LaunchedEffect(activePlayingMedia, isMiniPlaying) {
        if (activePlayingMedia != null && isMiniPlaying) {
            while (true) {
                playerViewModel?.updatePosition()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في الفيديوهات والمجلدات...", fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "إغلاق البحث")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedFolder != null) {
                                selectedFolder!!
                            } else {
                                when(currentTab) {
                                    0 -> stringResource(R.string.tab_video)
                                    1 -> stringResource(R.string.tab_music)
                                    2 -> stringResource(R.string.tab_my_files)
                                    else -> stringResource(R.string.tab_playlist)
                                }
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (selectedFolder != null) {
                            IconButton(onClick = { selectedFolder = null }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع"
                                )
                            }
                        }
                    },
                    actions = {
                        // Grid / List Toggle Button
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "عرض كقائمة" else "عرض كشبكة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Search Button
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "بحث")
                        }

                        // Security Vault Button
                        IconButton(onClick = onNavigateToSecurity) {
                            Icon(Icons.Default.Security, contentDescription = "الخزنة السرية", tint = MaterialTheme.colorScheme.primary)
                        }

                        // More options
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات إضافية")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_settings)) },
                                    onClick = { 
                                        showMoreMenu = false
                                        onNavigateToSettings() 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.desc_toggle_day_night)) },
                                    onClick = { 
                                        showMoreMenu = false
                                        com.example.util.ThemeManager.toggleDarkMode(context)
                                    },
                                    leadingIcon = { 
                                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null) 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("مركز المهام والتصدير") },
                                    onClick = { 
                                        showMoreMenu = false
                                        onNavigateToTaskCenter() 
                                    },
                                    leadingIcon = { Icon(Icons.Default.TaskAlt, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("فحص الذاكرة وتحديث الملفات") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.startScan()
                                        Toast.makeText(context, "بدأ فحص الجهاز وتحديث الوسائط...", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activePlayingMedia != null && playerViewModel != null) {
                    FloatingMiniPlayerBar(
                        media = activePlayingMedia,
                        isPlaying = isMiniPlaying,
                        currentPosition = miniPosition,
                        duration = miniDuration,
                        onTogglePlayPause = { playerViewModel.togglePlayPause() },
                        onNext = { playerViewModel.playNext() },
                        onClose = { playerViewModel.stopAndClearMedia() },
                        onOpenPlayer = { onNavigateToPlayer(activePlayingMedia) }
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = stringResource(R.string.tab_video)) },
                        label = { Text(stringResource(R.string.tab_video)) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = stringResource(R.string.tab_music)) },
                        label = { Text(stringResource(R.string.tab_music)) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = stringResource(R.string.tab_my_files)) },
                        label = { Text(stringResource(R.string.tab_my_files)) }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.tab_playlist)) },
                        label = { Text(stringResource(R.string.tab_playlist)) }
                    )
                }
            }
        }
    ) { padding ->
        if (!permissionState.allPermissionsGranted) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.prompt_permissions_required))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text(stringResource(R.string.action_grant_access))
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Scan progress bar if active
            if (scanProgress.isScanning) {
                item {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.title_library_scan_progress), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            Text(stringResource(R.string.format_scan_progress, scanProgress.scanned, scanProgress.total), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 1️⃣ Live Search Results Mode (if searching)
            if (isSearchActive && searchQuery.isNotBlank()) {
                val matchedMedia = allMedia.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.folder.contains(searchQuery, ignoreCase = true)
                }

                item {
                    Text(
                        text = "نتائج البحث (${matchedMedia.size}) لـ \"$searchQuery\":",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (matchedMedia.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            Text("لم يتم العثور على أي ملفات مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    if (isGridView) {
                        val chunked = matchedMedia.chunked(2)
                        items(chunked) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HomeMediaGridCard(
                                    media = pair[0],
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToPlayer(pair[0]) },
                                    onMoreClick = {
                                        selectedMediaForOptions = pair[0]
                                        showMediaOptionsSheet = true
                                    }
                                )
                                if (pair.size > 1) {
                                    HomeMediaGridCard(
                                        media = pair[1],
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigateToPlayer(pair[1]) },
                                        onMoreClick = {
                                            selectedMediaForOptions = pair[1]
                                            showMediaOptionsSheet = true
                                        }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(matchedMedia, key = { it.id }) { media ->
                            HomeMediaItemRow(
                                media = media,
                                onClick = { onNavigateToPlayer(media) },
                                onMoreClick = {
                                    selectedMediaForOptions = media
                                    showMediaOptionsSheet = true
                                }
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            // 2️⃣ Folder Content View (When a folder is selected)
            if (selectedFolder != null) {
                val folderName = selectedFolder!!
                val folderMedia = when (currentTab) {
                    0 -> allMedia.filter { it.mediaType == MediaType.VIDEO && (it.folder == folderName || (folderName == "Videos" && it.folder.isBlank())) }
                    1 -> allMedia.filter { it.mediaType == MediaType.AUDIO && (it.folder == folderName || (folderName == "Music" && it.folder.isBlank())) }
                    else -> allMedia.filter { (it.folder == folderName || (folderName == "SafePlayer" && it.folder.isBlank())) }
                }

                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${folderMedia.size} ${if (currentTab == 0) "فيديو" else "ملف صوتي"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Grid/List toggle inside folder
                            FilledTonalIconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }

                if (folderMedia.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد ملفات في هذا المجلد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (isGridView) {
                        val chunked = folderMedia.chunked(2)
                        items(chunked) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HomeMediaGridCard(
                                    media = pair[0],
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToPlayer(pair[0]) },
                                    onMoreClick = {
                                        selectedMediaForOptions = pair[0]
                                        showMediaOptionsSheet = true
                                    }
                                )
                                if (pair.size > 1) {
                                    HomeMediaGridCard(
                                        media = pair[1],
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigateToPlayer(pair[1]) },
                                        onMoreClick = {
                                            selectedMediaForOptions = pair[1]
                                            showMediaOptionsSheet = true
                                        }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(folderMedia, key = { it.id }) { media ->
                            HomeMediaItemRow(
                                media = media,
                                onClick = { onNavigateToPlayer(media) },
                                onMoreClick = {
                                    selectedMediaForOptions = media
                                    showMediaOptionsSheet = true
                                }
                            )
                        }
                    }
                }
            } else if (allMedia.isEmpty() && !scanProgress.isScanning) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.empty_media), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.startScan() }) {
                                Text(stringResource(R.string.action_scan_device))
                            }
                        }
                    }
                }
            } else {
                // 3️⃣ Main Hub: Hero Continue Watching, Quick Stats & Smart Filter Chips

                // A. Quick Stats Header
                item {
                    val videoMediaCount = allMedia.count { it.mediaType == MediaType.VIDEO }
                    val audioMediaCount = allMedia.count { it.mediaType == MediaType.AUDIO }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("🎬 $videoMediaCount فيديو", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("🎵 $audioMediaCount مقطع صوتي", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Text(
                                text = "تصفح الكل ←",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onNavigateToLibrary(if (currentTab == 1) MediaType.AUDIO else MediaType.VIDEO)
                                }
                            )
                        }
                    }
                }

                // B. Hero Continue Watching Carousel (قسم متابعة المشاهدة البانورامي)
                if (continueWatching.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "متابعة المشاهدة ⏳",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${continueWatching.size} مقاطع",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                items(continueWatching, key = { it.id }) { item ->
                                    HeroContinueWatchingCard(
                                        media = item,
                                        onClick = { onNavigateToPlayer(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                // C. Smart Filter Chips Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activeFilterChip == "ALL",
                            onClick = { activeFilterChip = "ALL" },
                            label = { Text("المجلدات والكل") }
                        )
                        FilterChip(
                            selected = activeFilterChip == "FAVORITES",
                            onClick = { activeFilterChip = "FAVORITES" },
                            label = { Text("المفضلة ⭐ (${favorites.size})") }
                        )
                        FilterChip(
                            selected = activeFilterChip == "NEW",
                            onClick = { activeFilterChip = "NEW" },
                            label = { Text("المضاف حديثاً 🆕") }
                        )
                        FilterChip(
                            selected = activeFilterChip == "IN_PROGRESS",
                            onClick = { activeFilterChip = "IN_PROGRESS" },
                            label = { Text("قيد المشاهدة ⏳ (${continueWatching.size})") }
                        )
                    }
                }

                // D. Tab-Specific Content or Filtered Content
                when (activeFilterChip) {
                    "FAVORITES" -> {
                        if (favorites.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد ملفات في المفضلة بعد ⭐", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            renderMediaListOrGrid(favorites, isGridView, onNavigateToPlayer) { media ->
                                selectedMediaForOptions = media
                                showMediaOptionsSheet = true
                            }
                        }
                    }
                    "NEW" -> {
                        val newMedia = allMedia.filter { it.isNew }
                        if (newMedia.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد ملفات مضافة حديثاً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            renderMediaListOrGrid(newMedia, isGridView, onNavigateToPlayer) { media ->
                                selectedMediaForOptions = media
                                showMediaOptionsSheet = true
                            }
                        }
                    }
                    "IN_PROGRESS" -> {
                        if (continueWatching.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد ملفات قيد المشاهدة حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            renderMediaListOrGrid(continueWatching, isGridView, onNavigateToPlayer) { media ->
                                selectedMediaForOptions = media
                                showMediaOptionsSheet = true
                            }
                        }
                    }
                    else -> {
                        // Standard Tab Folders
                        when (currentTab) {
                            0 -> {
                                // Video Folders grouped by MediaScanner folder name
                                val videoMedia = allMedia.filter { it.mediaType == MediaType.VIDEO }
                                val videoFolders = videoMedia.groupBy { it.folder.ifBlank { "Videos" } }
                                val recentlyAddedCount = videoMedia.count { it.isNew }
                                
                                item {
                                    Text(String.format(stringResource(R.string.folder_count), videoFolders.size), 
                                         modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), 
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (recentlyAddedCount > 0) {
                                    item {
                                        FolderListItem(
                                            title = stringResource(R.string.recently_added),
                                            count = recentlyAddedCount,
                                            countText = String.format(stringResource(R.string.recently_added_count), recentlyAddedCount),
                                            countColor = Color(0xFF4CAF50),
                                            isRecent = true,
                                            onClick = { onNavigateToLibrary(MediaType.VIDEO) }
                                        )
                                    }
                                }

                                items(videoFolders.keys.toList().sorted()) { folderName ->
                                    val count = videoFolders[folderName]?.size ?: 0
                                    FolderListItem(
                                        title = folderName,
                                        count = count,
                                        onClick = { selectedFolder = folderName }
                                    )
                                }
                            }
                            1 -> {
                                // Music Folders grouped by MediaScanner folder name
                                val audioMedia = allMedia.filter { it.mediaType == MediaType.AUDIO }
                                val audioFolders = audioMedia.groupBy { it.folder.ifBlank { "Music" } }
                                val recentlyAddedCount = audioMedia.count { it.isNew }
                                
                                item {
                                    Text(String.format(stringResource(R.string.folder_count), audioFolders.size), 
                                         modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), 
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (recentlyAddedCount > 0) {
                                    item {
                                        FolderListItem(
                                            title = stringResource(R.string.recently_added),
                                            count = recentlyAddedCount,
                                            countText = String.format(stringResource(R.string.recently_added_audio_count), recentlyAddedCount),
                                            countColor = Color(0xFF4CAF50),
                                            isRecent = true,
                                            onClick = { onNavigateToLibrary(MediaType.AUDIO) }
                                        )
                                    }
                                }

                                items(audioFolders.keys.toList().sorted()) { folderName ->
                                    val count = audioFolders[folderName]?.size ?: 0
                                    FolderListItem(
                                        title = folderName,
                                        count = count,
                                        onClick = { selectedFolder = folderName }
                                    )
                                }
                            }
                            2 -> {
                                // My Files (Edited videos)
                                val myFiles = allMedia.filter { !it.isOriginal }
                                val myFolders = myFiles.groupBy { it.folder.ifBlank { "SafePlayer" } }
                                
                                item {
                                    Text(String.format(stringResource(R.string.folder_count), myFolders.size), 
                                         modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), 
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                items(myFolders.keys.toList().sorted()) { folderName ->
                                    val count = myFolders[folderName]?.size ?: 0
                                    FolderListItem(
                                        title = folderName,
                                        count = count,
                                        onClick = { selectedFolder = folderName }
                                    )
                                }
                            }
                            3 -> {
                                // Playlists
                                item {
                                    FolderListItem(
                                        title = "قوائم تشغيل الفيديو",
                                        count = 0,
                                        countText = "عرض الكل",
                                        onClick = onNavigateToPlaylists
                                    )
                                    FolderListItem(
                                        title = "قوائم تشغيل الصوت",
                                        count = 0,
                                        countText = "عرض الكل",
                                        onClick = onNavigateToPlaylists
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Media Options (from 3 dots)
    if (showMediaOptionsSheet && selectedMediaForOptions != null) {
        val media = selectedMediaForOptions!!
        ModalBottomSheet(
            onDismissRequest = {
                showMediaOptionsSheet = false
                selectedMediaForOptions = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${media.folder} • ${Formatter.formatFileSize(context, media.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Direct Play
                ListItem(
                    headlineContent = { Text("تشغيل الآن", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        onNavigateToPlayer(media)
                    }
                )

                // Details & AI analysis screen
                ListItem(
                    headlineContent = { Text("تفاصيل ومعلومات التحليل الذكي") },
                    supportingContent = { Text("المشاهد، التعرف الذكي، الجودة") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        onNavigateToMediaDetails(media)
                    }
                )

                // Video Editing Studio
                if (media.mediaType == MediaType.VIDEO) {
                    ListItem(
                        headlineContent = { Text("استوديو التعديل والمونتاج") },
                        supportingContent = { Text("قص، تسريع، دمج، فلاتر") },
                        leadingContent = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showMediaOptionsSheet = false
                            onNavigateToEditing(media.id)
                        }
                    )
                }

                // Favorite
                ListItem(
                    headlineContent = { Text(if (media.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") },
                    leadingContent = {
                        Icon(
                            if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (media.isFavorite) Color.Red else LocalContentColor.current
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleFavorite(media)
                        showMediaOptionsSheet = false
                    }
                )

                // Hide in Safe Vault
                ListItem(
                    headlineContent = { Text("نقل إلى الخزنة السرية 🔐", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("إخفاء الملف من المكتبة وحمايته برمز PIN أو البصمة") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        onToggleVaultProtection(media.id)
                        Toast.makeText(context, "تم نقل '${media.name}' إلى الخزنة السرية 🔐", Toast.LENGTH_SHORT).show()
                    }
                )

                // Secure vault settings
                ListItem(
                    headlineContent = { Text("فتح الخزنة السرية وإعدادات الأمان") },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        onNavigateToSecurity()
                    }
                )

                // Share
                ListItem(
                    headlineContent = { Text("مشاركة الملف") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = media.mimeType ?: if (media.mediaType == MediaType.VIDEO) "video/*" else "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(media.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الوسائط"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "تعذر مشاركة الملف", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Delete
                ListItem(
                    headlineContent = { Text("حذف الملف", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        showMediaOptionsSheet = false
                        viewModel.deleteMedia(media)
                        Toast.makeText(context, "تم حذف الملف", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderMediaListOrGrid(
    items: List<MediaItem>,
    isGridView: Boolean,
    onNavigateToPlayer: (MediaItem) -> Unit,
    onMoreClick: (MediaItem) -> Unit
) {
    if (isGridView) {
        val chunked = items.chunked(2)
        items(chunked) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeMediaGridCard(
                    media = pair[0],
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToPlayer(pair[0]) },
                    onMoreClick = { onMoreClick(pair[0]) }
                )
                if (pair.size > 1) {
                    HomeMediaGridCard(
                        media = pair[1],
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPlayer(pair[1]) },
                        onMoreClick = { onMoreClick(pair[1]) }
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        items(items, key = { it.id }) { media ->
            HomeMediaItemRow(
                media = media,
                onClick = { onNavigateToPlayer(media) },
                onMoreClick = { onMoreClick(media) }
            )
        }
    }
}

// 🌟 Hero Continue Watching Card (بطاقة متابعة المشاهدة البانورامية)
@Composable
fun HeroContinueWatchingCard(
    media: MediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (media.durationMs > 0) (media.lastPositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingMs = (media.durationMs - media.lastPositionMs).coerceAtLeast(0L)

    Card(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.Black)
            ) {
                if (media.mediaType == MediaType.VIDEO) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(media.path)
                            .videoFrameMillis(media.lastPositionMs.coerceAtLeast(1000L))
                            .crossfade(true)
                            .build(),
                        contentDescription = media.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    }
                }

                // Gradient shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Play Button Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "استئناف", tint = MaterialTheme.colorScheme.onPrimary)
                }

                // Remaining time pill
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "متبقي ${formatTimeMinutes(remainingMs)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Info
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${media.folder} • ${(progress * 100).toInt()}% مكتمل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// 🌟 2-Column Responsive Media Grid Card (بطاقة الشبكة العصرية)
@Composable
fun HomeMediaGridCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (media.mediaType == MediaType.VIDEO) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(media.path)
                            .videoFrameMillis(2000L)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Duration badge
                if (media.durationMs > 0) {
                    val totalSec = media.durationMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    val durStr = "%02d:%02d".format(min, sec)
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = durStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Watched status indicator or Progress line
                if (media.watchedState == WatchedState.IN_PROGRESS && media.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (media.lastPositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Card text footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${Formatter.formatFileSize(context, media.sizeBytes)} • ${media.folder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FolderListItem(
    title: String,
    count: Int,
    countText: String? = null,
    countColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isRecent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isRecent) Icons.Default.Schedule else Icons.Default.Folder,
            contentDescription = "Folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = countText ?: count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = countColor
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(onClick = { /* More options */ }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeMediaItemRow(
    media: MediaItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media thumbnail / icon
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (media.mediaType == MediaType.VIDEO) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(media.path)
                            .videoFrameMillis(2000)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Duration badge
                if (media.durationMs > 0) {
                    val totalSec = media.durationMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    val durStr = "%02d:%02d".format(min, sec)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = media.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val sizeStr = Formatter.formatFileSize(context, media.sizeBytes)
                Text(
                    text = "$sizeStr • ${media.folder}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات الملف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// 🌟 Frosted Elevated Mini-Player Bar
@Composable
fun FloatingMiniPlayerBar(
    media: MediaItem,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpenPlayer),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (media.mediaType == MediaType.VIDEO) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(media.path)
                                .videoFrameMillis(2000)
                                .crossfade(true)
                                .build(),
                            contentDescription = media.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val curSec = (currentPosition / 1000).coerceAtLeast(0L)
                    val durSec = (duration / 1000).coerceAtLeast(0L)
                    Text(
                        text = "%02d:%02d / %02d:%02d".format(curSec / 60, curSec % 60, durSec / 60, durSec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "التالي",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق المشغل المصغر",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun formatTimeMinutes(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) "${min}د ${sec}ث" else "${sec}ث"
}
