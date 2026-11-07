@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }
        player = ExoPlayer.Builder(this, renderersFactory)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    AudioEffectsController.attachToSession(audioSessionId)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
                        AudioEffectsController.attachToSession(sessionId)
                    }
                }
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        AudioEffectsController.releaseAll()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    object AudioEffectsController {
        private var currentSessionId: Int = 0
        private var equalizer: Equalizer? = null
        private var bassBoost: BassBoost? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null

        private var isEnabled: Boolean = true
        private var bandLevelsDb: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        private var bassStrengthPercent: Int = 0
        private var loudnessPercent: Int = 0

        @Synchronized
        fun attachToSession(sessionId: Int) {
            if (sessionId <= 0) return
            if (currentSessionId == sessionId && equalizer != null) {
                applySavedSettings()
                return
            }
            releaseAll()
            currentSessionId = sessionId
            try {
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = isEnabled
                }
            } catch (_: Exception) {
                equalizer = null
            }
            try {
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = isEnabled && bassStrengthPercent > 0
                }
            } catch (_: Exception) {
                bassBoost = null
            }
            try {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    enabled = isEnabled && loudnessPercent > 0
                }
            } catch (_: Exception) {
                loudnessEnhancer = null
            }
            applySavedSettings()
        }

        @Synchronized
        fun updateSettings(
            enabled: Boolean,
            bandsDb: List<Float>,
            bassPercent: Int,
            volumeBoostPercent: Int
        ) {
            isEnabled = enabled
            for (i in 0 until minOf(5, bandsDb.size)) {
                bandLevelsDb[i] = bandsDb[i].coerceIn(-15f, 15f)
            }
            bassStrengthPercent = bassPercent.coerceIn(0, 100)
            loudnessPercent = volumeBoostPercent.coerceIn(0, 100)
            applySavedSettings()
        }

        @Synchronized
        private fun applySavedSettings() {
            try {
                equalizer?.let { eq ->
                    eq.enabled = isEnabled
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range.getOrNull(0) ?: -1500
                    val maxMb = range.getOrNull(1) ?: 1500
                    for (bandIdx in 0 until numBands) {
                        val sourceIdx = bandIdx.coerceAtMost(bandLevelsDb.lastIndex)
                        val db = if (isEnabled) bandLevelsDb[sourceIdx] else 0f
                        val targetMb = (db * 100f).toInt().coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                        eq.setBandLevel(bandIdx.toShort(), targetMb)
                    }
                }
            } catch (_: Exception) {}

            try {
                bassBoost?.let { bb ->
                    bb.enabled = isEnabled && bassStrengthPercent > 0
                    if (bb.strengthSupported) {
                        val strength = if (isEnabled) (bassStrengthPercent * 10).coerceIn(0, 1000).toShort() else 0
                        bb.setStrength(strength)
                    }
                }
            } catch (_: Exception) {}

            try {
                loudnessEnhancer?.let { le ->
                    le.enabled = isEnabled && loudnessPercent > 0
                    val gainMb = if (isEnabled) (loudnessPercent * 25).coerceIn(0, 2500) else 0
                    le.setTargetGain(gainMb)
                }
            } catch (_: Exception) {}
        }

        @Synchronized
        fun releaseAll() {
            try { equalizer?.release() } catch (_: Exception) {}
            try { bassBoost?.release() } catch (_: Exception) {}
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            equalizer = null
            bassBoost = null
            loudnessEnhancer = null
            currentSessionId = 0
        }
    }
}
