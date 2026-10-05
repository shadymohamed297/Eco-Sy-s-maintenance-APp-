package com.markazsayana.app.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.CustomerDetailResponse
import com.markazsayana.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

sealed interface CustomerDetailUiState {
    data object Loading : CustomerDetailUiState
    data class Success(val data: CustomerDetailResponse, val saving: Boolean = false, val saveError: String? = null, val savedVersion: Int = 0) : CustomerDetailUiState
    data class Error(val message: String) : CustomerDetailUiState
}

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val repository: CustomerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: Int = checkNotNull(savedStateHandle.get<String>("customerId")).toInt()

    private val _uiState = MutableStateFlow<CustomerDetailUiState>(CustomerDetailUiState.Loading)
    val uiState: StateFlow<CustomerDetailUiState> = _uiState
    private var request: Job? = null

    init {
        load()
    }

    fun load() {
        if ((_uiState.value as? CustomerDetailUiState.Success)?.saving == true) return
        request?.cancel()
        _uiState.value = CustomerDetailUiState.Loading
        request = viewModelScope.launch {
            try {
                _uiState.value = CustomerDetailUiState.Success(repository.detail(customerId))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.value = CustomerDetailUiState.Error("تعذّر تحميل بيانات العميل")
            }
        }
    }

    fun updateCustomer(name: String, phone: String, address: String?) {
        val current = _uiState.value as? CustomerDetailUiState.Success ?: return
        if (current.saving) return
        _uiState.value = current.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                val customer = repository.update(customerId, name, phone, address)
                _uiState.value = current.copy(data = current.data.copy(customer = customer), savedVersion = current.savedVersion + 1)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.value = current.copy(saveError = "تعذّر حفظ بيانات العميل. راجع الاسم ورقم الهاتف وحاول مرة أخرى.")
            }
        }
    }
}
