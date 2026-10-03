package com.markazsayana.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.ReportsResponse
import com.markazsayana.app.data.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Success(val period: String, val data: ReportsResponse) : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val uiState: StateFlow<ReportsUiState> = _uiState

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    init {
        load("month")
    }

    fun load(period: String) {
        _uiState.value = ReportsUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = ReportsUiState.Success(period, repository.reports(period))
            } catch (e: Exception) {
                _uiState.value = ReportsUiState.Error("تعذّر تحميل التقارير")
            }
        }
    }

    fun export() {
        val current = _uiState.value
        if (current !is ReportsUiState.Success) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                _exportedFile.value = repository.exportCsv(current.period)
            } catch (e: Exception) {
                // Export failure just leaves the button available to retry; the on-screen
                // report data itself already loaded fine, so nothing else needs to change.
            } finally {
                _exporting.value = false
            }
        }
    }

    fun consumeExportedFile() {
        _exportedFile.value = null
    }
}
