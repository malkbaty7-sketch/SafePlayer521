package com.example.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.security.SecurityManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class StorageManagerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: SecurityManager
    private lateinit var storageManager: StorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = SecurityManager(context, database.securityDao())
        storageManager = StorageManager(
            context,
            database.storageLocationDao(),
            database.mediaDao(),
            securityManager
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `test clearCache clears cache directory`() = runTest {
        val tempDir = File(context.filesDir, "temp")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "export_tmp_123.mp4")
        tempFile.writeText("temp")
        
        assertTrue(tempFile.exists())
        
        val result = storageManager.clearCache("TEMP")
        assertTrue(result.isSuccess)
        assertFalse(tempFile.exists()) 
    }
}
