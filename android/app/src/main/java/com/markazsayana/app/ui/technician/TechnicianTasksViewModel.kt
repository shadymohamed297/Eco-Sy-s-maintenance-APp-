package com.markazsayana.app.ui.technician

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianProgress
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.AuthRepository
import com.markazsayana.app.data.repository.SessionState
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface TechnicianTasksUiState {
    data object Loading : TechnicianTasksUiState
    data class Success(val items: List<WorkOrderDto>, val progress: TechnicianProgress, val user: UserDto?) : TechnicianTasksUiState
    data class Error(val message: String) : TechnicianTasksUiState
}

@HiltViewModel
class TechnicianTasksViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val sessionState: SessionState,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TechnicianTasksUiState>(TechnicianTasksUiState.Loading)
    val uiState: StateFlow<TechnicianTasksUiState> = _uiState

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.value = TechnicianTasksUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val response = repository.myWorkOrders()
                _uiState.value = TechnicianTasksUiState.Success(response.items, response.progress, sessionState.user.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TechnicianTasksUiState.Error(technicianRequestError(e, "تعذّر تحميل الزيارات. حاول مرة أخرى."))
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
