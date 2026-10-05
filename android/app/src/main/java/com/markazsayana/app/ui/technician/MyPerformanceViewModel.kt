package com.markazsayana.app.ui.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianPerformanceResponse
import com.markazsayana.app.data.repository.TechnicianRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

sealed interface MyPerformanceUiState {
    data object Loading : MyPerformanceUiState
    data class Success(val data: TechnicianPerformanceResponse) : MyPerformanceUiState
    data class Error(val message: String) : MyPerformanceUiState
}

@HiltViewModel
class MyPerformanceViewModel @Inject constructor(
    private val repository: TechnicianRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyPerformanceUiState>(MyPerformanceUiState.Loading)
    val uiState: StateFlow<MyPerformanceUiState> = _uiState

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = MyPerformanceUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                _uiState.value = MyPerformanceUiState.Success(repository.myPerformance())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MyPerformanceUiState.Error(technicianRequestError(e, "تعذّر تحميل تقرير الشغل. حاول مرة أخرى."))
            }
        }
    }
}
