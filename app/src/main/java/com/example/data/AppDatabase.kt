package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaItem::class, 
        ScanJob::class, 
        Playlist::class, 
        PlaylistMediaCrossRef::class,
        MediaSource::class,
        Scene::class,
        com.example.ai.DetectionResult::class,
        com.example.modelmanager.AiModelEntity::class,
        com.example.processing.ProcessingJob::class,
        TimelineMarker::class,
        EditingProject::class,
        EditingOperation::class,
        ExportTask::class,
        com.example.tasks.data.TaskRecord::class,
        com.example.security.data.SecurityConfig::class,
        com.example.security.data.ProtectedItem::class,
        com.example.security.data.SecurityAuditLog::class,
        com.example.storage.data.StorageLocation::class,
        com.example.streaming.StreamChannel::class,
        com.example.bookmarks.VideoBookmark::class
    ], 
    version = 12, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun aiModelDao(): com.example.modelmanager.AiModelDao
    abstract fun exportTaskDao(): ExportTaskDao
    abstract fun taskRecordDao(): com.example.tasks.data.TaskRecordDao
    abstract fun securityDao(): com.example.security.data.SecurityDao
    abstract fun storageLocationDao(): com.example.storage.data.StorageLocationDao
    abstract fun streamChannelDao(): com.example.streaming.StreamChannelDao
    abstract fun videoBookmarkDao(): com.example.bookmarks.VideoBookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to media_items
                database.execSQL("ALTER TABLE media_items ADD COLUMN isMissing INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE media_items ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE media_items ADD COLUMN mimeType TEXT")
                database.execSQL("ALTER TABLE media_items ADD COLUMN artist TEXT")
                database.execSQL("ALTER TABLE media_items ADD COLUMN album TEXT")
                
                // Create scan_jobs table
                database.execSQL("CREATE TABLE IF NOT EXISTS `scan_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `status` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `itemsScanned` INTEGER NOT NULL, `newItems` INTEGER NOT NULL, `changedItems` INTEGER NOT NULL, `missingItems` INTEGER NOT NULL, `errorMessage` TEXT)")
                
                // Create playlists table
                database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                
                // Create playlist_media_cross_ref table
                database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_media_cross_ref` (`playlistId` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaId`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_media_cross_ref_mediaId` ON `playlist_media_cross_ref` (`mediaId`)")
                
                // Create media_sources table
                database.execSQL("CREATE TABLE IF NOT EXISTS `media_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `isScanEnabled` INTEGER NOT NULL, `lastScanTime` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `scenes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER NOT NULL, `thumbnailPath` TEXT NOT NULL, `sceneIndex` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_scenes_mediaId` ON `scenes` (`mediaId`)")
                
                database.execSQL("ALTER TABLE media_items ADD COLUMN analysisStatus TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE media_items ADD COLUMN analyzedSize INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE media_items ADD COLUMN analyzedDate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `detection_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `sceneId` INTEGER NOT NULL, `category` TEXT NOT NULL, `modality` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER NOT NULL, `confidence` REAL NOT NULL, `modelId` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `sensitivity` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_detection_results_mediaId` ON `detection_results` (`mediaId`)")
                
                database.execSQL("ALTER TABLE media_items ADD COLUMN aiAnalysisStatus TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE media_items ADD COLUMN aiAnalyzedSize INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE media_items ADD COLUMN aiAnalyzedDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `ai_models` (`modelId` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `analysisType` TEXT NOT NULL, `minApiLevel` INTEGER NOT NULL, `minRamGb` INTEGER NOT NULL, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `checksum` TEXT NOT NULL, PRIMARY KEY(`modelId`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `processing_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalMediaId` INTEGER NOT NULL, `operationType` TEXT NOT NULL, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `tempFilePath` TEXT, `finalFilePath` TEXT, `errorMessage` TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_originalMediaId` ON `processing_jobs` (`originalMediaId`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `timeline_markers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timeMs` INTEGER NOT NULL, `name` TEXT, `description` TEXT, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_markers_mediaId` ON `timeline_markers` (`mediaId`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `editing_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_editing_projects_mediaId` ON `editing_projects` (`mediaId`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `editing_operations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `type` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `propertiesJson` TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_editing_operations_projectId` ON `editing_operations` (`projectId`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `export_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `sourceMediaId` INTEGER NOT NULL, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `outputFormat` TEXT NOT NULL, `videoCodec` TEXT NOT NULL, `quality` TEXT NOT NULL, `tempFilePath` TEXT, `finalFilePath` TEXT, `errorMessage` TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_export_tasks_projectId` ON `export_tasks` (`projectId`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // task_records
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskType` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `title` TEXT NOT NULL, `progress` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, `projectId` INTEGER, `mediaId` INTEGER, `errorMessage` TEXT, `retryCount` INTEGER NOT NULL, `maxRetries` INTEGER NOT NULL, `resourceRequirements` TEXT NOT NULL, `pipelineStage` TEXT, `payloadJson` TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_records_status` ON `task_records` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_records_priority` ON `task_records` (`priority`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_records_taskType` ON `task_records` (`taskType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_records_projectId` ON `task_records` (`projectId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_records_mediaId` ON `task_records` (`mediaId`)")

                // security_configs
                database.execSQL("CREATE TABLE IF NOT EXISTS `security_configs` (`id` INTEGER PRIMARY KEY NOT NULL, `isAppLockEnabled` INTEGER NOT NULL, `pinHash` TEXT, `salt` TEXT, `isBiometricEnabled` INTEGER NOT NULL, `autoLockTimeoutSeconds` INTEGER NOT NULL, `parentPinHash` TEXT, `parentPinSalt` TEXT, `isParentalControlEnabled` INTEGER NOT NULL, `userRole` TEXT NOT NULL, `blockDelete` INTEGER NOT NULL, `blockExport` INTEGER NOT NULL, `blockShare` INTEGER NOT NULL, `blockEdit` INTEGER NOT NULL, `blockAddSource` INTEGER NOT NULL, `blockSettings` INTEGER NOT NULL, `isScreenshotProtectionEnabled` INTEGER NOT NULL)")

                // protected_items
                database.execSQL("CREATE TABLE IF NOT EXISTS `protected_items` (`itemType` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `protectedAt` INTEGER NOT NULL, PRIMARY KEY(`itemType`, `itemId`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_protected_items_itemType` ON `protected_items` (`itemType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_protected_items_itemId` ON `protected_items` (`itemId`)")

                // security_audit_logs
                database.execSQL("CREATE TABLE IF NOT EXISTS `security_audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `details` TEXT NOT NULL, `userRole` TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_security_audit_logs_eventType` ON `security_audit_logs` (`eventType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_security_audit_logs_timestamp` ON `security_audit_logs` (`timestamp`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add storage_locations table
                database.execSQL("CREATE TABLE IF NOT EXISTS `storage_locations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT NOT NULL, `platform` TEXT NOT NULL, `path` TEXT NOT NULL, `uri` TEXT NOT NULL, `type` TEXT NOT NULL, `availableSpace` INTEGER NOT NULL, `totalSpace` INTEGER NOT NULL, `usedSpace` INTEGER NOT NULL, `permissionState` TEXT NOT NULL, `isAccessible` INTEGER NOT NULL, `isReadOnly` INTEGER NOT NULL, `lastCheckedAt` INTEGER NOT NULL)")
                
                // Add new columns to media_items
                database.execSQL("ALTER TABLE media_items ADD COLUMN isOriginal INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE media_items ADD COLUMN sourceMediaId INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add stream_channels table
                database.execSQL("CREATE TABLE IF NOT EXISTS `stream_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `groupTitle` TEXT NOT NULL, `logoUrl` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)")
                
                // Add video_bookmarks table
                database.execSQL("CREATE TABLE IF NOT EXISTS `video_bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_video_bookmarks_mediaId` ON `video_bookmarks` (`mediaId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safeplayer_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
