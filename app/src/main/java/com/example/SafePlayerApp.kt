package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.MediaRepository
import com.example.scanner.MediaScanner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SafePlayerApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao()) }
    val scanner by lazy { MediaScanner(this, repository) }
    val sceneAnalyzer by lazy { com.example.scanner.SceneAnalyzer(this, repository) }
    val aiAnalyzer by lazy { com.example.ai.AiAnalyzer(this, repository) }
    val modelManager by lazy { com.example.modelmanager.ModelManager(this, database.aiModelDao()) }
    val scanDataManager by lazy { com.example.scandata.ScanDataManager(this, repository) }
    val exportTaskDao by lazy { database.exportTaskDao() }
    val exportEngine by lazy { com.example.export.ExportEngine(this, exportTaskDao, database.mediaDao()) }
    val taskRecordDao by lazy { database.taskRecordDao() }
    val resourceMonitor by lazy { com.example.tasks.resource.ResourceMonitor(this) }
    val taskManager by lazy { com.example.tasks.SafeTaskManager(this, taskRecordDao, resourceMonitor) }
    val securityDao by lazy { database.securityDao() }
    val securityManager by lazy { com.example.security.SecurityManager(this, securityDao) }
    val storageLocationDao by lazy { database.storageLocationDao() }
    val storageManager by lazy { com.example.storage.StorageManager(this, storageLocationDao, database.mediaDao(), securityManager) }
    
    override fun onCreate() {
        super.onCreate()
        com.example.util.LanguageManager.init(this)
        com.example.util.ThemeManager.init(this)
        GlobalScope.launch {
            modelManager.initializeDefaultModels()
        }
    }
}
