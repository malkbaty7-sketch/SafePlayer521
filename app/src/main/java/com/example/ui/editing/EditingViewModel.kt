package com.example.ui.editing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.EditingOperation
import com.example.data.EditingProject
import com.example.data.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditingViewModel(
    application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    var player: ExoPlayer? = null
        private set

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = _isPlayerReady.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _project = MutableStateFlow<EditingProject?>(null)
    val project: StateFlow<EditingProject?> = _project.asStateFlow()

    private val _operations = MutableStateFlow<List<EditingOperation>>(emptyList())
    val operations: StateFlow<List<EditingOperation>> = _operations.asStateFlow()
    
    val conflicts: StateFlow<Map<Int, List<String>>> = _operations.map { ops ->
        val result = mutableMapOf<Int, MutableList<String>>()
        val deletes = ops.filter { it.type == "DELETE" && it.isEnabled }
        
        for (op in ops) {
            if (!op.isEnabled) continue
            val opConflicts = mutableListOf<String>()
            
            if (op.type != "DELETE") {
                val overlappingDelete = deletes.find { del -> 
                    op.startTimeMs < del.endTimeMs && op.endTimeMs > del.startTimeMs 
                }
                if (overlappingDelete != null) {
                    opConflicts.add("Overlaps with deleted segment")
                }
            } else {
                val overlappingDelete = deletes.find { del -> 
                    del.id != op.id && op.startTimeMs < del.endTimeMs && op.endTimeMs > del.startTimeMs 
                }
                if (overlappingDelete != null) {
                    opConflicts.add("Redundant delete")
                }
            }
            
            if (op.type.startsWith("CENSOR")) {
                val props = OperationProperties.fromJson(op.propertiesJson)
                val outOfBounds = props.keyframes.any { it.timeMs < op.startTimeMs || it.timeMs > op.endTimeMs }
                if (outOfBounds) {
                    opConflicts.add("Keyframes outside duration")
                }
            }
            
            if (opConflicts.isNotEmpty()) {
                result[op.id] = opConflicts
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    
    private val undoStack = mutableListOf<List<EditingOperation>>()
    private val redoStack = mutableListOf<List<EditingOperation>>()
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var operationsJob: Job? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = duration.coerceAtLeast(0L)
                    }
                }
            })
        }
    }

    fun loadMediaAndProject(mediaId: Int) {
        viewModelScope.launch {
            // 1. Fetch or create project without blocking flow collection
            val existingProjects = repository.getProjectsForMedia(mediaId).first()
            val currentProject = if (existingProjects.isNotEmpty()) {
                existingProjects.first()
            } else {
                val newProject = EditingProject(mediaId = mediaId, name = "Project $mediaId")
                val projectId = repository.insertProject(newProject)
                newProject.copy(id = projectId.toInt())
            }
            _project.value = currentProject

            // 2. Load media into ExoPlayer
            val mediaItem = repository.getMediaById(mediaId).first()
            if (mediaItem != null) {
                val uri = when {
                    mediaItem.path.startsWith("content://") || mediaItem.path.startsWith("file://") -> android.net.Uri.parse(mediaItem.path)
                    else -> android.net.Uri.fromFile(java.io.File(mediaItem.path))
                }
                val exoMediaItem = MediaItem.fromUri(uri)
                player?.setMediaItem(exoMediaItem)
                player?.prepare()
                _isPlayerReady.value = true
            }

            // 3. Continuously observe operations in a dedicated job
            operationsJob?.cancel()
            operationsJob = viewModelScope.launch {
                repository.getOperationsForProject(currentProject.id).collect { ops ->
                    _operations.value = ops
                }
            }
        }
    }

    private fun saveSnapshot() {
        undoStack.add(_operations.value.map { it.copy() })
        if (undoStack.size > 25) undoStack.removeAt(0)
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val proj = _project.value ?: return
            redoStack.add(_operations.value.map { it.copy() })
            val previousState = undoStack.removeLast()
            updateUndoRedoState()
            
            viewModelScope.launch {
                val currentOps = _operations.value
                currentOps.forEach { repository.deleteOperation(it.id) }
                previousState.forEach { repository.insertOperation(it.copy(id = 0, projectId = proj.id)) }
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val proj = _project.value ?: return
            undoStack.add(_operations.value.map { it.copy() })
            val nextState = redoStack.removeLast()
            updateUndoRedoState()
            
            viewModelScope.launch {
                val currentOps = _operations.value
                currentOps.forEach { repository.deleteOperation(it.id) }
                nextState.forEach { repository.insertOperation(it.copy(id = 0, projectId = proj.id)) }
            }
        }
    }

    fun updatePosition() {
        val p = player ?: return
        val pos = p.currentPosition
        _currentPosition.value = pos

        // Preview: Delete segments skip
        val activeDelete = _operations.value.firstOrNull { op ->
            op.isEnabled && op.type == "DELETE" && pos in op.startTimeMs until op.endTimeMs
        }
        if (activeDelete != null && p.isPlaying) {
            p.seekTo(activeDelete.endTimeMs)
            return
        }

        // Preview: Audio Mute ranges
        val shouldMute = _operations.value.any { op ->
            op.isEnabled && op.type == "MUTE" && pos in op.startTimeMs..op.endTimeMs
        }
        val targetVolume = if (shouldMute) 0f else 1f
        if (p.volume != targetVolume) {
            p.volume = targetVolume
        }
    }

    fun playPause() {
        player?.let {
            it.playWhenReady = !it.isPlaying
        }
    }

    fun seekTo(position: Long) {
        val clamped = position.coerceIn(0L, _duration.value.coerceAtLeast(0L))
        player?.seekTo(clamped)
        updatePosition()
    }

    fun seekBy(offsetMs: Long) {
        val newPos = (_currentPosition.value + offsetMs).coerceIn(0L, _duration.value.coerceAtLeast(0L))
        seekTo(newPos)
    }

    fun addOperation(type: String, start: Long, end: Long, properties: String = "{}") {
        saveSnapshot()
        _project.value?.let { proj ->
            viewModelScope.launch {
                val validStart = start.coerceAtLeast(0L)
                val validEnd = if (_duration.value > 0) end.coerceIn(validStart + 100, _duration.value) else end.coerceAtLeast(validStart + 100)
                val op = EditingOperation(
                    projectId = proj.id,
                    type = type,
                    startTimeMs = validStart,
                    endTimeMs = validEnd,
                    isEnabled = true,
                    propertiesJson = properties
                )
                repository.insertOperation(op)
            }
        }
    }

    fun duplicateOperation(op: EditingOperation) {
        saveSnapshot()
        _project.value?.let { proj ->
            viewModelScope.launch {
                val offset = 2000L
                val maxDur = _duration.value
                val newStart = (op.startTimeMs + offset).let { if (maxDur > 0) it.coerceAtMost(maxDur - 500) else it }
                val newEnd = (op.endTimeMs + offset).let { if (maxDur > 0) it.coerceAtMost(maxDur) else it }
                val clone = op.copy(
                    id = 0,
                    projectId = proj.id,
                    startTimeMs = newStart,
                    endTimeMs = newEnd.coerceAtLeast(newStart + 100)
                )
                repository.insertOperation(clone)
            }
        }
    }

    fun removeOperation(id: Int) {
        saveSnapshot()
        viewModelScope.launch {
            repository.deleteOperation(id)
        }
    }

    fun updateOperation(op: EditingOperation) {
        saveSnapshot()
        viewModelScope.launch {
            repository.updateOperation(op)
        }
    }

    fun toggleOperation(op: EditingOperation) {
        saveSnapshot()
        viewModelScope.launch {
            repository.updateOperation(op.copy(isEnabled = !op.isEnabled))
        }
    }

    override fun onCleared() {
        super.onCleared()
        operationsJob?.cancel()
        player?.stop()
        player?.release()
        player = null
    }
}
