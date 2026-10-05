package com.markazsayana.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
        if (_uiState.value is ChangePasswordUiState.Loading) return
        if (currentPassword.isBlank() || newPassword.length < 8 || newPassword.toByteArray(Charsets.UTF_8).size > 72) {
            _uiState.value = ChangePasswordUiState.Error("اكتب كلمة المرور الحالية، واختر كلمة جديدة من ٨ أحرف على الأقل")
            return
        }
        _uiState.value = ChangePasswordUiState.Loading
        viewModelScope.launch {
            try {
                authRepository.changePassword(currentPassword, newPassword)
                _uiState.value = ChangePasswordUiState.Success
            } catch (e: HttpException) {
                val message = runCatching {
                    val body = e.response()?.errorBody()?.string() ?: return@runCatching null
                    Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (e.code() == 401) "انتهت الجلسة. سجّل الدخول من جديد" else "تعذّر تغيير كلمة المرور"
                _uiState.value = ChangePasswordUiState.Error(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ChangePasswordUiState.Error("تعذّر تغيير كلمة المرور. راجع اتصال الإنترنت وحاول مرة أخرى")
            }
        }
    }

    fun reset() {
        _uiState.value = ChangePasswordUiState.Idle
    }
}
