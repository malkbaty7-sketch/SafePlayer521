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
import androidx.compose.ui.graphics.asComposeRenderEffect
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
import android.view.LayoutInflater
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.MediaItem
import com.example.ui.glass.*
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditing: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val liquidGlassController = viewModel.liquidGlassController
    val liquidGlassConfig by liquidGlassController.config.collectAsState()
    var showLiquidGlassSheet by remember { mutableStateOf(false) }

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

    // Real-time Liquid Glass video backdrop sampling (only active when glass UI is visible)
    val anyGlassOverlayVisible = areControlsVisible ||
        showLiquidGlassSheet ||
        showPlaylistSheet ||
        showBookmarksSheet ||
        showSafeWatchSheet ||
        showInfoDialog ||
        showEqualizerDialog ||
        showSubtitlesDialog ||
        showOnlineSubtitlesDialog ||
        showVideoFiltersDialog ||
        showSleepTimerDialog ||
        showSpeedDialog ||
        showAddBookmarkDialog ||
        showImportSafeWatchDialog ||
        showMoreMenu ||
        gestureBrightnessLevel != null ||
        gestureVolumeLevel != null ||
        doubleTapSeekFeedback != null ||
        (subtitlesEnabled && !currentSubtitleText.isNullOrBlank())

    LaunchedEffect(isPlayerReady, anyGlassOverlayVisible, liquidGlassConfig.blurRadius, liquidGlassConfig.enableLiveVideoRefraction) {
        if (isPlayerReady && anyGlassOverlayVisible && !isAudio && liquidGlassConfig.blurRadius > 0.5f) {
            while (true) {
                liquidGlassController.sampleLiveVideoFrame()
                delay(45L)
            }
        }
    }

    // Auto-hide controls after 4 seconds of inactivity if playing and not locked
    LaunchedEffect(areControlsVisible, isPlaying, userInteractedTime, isControlsLocked, showMoreMenu, showLiquidGlassSheet) {
        if (areControlsVisible && isPlaying && !isControlsLocked && !showMoreMenu && !showLiquidGlassSheet) {
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
        // Video Surface with Pinch-to-Zoom, Pan & Hardware TextureView for Liquid Glass Refraction
        CompositionLocalProvider(LocalLiquidGlassController provides liquidGlassController) {
        if (isPlayerReady && viewModel.player != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_texture_view, null) as PlayerView).apply {
                        player = viewModel.player
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        liquidGlassController.attachPlayerView(this)
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.player
                    playerView.resizeMode = when (aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    liquidGlassController.attachPlayerView(playerView)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val baseScaleX = if (isMirrored) -1f else 1f
                        scaleX = baseScaleX * zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isSafeBlurActive && isSafeModeEnabled) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                65f,
                                65f,
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Live Video Filters Overlay (Brightness, Contrast, Saturation & Preset Tint)
        if (videoFilterPreset != "Normal" || filterBrightness != 0f || filterContrast != 1f || filterSaturation != 1f) {
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

        // Smart Safe-Watch Privacy Blur / Shield Overlay (Track 3 - Liquid Glass Shield)
        if (isSafeBlurActive && isSafeModeEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070C16).copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassContainer(
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
                    modifier = Modifier.padding(24.dp).widthIn(max = 440.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "🛡️ مشهد محجوب بواسطة الحارس العائلي",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "تم تضبيب الشاشة تلقائياً بتقنية الزجاج السائل لحماية المشاهدة العائلية حتى انتهاء المشهد",
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodySmall,
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
        }

        // Active Family Guard Notification Banner (Liquid Glass Pill)
        if (safeWatchBannerMessage != null && isSafeModeEnabled) {
            LiquidGlassContainer(
                shape = RoundedCornerShape(50),
                isHighlighted = true,
                highlightColor = Color(0xFF2E7D32),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 62.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
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

        // Synchronized External Subtitles Overlay (Liquid Glass Subtitle Container)
        if (subtitlesEnabled && !currentSubtitleText.isNullOrBlank()) {
            val subColor = when (subtitleColorIndex) {
                1 -> Color(0xFFFFEB3B) // Yellow
                2 -> Color(0xFF00E5FF) // Cyan
                3 -> Color(0xFF69F0AE) // Green
                else -> Color.White
            }
            LiquidGlassContainer(
                shape = RoundedCornerShape(14.dp),
                glassOpacityOverride = subtitleBgOpacity,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (areControlsVisible && !isControlsLocked) 144.dp else 28.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
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

        // Floating Reset Zoom Pill when zoomed in (Liquid Glass)
        if (zoomScale > 1.05f && !isControlsLocked) {
            LiquidGlassPill(
                icon = Icons.Default.ZoomOutMap,
                label = "تكبير ${(zoomScale * 100).toInt()}% • إعادة ضبط",
                isActive = true,
                onClick = {
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 66.dp)
            )
        }

        // Gesture Feedback Indicators (Liquid Glass HUDs)
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
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                LiquidGlassContainer(
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.align(alignment)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (doubleTapSide == -1) Icons.Default.Replay10 else Icons.Default.Forward10,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
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
            // Screen is locked: Show only the Liquid Glass Unlock Floating Pill when tapped
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
            ) {
                LiquidGlassPill(
                    icon = Icons.Default.LockOpen,
                    label = stringResource(R.string.player_unlock_prompt),
                    isActive = true,
                    onClick = {
                        viewModel.toggleControlsLock()
                        Toast.makeText(context, "تم فتح قفل الشاشة", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "unlock_screen_button"
                )
            }
        } else {
            // Normal Controls Overlay (Liquid Glass UI System)
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(240))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.52f)
                                )
                            )
                        )
                ) {
                    // 1. FLOATING LIQUID GLASS TOP BAR
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        testTag = "player_top_glass_bar"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onNavigateBack,
                                size = 34.dp,
                                iconSize = 18.dp,
                                testTag = "player_back_button"
                            )

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentMedia?.name ?: "SafePlayer Video",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Liquid Glass • Blur ${liquidGlassConfig.blurRadius.toInt()}px • ${(liquidGlassConfig.glassOpacity * 100).toInt()}%",
                                    color = Color(0xFF80DEEA),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            // Liquid Glass Studio Quick Trigger Button
                            LiquidGlassIconButton(
                                icon = Icons.Default.BlurOn,
                                contentDescription = "Liquid Glass Studio",
                                isActive = showLiquidGlassSheet,
                                iconTint = Color(0xFF00E5FF),
                                onClick = { showLiquidGlassSheet = true },
                                size = 34.dp,
                                iconSize = 19.dp,
                                testTag = "top_liquid_glass_button"
                            )

                            Spacer(Modifier.width(6.dp))

                            // Cast button
                            LiquidGlassIconButton(
                                icon = Icons.Default.Cast,
                                contentDescription = "Cast",
                                onClick = {
                                    Toast.makeText(context, "جاري البحث عن أجهزة العرض والشاشات الذكية...", Toast.LENGTH_SHORT).show()
                                },
                                size = 34.dp,
                                iconSize = 18.dp
                            )

                            Spacer(Modifier.width(6.dp))

                            // Subtitles CC
                            LiquidGlassIconButton(
                                icon = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                isActive = subtitlesEnabled,
                                onClick = { showSubtitlesDialog = true },
                                size = 34.dp,
                                iconSize = 19.dp
                            )

                            Spacer(Modifier.width(6.dp))

                            // Playlist
                            LiquidGlassIconButton(
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Playlist",
                                onClick = { showPlaylistSheet = true },
                                size = 34.dp,
                                iconSize = 20.dp
                            )

                            Spacer(Modifier.width(6.dp))

                            // 3 dots More Menu trigger
                            LiquidGlassIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "More",
                                isActive = showMoreMenu,
                                onClick = { showMoreMenu = !showMoreMenu },
                                size = 34.dp,
                                iconSize = 19.dp,
                                testTag = "player_more_menu_button"
                            )
                        }
                    }

                    // FLOATING LIQUID GLASS POP-UP MENU (Sits directly over active video surface)
                    LiquidGlassPopupMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        items = listOf(
                            LiquidGlassMenuItem(
                                title = "استوديو الزجاج السائل 💧",
                                icon = Icons.Default.BlurOn,
                                iconTint = Color(0xFF00E5FF),
                                badgeText = "${liquidGlassConfig.blurRadius.toInt()}px",
                                onClick = {
                                    showMoreMenu = false
                                    showLiquidGlassSheet = true
                                }
                            ),
                            LiquidGlassMenuItem(
                                title = stringResource(R.string.player_video_info),
                                icon = Icons.Default.Info,
                                onClick = {
                                    showMoreMenu = false
                                    showInfoDialog = true
                                }
                            ),
                            LiquidGlassMenuItem(
                                title = stringResource(R.string.player_equalizer),
                                icon = Icons.Default.Tune,
                                badgeText = if (isEqualizerEnabled) equalizerPreset else null,
                                onClick = {
                                    showMoreMenu = false
                                    showEqualizerDialog = true
                                }
                            ),
                            LiquidGlassMenuItem(
                                title = stringResource(R.string.player_sleep_timer),
                                icon = Icons.Default.Timer,
                                onClick = {
                                    showMoreMenu = false
                                    showSleepTimerDialog = true
                                }
                            ),
                            LiquidGlassMenuItem(
                                title = stringResource(R.string.player_safe_mode),
                                icon = Icons.Default.Shield,
                                iconTint = if (isSafeModeEnabled) Color(0xFF69F0AE) else Color.White,
                                isSwitch = true,
                                switchChecked = isSafeModeEnabled,
                                onClick = {
                                    viewModel.toggleSafeMode()
                                }
                            ),
                            LiquidGlassMenuItem(
                                title = stringResource(R.string.action_open_editing_studio),
                                icon = Icons.Default.ContentCut,
                                onClick = {
                                    showMoreMenu = false
                                    currentMedia?.id?.let { onNavigateToEditing(it) }
                                }
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 60.dp, end = 16.dp)
                    )

                    // 2. CENTER LIQUID GLASS QUICK ACTION PILLS
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Liquid Glass Studio Quick Pill (الزجاج السائل 💧)
                        PlayerPillAction(
                            icon = Icons.Default.BlurOn,
                            label = "الزجاج السائل 💧",
                            isActive = showLiquidGlassSheet,
                            onClick = { showLiquidGlassSheet = true }
                        )

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

                        // Lock Orientation
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

                    // 3. FLOATING LIQUID GLASS BOTTOM CONTROLS & TIMELINE DECK
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(26.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp, start = 14.dp, end = 14.dp),
                        testTag = "player_bottom_glass_deck"
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                        val timerText = if (sleepTimerMinutes == -1) {
                                            "مؤقت النوم: عند انتهاء المقطع"
                                        } else {
                                            "مؤقت النوم: ${formatDuration((sleepTimerRemainingSeconds ?: 0L) * 1000L)}"
                                        }
                                        LiquidGlassPill(
                                            icon = Icons.Default.Timer,
                                            label = timerText,
                                            isActive = true,
                                            activeColor = Color(0xFF00897B),
                                            onClick = { showSleepTimerDialog = true }
                                        )
                                    }

                                    if (abPointA != null) {
                                        val aStr = formatDuration(abPointA ?: 0L)
                                        val bStr = abPointB?.let { formatDuration(it) } ?: "..."
                                        LiquidGlassPill(
                                            icon = Icons.Default.RepeatOne,
                                            label = "تكرار A-B ($aStr - $bStr) ✕",
                                            isActive = true,
                                            onClick = { viewModel.clearAbRepeat() }
                                        )
                                    }

                                    markers.forEach { marker ->
                                        LiquidGlassPill(
                                            icon = Icons.Default.Bookmark,
                                            label = "${marker.name} (${formatDuration(marker.timeMs)})",
                                            isActive = false,
                                            onClick = { viewModel.seekTo(marker.timeMs) }
                                        )
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
                                    fontSize = 11.5.sp,
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
                                        .height(32.dp)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00E5FF),
                                        activeTrackColor = Color(0xFF00E5FF),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                    )
                                )

                                val remainingMs = (duration - currentPosition).coerceAtLeast(0L)
                                Text(
                                    text = "-${formatDuration(remainingMs)}",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 11.5.sp,
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
                                LiquidGlassIconButton(
                                    icon = Icons.Default.FitScreen,
                                    contentDescription = "Aspect Ratio",
                                    onClick = {
                                        viewModel.cycleAspectRatio()
                                        Toast.makeText(context, context.getString(aspectRatioMode.labelRes), Toast.LENGTH_SHORT).show()
                                    },
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )

                                // Playback Speed quick pill
                                LiquidGlassContainer(
                                    shape = RoundedCornerShape(10.dp),
                                    isHighlighted = playbackSpeed != 1.0f,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    onClick = { showSpeedDialog = true }
                                ) {
                                    Text(
                                        text = "${playbackSpeed}X",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Rewind 10s
                                LiquidGlassIconButton(
                                    icon = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    onClick = { viewModel.seekRelative(-10000L) },
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )

                                // Previous
                                LiquidGlassIconButton(
                                    icon = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    onClick = { viewModel.playPrevious() },
                                    size = 38.dp,
                                    iconSize = 22.dp
                                )

                                // Play / Pause (Liquid Glass Illuminated Orb with Long Press for Background Play)
                                LiquidGlassContainer(
                                    shape = CircleShape,
                                    isHighlighted = true,
                                    highlightColor = if (isVideoBgPlayEnabled) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary,
                                    borderWidth = 1.5.dp,
                                    modifier = Modifier
                                        .size(48.dp)
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
                                    testTag = "play_pause_glass_orb"
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Next
                                LiquidGlassIconButton(
                                    icon = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    onClick = { viewModel.playNext() },
                                    size = 38.dp,
                                    iconSize = 22.dp
                                )

                                // Forward 10s
                                LiquidGlassIconButton(
                                    icon = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    onClick = { viewModel.seekRelative(10000L) },
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )

                                // Lock Screen Controls (🔒)
                                LiquidGlassIconButton(
                                    icon = Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    onClick = {
                                        viewModel.toggleControlsLock()
                                        Toast.makeText(context, context.getString(R.string.player_screen_locked), Toast.LENGTH_SHORT).show()
                                    },
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // LIQUID GLASS BOTTOM SHEET: Real-time Liquid Glass Customizer Studio (SeekBars + Presets)
        LiquidGlassCustomizerSheet(
            visible = showLiquidGlassSheet,
            onDismissRequest = { showLiquidGlassSheet = false },
            controller = liquidGlassController
        )

        // LIQUID GLASS DIALOG: Video Information
        val media = currentMedia
        LiquidGlassDialog(
            visible = showInfoDialog,
            onDismissRequest = { showInfoDialog = false },
            title = stringResource(R.string.player_video_info),
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.action_close), color = Color(0xFF00E5FF))
                }
            }
        ) {
            Text("اسم الملف: ${media?.name ?: "غير معروف"}", color = Color.White)
            Text("المسار: ${media?.path ?: "غير معروف"}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Text("الحجم: ${formatFileSize(media?.sizeBytes ?: 0L)}", color = Color.White)
            Text("المدة: ${formatDuration(duration)}", color = Color.White)
            Text("النوع البرمجي: ${media?.mimeType ?: "فيديو"}", color = Color.White)
            Text("النوع: ${if (media?.mediaType == com.example.data.MediaType.VIDEO) "فيديو" else "صوت"}", color = Color.White)
        }

        // LIQUID GLASS DIALOG: Professional 5-Band Equalizer & Audio Boost
        val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        val eqPresets = listOf(
            "Flat" to "طبيعي Flat",
            "Cinema" to "سينما 🎬",
            "Podcast" to "بودكاست 🎙️",
            "Music" to "موسيقى 🎵",
            "Bass Boost" to "تضخيم الباس 🔊",
            "Vocal" to "صوت بشري 🗣️",
            "Rock" to "روك 🎸",
            "Classical" to "كلاسيك 🎻"
        )
        LiquidGlassDialog(
            visible = showEqualizerDialog,
            onDismissRequest = { showEqualizerDialog = false },
            title = "موازن الصوت الاحترافي 🎚️",
            headerTrailing = {
                Switch(
                    checked = isEqualizerEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                )
            },
            confirmButton = {
                Button(onClick = { showEqualizerDialog = false }) {
                    Text("تم")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetEqualizer() }) {
                    Text("إعادة ضبط", color = Color.White.copy(alpha = 0.8f))
                }
            }
        ) {
            Text("الأنماط الصوتية الجاهزة:", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eqPresets.forEach { (presetKey, labelAr) ->
                    LiquidGlassPill(
                        icon = Icons.Default.Tune,
                        label = labelAr,
                        isActive = equalizerPreset == presetKey,
                        onClick = { if (isEqualizerEnabled) viewModel.setEqualizerPreset(presetKey) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Text("ترددات الصوت (5-Band EQ):", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            bandLabels.forEachIndexed { idx, freqLabel ->
                val levelDb = eqBandLevels.getOrElse(idx) { 0f }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = freqLabel,
                        color = Color.White,
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
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.width(46.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("تضخيم الصوت الجهوري (Bass Boost)", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text("$bassBoostLevel%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = bassBoostLevel.toFloat(),
                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isEqualizerEnabled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("مضاعفة مستوى الصوت (Loudness Boost)", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text("+${volumeBoostLevel}%", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = volumeBoostLevel.toFloat(),
                onValueChange = { viewModel.setVolumeBoost(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isEqualizerEnabled
            )
        }

        // LIQUID GLASS DIALOG: External Subtitles Settings (SRT / VTT)
        val colorOptions = listOf(
            0 to ("أبيض" to Color.White),
            1 to ("أصفر" to Color(0xFFFFEB3B)),
            2 to ("سماوي" to Color(0xFF00E5FF)),
            3 to ("أخضر" to Color(0xFF69F0AE))
        )
        LiquidGlassDialog(
            visible = showSubtitlesDialog,
            onDismissRequest = { showSubtitlesDialog = false },
            title = "إعدادات الترجمة (SRT / VTT) 📝",
            headerTrailing = {
                Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = { viewModel.toggleSubtitles(it) }
                )
            },
            confirmButton = {
                Button(onClick = { showSubtitlesDialog = false }) {
                    Text("إغلاق")
                }
            },
            dismissButton = if (subtitleCues.isNotEmpty()) {
                {
                    TextButton(onClick = { viewModel.clearSubtitles() }) {
                        Text("إزالة الترجمة", color = Color(0xFFFF5252))
                    }
                }
            } else null
        ) {
            LiquidGlassContainer(
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = subtitleFileName ?: "لم يتم اختيار ملف ترجمة بعد",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (subtitleCues.isNotEmpty()) "عدد الجمل المحملة: ${subtitleCues.size} جملة" else "يدعم ملفات .srt و .vtt باللغة العربية والإنجليزية",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

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
                    Text("فتح ملف (SRT/VTT)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showOnlineSubtitlesDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("بحث أونلاين", color = Color.White, fontSize = 11.sp)
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

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Text("نوع الخط العربي للترجمة:", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "افتراضي", 1 to "نسخ", 2 to "عريض", 3 to "رقعة").forEach { (idx, label) ->
                    FilterChip(
                        selected = subtitleFontFamilyIndex == idx,
                        onClick = { viewModel.setSubtitleFontFamilyIndex(idx) },
                        label = { Text(label, color = Color.White) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            val delaySeconds = subtitleDelayMs / 1000.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("مزامنة توقيت الترجمة:", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    text = String.format("%+.1f ثانية", delaySeconds),
                    color = Color(0xFF00E5FF),
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
                ) { Text("-0.5s", color = Color.White) }
                OutlinedButton(
                    onClick = { viewModel.adjustSubtitleDelay(-100L) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("-0.1s", color = Color.White) }
                TextButton(
                    onClick = { viewModel.resetSubtitleDelay() }
                ) { Text("0.0s", color = Color(0xFF00E5FF)) }
                OutlinedButton(
                    onClick = { viewModel.adjustSubtitleDelay(100L) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("+0.1s", color = Color.White) }
                OutlinedButton(
                    onClick = { viewModel.adjustSubtitleDelay(500L) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("+0.5s", color = Color.White) }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("حجم خط الترجمة:", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${subtitleFontSizeSp.toInt()} SP", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = subtitleFontSizeSp,
                onValueChange = { viewModel.setSubtitleFontSize(it) },
                valueRange = 14f..30f
            )

            Text("لون النص:", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorOptions.forEach { (idx, pair) ->
                    val (name, colorVal) = pair
                    FilterChip(
                        selected = subtitleColorIndex == idx,
                        onClick = { viewModel.setSubtitleColorIndex(idx) },
                        label = { Text(name, color = Color.White) },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("شفافية خلفية الترجمة:", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${(subtitleBgOpacity * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = subtitleBgOpacity,
                onValueChange = { viewModel.setSubtitleBgOpacity(it) },
                valueRange = 0f..1f
            )
        }

        // LIQUID GLASS DIALOG: Smart Online Subtitles Search & 1-Tap Load
        if (showOnlineSubtitlesDialog) {
            var isSearching by remember { mutableStateOf(false) }
            var searchResults by remember { mutableStateOf<List<com.example.subtitles.SubtitleDownloader.SubtitleResult>>(emptyList()) }

            LaunchedEffect(Unit) {
                isSearching = true
                searchResults = com.example.subtitles.SubtitleDownloader.searchSubtitles(currentMedia?.name ?: "video")
                isSearching = false
            }

            LiquidGlassDialog(
                visible = showOnlineSubtitlesDialog,
                onDismissRequest = { showOnlineSubtitlesDialog = false },
                title = "البحث الذكي عن الترجمات 🌐",
                confirmButton = {
                    TextButton(onClick = { showOnlineSubtitlesDialog = false }) {
                        Text("إغلاق", color = Color.White)
                    }
                }
            ) {
                Text(
                    text = "نتائج البحث لـ: ${currentMedia?.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                if (isSearching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                    }
                } else if (searchResults.isEmpty()) {
                    Text("لم يتم العثور على ترجمات متطابقة.", color = Color(0xFFFF5252))
                } else {
                    searchResults.forEach { sub ->
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(12.dp),
                            onClick = {
                                viewModel.loadSubtitleText(sub.title, sub.srtContent)
                                Toast.makeText(context, "تم تطبيق الترجمة بنجاح! 📝", Toast.LENGTH_SHORT).show()
                                showOnlineSubtitlesDialog = false
                                showSubtitlesDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFF00E5FF))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sub.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = sub.language,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF00E5FF),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(sub.source, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // LIQUID GLASS DIALOG: Smart Sleep Timer with Live Countdown
        LiquidGlassDialog(
            visible = showSleepTimerDialog,
            onDismissRequest = { showSleepTimerDialog = false },
            title = "مؤقت النوم الذكي ⏰",
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(R.string.action_close), color = Color.White)
                }
            }
        ) {
            if (sleepTimerMinutes != null) {
                LiquidGlassContainer(
                    shape = RoundedCornerShape(16.dp),
                    isHighlighted = true,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                            color = Color.White
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
                                Text("إلغاء المؤقت", color = Color.White)
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
                        .padding(vertical = 6.dp, horizontal = 8.dp),
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
                    Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // LIQUID GLASS DIALOG: Speed Selector
        LiquidGlassDialog(
            visible = showSpeedDialog,
            onDismissRequest = { showSpeedDialog = false },
            title = "سرعة التشغيل ⚡",
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.action_close), color = Color.White)
                }
            }
        ) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setPlaybackSpeed(speed)
                            showSpeedDialog = false
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
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
                    Text(
                        text = "${speed}X",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // LIQUID GLASS BOTTOM SHEET: Playlist Items
        LiquidGlassBottomSheet(
            visible = showPlaylistSheet,
            onDismissRequest = { showPlaylistSheet = false },
            title = "قائمة المقاطع (${playlistItems.size}) 🎬"
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlistItems) { item ->
                    val isCurrent = item.id == currentMedia?.id
                    LiquidGlassContainer(
                        shape = RoundedCornerShape(14.dp),
                        isHighlighted = isCurrent,
                        contentPadding = PaddingValues(12.dp),
                        onClick = {
                            viewModel.loadMediaItem(item)
                            showPlaylistSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = if (isCurrent) Color(0xFF00E5FF) else Color.White
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = Color.White,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatDuration(item.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // LIQUID GLASS DIALOG: Add Bookmark (حفظ علامة مرجعية)
        val bookmarkTime = currentPosition
        LiquidGlassDialog(
            visible = showAddBookmarkDialog,
            onDismissRequest = { showAddBookmarkDialog = false },
            title = "حفظ علامة مرجعية 🔖",
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
                    Text(stringResource(R.string.action_close), color = Color.White)
                }
            }
        ) {
            Text(
                text = "التوقيت المحدد: ${formatDuration(bookmarkTime)}",
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = bookmarkNoteInput,
                onValueChange = { bookmarkNoteInput = it },
                label = { Text("عنوان أو ملاحظة المشهد", color = Color.White.copy(alpha = 0.8f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // LIQUID GLASS BOTTOM SHEET: Bookmarks List (قائمة العلامات المرجعية)
        LiquidGlassBottomSheet(
            visible = showBookmarksSheet,
            onDismissRequest = { showBookmarksSheet = false },
            title = "العلامات المرجعية (${markers.size}) 🔖",
            headerAction = {
                FilledTonalButton(
                    onClick = {
                        showBookmarksSheet = false
                        bookmarkNoteInput = "مشهد عند ${formatDuration(currentPosition)}"
                        showAddBookmarkDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة", fontSize = 12.sp)
                }
            }
        ) {
            if (markers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "لا توجد علامات مرجعية محفوظة لهذا المقطع",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(markers, key = { it.id }) { marker ->
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            onClick = {
                                viewModel.seekTo(marker.timeMs)
                                showBookmarksSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = marker.name ?: "علامة مرجعية",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatDuration(marker.timeMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                IconButton(onClick = { viewModel.removeMarker(marker.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "حذف العلامة",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // LIQUID GLASS DIALOG: Live Video Filters & Color Enhancement (Track 2)
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
        LiquidGlassDialog(
            visible = showVideoFiltersDialog,
            onDismissRequest = { showVideoFiltersDialog = false },
            title = "فلاتر وتحسين الصورة المباشرة 🎨",
            confirmButton = {
                Button(onClick = { showVideoFiltersDialog = false }) {
                    Text("تطبيق")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetVideoFilters() }) {
                    Text("إعادة ضبط", color = Color.White.copy(alpha = 0.8f))
                }
            }
        ) {
            Text("الأنماط البصرية الجاهزة:", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterPresets.forEach { (key, labelAr) ->
                    LiquidGlassPill(
                        icon = Icons.Default.Palette,
                        label = labelAr,
                        isActive = videoFilterPreset == key,
                        onClick = { viewModel.setVideoFilterPreset(key) }
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("إضاءة الفيديو (Brightness):", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${(filterBrightness * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = filterBrightness,
                onValueChange = { viewModel.setFilterBrightness(it) },
                valueRange = -0.5f..0.5f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("التباين (Contrast):", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${(filterContrast * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = filterContrast,
                onValueChange = { viewModel.setFilterContrast(it) },
                valueRange = 0.5f..2.0f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("تشبع الألوان (Saturation):", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${(filterSaturation * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = filterSaturation,
                onValueChange = { viewModel.setFilterSaturation(it) },
                valueRange = 0f..2.0f
            )
        }

        // LIQUID GLASS BOTTOM SHEET: Smart Safe-Watch & Family Guard Rules (Track 3)
        val safeRules = markers.filter { it.type.startsWith("SAFE_") }
        LiquidGlassBottomSheet(
            visible = showSafeWatchSheet,
            onDismissRequest = { showSafeWatchSheet = false },
            title = "الحارس العائلي والمشاهدة الآمنة 🛡️",
            subtitle = "تخطي أو كتم أو تعتيم المشاهد غير الملائمة تلقائياً",
            headerAction = {
                Switch(
                    checked = isSafeModeEnabled,
                    onCheckedChange = { viewModel.toggleSafeMode() }
                )
            }
        ) {
            LiquidGlassContainer(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "إضافة قاعدة حماية فورية من الثانية الحالية (${formatDuration(currentPosition)} لمدة 5 ثوانٍ):",
                        color = Color.White,
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
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تصدير", color = Color.White, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showImportSafeWatchDialog = true }
                ) {
                    Text("استيراد", color = Color.White, fontSize = 12.sp)
                }
            }

            Text(
                text = "قواعد الحماية النشطة لهذا الفيديو (${safeRules.size}):",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )

            if (safeRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد قواعد حماية مضافة بعد. اضغط على 'توليد حماية ذكية 🤖' أو أضف قاعدة يدوياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
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
                        LiquidGlassContainer(
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name ?: badgeText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "$badgeText • من ${formatDuration(rule.timeMs)} إلى ${formatDuration(endMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                IconButton(onClick = { viewModel.removeMarker(rule.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف القاعدة", tint = Color(0xFFFF5252))
                                }
                            }
                        }
                    }
                }
            }
        }

        // LIQUID GLASS DIALOG: Import .safeplay Family Guard JSON
        LiquidGlassDialog(
            visible = showImportSafeWatchDialog,
            onDismissRequest = { showImportSafeWatchDialog = false },
            title = "استيراد ملف الحماية العائلية (.safeplay)",
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
                    Text("إلغاء", color = Color.White)
                }
            }
        ) {
            Text(
                text = "الصق محتوى ملف الحماية (.safeplay JSON) لتطبيق قواعد التخطي والكتم تلقائياً على هذا الفيديو:",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = importSafeWatchJsonInput,
                onValueChange = { importSafeWatchJsonInput = it },
                label = { Text("محتوى ملف .safeplay", color = Color.White.copy(alpha = 0.75f)) },
                minLines = 4,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        }
    }
}

// Reusable Liquid Glass quick action pill button
@Composable
private fun PlayerPillAction(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassPill(
        icon = icon,
        label = label,
        isActive = isActive,
        onClick = onClick
    )
}

@Composable
private fun GestureOverlayIndicator(
    icon: ImageVector,
    label: String,
    progress: Float
) {
    LiquidGlassContainer(
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
            }
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(140.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00E5FF),
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
