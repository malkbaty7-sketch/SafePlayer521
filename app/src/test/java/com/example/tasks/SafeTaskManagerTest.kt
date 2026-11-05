package com.example.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.tasks.data.TaskRecord
import com.example.tasks.resource.ResourceMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.delay

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class SafeTaskManagerTest {

    private lateinit var database: AppDatabase
    private lateinit var taskManager: SafeTaskManager
    private lateinit var resourceMonitor: ResourceMonitor

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resourceMonitor = ResourceMonitor(context)
        taskManager = SafeTaskManager(context, database.taskRecordDao(), resourceMonitor)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `test submit task creates queued state`() = runTest {
        val id = taskManager.enqueueTask(
            taskType = "STORAGE_CLEANUP",
            priority = "HIGH",
            title = "Cleanup Task"
        )
        
        val task = database.taskRecordDao().getTaskById(id)
        assertNotNull(task)
        assertEquals("QUEUED", task?.status)
    }
}