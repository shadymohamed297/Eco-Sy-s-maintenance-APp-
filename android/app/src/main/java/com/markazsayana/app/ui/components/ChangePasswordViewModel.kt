package com.markazsayana.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import retrofit2.HttpException

sealed interface ChangePasswordUiState {
    data object Idle : ChangePasswordUiState
    data object Loading : ChangePasswordUiState
    data object Success : ChangePasswordUiState
    data class Error(val message: String) : ChangePasswordUiState
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val uiState: StateFlow<ChangePasswordUiState> = _uiState

    fun submit(currentPassword: String, newPassword: String) {
        _uiState.value = ChangePasswordUiState.Loading
        viewModelScope.launch {
            try {
                authRepository.changePassword(currentPassword, newPassword)
                _uiState.value = ChangePasswordUiState.Success
            } catch (e: HttpException) {
                val message = if (e.code() == 401) "كلمة المرور الحالية غير صحيحة" else "تعذّر تغيير كلمة المرور"
                _uiState.value = ChangePasswordUiState.Error(message)
            } catch (e: Exception) {
                _uiState.value = ChangePasswordUiState.Error("تعذّر تغيير كلمة المرور — تحقق من الاتصال")
            }
        }
    }

    fun reset() {
        _uiState.value = ChangePasswordUiState.Idle
    }
}
