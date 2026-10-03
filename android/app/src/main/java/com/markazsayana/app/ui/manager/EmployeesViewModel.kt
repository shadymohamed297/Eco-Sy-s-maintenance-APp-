package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.EmployeeDto
import com.markazsayana.app.data.repository.EmployeeRepository
import com.markazsayana.app.data.repository.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EmployeesUiState {
    data object Loading : EmployeesUiState
    data class Success(val employees: List<EmployeeDto>) : EmployeesUiState
    data class Error(val message: String) : EmployeesUiState
}

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val repository: EmployeeRepository,
    sessionState: SessionState,
) : ViewModel() {

    val currentUserId: Int? = sessionState.user.value?.id

    private val _uiState = MutableStateFlow<EmployeesUiState>(EmployeesUiState.Loading)
    val uiState: StateFlow<EmployeesUiState> = _uiState

    private val _temporaryPassword = MutableStateFlow<Pair<String, String>?>(null)
    val temporaryPassword: StateFlow<Pair<String, String>?> = _temporaryPassword

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    init {
        load()
    }

    fun load() {
        _uiState.value = EmployeesUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = EmployeesUiState.Success(repository.list())
            } catch (e: Exception) {
                _uiState.value = EmployeesUiState.Error("تعذّر تحميل قائمة الموظفين")
            }
        }
    }

    fun createEmployee(
        name: String,
        role: String,
        email: String?,
        phone: String?,
        branch: String?,
        title: String?,
        specialty: String?,
    ) {
        viewModelScope.launch {
            try {
                val response = repository.create(name, role, email?.ifBlank { null }, phone?.ifBlank { null }, branch?.ifBlank { null }, title?.ifBlank { null }, specialty?.ifBlank { null })
                _temporaryPassword.value = name to (response.temporaryPassword ?: "")
                load()
            } catch (e: Exception) {
                _actionError.value = "تعذّر إضافة الموظف — تأكد أن البريد أو الهاتف غير مستخدم من قبل"
            }
        }
    }

    fun setActive(id: Int, active: Boolean) {
        viewModelScope.launch {
            try {
                repository.setActive(id, active)
                load()
            } catch (e: Exception) {
                _actionError.value = "تعذّر تحديث حالة الحساب"
            }
        }
    }

    fun resetPassword(id: Int, name: String) {
        viewModelScope.launch {
            try {
                val response = repository.resetPassword(id)
                _temporaryPassword.value = name to (response.temporaryPassword ?: "")
            } catch (e: Exception) {
                _actionError.value = "تعذّر إعادة تعيين كلمة المرور"
            }
        }
    }

    fun updateEmployee(id: Int, name: String, branch: String?, title: String?, specialty: String?) {
        viewModelScope.launch {
            try {
                repository.update(id, name = name, branch = branch?.ifBlank { null }, title = title?.ifBlank { null }, specialty = specialty?.ifBlank { null })
                load()
            } catch (e: Exception) {
                _actionError.value = "تعذّر حفظ التعديلات"
            }
        }
    }

    fun dismissTemporaryPassword() {
        _temporaryPassword.value = null
    }

    fun dismissActionError() {
        _actionError.value = null
    }
}
