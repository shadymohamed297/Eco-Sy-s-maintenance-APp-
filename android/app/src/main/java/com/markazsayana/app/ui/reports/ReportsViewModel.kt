package com.markazsayana.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.ReportsResponse
import com.markazsayana.app.data.repository.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError
    private var request: Job? = null
    private var currentPeriod = "month"

    init {
        load("month")
    }

    fun load(period: String) {
        request?.cancel()
        currentPeriod = period
        _uiState.value = ReportsUiState.Loading
        request = viewModelScope.launch {
            try {
                _uiState.value = ReportsUiState.Success(period, repository.reports(period))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ReportsUiState.Error("تعذّر تحميل التقارير")
            }
        }
    }
    fun refresh() = load(currentPeriod)

    fun export() {
        val current = _uiState.value
        if (current !is ReportsUiState.Success || _exporting.value) return
        _exporting.value = true
        _exportError.value = null
        viewModelScope.launch {
            try {
                _exportedFile.value = repository.exportCsv(current.period)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportError.value = "تعذّر تجهيز ملف التقرير. حاول مرة أخرى."
            } finally {
                _exporting.value = false
            }
        }
    }

    fun consumeExportedFile() {
        _exportedFile.value = null
    }

    fun reportShareFailure() { _exportError.value = "تعذّر فتح قائمة المشاركة على هذا الجهاز." }
}
