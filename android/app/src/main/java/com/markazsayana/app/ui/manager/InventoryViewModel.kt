package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.InventoryResponse
import com.markazsayana.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InventoryUiState {
    data object Loading : InventoryUiState
    data class Success(val filter: String, val data: InventoryResponse) : InventoryUiState
    data class Error(val message: String) : InventoryUiState
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val uiState: StateFlow<InventoryUiState> = _uiState

    init {
        load("low")
    }

    fun load(filter: String) {
        _uiState.value = InventoryUiState.Loading
        viewModelScope.launch {
            try {
                val data = repository.items(filter)
                _uiState.value = InventoryUiState.Success(filter, data)
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("تعذّر تحميل المخزون")
            }
        }
    }

    fun createPurchaseOrder(itemId: Int) {
        viewModelScope.launch {
            try {
                repository.createPurchaseOrder(itemId)
                val current = _uiState.value
                if (current is InventoryUiState.Success) load(current.filter)
            } catch (e: Exception) {
                // Silently ignore — the item row stays as-is and the manager can retry.
            }
        }
    }
}
