package com.example.tasks

import android.content.Context
import com.example.util.SafeLogger
import com.example.tasks.data.TaskRecord
import com.example.tasks.data.TaskRecordDao
import com.example.tasks.resource.PerformancePreset
import com.example.tasks.resource.ResourceMonitor
import com.example.tasks.resource.ResourceSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SafeTaskManager(
    private val context: Context,
    private val taskDao: TaskRecordDao,
    val resourceMonitor: ResourceMonitor
) {
    private val tag = "SafeTaskManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<Long, Job>()

    private val _activeHeavyCount = MutableStateFlow(0)
    private val _activeLightCount = MutableStateFlow(0)
    private val queueMutex = kotlinx.coroutines.sync.Mutex()

    val allTasks: Flow<List<TaskRecord>> = taskDao.getAllTasksFlow()
    val activeAndQueuedTasks: Flow<List<TaskRecord>> = taskDao.getActiveAndQueuedTasksFlow()

    init {
        scope.launch {
            recoverInterruptedTasks()
            startSchedulerLoop()
        }
    }

    /**
     * Crash recovery: identify tasks that were left RUNNING/PREPARING on unexpected app termination.
     */
    private suspend fun recoverInterruptedTasks() {
        try {
            val runningTasks = taskDao.getRunningTasks()
            for (task in runningTasks) {
                SafeLogger.w(tag, "Recovering task ${task.id} (${task.taskType}) from interrupted state")
                // Cleanup temp files if defined
                task.payloadJson?.let { payload ->
                    cleanupTempFilesFromPayload(payload)
                }

                if (task.retryCount < task.maxRetries && (task.taskType == "THUMBNAIL_GEN" || task.taskType == "SCAN")) {
                    taskDao.update(
                        task.copy(
                            status = "QUEUED",
                            retryCount = task.retryCount + 1,
                            pipelineStage = "Recovered after app interruption"
                        )
                    )
                } else {
                    taskDao.update(
                        task.copy(
                            status = "INTERRUPTED",
                            errorMessage = "Task interrupted by application shutdown",
                            completedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            SafeLogger.e(tag, "Error during task recovery", e)
        }
    }

    private fun cleanupTempFilesFromPayload(payload: String) {
        try {
            if (payload.contains("\"tempPath\":\"")) {
                val start = payload.indexOf("\"tempPath\":\"") + 12
                val end = payload.indexOf("\"", start)
                if (start in 0 until end) {
                    val path = payload.substring(start, end)
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Enqueue a new task with deduplication check
     */
    suspend fun enqueueTask(
        taskType: String,
        title: String,
        priority: String = "NORMAL",
        projectId: Int? = null,
        mediaId: Int? = null,
        resourceRequirements: String = "NORMAL",
        payloadJson: String? = null
    ): Long {
        // Deduplication check
        if (mediaId != null) {
            val existing = taskDao.findExistingPendingOrRunning(taskType, mediaId)
            if (existing != null) {
                SafeLogger.d(tag, "Task of type $taskType for media $mediaId already active/queued with id ${existing.id}")
                return existing.id
            }
        }

        val newTask = TaskRecord(
            taskType = taskType,
            status = "QUEUED",
            priority = priority,
            title = title,
            progress = 0,
            createdAt = System.currentTimeMillis(),
            projectId = projectId,
            mediaId = mediaId,
            resourceRequirements = resourceRequirements,
            payloadJson = payloadJson,
            pipelineStage = "Queued in task manager"
        )
        val id = taskDao.insert(newTask)
        triggerScheduler()
        return id
    }

    fun triggerScheduler() {
        scope.launch {
            processQueue()
        }
    }

    private fun startSchedulerLoop() {
        scope.launch {
            while (isActive) {
                delay(2000)
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        queueMutex.withLock {
            val snapshot = resourceMonitor.refresh()
            val queued = taskDao.getQueuedTasks()
            if (queued.isEmpty()) return@withLock

            for (task in queued) {
                val isHeavy = task.resourceRequirements == "HEAVY_VIDEO" || task.taskType == "EXPORT"
                val currentHeavy = _activeHeavyCount.value
                val currentLight = _activeLightCount.value

                if (isHeavy) {
                    if (currentHeavy >= snapshot.maxConcurrentHeavyTasks) {
                        continue // Heavy queue full
                    }
                } else {
                    if (currentLight >= snapshot.maxConcurrentLightTasks) {
                        continue // Light queue full
                    }
                }

                // Launch task
                startTaskExecution(task, isHeavy)
            }
        }
    }

    private fun startTaskExecution(task: TaskRecord, isHeavy: Boolean) {
        val job = scope.launch {
            try {
                if (isHeavy) _activeHeavyCount.value += 1 else _activeLightCount.value += 1

                taskDao.update(
                    task.copy(
                        status = "RUNNING",
                        startedAt = System.currentTimeMillis(),
                        pipelineStage = "Processing started"
                    )
                )

                // Execute the work based on taskType
                runTaskWorker(task)

                taskDao.update(
                    task.copy(
                        status = "COMPLETED",
                        progress = 100,
                        completedAt = System.currentTimeMillis(),
                        pipelineStage = "Successfully finished"
                    )
                )
            } catch (ce: CancellationException) {
                SafeLogger.i(tag, "Task ${task.id} cancelled")
                taskDao.update(
                    task.copy(
                        status = "CANCELLED",
                        completedAt = System.currentTimeMillis(),
                        pipelineStage = "Cancelled by user"
                    )
                )
            } catch (e: Exception) {
                SafeLogger.e(tag, "Task ${task.id} failed: ${e.message}", e)
                val newRetry = task.retryCount + 1
                if (newRetry <= task.maxRetries && isRetryableError(e)) {
                    taskDao.update(
                        task.copy(
                            status = "QUEUED",
                            retryCount = newRetry,
                            pipelineStage = "Retrying (attempt $newRetry of ${task.maxRetries})"
                        )
                    )
                } else {
                    taskDao.update(
                        task.copy(
                            status = "FAILED",
                            errorMessage = e.message ?: "Unknown error",
                            completedAt = System.currentTimeMillis(),
                            pipelineStage = "Execution failed"
                        )
                    )
                }
            } finally {
                runningJobs.remove(task.id)
                if (isHeavy) {
                    _activeHeavyCount.value = (_activeHeavyCount.value - 1).coerceAtLeast(0)
                } else {
                    _activeLightCount.value = (_activeLightCount.value - 1).coerceAtLeast(0)
                }
                triggerScheduler()
            }
        }
        runningJobs[task.id] = job
    }

    private suspend fun runTaskWorker(task: TaskRecord) {
        when (task.taskType) {
            "STORAGE_CLEANUP" -> {
                updateProgress(task.id, 20, "Scanning cache directory")
                val cacheDir = context.cacheDir
                val exportedVideosDir = context.getExternalFilesDir("exported_videos")
                delay(300)
                updateProgress(task.id, 60, "Removing obsolete temporary files")
                cacheDir.listFiles()?.filter { it.name.startsWith("export_") && it.name.endsWith(".mp4") }?.forEach {
                    if (System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000) {
                        it.delete()
                    }
                }
                updateProgress(task.id, 90, "Finalizing cleanup")
                delay(200)
            }
            "THUMBNAIL_GEN" -> {
                updateProgress(task.id, 30, "Extracting video keyframe")
                delay(400)
                updateProgress(task.id, 80, "Compressing and caching thumbnail")
                delay(200)
            }
            "METADATA_EXTRACT" -> {
                updateProgress(task.id, 40, "Reading media container headers")
                delay(300)
                updateProgress(task.id, 80, "Indexing audio/video streams")
                delay(200)
            }
            else -> {
                // Generic simulation / worker
                for (step in 1..5) {
                    delay(300)
                    updateProgress(task.id, step * 20, "Executing stage $step of 5")
                }
            }
        }
    }

    suspend fun updateProgress(taskId: Long, progress: Int, stage: String) {
        val current = taskDao.getTaskById(taskId)
        if (current != null && current.status == "RUNNING") {
            taskDao.update(
                current.copy(
                    progress = progress.coerceIn(0, 100),
                    pipelineStage = stage
                )
            )
        }
    }

    fun cancelTask(taskId: Long) {
        runningJobs[taskId]?.cancel()
        scope.launch {
            val task = taskDao.getTaskById(taskId)
            if (task != null && (task.status == "QUEUED" || task.status == "PENDING")) {
                taskDao.update(
                    task.copy(
                        status = "CANCELLED",
                        completedAt = System.currentTimeMillis(),
                        pipelineStage = "Cancelled prior to start"
                    )
                )
            }
        }
    }

    fun retryTask(taskId: Long) {
        scope.launch {
            val task = taskDao.getTaskById(taskId)
            if (task != null && (task.status == "FAILED" || task.status == "INTERRUPTED" || task.status == "CANCELLED")) {
                taskDao.update(
                    task.copy(
                        status = "QUEUED",
                        progress = 0,
                        errorMessage = null,
                        completedAt = null,
                        pipelineStage = "Re-queued for execution"
                    )
                )
                triggerScheduler()
            }
        }
    }

    fun clearCompletedTasks() {
        scope.launch {
            taskDao.clearCompleted()
        }
    }

    private fun isRetryableError(e: Exception): Boolean {
        return e !is SecurityException && e !is IllegalArgumentException
    }
}
