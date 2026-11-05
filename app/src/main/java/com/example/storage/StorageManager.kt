package com.example.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.MediaDao
import com.example.security.SecurityAction
import com.example.security.SecurityManager
import com.example.storage.data.StorageLocation
import com.example.storage.data.StorageLocationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class StorageManager(
    private val context: Context,
    private val storageLocationDao: StorageLocationDao,
    private val mediaDao: MediaDao,
    private val securityManager: SecurityManager
) {
    val storageLocations: Flow<List<StorageLocation>> = storageLocationDao.getAllLocations()

    suspend fun discoverAndRefreshLocations() {
        withContext(Dispatchers.IO) {
            // App internal storage
            val internalFiles = context.filesDir
            updateLocationInfo(
                path = internalFiles.absolutePath,
                displayName = "App Internal Storage",
                type = "APP_STORAGE",
                isReadOnly = false
            )

            // External storage (primary)
            val externalFiles = context.getExternalFilesDir(null)
            if (externalFiles != null) {
                updateLocationInfo(
                    path = externalFiles.absolutePath,
                    displayName = "App External Storage",
                    type = "DEVICE_STORAGE",
                    isReadOnly = false
                )
            }
        }
    }

    private suspend fun updateLocationInfo(
        path: String,
        displayName: String,
        type: String,
        isReadOnly: Boolean
    ) {
        val file = File(path)
        if (!file.exists()) return

        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalSpace = totalBlocks * blockSize
        val availableSpace = availableBlocks * blockSize
        val usedSpace = totalSpace - availableSpace

        val existing = storageLocationDao.getLocationByPath(path)
        val location = StorageLocation(
            id = existing?.id ?: 0,
            displayName = displayName,
            path = path,
            uri = path,
            type = type,
            totalSpace = totalSpace,
            availableSpace = availableSpace,
            usedSpace = usedSpace,
            permissionState = "GRANTED",
            isAccessible = true,
            isReadOnly = isReadOnly,
            lastCheckedAt = System.currentTimeMillis()
        )

        if (existing == null) {
            storageLocationDao.insertLocation(location)
        } else {
            storageLocationDao.updateLocation(location)
        }
    }

    suspend fun clearCache(type: String): Result<Long> {
        return withContext(Dispatchers.IO) {
            if (!securityManager.canPerformAction(SecurityAction.EDIT)) {
                return@withContext Result.failure(Exception("Cleanup restricted by Parental Controls"))
            }

            var clearedBytes = 0L
            val cacheDir = context.cacheDir

            // Simulated cache clearing for different types
            val targetDir = when (type) {
                "THUMBNAILS" -> File(cacheDir, "image_manager_disk_cache") // Coil cache
                "TEMP" -> File(context.filesDir, "temp")
                "EXPORTS" -> File(context.filesDir, "exports/temp")
                else -> null
            }

            if (targetDir != null && targetDir.exists()) {
                targetDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        clearedBytes += file.length()
                        file.delete()
                    }
                }
            }

            Result.success(clearedBytes)
        }
    }

    suspend fun getLibrarySize(): Long {
        return withContext(Dispatchers.IO) {
            mediaDao.getAllMediaSync().sumOf { it.sizeBytes }
        }
    }

    suspend fun reconcileDatabase(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            var missingCount = 0
            var orphanedCount = 0 // Concept: files on disk not in DB

            val allMedia = mediaDao.getAllMediaSync()
            for (media in allMedia) {
                val file = File(media.path)
                if (!file.exists()) {
                    missingCount++
                    mediaDao.updateMedia(media.copy(isMissing = true))
                } else if (media.isMissing) {
                    // It was missing but now found
                    mediaDao.updateMedia(media.copy(isMissing = false))
                }
            }

            mapOf(
                "missing" to missingCount,
                "orphaned" to orphanedCount
            )
        }
    }

    suspend fun renameMedia(mediaId: Int, newName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (!securityManager.canPerformAction(SecurityAction.EDIT)) {
                return@withContext Result.failure(Exception("Renaming is restricted by Parental Controls"))
            }

            val media = mediaDao.getMediaByIdSync(mediaId) ?: return@withContext Result.failure(Exception("Media not found"))
            val isProtected = securityManager.isMediaProtected(mediaId)
            if (isProtected && !securityManager.isUnlocked.value) {
                return@withContext Result.failure(Exception("Media is protected. Please unlock first."))
            }

            val oldFile = File(media.path)
            if (!oldFile.exists()) return@withContext Result.failure(Exception("File not found on disk"))

            val sanitizedName = newName.replace(Regex("[/\\\\?%*:|\"<>]"), "").replace("..", "").trim()
            if (sanitizedName.isEmpty()) {
                return@withContext Result.failure(Exception("Invalid file name"))
            }

            val parentDir = oldFile.parentFile ?: return@withContext Result.failure(Exception("Invalid parent directory"))
            val newFile = File(parentDir, "$sanitizedName.${oldFile.extension}")

            // Verify canonical path to prevent path traversal
            if (!newFile.canonicalPath.startsWith(parentDir.canonicalPath)) {
                return@withContext Result.failure(Exception("Security violation: path traversal detected"))
            }

            if (newFile.exists()) return@withContext Result.failure(Exception("A file with this name already exists"))

            val success = oldFile.renameTo(newFile)
            if (success) {
                val updatedMedia = media.copy(
                    name = sanitizedName,
                    path = newFile.absolutePath,
                    dateModified = System.currentTimeMillis() / 1000
                )
                mediaDao.updateMedia(updatedMedia)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to rename file on disk"))
            }
        }
    }

    suspend fun deleteMedia(mediaId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (!securityManager.canPerformAction(SecurityAction.DELETE)) {
                return@withContext Result.failure(Exception("Deletion is restricted by Parental Controls"))
            }

            val media = mediaDao.getMediaByIdSync(mediaId) ?: return@withContext Result.failure(Exception("Media not found"))
            val isProtected = securityManager.isMediaProtected(mediaId)
            if (isProtected && !securityManager.isUnlocked.value) {
                return@withContext Result.failure(Exception("Media is protected. Please unlock first."))
            }

            val file = File(media.path)
            val success = if (file.exists()) file.delete() else true

            if (success) {
                mediaDao.deleteMedia(media)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file from disk"))
            }
        }
    }

    suspend fun moveMedia(mediaId: Int, destinationPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (!securityManager.canPerformAction(SecurityAction.EDIT)) {
                return@withContext Result.failure(Exception("Moving is restricted by Parental Controls"))
            }

            val media = mediaDao.getMediaByIdSync(mediaId) ?: return@withContext Result.failure(Exception("Media not found"))
            val isProtected = securityManager.isMediaProtected(mediaId)
            if (isProtected && !securityManager.isUnlocked.value) {
                return@withContext Result.failure(Exception("Media is protected. Please unlock first."))
            }

            val oldFile = File(media.path)
            if (!oldFile.exists()) return@withContext Result.failure(Exception("Source file not found on disk"))

            val destDir = File(destinationPath)
            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(Exception("Invalid destination directory"))
            }

            val newFile = File(destDir, oldFile.name)
            if (newFile.exists()) return@withContext Result.failure(Exception("A file with the same name already exists in destination"))

            val success = oldFile.renameTo(newFile)
            if (success) {
                val updatedMedia = media.copy(
                    path = newFile.absolutePath,
                    folder = destDir.name,
                    dateModified = System.currentTimeMillis() / 1000
                )
                mediaDao.updateMedia(updatedMedia)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to move file on disk. Ensure it is on the same mount or implement full stream copy."))
            }
        }
    }
}
