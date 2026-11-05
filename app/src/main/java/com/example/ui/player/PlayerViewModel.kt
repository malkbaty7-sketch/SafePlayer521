package com.example.ui.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.R
import com.example.data.MediaRepository
import com.example.data.WatchedState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class AspectRatioMode(val labelRes: Int) {
    FIT(R.string.player_aspect_fit),
    FILL(R.string.player_aspect_fill),
    ZOOM(R.string.player_aspect_zoom)
}

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

class PlayerViewModel(
    application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: Player? = null
        private set

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = _isPlayerReady.asStateFlow()

    var currentMediaId: Int? = null
        private set
    private var pendingMediaItem: com.example.data.MediaItem? = null
    private var pendingStartMs: Long? = null

    private val _currentMedia = MutableStateFlow<com.example.data.MediaItem?>(null)
    val currentMedia: StateFlow<com.example.data.MediaItem?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _markers = MutableStateFlow<List<com.example.data.TimelineMarker>>(emptyList())
    val markers: StateFlow<List<com.example.data.TimelineMarker>> = _markers.asStateFlow()

    // Enhanced Player State
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isMirrored = MutableStateFlow(false)
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _isControlsLocked = MutableStateFlow(false)
    val isControlsLocked: StateFlow<Boolean> = _isControlsLocked.asStateFlow()

    // Smart Safe-Watch & Family Guard State (Track 3)
    private val _isSafeModeEnabled = MutableStateFlow(true)
    val isSafeModeEnabled: StateFlow<Boolean> = _isSafeModeEnabled.asStateFlow()

    private val _isSafeBlurActive = MutableStateFlow(false)
    val isSafeBlurActive: StateFlow<Boolean> = _isSafeBlurActive.asStateFlow()

    private val _isSafeMuteActive = MutableStateFlow(false)
    val isSafeMuteActive: StateFlow<Boolean> = _isSafeMuteActive.asStateFlow()

    private val _safeWatchBannerMessage = MutableStateFlow<String?>(null)
    val safeWatchBannerMessage: StateFlow<String?> = _safeWatchBannerMessage.asStateFlow()

    // Live Video Filters & Color Adjustments State (Track 2)
    private val _videoFilterPreset = MutableStateFlow("Normal")
    val videoFilterPreset: StateFlow<String> = _videoFilterPreset.asStateFlow()

    private val _filterBrightness = MutableStateFlow(0f) // -0.5f to +0.5f
    val filterBrightness: StateFlow<Float> = _filterBrightness.asStateFlow()

    private val _filterContrast = MutableStateFlow(1f) // 0.5f to 2.0f
    val filterContrast: StateFlow<Float> = _filterContrast.asStateFlow()

    private val _filterSaturation = MutableStateFlow(1f) // 0f to 2.0f
    val filterSaturation: StateFlow<Float> = _filterSaturation.asStateFlow()

    // Video Background Play (Enabled by default for Mini-Player & Background Playback)
    private val _isVideoBgPlayEnabled = MutableStateFlow(true)
    val isVideoBgPlayEnabled: StateFlow<Boolean> = _isVideoBgPlayEnabled.asStateFlow()

    // Orientation Lock (Fixed screen angle lock)
    private val _isOrientationLocked = MutableStateFlow(false)
    val isOrientationLocked: StateFlow<Boolean> = _isOrientationLocked.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isAbRepeatActive = MutableStateFlow(false)
    val isAbRepeatActive: StateFlow<Boolean> = _isAbRepeatActive.asStateFlow()

    private val _abPointA = MutableStateFlow<Long?>(null)
    val abPointA: StateFlow<Long?> = _abPointA.asStateFlow()

    private val _abPointB = MutableStateFlow<Long?>(null)
    val abPointB: StateFlow<Long?> = _abPointB.asStateFlow()

    // Smart Sleep Timer State
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    private val _playlistItems = MutableStateFlow<List<com.example.data.MediaItem>>(emptyList())
    val playlistItems: StateFlow<List<com.example.data.MediaItem>> = _playlistItems.asStateFlow()

    // Equalizer & Audio Boost State (5 Bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
    private val _isEqualizerEnabled = MutableStateFlow(true)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _eqBandLevels = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f))
    val eqBandLevels: StateFlow<List<Float>> = _eqBandLevels.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0)
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _volumeBoostLevel = MutableStateFlow(0)
    val volumeBoostLevel: StateFlow<Int> = _volumeBoostLevel.asStateFlow()

    // External Subtitles (SRT / VTT) State
    private val _subtitlesEnabled = MutableStateFlow(false)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    private val _subtitleFileName = MutableStateFlow<String?>(null)
    val subtitleFileName: StateFlow<String?> = _subtitleFileName.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    private val _currentSubtitleText = MutableStateFlow<String?>(null)
    val currentSubtitleText: StateFlow<String?> = _currentSubtitleText.asStateFlow()

    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()

    private val _subtitleFontSizeSp = MutableStateFlow(18f)
    val subtitleFontSizeSp: StateFlow<Float> = _subtitleFontSizeSp.asStateFlow()

    private val _subtitleColorIndex = MutableStateFlow(0) // 0=White, 1=Yellow, 2=Cyan, 3=Green
    val subtitleColorIndex: StateFlow<Int> = _subtitleColorIndex.asStateFlow()

    private val _subtitleBgOpacity = MutableStateFlow(0.65f)
    val subtitleBgOpacity: StateFlow<Float> = _subtitleBgOpacity.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = player?.duration ?: 0L
                syncAudioEffects()
            } else if (playbackState == Player.STATE_ENDED) {
                savePosition(true)
                if (_sleepTimerMinutes.value == -1) {
                    player?.pause()
                    _sleepTimerMinutes.value = null
                    _sleepTimerRemainingSeconds.value = null
                }
            }
        }
    }

    init {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            player = controllerFuture?.get()
            player?.addListener(playerListener)
            _isPlayerReady.value = true
            syncAudioEffects()

            pendingMediaItem?.let {
                loadMediaItem(it, pendingStartMs)
                pendingMediaItem = null
                pendingStartMs = null
            }
        }, MoreExecutors.directExecutor())
    }

    fun loadMediaItem(dbItem: com.example.data.MediaItem, startMs: Long? = null) {
        val isSameMedia = currentMediaId == dbItem.id && player?.currentMediaItem != null
        _currentMedia.value = dbItem
        currentMediaId = dbItem.id

        viewModelScope.launch {
            try {
                repository.allMedia.collect { all ->
                    _playlistItems.value = all.filter { it.mediaType == dbItem.mediaType }
                }
            } catch (_: Exception) {}
        }

        if (player == null) {
            pendingMediaItem = dbItem
            pendingStartMs = startMs
            return
        }

        if (isSameMedia) {
            if (startMs != null) {
                player?.seekTo(startMs)
            }
            if (player?.isPlaying != true) {
                player?.play()
            }
            return
        }

        val uri = when {
            dbItem.path.startsWith("content://") || dbItem.path.startsWith("file://") -> Uri.parse(dbItem.path)
            else -> Uri.fromFile(java.io.File(dbItem.path))
        }
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        if (startMs != null) {
            player?.seekTo(startMs)
        } else if (dbItem.lastPositionMs > 0) {
            player?.seekTo(dbItem.lastPositionMs)
        }
        player?.playWhenReady = true

        viewModelScope.launch {
            repository.getMarkersForMedia(dbItem.id).collect {
                _markers.value = it
            }
        }
    }

    fun stopAndClearMedia() {
        savePosition(false)
        player?.pause()
        player?.clearMediaItems()
        _currentMedia.value = null
        currentMediaId = null
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
        updateSubtitleForPosition(positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        val cur = player?.currentPosition ?: 0L
        val maxDur = _duration.value.coerceAtLeast(1L)
        val target = (cur + offsetMs).coerceIn(0L, maxDur)
        seekTo(target)
    }

    fun savePosition(finished: Boolean = false) {
        currentMediaId?.let { id ->
            viewModelScope.launch {
                val position = if (finished) 0L else (player?.currentPosition ?: 0L)
                val state = if (finished) WatchedState.WATCHED else WatchedState.IN_PROGRESS
                repository.updatePlaybackState(id, state, position)
            }
        }
    }

    fun updatePosition() {
        var cur = player?.currentPosition ?: 0L
        _currentPosition.value = cur

        // Handle AB Repeat
        if (_isAbRepeatActive.value) {
            val a = _abPointA.value
            val b = _abPointB.value
            if (a != null && b != null && cur >= b) {
                player?.seekTo(a)
                cur = a
            }
        }

        // Handle Smart Safe-Watch Family Guard Rules (Track 3)
        evaluateSafeWatchRules(cur)

        // Update active subtitle cue
        updateSubtitleForPosition(cur)
    }

    private fun evaluateSafeWatchRules(positionMs: Long) {
        if (!_isSafeModeEnabled.value) {
            if (_isSafeBlurActive.value) _isSafeBlurActive.value = false
            if (_isSafeMuteActive.value) {
                _isSafeMuteActive.value = false
                player?.volume = if (_isMuted.value) 0f else 1f
            }
            _safeWatchBannerMessage.value = null
            return
        }

        val safeMarkers = _markers.value.filter { it.type.startsWith("SAFE_") }
        if (safeMarkers.isEmpty()) {
            if (_isSafeBlurActive.value) _isSafeBlurActive.value = false
            if (_isSafeMuteActive.value) {
                _isSafeMuteActive.value = false
                player?.volume = if (_isMuted.value) 0f else 1f
            }
            _safeWatchBannerMessage.value = null
            return
        }

        // 1. Check SAFE_SKIP
        val skipMarker = safeMarkers.firstOrNull { marker ->
            if (marker.type != "SAFE_SKIP") return@firstOrNull false
            val endMs = marker.description?.toLongOrNull() ?: (marker.timeMs + 5000L)
            positionMs in marker.timeMs until (endMs - 200L)
        }
        if (skipMarker != null) {
            val targetEndMs = skipMarker.description?.toLongOrNull() ?: (skipMarker.timeMs + 5000L)
            player?.seekTo(targetEndMs)
            _currentPosition.value = targetEndMs
            _safeWatchBannerMessage.value = "🛡️ الحارس العائلي: تم تخطي مشهد (${skipMarker.name ?: "محجوب"}) تلقائياً"
            return
        }

        // 2. Check SAFE_MUTE
        val muteMarker = safeMarkers.firstOrNull { marker ->
            if (marker.type != "SAFE_MUTE") return@firstOrNull false
            val endMs = marker.description?.toLongOrNull() ?: (marker.timeMs + 5000L)
            positionMs in marker.timeMs..endMs
        }
        if (muteMarker != null) {
            if (!_isSafeMuteActive.value) {
                _isSafeMuteActive.value = true
                player?.volume = 0f
            }
            _safeWatchBannerMessage.value = "🔇 الحارس العائلي: كتم الصوت مؤقتاً (${muteMarker.name ?: "حماية سمعية"})"
        } else if (_isSafeMuteActive.value) {
            _isSafeMuteActive.value = false
            player?.volume = if (_isMuted.value) 0f else 1f
            _safeWatchBannerMessage.value = null
        }

        // 3. Check SAFE_BLUR
        val blurMarker = safeMarkers.firstOrNull { marker ->
            if (marker.type != "SAFE_BLUR") return@firstOrNull false
            val endMs = marker.description?.toLongOrNull() ?: (marker.timeMs + 5000L)
            positionMs in marker.timeMs..endMs
        }
        if (blurMarker != null) {
            _isSafeBlurActive.value = true
            _safeWatchBannerMessage.value = "🛡️ الحارس العائلي: تم تعتيم المشهد لحماية المشاهدة (${blurMarker.name ?: "مشهد محجوب"})"
        } else {
            if (_isSafeBlurActive.value) {
                _isSafeBlurActive.value = false
                if (muteMarker == null) _safeWatchBannerMessage.value = null
            }
        }
    }

    private fun updateSubtitleForPosition(positionMs: Long) {
        if (!_subtitlesEnabled.value || _subtitleCues.value.isEmpty()) {
            if (_currentSubtitleText.value != null) {
                _currentSubtitleText.value = null
            }
            return
        }
        val adjustedTime = positionMs - _subtitleDelayMs.value
        val activeCue = _subtitleCues.value.firstOrNull { cue ->
            adjustedTime in cue.startMs..cue.endMs
        }
        _currentSubtitleText.value = activeCue?.text
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player?.setPlaybackSpeed(speed)
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOfFirst { kotlin.math.abs(it - _playbackSpeed.value) < 0.05f }
        val nextIndex = if (currentIndex >= 0 && currentIndex < speeds.size - 1) currentIndex + 1 else 0
        setPlaybackSpeed(speeds[nextIndex])
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        player?.volume = if (newMuted || _isSafeMuteActive.value) 0f else 1f
    }

    fun toggleMirror() {
        _isMirrored.value = !_isMirrored.value
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun toggleControlsLock() {
        _isControlsLocked.value = !_isControlsLocked.value
    }

    fun toggleSafeMode() {
        val newVal = !_isSafeModeEnabled.value
        _isSafeModeEnabled.value = newVal
        evaluateSafeWatchRules(_currentPosition.value)
    }

    // --- Track 2: Live Video Filters & Instant Snapshot ---
    fun setVideoFilterPreset(preset: String) {
        _videoFilterPreset.value = preset
        when (preset) {
            "Normal" -> {
                _filterBrightness.value = 0f
                _filterContrast.value = 1f
                _filterSaturation.value = 1f
            }
            "Cinema HDR" -> {
                _filterBrightness.value = 0.04f
                _filterContrast.value = 1.28f
                _filterSaturation.value = 1.25f
            }
            "Vivid" -> {
                _filterBrightness.value = 0.06f
                _filterContrast.value = 1.15f
                _filterSaturation.value = 1.55f
            }
            "B&W" -> {
                _filterBrightness.value = 0.02f
                _filterContrast.value = 1.25f
                _filterSaturation.value = 0f
            }
            "Warm EyeCare" -> {
                _filterBrightness.value = -0.05f
                _filterContrast.value = 0.95f
                _filterSaturation.value = 0.85f
            }
            "Sharp" -> {
                _filterBrightness.value = 0.05f
                _filterContrast.value = 1.38f
                _filterSaturation.value = 1.1f
            }
        }
    }

    fun setFilterBrightness(value: Float) {
        _filterBrightness.value = value.coerceIn(-0.5f, 0.5f)
        _videoFilterPreset.value = "Custom"
    }

    fun setFilterContrast(value: Float) {
        _filterContrast.value = value.coerceIn(0.5f, 2.0f)
        _videoFilterPreset.value = "Custom"
    }

    fun setFilterSaturation(value: Float) {
        _filterSaturation.value = value.coerceIn(0f, 2.0f)
        _videoFilterPreset.value = "Custom"
    }

    fun resetVideoFilters() {
        setVideoFilterPreset("Normal")
    }

    fun captureVideoSnapshot(context: Context, onResult: (Boolean, String?, Uri?) -> Unit) {
        val media = _currentMedia.value
        if (media == null) {
            onResult(false, null, null)
            return
        }
        val posMs = _currentPosition.value
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = when {
                        media.path.startsWith("content://") || media.path.startsWith("file://") -> Uri.parse(media.path)
                        else -> Uri.fromFile(File(media.path))
                    }
                    retriever.setDataSource(context, uri)
                    val frameBitmap = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime()
                        ?: return@withContext Pair<String?, Uri?>(null, null)

                    val fileName = "SafePlayer_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SafePlayer")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val imgUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (imgUri != null) {
                            resolver.openOutputStream(imgUri)?.use { out ->
                                frameBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(imgUri, values, null, null)
                            return@withContext Pair(fileName, imgUri)
                        }
                    }
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SafePlayer")
                    if (!dir.exists()) dir.mkdirs()
                    val outFile = File(dir, fileName)
                    FileOutputStream(outFile).use { out ->
                        frameBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    Pair(fileName, Uri.fromFile(outFile))
                } catch (_: Exception) {
                    Pair(null, null)
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
            if (saved.first != null && saved.second != null) {
                onResult(true, saved.first, saved.second)
            } else {
                onResult(false, null, null)
            }
        }
    }

    // --- Track 3: Smart Safe-Watch Family Guard Rules ---
    fun addSafeWatchRule(startMs: Long, endMs: Long, ruleType: String, title: String) {
        val mediaId = currentMediaId ?: return
        val validEnd = endMs.coerceAtLeast(startMs + 1000L)
        viewModelScope.launch {
            val marker = com.example.data.TimelineMarker(
                mediaId = mediaId,
                timeMs = startMs,
                name = title,
                description = validEnd.toString(),
                type = ruleType // SAFE_SKIP, SAFE_MUTE, SAFE_BLUR
            )
            repository.insertMarker(marker)
            _isSafeModeEnabled.value = true
        }
    }

    fun generateDemoAiSafeWatchRules() {
        val mediaId = currentMediaId ?: return
        val cur = _currentPosition.value
        viewModelScope.launch {
            val rules = listOf(
                com.example.data.TimelineMarker(
                    mediaId = mediaId,
                    timeMs = cur + 2000L,
                    name = "تخطي تلقائي لمشهد غير ملائم",
                    description = (cur + 7000L).toString(),
                    type = "SAFE_SKIP"
                ),
                com.example.data.TimelineMarker(
                    mediaId = mediaId,
                    timeMs = cur + 10000L,
                    name = "كتم صوت تلقائي لكلمات غير لائقة",
                    description = (cur + 15000L).toString(),
                    type = "SAFE_MUTE"
                ),
                com.example.data.TimelineMarker(
                    mediaId = mediaId,
                    timeMs = cur + 18000L,
                    name = "تعتيم بصري لمشهد حساس",
                    description = (cur + 23000L).toString(),
                    type = "SAFE_BLUR"
                )
            )
            rules.forEach { repository.insertMarker(it) }
            _isSafeModeEnabled.value = true
        }
    }

    fun exportSafeWatchRulesJson(): String {
        val safeMarkers = _markers.value.filter { it.type.startsWith("SAFE_") }
        val root = JSONObject()
        root.put("app", "SafePlayer")
        root.put("version", 1)
        root.put("mediaName", _currentMedia.value?.name ?: "video")
        val arr = JSONArray()
        safeMarkers.forEach { m ->
            val obj = JSONObject()
            obj.put("startMs", m.timeMs)
            obj.put("endMs", m.description?.toLongOrNull() ?: (m.timeMs + 5000L))
            obj.put("type", m.type)
            obj.put("name", m.name ?: "قاعدة حماية")
            arr.put(obj)
        }
        root.put("rules", arr)
        return root.toString(2)
    }

    fun importSafeWatchRulesJson(rawJson: String, onResult: (Boolean, Int) -> Unit) {
        val mediaId = currentMediaId
        if (mediaId == null) {
            onResult(false, 0)
            return
        }
        viewModelScope.launch {
            try {
                val root = JSONObject(rawJson.trim())
                val arr = root.getJSONArray("rules")
                var count = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val startMs = obj.optLong("startMs", -1L)
                    val endMs = obj.optLong("endMs", startMs + 5000L)
                    val type = obj.optString("type", "SAFE_SKIP")
                    val name = obj.optString("name", "قاعدة مستوردة")
                    if (startMs >= 0L) {
                        repository.insertMarker(
                            com.example.data.TimelineMarker(
                                mediaId = mediaId,
                                timeMs = startMs,
                                name = name,
                                description = endMs.toString(),
                                type = type
                            )
                        )
                        count++
                    }
                }
                _isSafeModeEnabled.value = true
                onResult(count > 0, count)
            } catch (_: Exception) {
                onResult(false, 0)
            }
        }
    }

    fun toggleVideoBgPlay(): Boolean {
        val newState = !_isVideoBgPlayEnabled.value
        _isVideoBgPlayEnabled.value = newState
        return newState
    }

    fun setVideoBgPlay(enabled: Boolean) {
        _isVideoBgPlayEnabled.value = enabled
    }

    fun toggleOrientationLock(): Boolean {
        val newState = !_isOrientationLocked.value
        _isOrientationLocked.value = newState
        return newState
    }

    fun setOrientationLock(locked: Boolean) {
        _isOrientationLocked.value = locked
    }

    fun cycleAspectRatio() {
        val values = AspectRatioMode.values()
        val nextOrdinal = (_aspectRatioMode.value.ordinal + 1) % values.size
        _aspectRatioMode.value = values[nextOrdinal]
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        player?.repeatMode = next
    }

    fun setAbRepeatPoint() {
        val cur = player?.currentPosition ?: 0L
        if (_abPointA.value == null) {
            _abPointA.value = cur
            _isAbRepeatActive.value = false
        } else if (_abPointB.value == null) {
            if (cur > (_abPointA.value ?: 0L)) {
                _abPointB.value = cur
                _isAbRepeatActive.value = true
                player?.seekTo(_abPointA.value!!)
            } else {
                _abPointA.value = cur
            }
        } else {
            clearAbRepeat()
        }
    }

    fun clearAbRepeat() {
        _abPointA.value = null
        _abPointB.value = null
        _isAbRepeatActive.value = false
    }

    // --- Smart Sleep Timer ---
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes == null) {
            _sleepTimerRemainingSeconds.value = null
            return
        }
        if (minutes == -1) {
            _sleepTimerRemainingSeconds.value = null
            return
        }
        if (minutes > 0) {
            var remainingSec = minutes * 60L
            _sleepTimerRemainingSeconds.value = remainingSec
            sleepTimerJob = viewModelScope.launch {
                while (remainingSec > 0) {
                    delay(1000L)
                    remainingSec -= 1L
                    _sleepTimerRemainingSeconds.value = remainingSec
                }
                player?.pause()
                _sleepTimerMinutes.value = null
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    fun extendSleepTimer(extraMinutes: Int = 5) {
        val currentRemSec = _sleepTimerRemainingSeconds.value ?: 0L
        val newTotalSeconds = currentRemSec + (extraMinutes * 60L)
        val approxMinutes = ((newTotalSeconds + 59) / 60).toInt()
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = approxMinutes
        var remainingSec = newTotalSeconds
        _sleepTimerRemainingSeconds.value = remainingSec
        sleepTimerJob = viewModelScope.launch {
            while (remainingSec > 0) {
                delay(1000L)
                remainingSec -= 1L
                _sleepTimerRemainingSeconds.value = remainingSec
            }
            player?.pause()
            _sleepTimerMinutes.value = null
            _sleepTimerRemainingSeconds.value = null
        }
    }

    // --- Equalizer & Audio Boost ---
    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        syncAudioEffects()
    }

    fun setEqualizerPreset(preset: String) {
        _equalizerPreset.value = preset
        val bands = when (preset) {
            "Flat" -> listOf(0f, 0f, 0f, 0f, 0f)
            "Cinema" -> listOf(6f, 4f, 0f, 3f, 6f)
            "Podcast" -> listOf(-3f, 2f, 7f, 5f, 1f)
            "Music" -> listOf(5f, 3f, -1f, 3f, 5f)
            "Bass Boost" -> listOf(9f, 6f, 1f, 0f, 1f)
            "Vocal" -> listOf(-4f, 1f, 6f, 5f, 2f)
            "Rock" -> listOf(6f, 3f, -2f, 4f, 7f)
            "Classical" -> listOf(4f, 2f, -1f, 3f, 5f)
            else -> _eqBandLevels.value
        }
        _eqBandLevels.value = bands
        if (preset == "Bass Boost" && _bassBoostLevel.value < 60) {
            _bassBoostLevel.value = 70
        }
        syncAudioEffects()
    }

    fun setEqBandLevel(bandIndex: Int, levelDb: Float) {
        if (bandIndex !in 0..4) return
        val mutable = _eqBandLevels.value.toMutableList()
        mutable[bandIndex] = levelDb.coerceIn(-15f, 15f)
        _eqBandLevels.value = mutable
        _equalizerPreset.value = "Custom"
        syncAudioEffects()
    }

    fun setBassBoost(level: Int) {
        _bassBoostLevel.value = level.coerceIn(0, 100)
        syncAudioEffects()
    }

    fun setVolumeBoost(level: Int) {
        _volumeBoostLevel.value = level.coerceIn(0, 100)
        syncAudioEffects()
    }

    fun resetEqualizer() {
        _isEqualizerEnabled.value = true
        _equalizerPreset.value = "Flat"
        _eqBandLevels.value = listOf(0f, 0f, 0f, 0f, 0f)
        _bassBoostLevel.value = 0
        _volumeBoostLevel.value = 0
        syncAudioEffects()
    }

    private fun syncAudioEffects() {
        PlaybackService.AudioEffectsController.updateSettings(
            enabled = _isEqualizerEnabled.value,
            bandsDb = _eqBandLevels.value,
            bassPercent = _bassBoostLevel.value,
            volumeBoostPercent = _volumeBoostLevel.value
        )
    }

    // --- External Subtitles (SRT / VTT) ---
    fun toggleSubtitles(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        updateSubtitleForPosition(_currentPosition.value)
    }

    fun clearSubtitles() {
        _subtitlesEnabled.value = false
        _subtitleCues.value = emptyList()
        _subtitleFileName.value = null
        _currentSubtitleText.value = null
        _subtitleDelayMs.value = 0L
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        _subtitleDelayMs.value = (_subtitleDelayMs.value + deltaMs).coerceIn(-15000L, 15000L)
        updateSubtitleForPosition(_currentPosition.value)
    }

    fun resetSubtitleDelay() {
        _subtitleDelayMs.value = 0L
        updateSubtitleForPosition(_currentPosition.value)
    }

    fun setSubtitleFontSize(sizeSp: Float) {
        _subtitleFontSizeSp.value = sizeSp.coerceIn(12f, 32f)
    }

    fun setSubtitleColorIndex(index: Int) {
        _subtitleColorIndex.value = index.coerceIn(0, 3)
    }

    fun setSubtitleBgOpacity(opacity: Float) {
        _subtitleBgOpacity.value = opacity.coerceIn(0f, 1f)
    }

    fun loadExternalSubtitleFromUri(context: Context, uri: Uri, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    var fileName = "subtitle.srt"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIdx) ?: fileName
                        }
                    }
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@withContext Pair<String?, List<SubtitleCue>>(null, emptyList())
                    val cues = parseSubtitles(content)
                    Pair(fileName, cues)
                } catch (_: Exception) {
                    Pair(null, emptyList())
                }
            }
            val fileName = result.first
            val cues = result.second
            if (cues.isNotEmpty()) {
                _subtitleFileName.value = fileName
                _subtitleCues.value = cues
                _subtitlesEnabled.value = true
                updateSubtitleForPosition(_currentPosition.value)
                onResult(true, cues.size)
            } else {
                onResult(false, 0)
            }
        }
    }

    fun loadSampleArabicSubtitles() {
        val totalDur = (_duration.value).coerceAtLeast(180_000L)
        val demoCues = mutableListOf<SubtitleCue>()
        var t = 0L
        val messages = listOf(
            "مرحباً بك في مشغل SafePlayer الاحترافي 🎬",
            "يمكنك التحكم في حجم ولون ومزامنة الترجمة بسهولة",
            "جرب تعديل توقيت الترجمة (+0.1s / -0.1s) لمطابقة الصوت",
            "كما يمكنك تحميل أي ملف ترجمة خارجي بصيغة SRT أو VTT",
            "استمتع بمشاهدة آمنة وصوت نقي مع موازن الصوت المدمج 🎧"
        )
        var idx = 0
        while (t < totalDur) {
            val msg = messages[idx % messages.size]
            demoCues.add(SubtitleCue(startMs = t, endMs = t + 4500L, text = msg))
            t += 5000L
            idx++
        }
        _subtitleFileName.value = "ترجمة تجريبية بالعربية (Arabic Demo.srt)"
        _subtitleCues.value = demoCues
        _subtitlesEnabled.value = true
        updateSubtitleForPosition(_currentPosition.value)
    }

    private fun parseSubtitles(rawContent: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val blocks = normalized.split(Regex("\n\\s*\n"))
        val timeArrowRegex = Regex("""(\d{1,2}:)?\d{2}:\d{2}[,\.]\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[,\.]\d{3}""")

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timeLineIndex = lines.indexOfFirst { timeArrowRegex.containsMatchIn(it) }
            if (timeLineIndex == -1) continue

            val timeLine = lines[timeLineIndex]
            val parts = timeLine.split("-->")
            if (parts.size < 2) continue

            val startMs = parseTimestampToMs(parts[0].trim())
            val endMs = parseTimestampToMs(parts[1].trim().split(" ").first())
            if (startMs < 0L || endMs <= startMs) continue

            val textLines = lines.subList(timeLineIndex + 1, lines.size)
                .map { it.replace(Regex("<[^>]*>"), "") }
                .filter { it.isNotBlank() }

            if (textLines.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        startMs = startMs,
                        endMs = endMs,
                        text = textLines.joinToString("\n")
                    )
                )
            }
        }
        return cues.sortedBy { it.startMs }
    }

    private fun parseTimestampToMs(timestamp: String): Long {
        return try {
            val clean = timestamp.replace(',', '.').trim()
            val segments = clean.split(":")
            when (segments.size) {
                3 -> {
                    val hours = segments[0].toLong()
                    val minutes = segments[1].toLong()
                    val secParts = segments[2].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((hours * 3600) + (minutes * 60) + seconds) * 1000L + millis
                }
                2 -> {
                    val minutes = segments[0].toLong()
                    val secParts = segments[1].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((minutes * 60) + seconds) * 1000L + millis
                }
                else -> -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    fun playNext() {
        val items = _playlistItems.value
        val curId = currentMediaId ?: return
        val idx = items.indexOfFirst { it.id == curId }
        if (idx in 0 until items.size - 1) {
            savePosition(false)
            loadMediaItem(items[idx + 1])
        }
    }

    fun playPrevious() {
        val items = _playlistItems.value
        val curId = currentMediaId ?: return
        val idx = items.indexOfFirst { it.id == curId }
        if (idx > 0) {
            savePosition(false)
            loadMediaItem(items[idx - 1])
        } else {
            seekTo(0L)
        }
    }

    fun addMarker(timeMs: Long, name: String? = null, type: String = "STANDARD") {
        currentMediaId?.let { mediaId ->
            viewModelScope.launch {
                val marker = com.example.data.TimelineMarker(
                    mediaId = mediaId,
                    timeMs = timeMs,
                    name = name ?: "Marker at ${timeMs / 1000}s",
                    type = type
                )
                repository.insertMarker(marker)
            }
        }
    }

    fun removeMarker(markerId: Int) {
        viewModelScope.launch {
            repository.deleteMarker(markerId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePosition()
        sleepTimerJob?.cancel()
        player?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
