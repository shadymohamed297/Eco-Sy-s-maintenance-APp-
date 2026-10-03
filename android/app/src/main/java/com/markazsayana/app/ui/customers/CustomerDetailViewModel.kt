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
import javax.inject.Inject

sealed interface CustomerDetailUiState {
    data object Loading : CustomerDetailUiState
    data class Success(val data: CustomerDetailResponse) : CustomerDetailUiState
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

    init {
        load()
    }

    fun load() {
        _uiState.value = CustomerDetailUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = CustomerDetailUiState.Success(repository.detail(customerId))
            } catch (e: Exception) {
                _uiState.value = CustomerDetailUiState.Error("تعذّر تحميل بيانات العميل")
            }
        }
    }

    fun updateCustomer(name: String, phone: String, address: String?) {
        viewModelScope.launch {
            try {
                repository.update(customerId, name, phone, address)
                load()
            } catch (e: Exception) {
                // The detail screen keeps showing the previous values; the manager can retry from the edit dialog.
            }
        }
    }
}
