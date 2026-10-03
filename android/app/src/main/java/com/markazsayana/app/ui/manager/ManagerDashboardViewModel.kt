package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.ManagerDashboardResponse
import com.markazsayana.app.data.repository.AuthRepository
import com.markazsayana.app.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ManagerDashboardUiState {
    data object Loading : ManagerDashboardUiState
    data class Success(val period: String, val data: ManagerDashboardResponse) : ManagerDashboardUiState
    data class Error(val message: String) : ManagerDashboardUiState
}

@HiltViewModel
class ManagerDashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManagerDashboardUiState>(ManagerDashboardUiState.Loading)
    val uiState: StateFlow<ManagerDashboardUiState> = _uiState

    init {
        load("month")
    }

    fun load(period: String) {
        _uiState.value = ManagerDashboardUiState.Loading
        viewModelScope.launch {
            try {
                val data = repository.manager(period)
                _uiState.value = ManagerDashboardUiState.Success(period, data)
            } catch (e: Exception) {
                _uiState.value = ManagerDashboardUiState.Error("تعذّر تحميل لوحة الأداء")
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
