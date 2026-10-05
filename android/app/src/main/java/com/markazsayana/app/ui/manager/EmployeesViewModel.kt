package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.EmployeeDto
import com.markazsayana.app.data.repository.EmployeeRepository
import com.markazsayana.app.data.repository.SessionState
import com.markazsayana.app.ui.components.CENTER_LOCATION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject

sealed interface EmployeesUiState {
    data object Loading : EmployeesUiState
    data class Success(val employees: List<EmployeeDto>) : EmployeesUiState
    data class Error(val message: String) : EmployeesUiState
}

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val repository: EmployeeRepository,
    private val sessionState: SessionState,
) : ViewModel() {
    val currentUserId: Int? get() = sessionState.user.value?.id
    private val _uiState = MutableStateFlow<EmployeesUiState>(EmployeesUiState.Loading)
    val uiState: StateFlow<EmployeesUiState> = _uiState
    private val _temporaryPassword = MutableStateFlow<Pair<String, String>?>(null)
    val temporaryPassword: StateFlow<Pair<String, String>?> = _temporaryPassword
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress

    init { load() }
    fun load() {
        _uiState.value = EmployeesUiState.Loading
        viewModelScope.launch {
            try { _uiState.value = EmployeesUiState.Success(repository.list())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _uiState.value = EmployeesUiState.Error("تعذّر تحميل حسابات الفريق. حاول مرة أخرى") }
        }
    }

    private fun action(fallback: String, onComplete: () -> Unit, block: suspend () -> Unit) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                block()
                onComplete()
                load()
            } catch (e: CancellationException) { throw e
            } catch (e: HttpException) {
                _actionError.value = runCatching {
                    val body = e.response()?.errorBody()?.string() ?: return@runCatching null
                    Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
            } catch (e: Exception) { _actionError.value = "$fallback. راجع اتصال الإنترنت وحاول مرة أخرى"
            } finally { _actionInProgress.value = false }
        }
    }

    fun createEmployee(name: String, role: String, email: String, phone: String, title: String, specialty: String, onComplete: () -> Unit) {
        action("تعذّر إضافة حساب الموظف", onComplete) {
            val response = repository.create(name.trim(), role, email.trim().ifBlank { null }, phone.trim().ifBlank { null }, CENTER_LOCATION, title.trim().ifBlank { null }, specialty.trim().ifBlank { null })
            _temporaryPassword.value = response.temporaryPassword?.takeIf { it.isNotBlank() }?.let { response.employee.name to it }
        }
    }
    fun setActive(id: Int, active: Boolean, onComplete: () -> Unit) = action("تعذّر تحديث حالة الحساب", onComplete) { repository.setActive(id, active) }
    fun resetPassword(id: Int, name: String, onComplete: () -> Unit) = action("تعذّر إعادة تعيين كلمة المرور", onComplete) {
        val response = repository.resetPassword(id)
        _temporaryPassword.value = response.temporaryPassword?.takeIf { it.isNotBlank() }?.let { name to it }
    }
    fun updateEmployee(id: Int, name: String, role: String, email: String, phone: String, title: String, specialty: String, onComplete: () -> Unit) {
        action("تعذّر حفظ بيانات الموظف", onComplete) {
            val employee = repository.update(id, name = name.trim(), role = role, email = email.trim(), phone = phone.trim(), branch = CENTER_LOCATION, title = title.trim(), specialty = specialty.trim())
            sessionState.user.value?.takeIf { it.id == id }?.let {
                sessionState.set(it.copy(name = employee.name, initials = employee.initials, email = employee.email, phone = employee.phone, title = employee.title, specialty = employee.specialty, branch = CENTER_LOCATION))
            }
        }
    }
    fun dismissTemporaryPassword() { _temporaryPassword.value = null }
    fun dismissActionError() { _actionError.value = null }
}
