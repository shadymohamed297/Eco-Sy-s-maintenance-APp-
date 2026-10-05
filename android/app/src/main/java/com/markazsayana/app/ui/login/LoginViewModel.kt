package com.markazsayana.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.local.ServerSettingsStore
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.data.repository.AuthRepository
import com.markazsayana.app.ui.components.westernDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
        if (_uiState.value is LoginUiState.Loading) return
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("اكتب البريد الإلكتروني أو رقم الهاتف وكلمة المرور")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val loginIdentifier = identifier.trim().let { if ('@' in it) it else westernDigits(it) }
                val user = authRepository.login(loginIdentifier, password)
                _uiState.value = LoginUiState.Success(user)
            } catch (e: retrofit2.HttpException) {
                _uiState.value = LoginUiState.Error(when (e.code()) {
                    401, 403 -> "راجع بيانات الدخول أو حالة حسابك مع مسؤول المركز"
                    429 -> "محاولات الدخول كثيرة. انتظر قليلًا ثم حاول مرة أخرى"
                    else -> "تعذّر تسجيل الدخول الآن. حاول مرة أخرى"
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("تعذّر الاتصال بالنظام. راجع اتصال الإنترنت وحاول مرة أخرى")
            }
        }
    }

    fun setServerUrl(url: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                serverSettingsStore.setBaseUrl(url)
                _uiState.value = LoginUiState.Idle
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                onResult(e.message)
            } catch (e: Exception) {
                onResult("تعذّر حفظ رابط النظام. حاول مرة أخرى")
            }
        }
    }
}
