@file:kotlin.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.util.Rational
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.MediaItem
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditing: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isPlayerReady by viewModel.isPlayerReady.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentMedia by viewModel.currentMedia.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMirrored by viewModel.isMirrored.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val isControlsLocked by viewModel.isControlsLocked.collectAsState()
    val isSafeModeEnabled by viewModel.isSafeModeEnabled.collectAsState()
    val isVideoBgPlayEnabled by viewModel.isVideoBgPlayEnabled.collectAsState()
    val isOrientationLocked by viewModel.isOrientationLocked.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isAbRepeatActive by viewModel.isAbRepeatActive.collectAsState()
    val abPointA by viewModel.abPointA.collectAsState()
    val abPointB by viewModel.abPointB.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val playlistItems by viewModel.playlistItems.collectAsState()

    // Phase 2: Equalizer & Audio Boost State
    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val eqBandLevels by viewModel.eqBandLevels.collectAsState()
    val bassBoostLevel by viewModel.bassBoostLevel.collectAsState()
    val volumeBoostLevel by viewModel.volumeBoostLevel.collectAsState()

    // Phase 2: External Subtitles State
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val subtitleFileName by viewModel.subtitleFileName.collectAsState()
    val subtitleCues by viewModel.subtitleCues.collectAsState()
    val currentSubtitleText by viewModel.currentSubtitleText.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val subtitleFontSizeSp by viewModel.subtitleFontSizeSp.collectAsState()
    val subtitleColorIndex by viewModel.subtitleColorIndex.collectAsState()
    val subtitleBgOpacity by viewModel.subtitleBgOpacity.collectAsState()
    val subtitleFontFamilyIndex by viewModel.subtitleFontFamilyIndex.collectAsState()
    var showOnlineSubtitlesDialog by remember { mutableStateOf(false) }

    // Track 2: Live Video Filters State
    val videoFilterPreset by viewModel.videoFilterPreset.collectAsState()
    val filterBrightness by viewModel.filterBrightness.collectAsState()
    val filterContrast by viewModel.filterContrast.collectAsState()
    val filterSaturation by viewModel.filterSaturation.collectAsState()

    // Track 3: Smart Safe-Watch & Family Guard State
    val isSafeBlurActive by viewModel.isSafeBlurActive.collectAsState()
    val isSafeMuteActive by viewModel.isSafeMuteActive.collectAsState()
    val safeWatchBannerMessage by viewModel.safeWatchBannerMessage.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var userInteractedTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Pinch-to-Zoom & Pan states
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var showZoomFeedback by remember { mutableStateOf(false) }

    // Gesture feedback states
    var gestureBrightnessLevel by remember { mutableStateOf<Float?>(null) }
    var gestureVolumeLevel by remember { mutableStateOf<Float?>(null) }
    var doubleTapSeekFeedback by remember { mutableStateOf<String?>(null) }
    var doubleTapSide by remember { mutableStateOf<Int?>(null) } // -1 left, 1 right

    // Dialog states
    var showInfoDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showVideoFiltersDialog by remember { mutableStateOf(false) }
    var showSafeWatchSheet by remember { mutableStateOf(false) }
    var showImportSafeWatchDialog by remember { mutableStateOf(false) }
    var importSafeWatchJsonInput by remember { mutableStateOf("") }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadExternalSubtitleFromUri(context, uri) { success, count ->
                if (success) {
                    Toast.makeText(context, "تم تحميل ملف الترجمة بنجاح ($count جملة) 📝", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر قراءة ملف الترجمة، تأكد من صيغة SRT أو VTT", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Track current orientation (Video defaults to landscape as required)
    var isLandscape by remember { mutableStateOf(true) }

    val isAudio = currentMedia?.mediaType == com.example.data.MediaType.AUDIO

    // Set video orientation to landscape by default
    LaunchedEffect(currentMedia?.id) {
        if (!isAudio) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isLandscape = true
        }
    }

    // Lifecycle observer: Videos pause on stop unless background play is explicitly enabled. Audio always continues.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isVideoBgPlayEnabled, currentMedia) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val isVideoMedia = currentMedia?.mediaType != com.example.data.MediaType.AUDIO
                if (isVideoMedia && !isVideoBgPlayEnabled) {
                    viewModel.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Audio Manager for volume gestures
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Intercept back button when controls are locked
    BackHandler(enabled = isControlsLocked) {
        Toast.makeText(context, context.getString(R.string.player_screen_locked), Toast.LENGTH_SHORT).show()
    }

    // Auto-update position every 500ms
    LaunchedEffect(isPlayerReady) {
        if (isPlayerReady) {
            while (true) {
                viewModel.updatePosition()
                delay(500)
            }
        }
    }

    // Auto-hide controls after 4 seconds of inactivity if playing and not locked
    LaunchedEffect(areControlsVisible, isPlaying, userInteractedTime, isControlsLocked) {
        if (areControlsVisible && isPlaying && !isControlsLocked) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Clear gesture feedbacks after 1.5s
    LaunchedEffect(gestureBrightnessLevel, gestureVolumeLevel, doubleTapSeekFeedback, showZoomFeedback) {
        if (gestureBrightnessLevel != null || gestureVolumeLevel != null || doubleTapSeekFeedback != null || showZoomFeedback) {
            delay(1500)
            gestureBrightnessLevel = null
            gestureVolumeLevel = null
            doubleTapSeekFeedback = null
            doubleTapSide = null
            showZoomFeedback = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePosition(finished = false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val isVideoMedia = currentMedia?.mediaType != com.example.data.MediaType.AUDIO
            if (isVideoMedia && !viewModel.isVideoBgPlayEnabled.value) {
                viewModel.pause()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isControlsLocked) {
                if (!isControlsLocked) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                            userInteractedTime = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.38f) {
                                viewModel.seekRelative(-10000L)
                                doubleTapSeekFeedback = "-10 ثوانٍ ⏪"
                                doubleTapSide = -1
                            } else if (offset.x > screenWidth * 0.62f) {
                                viewModel.seekRelative(10000L)
                                doubleTapSeekFeedback = "⏩ +10 ثوانٍ"
                                doubleTapSide = 1
                            } else {
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    zoomOffset = Offset.Zero
                                    showZoomFeedback = true
                                } else {
                                    viewModel.togglePlayPause()
                                }
                            }
                            userInteractedTime = System.currentTimeMillis()
                        }
                    )
                } else {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = true
                            userInteractedTime = System.currentTimeMillis()
                        }
                    )
                }
            }
            .pointerInput(isControlsLocked) {
                if (!isControlsLocked) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (kotlin.math.abs(zoom - 1f) > 0.01f || zoomScale > 1.02f) {
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            zoomScale = newScale
                            if (newScale > 1.02f) {
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                zoomOffset = Offset(
                                    x = (zoomOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (zoomOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                zoomOffset = Offset.Zero
                            }
                            showZoomFeedback = true
                            userInteractedTime = System.currentTimeMillis()
                        }
                    }
                }
            }
            .pointerInput(isControlsLocked) {
                if (!isControlsLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (zoomScale <= 1.05f) {
                            change.consume()
                            val screenWidth = size.width
                            val isLeftSide = change.position.x < screenWidth * 0.5f

                            if (isLeftSide) {
                                // Adjust brightness
                                activity?.let { act ->
                                    val layoutParams = act.window.attributes
                                    val currentBrightness = if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness
                                    val delta = -dragAmount / 300f
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                    layoutParams.screenBrightness = newBrightness
                                    act.window.attributes = layoutParams
                                    gestureBrightnessLevel = newBrightness
                                }
                            } else {
                                // Adjust volume
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = if (dragAmount < 0) 1 else -1
                                val newVol = (currentVol + delta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                gestureVolumeLevel = newVol.toFloat() / maxVolume.toFloat()
                            }
                        }
                    }
                }
            }
    ) {
        // Video Surface with Pinch-to-Zoom & Pan
        if (isPlayerReady && viewModel.player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.player
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = when (aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val baseScaleX = if (isMirrored) -1f else 1f
                        scaleX = baseScaleX * zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Live Video Filters Overlay (Brightness, Contrast, Saturation & Preset Tint)
        if (videoFilterPreset != "Normal" || filterBrightness != 0f || filterContrast != 1f || filterSaturation != 1f) {
            // Brightness adjustment overlay
            if (filterBrightness > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = (filterBrightness * 0.65f).coerceIn(0f, 0.45f)))
                )
            } else if (filterBrightness < -0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (-filterBrightness * 0.85f).coerceIn(0f, 0.65f)))
                )
            }
            // Preset & Saturation atmospheric tint
            val presetOverlayColor = when (videoFilterPreset) {
                "Cinema HDR" -> Color(0xFF0D253F).copy(alpha = 0.12f)
                "Vivid" -> Color(0xFFFF6F00).copy(alpha = 0.08f)
                "Ultra Clarity 4K" -> Color(0xFF00E5FF).copy(alpha = 0.06f)
                "AMOLED True Black" -> Color(0xFF000000).copy(alpha = 0.18f)
                "Warm EyeCare" -> Color(0xFF3E2723).copy(alpha = 0.24f)
                "B&W" -> Color(0xFF757575).copy(alpha = 0.35f)
                else -> Color.Transparent
            }
            if (presetOverlayColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(presetOverlayColor)
                )
            }
        }

        // Night Mode Tint Overlay
        if (isNightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF281806).copy(alpha = 0.38f))
            )
        }

        // Smart Safe-Watch Privacy Blur / Shield Overlay (Track 3)
        if (isSafeBlurActive && isSafeModeEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A101D).copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "🛡️ مشهد محجوب بواسطة الحارس العائلي",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تم تعتيم الشاشة تلقائياً لحماية المشاهدة العائلية حتى انتهاء المشهد",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    FilledTonalButton(
                        onClick = { viewModel.seekRelative(5000L) }
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تخطي المشهد فوراً (+5s)")
                    }
                }
            }
        }

        // Active Family Guard Notification Banner
        if (safeWatchBannerMessage != null && isSafeModeEnabled) {
            Surface(
                color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = safeWatchBannerMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Synchronized External Subtitles Overlay
        if (subtitlesEnabled && !currentSubtitleText.isNullOrBlank()) {
            val subColor = when (subtitleColorIndex) {
                1 -> Color(0xFFFFEB3B) // Yellow
                2 -> Color(0xFF00E5FF) // Cyan
                3 -> Color(0xFF69F0AE) // Green
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (areControlsVisible && !isControlsLocked) 136.dp else 28.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                    .background(
                        color = Color.Black.copy(alpha = subtitleBgOpacity),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = currentSubtitleText ?: "",
                    color = subColor,
                    fontSize = subtitleFontSizeSp.sp,
                    fontFamily = when (subtitleFontFamilyIndex) {
                        1 -> androidx.compose.ui.text.font.FontFamily.Serif
                        2 -> androidx.compose.ui.text.font.FontFamily.Monospace
                        3 -> androidx.compose.ui.text.font.FontFamily.Cursive
                        else -> androidx.compose.ui.text.font.FontFamily.Default
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = (subtitleFontSizeSp * 1.3f).sp
                )
            }
        }

        // Floating Reset Zoom Pill when zoomed in
        if (zoomScale > 1.05f && !isControlsLocked) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                },
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "تكبير ${(zoomScale * 100).toInt()}% • إعادة ضبط",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gesture Feedback Indicators
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                gestureBrightnessLevel?.let { level ->
                    GestureOverlayIndicator(
                        icon = Icons.Default.BrightnessMedium,
                        label = stringResource(R.string.player_brightness),
                        progress = level
                    )
                }
                gestureVolumeLevel?.let { level ->
                    GestureOverlayIndicator(
                        icon = if (level <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        label = stringResource(R.string.player_volume),
                        progress = level
                    )
                }
                if (showZoomFeedback && gestureBrightnessLevel == null && gestureVolumeLevel == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            doubleTapSeekFeedback?.let { feedback ->
                val alignment = when (doubleTapSide) {
                    -1 -> Alignment.CenterStart
                    1 -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
                Card(
                    modifier = Modifier.align(alignment),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.78f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (doubleTapSide == -1) Icons.Default.Replay10 else Icons.Default.Forward10,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = feedback,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // Controls Layer
        if (isControlsLocked) {
            // Screen is locked: Show only the Unlock Floating Pill when tapped
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.toggleControlsLock()
                        Toast.makeText(context, "تم فتح قفل الشاشة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock")
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_unlock_prompt), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Normal Controls Overlay
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // SLEEK & COMPACT TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = currentMedia?.name ?: "SafePlayer Video",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Cast button
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "جاري البحث عن أجهزة العرض والشاشات الذكية...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(19.dp))
                        }

                        // Subtitles CC
                        IconButton(
                            onClick = { showSubtitlesDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Playlist
                        IconButton(
                            onClick = { showPlaylistSheet = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlist", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 3 dots More Menu
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_video_info)) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showInfoDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_equalizer)) },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showEqualizerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_sleep_timer)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showSleepTimerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_safe_mode)) },
                                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = if (isSafeModeEnabled) Color(0xFF4CAF50) else LocalContentColor.current) },
                                    trailingIcon = {
                                        Switch(checked = isSafeModeEnabled, onCheckedChange = { viewModel.toggleSafeMode() })
                                    },
                                    onClick = {
                                        viewModel.toggleSafeMode()
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_editing_studio)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        currentMedia?.id?.let { onNavigateToEditing(it) }
                                    }
                                )
                            }
                        }
                    }

                    // CENTER QUICK ACTION ICONS (Matching Screenshot_٢٠٢٦٠٩١١-٢١٠٨٢٢.png)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Instant Snapshot (التقاط صورة فورية من الفيديو 📸)
                        if (!isAudio) {
                            PlayerPillAction(
                                icon = Icons.Default.PhotoCamera,
                                label = "لقطة شاشة 📸",
                                isActive = false,
                                onClick = {
                                    viewModel.captureVideoSnapshot(context) { success, fileName, _ ->
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                "تم حفظ لقطة الفيديو بنجاح في الاستوديو 📸 ($fileName)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "تعذر التقاط الإطار الحالي، جرب أثناء تشغيل الفيديو",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )

                            // Live Video Filters (فلاتر الصورة والألوان 🎨)
                            PlayerPillAction(
                                icon = Icons.Default.Palette,
                                label = if (videoFilterPreset != "Normal") "فلتر ($videoFilterPreset)" else "فلاتر الصورة 🎨",
                                isActive = videoFilterPreset != "Normal" || filterBrightness != 0f || filterContrast != 1f || filterSaturation != 1f,
                                onClick = { showVideoFiltersDialog = true }
                            )
                        }

                        // Smart Safe-Watch Family Guard (الحارس العائلي 🛡️)
                        val safeRulesCount = markers.count { it.type.startsWith("SAFE_") }
                        PlayerPillAction(
                            icon = Icons.Default.Shield,
                            label = if (safeRulesCount > 0) "الحارس ($safeRulesCount) 🛡️" else "الحارس العائلي",
                            isActive = isSafeModeEnabled && safeRulesCount > 0,
                            onClick = { showSafeWatchSheet = true }
                        )

                        // Night Mode
                        PlayerPillAction(
                            icon = Icons.Default.Nightlight,
                            label = stringResource(R.string.player_night_mode),
                            isActive = isNightMode,
                            onClick = { viewModel.toggleNightMode() }
                        )

                        // AB Repeat
                        PlayerPillAction(
                            icon = if (isAbRepeatActive) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            label = when {
                                isAbRepeatActive -> "تكرار AB مفعل"
                                abPointA != null -> "حدد النقطة B"
                                else -> stringResource(R.string.player_ab_repeat)
                            },
                            isActive = isAbRepeatActive || abPointA != null,
                            onClick = {
                                val hadA = abPointA != null
                                val hadB = abPointB != null
                                viewModel.setAbRepeatPoint()
                                Toast.makeText(
                                    context,
                                    when {
                                        !hadA -> "تم تحديد النقطة A (${formatDuration(currentPosition)}) • اضغط مرة أخرى لتحديد B"
                                        !hadB -> "تم تفعيل تكرار المقطع A-B 🔁"
                                        else -> "تم إلغاء تكرار المقطع A-B"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        // Bookmarks (العلامات المرجعية)
                        PlayerPillAction(
                            icon = Icons.Default.BookmarkAdd,
                            label = "حفظ علامة",
                            isActive = false,
                            onClick = {
                                bookmarkNoteInput = "مشهد عند ${formatDuration(currentPosition)}"
                                showAddBookmarkDialog = true
                            }
                        )

                        PlayerPillAction(
                            icon = Icons.Default.Bookmarks,
                            label = "العلامات (${markers.size})",
                            isActive = markers.isNotEmpty(),
                            onClick = { showBookmarksSheet = true }
                        )

                        // PiP Window
                        PlayerPillAction(
                            icon = Icons.Default.PictureInPicture,
                            label = stringResource(R.string.player_pip),
                            isActive = false,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val paramsBuilder = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                        activity?.enterPictureInPictureMode(paramsBuilder.build())
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "تعذر تشغيل النافذة العائمة PiP", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "ميزة PiP غير مدعومة على هذا الإصدار", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Subtitles (الترجمة)
                        PlayerPillAction(
                            icon = Icons.Default.ClosedCaption,
                            label = if (subtitlesEnabled) "الترجمة مفعلة" else "الترجمة CC",
                            isActive = subtitlesEnabled,
                            onClick = { showSubtitlesDialog = true }
                        )

                        // Equalizer
                        PlayerPillAction(
                            icon = Icons.Default.Tune,
                            label = if (equalizerPreset != "Flat" || bassBoostLevel > 0 || volumeBoostLevel > 0)
                                "الصوت (${equalizerPreset})"
                            else
                                stringResource(R.string.player_equalizer),
                            isActive = isEqualizerEnabled && (equalizerPreset != "Flat" || bassBoostLevel > 0 || volumeBoostLevel > 0),
                            onClick = { showEqualizerDialog = true }
                        )

                        // Sleep Timer Quick Pill
                        PlayerPillAction(
                            icon = Icons.Default.Timer,
                            label = when {
                                sleepTimerRemainingSeconds != null -> formatDuration((sleepTimerRemainingSeconds ?: 0L) * 1000L)
                                sleepTimerMinutes == -1 -> "نهاية المقطع"
                                else -> "مؤقت النوم"
                            },
                            isActive = sleepTimerMinutes != null,
                            onClick = { showSleepTimerDialog = true }
                        )

                        // Mirror
                        PlayerPillAction(
                            icon = Icons.Default.Flip,
                            label = stringResource(R.string.player_mirror),
                            isActive = isMirrored,
                            onClick = { viewModel.toggleMirror() }
                        )

                        // Orientation (Rotate & Flip)
                        PlayerPillAction(
                            icon = if (isOrientationLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                            label = if (isOrientationLocked) "مؤمن 🔒" else stringResource(R.string.player_orientation),
                            isActive = isOrientationLocked || isLandscape,
                            onClick = {
                                if (!isOrientationLocked) {
                                    isLandscape = !isLandscape
                                    activity?.requestedOrientation = if (isLandscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                    Toast.makeText(context, if (isLandscape) "أفقي معكوس" else "أفقي", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "الوضع الحالي مؤمن 🔒. اضغط على تأمين الوضع لفك القفل.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Lock Orientation (تأمين وضع الشاشة الحالية لمنع الانقلاب)
                        PlayerPillAction(
                            icon = if (isOrientationLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            label = if (isOrientationLocked) "فك التأمين" else "تأمين الوضع",
                            isActive = isOrientationLocked,
                            onClick = {
                                val locked = viewModel.toggleOrientationLock()
                                if (locked) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                    Toast.makeText(context, "تم تأمين وضع الشاشة الحالي ومنع الانقلاب 🔒", Toast.LENGTH_SHORT).show()
                                } else {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    Toast.makeText(context, "تم فك تأمين الشاشة (التدوير مفعل)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Mute
                        PlayerPillAction(
                            icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            label = if (isMuted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                            isActive = isMuted,
                            onClick = { viewModel.toggleMute() }
                        )

                        // Background Play
                        PlayerPillAction(
                            icon = Icons.Default.Headphones,
                            label = stringResource(R.string.player_bg_play),
                            isActive = isVideoBgPlayEnabled || isAudio,
                            onClick = {
                                if (!isAudio) {
                                    val enabled = viewModel.toggleVideoBgPlay()
                                    Toast.makeText(
                                        context,
                                        if (enabled) "تم تفعيل تشغيل الفيديو في الخلفية 🎧 (اضغط مطولاً على زر التشغيل للتحكم)"
                                        else "تم إيقاف تشغيل الفيديو في الخلفية",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "الصوت يعمل في الخلفية تلقائياً 🎧", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // Speed
                        PlayerPillAction(
                            icon = Icons.Default.Speed,
                            label = "${playbackSpeed}X",
                            isActive = playbackSpeed != 1.0f,
                            onClick = { viewModel.cyclePlaybackSpeed() }
                        )
                    }

                    // BOTTOM CONTROLS & TIMELINE
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        // Active A-B Repeat Status Banner, Sleep Timer Countdown & Bookmarks Quick Chips
                        if (abPointA != null || sleepTimerMinutes != null || markers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sleepTimerMinutes != null) {
                                    Surface(
                                        onClick = { showSleepTimerDialog = true },
                                        color = Color(0xFF00897B),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            val timerText = if (sleepTimerMinutes == -1) {
                                                "مؤقت النوم: عند انتهاء المقطع"
                                            } else {
                                                "مؤقت النوم: ${formatDuration((sleepTimerRemainingSeconds ?: 0L) * 1000L)}"
                                            }
                                            Text(
                                                text = timerText,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (abPointA != null) {
                                    Surface(
                                        onClick = { viewModel.clearAbRepeat() },
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.RepeatOne, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            val aStr = formatDuration(abPointA ?: 0L)
                                            val bStr = abPointB?.let { formatDuration(it) } ?: "..."
                                            Text(
                                                text = "تكرار A-B ($aStr - $bStr) ✕",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                markers.forEach { marker ->
                                    Surface(
                                        onClick = { viewModel.seekTo(marker.timeMs) },
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "${marker.name} (${formatDuration(marker.timeMs)})",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sleek Timeline Seek Bar & Time Labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() else 0f,
                                onValueChange = { targetPos ->
                                    viewModel.seekTo(targetPos.toLong())
                                    userInteractedTime = System.currentTimeMillis()
                                },
                                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            val remainingMs = (duration - currentPosition).coerceAtLeast(0L)
                            Text(
                                text = "-${formatDuration(remainingMs)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Compact Playback Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aspect Ratio
                            IconButton(
                                onClick = {
                                    viewModel.cycleAspectRatio()
                                    Toast.makeText(context, context.getString(aspectRatioMode.labelRes), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.FitScreen, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(19.dp))
                            }

                            // Playback Speed quick button
                            Text(
                                text = "${playbackSpeed}X",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { showSpeedDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            // Rewind 10s
                            IconButton(
                                onClick = { viewModel.seekRelative(-10000L) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            // Previous
                            IconButton(
                                onClick = { viewModel.playPrevious() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            // Play / Pause (Compact Circle with Long Press for Background Play)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isVideoBgPlayEnabled) Color(0xFF2196F3) else Color.White)
                                    .pointerInput(currentMedia, isVideoBgPlayEnabled) {
                                        detectTapGestures(
                                            onTap = { viewModel.togglePlayPause() },
                                            onLongPress = {
                                                if (!isAudio) {
                                                    val enabled = viewModel.toggleVideoBgPlay()
                                                    Toast.makeText(
                                                        context,
                                                        if (enabled) "تم تفعيل تشغيل الفيديو في الخلفية 🎧"
                                                        else "تم إيقاف تشغيل الفيديو في الخلفية",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(context, "الصوت يعمل في الخلفية تلقائياً 🎧", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = if (isVideoBgPlayEnabled) Color.White else Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Next
                            IconButton(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { viewModel.seekRelative(10000L) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            // Lock Screen Controls (🔒)
                            IconButton(
                                onClick = {
                                    viewModel.toggleControlsLock()
                                    Toast.makeText(context, context.getString(R.string.player_screen_locked), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Video Information
    if (showInfoDialog) {
        val media = currentMedia
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.player_video_info), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اسم الملف: ${media?.name ?: "غير معروف"}")
                    Text("المسار: ${media?.path ?: "غير معروف"}")
                    Text("الحجم: ${formatFileSize(media?.sizeBytes ?: 0L)}")
                    Text("المدة: ${formatDuration(duration)}")
                    Text("النوع البرمجي: ${media?.mimeType ?: "فيديو"}")
                    Text("النوع: ${if (media?.mediaType == com.example.data.MediaType.VIDEO) "فيديو" else "صوت"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // DIALOG: Professional 5-Band Equalizer & Audio Boost
    if (showEqualizerDialog) {
        val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        val presets = listOf(
            "Flat" to "طبيعي Flat",
            "Cinema" to "سينما 🎬",
            "Podcast" to "بودكاست 🎙️",
            "Music" to "موسيقى 🎵",
            "Bass Boost" to "تضخيم الباس 🔊",
            "Vocal" to "صوت بشري 🗣️",
            "Rock" to "روك 🎸",
            "Classical" to "كلاسيك 🎻"
        )
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("موازن الصوت الاحترافي 🎚️", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isEqualizerEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Presets Row
                    Text("الأنماط الصوتية الجاهزة:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (presetKey, labelAr) ->
                            FilterChip(
                                selected = equalizerPreset == presetKey,
                                onClick = { viewModel.setEqualizerPreset(presetKey) },
                                enabled = isEqualizerEnabled,
                                label = { Text(labelAr) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // 5-Band Sliders
                    Text("ترددات الصوت (5-Band EQ):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    bandLabels.forEachIndexed { idx, freqLabel ->
                        val levelDb = eqBandLevels.getOrElse(idx) { 0f }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                modifier = Modifier.width(52.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = levelDb,
                                onValueChange = { viewModel.setEqBandLevel(idx, it) },
                                valueRange = -15f..15f,
                                enabled = isEqualizerEnabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (levelDb >= 0) "+" else ""}${levelDb.toInt()}dB",
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    HorizontalDivider()

                    // Bass Boost Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تضخيم الصوت الجهوري (Bass Boost)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text("$bassBoostLevel%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoostLevel.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = isEqualizerEnabled
                    )

                    // Volume / Loudness Boost Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مضاعفة مستوى الصوت (Loudness Boost)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text("+${volumeBoostLevel}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = volumeBoostLevel.toFloat(),
                        onValueChange = { viewModel.setVolumeBoost(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = isEqualizerEnabled
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEqualizerDialog = false }) {
                    Text("تم")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetEqualizer() }) {
                    Text("إعادة ضبط")
                }
            }
        )
    }

    // DIALOG: External Subtitles Settings (SRT / VTT)
    if (showSubtitlesDialog) {
        val colorOptions = listOf(
            0 to ("أبيض" to Color.White),
            1 to ("أصفر" to Color(0xFFFFEB3B)),
            2 to ("سماوي" to Color(0xFF00E5FF)),
            3 to ("أخضر" to Color(0xFF69F0AE))
        )
        AlertDialog(
            onDismissRequest = { showSubtitlesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إعدادات الترجمة (SRT / VTT) 📝", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = subtitlesEnabled,
                        onCheckedChange = { viewModel.toggleSubtitles(it) }
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active file status
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = subtitleFileName ?: "لم يتم اختيار ملف ترجمة بعد",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (subtitleCues.isNotEmpty()) "عدد الجمل المحملة: ${subtitleCues.size} جملة" else "يدعم ملفات .srt و .vtt باللغة العربية والإنجليزية",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Load File / Online / Sample Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                subtitlePickerLauncher.launch(
                                    arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")
                                ) 
                            },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فتح ملف ترجمة (SRT/VTT)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showOnlineSubtitlesDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("بحث أونلاين", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.loadSampleArabicSubtitles()
                                Toast.makeText(context, "تم تفعيل الترجمة التجريبية العربية 📝", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("تجريبية", fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider()

                    // Arabic Font Selector
                    Text("نوع الخط العربي للترجمة:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "افتراضي", 1 to "نسخ", 2 to "عريض", 3 to "رقعة").forEach { (idx, label) ->
                            FilterChip(
                                selected = subtitleFontFamilyIndex == idx,
                                onClick = { viewModel.setSubtitleFontFamilyIndex(idx) },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Subtitle Sync Delay (-0.5s, -0.1s, 0.0s, +0.1s, +0.5s)
                    val delaySeconds = subtitleDelayMs / 1000.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مزامنة توقيت الترجمة:", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format("%+.1f ثانية", delaySeconds),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.adjustSubtitleDelay(-500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("-0.5s") }
                        OutlinedButton(
                            onClick = { viewModel.adjustSubtitleDelay(-100L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("-0.1s") }
                        TextButton(
                            onClick = { viewModel.resetSubtitleDelay() }
                        ) { Text("0.0s") }
                        OutlinedButton(
                            onClick = { viewModel.adjustSubtitleDelay(100L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("+0.1s") }
                        OutlinedButton(
                            onClick = { viewModel.adjustSubtitleDelay(500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("+0.5s") }
                    }

                    HorizontalDivider()

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("حجم خط الترجمة:", fontWeight = FontWeight.SemiBold)
                        Text("${subtitleFontSizeSp.toInt()} SP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = subtitleFontSizeSp,
                        onValueChange = { viewModel.setSubtitleFontSize(it) },
                        valueRange = 14f..30f
                    )

                    // Text Color Selector
                    Text("لون النص:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOptions.forEach { (idx, pair) ->
                            val (name, colorVal) = pair
                            FilterChip(
                                selected = subtitleColorIndex == idx,
                                onClick = { viewModel.setSubtitleColorIndex(idx) },
                                label = { Text(name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(colorVal)
                                            .border(1.dp, Color.Gray, CircleShape)
                                    )
                                }
                            )
                        }
                    }

                    // Background Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("شفافية خلفية الترجمة:", fontWeight = FontWeight.SemiBold)
                        Text("${(subtitleBgOpacity * 100).toInt()}%", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = subtitleBgOpacity,
                        onValueChange = { viewModel.setSubtitleBgOpacity(it) },
                        valueRange = 0f..1f
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSubtitlesDialog = false }) {
                    Text("إغلاق")
                }
            },
            dismissButton = {
                if (subtitleCues.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearSubtitles() }) {
                        Text("إزالة الترجمة", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // DIALOG: Smart Online Subtitles Search & 1-Tap Load
    if (showOnlineSubtitlesDialog) {
        var isSearching by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<List<com.example.subtitles.SubtitleDownloader.SubtitleResult>>(emptyList()) }

        LaunchedEffect(Unit) {
            isSearching = true
            searchResults = com.example.subtitles.SubtitleDownloader.searchSubtitles(currentMedia?.name ?: "video")
            isSearching = false
        }

        AlertDialog(
            onDismissRequest = { showOnlineSubtitlesDialog = false },
            title = { Text("البحث الذكي عن الترجمات 🌐", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "نتائج البحث لـ: ${currentMedia?.name ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSearching) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (searchResults.isEmpty()) {
                        Text("لم يتم العثور على ترجمات متطابقة.", color = MaterialTheme.colorScheme.error)
                    } else {
                        searchResults.forEach { sub ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.loadSubtitleText(sub.title, sub.srtContent)
                                        Toast.makeText(context, "تم تطبيق الترجمة بنجاح! 📝", Toast.LENGTH_SHORT).show()
                                        showOnlineSubtitlesDialog = false
                                        showSubtitlesDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sub.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = sub.language,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(sub.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOnlineSubtitlesDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // DIALOG: Smart Sleep Timer with Live Countdown
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("مؤقت النوم الذكي ⏰", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sleepTimerMinutes != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (sleepTimerMinutes == -1) {
                                        "سيتم إيقاف التشغيل عند انتهاء المقطع الحالي"
                                    } else {
                                        "الوقت المتبقي: ${formatDuration((sleepTimerRemainingSeconds ?: 0L) * 1000L)}"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (sleepTimerMinutes != -1) {
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.extendSleepTimer(5)
                                                Toast.makeText(context, "تم تمديد مؤقت النوم +5 دقائق ⏰", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("+5 دقائق إضافية")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setSleepTimer(null)
                                            Toast.makeText(context, "تم إيقاف مؤقت النوم", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("إلغاء المؤقت")
                                    }
                                }
                            }
                        }
                    }

                    listOf(
                        null to stringResource(R.string.player_sleep_timer_off),
                        5 to "5 دقائق",
                        10 to "10 دقائق",
                        15 to "15 دقيقة",
                        20 to "20 دقيقة",
                        30 to "30 دقيقة",
                        45 to "45 دقيقة",
                        60 to "60 دقيقة",
                        -1 to stringResource(R.string.player_sleep_timer_end_video)
                    ).forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                    Toast.makeText(context, "تم ضبط مؤقت النوم: $label", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sleepTimerMinutes == minutes,
                                onClick = {
                                    viewModel.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // DIALOG: Speed Selector
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("سرعة التشغيل", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = kotlin.math.abs(playbackSpeed - speed) < 0.05f,
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${speed}X", style = MaterialTheme.typography.bodyLarge, fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // BOTTOM SHEET: Playlist Items
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "قائمة المقاطع (${playlistItems.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlistItems) { item ->
                        val isCurrent = item.id == currentMedia?.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.loadMediaItem(item)
                                    showPlaylistSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatDuration(item.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Add Bookmark (حفظ علامة مرجعية)
    if (showAddBookmarkDialog) {
        val bookmarkTime = currentPosition
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("حفظ علامة مرجعية 🔖", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "التوقيت المحدد: ${formatDuration(bookmarkTime)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = bookmarkNoteInput,
                        onValueChange = { bookmarkNoteInput = it },
                        label = { Text("عنوان أو ملاحظة المشهد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = bookmarkNoteInput.ifBlank { "علامة عند ${formatDuration(bookmarkTime)}" }
                        viewModel.addMarker(bookmarkTime, title)
                        showAddBookmarkDialog = false
                        Toast.makeText(context, "تم حفظ العلامة المرجعية بنجاح 🔖", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // BOTTOM SHEET: Bookmarks List (قائمة العلامات المرجعية)
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "العلامات المرجعية (${markers.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            showBookmarksSheet = false
                            bookmarkNoteInput = "مشهد عند ${formatDuration(currentPosition)}"
                            showAddBookmarkDialog = true
                        }
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إضافة")
                    }
                }

                if (markers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "لا توجد علامات مرجعية محفوظة لهذا المقطع",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(markers, key = { it.id }) { marker ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.seekTo(marker.timeMs)
                                        showBookmarksSheet = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = marker.name ?: "علامة مرجعية",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatDuration(marker.timeMs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeMarker(marker.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "حذف العلامة",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // DIALOG: Live Video Filters & Color Enhancement (Track 2)
    if (showVideoFiltersDialog) {
        val filterPresets = listOf(
            "Normal" to "طبيعي",
            "Cinema HDR" to "سينما HDR 🎬",
            "Ultra Clarity 4K" to "وضوح فائق 4K 💎",
            "Vivid" to "ألوان زاهية ✨",
            "AMOLED True Black" to "سواد AMOLED 🌑",
            "Warm EyeCare" to "حماية العين 👁️",
            "Sharp" to "تباين حاد ⚡",
            "B&W" to "أبيض وأسود 🎞️"
        )
        AlertDialog(
            onDismissRequest = { showVideoFiltersDialog = false },
            title = {
                Text("فلاتر وتحسين الصورة المباشرة 🎨", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("الأنماط البصرية الجاهزة:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterPresets.forEach { (key, labelAr) ->
                            FilterChip(
                                selected = videoFilterPreset == key,
                                onClick = { viewModel.setVideoFilterPreset(key) },
                                label = { Text(labelAr) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Brightness
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("إضاءة الفيديو (Brightness):", fontWeight = FontWeight.SemiBold)
                        Text("${(filterBrightness * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = filterBrightness,
                        onValueChange = { viewModel.setFilterBrightness(it) },
                        valueRange = -0.5f..0.5f
                    )

                    // Contrast
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("التباين (Contrast):", fontWeight = FontWeight.SemiBold)
                        Text("${(filterContrast * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = filterContrast,
                        onValueChange = { viewModel.setFilterContrast(it) },
                        valueRange = 0.5f..2.0f
                    )

                    // Saturation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تشبع الألوان (Saturation):", fontWeight = FontWeight.SemiBold)
                        Text("${(filterSaturation * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = filterSaturation,
                        onValueChange = { viewModel.setFilterSaturation(it) },
                        valueRange = 0f..2.0f
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showVideoFiltersDialog = false }) {
                    Text("تطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetVideoFilters() }) {
                    Text("إعادة ضبط")
                }
            }
        )
    }

    // BOTTOM SHEET: Smart Safe-Watch & Family Guard Rules (Track 3)
    if (showSafeWatchSheet) {
        val safeRules = markers.filter { it.type.startsWith("SAFE_") }
        ModalBottomSheet(
            onDismissRequest = { showSafeWatchSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isSafeModeEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "الحارس العائلي والمشاهدة الآمنة 🛡️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تخطي أو كتم أو تعتيم المشاهد غير الملائمة تلقائياً",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isSafeModeEnabled,
                        onCheckedChange = { viewModel.toggleSafeMode() }
                    )
                }

                // Quick Add Rule at Current Position
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "إضافة قاعدة حماية فورية من الثانية الحالية (${formatDuration(currentPosition)} لمدة 5 ثوانٍ):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.addSafeWatchRule(
                                        startMs = currentPosition,
                                        endMs = currentPosition + 5000L,
                                        ruleType = "SAFE_SKIP",
                                        title = "تخطي مشهد عند ${formatDuration(currentPosition)}"
                                    )
                                    Toast.makeText(context, "تمت إضافة قاعدة تخطي تلقائي ⏩", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("⏩ تخطي", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.addSafeWatchRule(
                                        startMs = currentPosition,
                                        endMs = currentPosition + 5000L,
                                        ruleType = "SAFE_MUTE",
                                        title = "كتم صوت عند ${formatDuration(currentPosition)}"
                                    )
                                    Toast.makeText(context, "تمت إضافة قاعدة كتم صوت تلقائي 🔇", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("🔇 كتم صوت", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.addSafeWatchRule(
                                        startMs = currentPosition,
                                        endMs = currentPosition + 5000L,
                                        ruleType = "SAFE_BLUR",
                                        title = "تعتيم شاشة عند ${formatDuration(currentPosition)}"
                                    )
                                    Toast.makeText(context, "تمت إضافة قاعدة تعتيم بصري 🛡️", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("🛡️ تعتيم", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // AI Auto-Generate & Export/Import .safeplay Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.generateDemoAiSafeWatchRules()
                            Toast.makeText(context, "🤖 تم توليد 3 قواعد حماية ذكية (تخطي + كتم + تعتيم) على الشريط الزمني!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("توليد حماية ذكية 🤖", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val json = viewModel.exportSafeWatchRulesJson()
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ملف الحماية العائلية SafePlayer (.safeplay)")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة ملف الحماية (.safeplay)"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر مشاركة ملف الحماية", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تصدير", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportSafeWatchDialog = true }
                    ) {
                        Text("استيراد", fontSize = 12.sp)
                    }
                }

                Text(
                    text = "قواعد الحماية النشطة لهذا الفيديو (${safeRules.size}):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (safeRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد قواعد حماية مضافة بعد. اضغط على 'توليد حماية ذكية 🤖' أو أضف قاعدة يدوياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(safeRules, key = { it.id }) { rule ->
                            val endMs = rule.description?.toLongOrNull() ?: (rule.timeMs + 5000L)
                            val badgeText = when (rule.type) {
                                "SAFE_SKIP" -> "⏩ تخطي تلقائي"
                                "SAFE_MUTE" -> "🔇 كتم الصوت"
                                "SAFE_BLUR" -> "🛡️ تعتيم الشاشة"
                                else -> "حماية"
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.name ?: badgeText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "$badgeText • من ${formatDuration(rule.timeMs)} إلى ${formatDuration(endMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeMarker(rule.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف القاعدة", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // DIALOG: Import .safeplay Family Guard JSON
    if (showImportSafeWatchDialog) {
        AlertDialog(
            onDismissRequest = { showImportSafeWatchDialog = false },
            title = { Text("استيراد ملف الحماية العائلية (.safeplay)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الصق محتوى ملف الحماية (.safeplay JSON) لتطبيق قواعد التخطي والكتم تلقائياً على هذا الفيديو:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importSafeWatchJsonInput,
                        onValueChange = { importSafeWatchJsonInput = it },
                        label = { Text("محتوى ملف .safeplay") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importSafeWatchRulesJson(importSafeWatchJsonInput) { ok, count ->
                            if (ok) {
                                Toast.makeText(context, "تم استيراد $count قواعد حماية عائلية بنجاح 🛡️", Toast.LENGTH_SHORT).show()
                                importSafeWatchJsonInput = ""
                                showImportSafeWatchDialog = false
                            } else {
                                Toast.makeText(context, "صيغة النص غير صالحة، تأكد من نسخ ملف .safeplay كامل", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("استيراد وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportSafeWatchDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// Quick action pill button matching Screenshot_٢٠٢٦٠٩١١-٢١٠٨٢٢.png
@Composable
private fun PlayerPillAction(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgAnimColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
        animationSpec = tween(200),
        label = "pill_bg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgAnimColor,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f)
        ),
        tonalElevation = if (isActive) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GestureOverlayIndicator(
    icon: ImageVector,
    label: String,
    progress: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(140.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
