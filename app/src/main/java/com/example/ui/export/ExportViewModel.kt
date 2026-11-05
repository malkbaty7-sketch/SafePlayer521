package com.example.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EditingOperation
import com.example.data.EditingProject
import com.example.data.ExportTask
import com.example.data.ExportTaskDao
import com.example.export.ExportEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportViewModel(
    private val exportEngine: ExportEngine,
    private val exportTaskDao: ExportTaskDao
) : ViewModel() {

    fun getTasksForProject(projectId: Int): Flow<List<ExportTask>> {
        return exportTaskDao.getTasksForProject(projectId)
    }

    fun startExport(project: EditingProject, operations: List<EditingOperation>, quality: String) {
        viewModelScope.launch {
            exportEngine.startExport(
                project = project,
                operations = operations,
                quality = quality
            )
        }
    }
    
    fun cancelExport() {
        exportEngine.cancelCurrentExport()
    }
}
