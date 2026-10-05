package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianRosterDto
import com.markazsayana.app.data.repository.TechnicianRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface TechniciansRosterUiState {
    data object Loading : TechniciansRosterUiState
    data class Success(val technicians: List<TechnicianRosterDto>) : TechniciansRosterUiState
    data class Error(val message: String) : TechniciansRosterUiState
}

@HiltViewModel
class TechniciansRosterViewModel @Inject constructor(
    private val repository: TechnicianRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TechniciansRosterUiState>(TechniciansRosterUiState.Loading)
    val uiState: StateFlow<TechniciansRosterUiState> = _uiState
    private var request: Job? = null

    init {
        load()
    }

    fun load() {
        request?.cancel()
        _uiState.value = TechniciansRosterUiState.Loading
        request = viewModelScope.launch {
            try {
                _uiState.value = TechniciansRosterUiState.Success(repository.roster())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.value = TechniciansRosterUiState.Error("تعذّر تحميل قائمة الفنيين")
            }
        }
    }
}
