package com.markazsayana.app.ui.reception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.ReceptionDashboardResponse
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.data.repository.AuthRepository
import com.markazsayana.app.data.repository.DashboardRepository
import com.markazsayana.app.data.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReceptionDashboardUiState {
    data object Loading : ReceptionDashboardUiState
    data class Success(val data: ReceptionDashboardResponse, val user: UserDto?) : ReceptionDashboardUiState
    data class Error(val message: String) : ReceptionDashboardUiState
}

@HiltViewModel
class ReceptionDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val sessionState: SessionState,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceptionDashboardUiState>(ReceptionDashboardUiState.Loading)
    val uiState: StateFlow<ReceptionDashboardUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.value = ReceptionDashboardUiState.Loading
        viewModelScope.launch {
            try {
                val data = dashboardRepository.reception()
                _uiState.value = ReceptionDashboardUiState.Success(data, sessionState.user.value)
            } catch (e: Exception) {
                _uiState.value = ReceptionDashboardUiState.Error("تعذّر تحميل لوحة الاستقبال")
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
