package com.markazsayana.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.local.ServerSettingsStore
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: UserDto) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverSettingsStore: ServerSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    val serverUrl: StateFlow<String> = serverSettingsStore.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("من فضلك أدخل البريد/الهاتف وكلمة المرور")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val user = authRepository.login(identifier.trim(), password)
                _uiState.value = LoginUiState.Success(user)
            } catch (e: retrofit2.HttpException) {
                _uiState.value = LoginUiState.Error("بيانات الدخول غير صحيحة")
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("تعذّر الاتصال بالخادم، حاول مرة أخرى")
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { serverSettingsStore.setBaseUrl(url.trim()) }
    }
}
