package com.example.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.data.WatchedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    mediaType: MediaType,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val allItems by if (mediaType == MediaType.VIDEO) {
        viewModel.videos.collectAsState()
    } else {
        viewModel.audios.collectAsState()
    }
    
    var sortMode by remember { mutableStateOf("DATE_MODIFIED") }
    var filterMode by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val items = allItems
        .filter { !it.isMissing }
        .filter {
            when (filterMode) {
                "WATCHED" -> it.watchedState == WatchedState.WATCHED
                "UNWATCHED" -> it.watchedState == WatchedState.UNWATCHED
                "IN_PROGRESS" -> it.watchedState == WatchedState.IN_PROGRESS
                "FAVORITES" -> it.isFavorite
                else -> true
            }
        }
        .filter {
            if (searchQuery.isNotEmpty()) {
                it.name.contains(searchQuery, ignoreCase = true) || it.folder.contains(searchQuery, ignoreCase = true)
            } else {
                true
            }
        }
        .let { list ->
            when (sortMode) {
                "NAME" -> list.sortedBy { it.name.lowercase() }
                "SIZE" -> list.sortedByDescending { it.sizeBytes }
                "DURATION" -> list.sortedByDescending { it.durationMs }
                "NEW" -> list.sortedByDescending { it.isNew }
                else -> list.sortedByDescending { it.dateModified }
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
                            placeholder = { Text("بحث في المكتبة...", fontSize = 14.sp) },
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
                            text = if (mediaType == MediaType.VIDEO) "مكتبة الفيديوهات (${items.size})" else "مكتبة الصوتيات (${items.size})",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { 
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") 
                        }
                    },
                    actions = {
                        // Search Button
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "بحث")
                        }

                        // Grid / List Toggle
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "عرض كقائمة" else "عرض كشبكة"
                            )
                        }

                        // Sort Menu
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "ترتيب")
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("الأحدث أولاً (تاريخ التعديل)") },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                    onClick = { sortMode = "DATE_MODIFIED"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("الاسم أبجدياً (A-Z)") },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                                    onClick = { sortMode = "NAME"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("الحجم (الأكبر أولاً)") },
                                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                    onClick = { sortMode = "SIZE"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("المدة (الأطول أولاً)") },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = { sortMode = "DURATION"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("المضاف حديثاً 🆕") },
                                    leadingIcon = { Icon(Icons.Default.FiberNew, contentDescription = null) },
                                    onClick = { sortMode = "NEW"; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("الكل (${allItems.size})") }
                )
                FilterChip(
                    selected = filterMode == "IN_PROGRESS",
                    onClick = { filterMode = "IN_PROGRESS" },
                    label = { Text("قيد المشاهدة ⏳") }
                )
                FilterChip(
                    selected = filterMode == "FAVORITES",
                    onClick = { filterMode = "FAVORITES" },
                    label = { Text("المفضلة ⭐") }
                )
                FilterChip(
                    selected = filterMode == "UNWATCHED",
                    onClick = { filterMode = "UNWATCHED" },
                    label = { Text("غير المشاهدة 👁️") }
                )
                FilterChip(
                    selected = filterMode == "WATCHED",
                    onClick = { filterMode = "WATCHED" },
                    label = { Text("مكتمل المشاهدة ✅") }
                )
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (mediaType == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد وسائط تطابق هذا التصنيف",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isGridView) {
                        val chunked = items.chunked(2)
                        items(chunked) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LibraryGridCard(
                                    media = pair[0],
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToPlayer(pair[0]) }
                                )
                                if (pair.size > 1) {
                                    LibraryGridCard(
                                        media = pair[1],
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigateToPlayer(pair[1]) }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(items, key = { it.id }) { media ->
                            LibraryItemRow(media, onClick = { onNavigateToPlayer(media) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryGridCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
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
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "%02d:%02d".format(min, sec),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Progress Bar if in progress
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

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Formatter.formatFileSize(context, media.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (media.isFavorite) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryItemRow(media: MediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 110.dp, height = 75.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (media.mediaType == MediaType.VIDEO) {
                    val request = ImageRequest.Builder(context)
                        .data(media.path)
                        .videoFrameMillis(2000)
                        .crossfade(true)
                        .build()
                    AsyncImage(
                        model = request,
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.LibraryMusic, contentDescription = "Audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                
                if (media.durationMs > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "${media.durationMs / 1000 / 60}:${String.format("%02d", (media.durationMs / 1000) % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (media.durationMs > 0 && media.lastPositionMs > 0) {
                    LinearProgressIndicator(
                        progress = { (media.lastPositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp)) {
                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${media.folder} • ${Formatter.formatFileSize(context, media.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (media.watchedState == WatchedState.IN_PROGRESS) {
                    Text(
                        text = "قيد المشاهدة ⏳",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (media.watchedState == WatchedState.WATCHED) {
                    Text(
                        text = "تمت المشاهدة ✅",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (media.isFavorite) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.padding(end = 12.dp).size(20.dp))
            }
        }
    }
}
