package com.markazsayana.app.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.InventoryResponse
import com.markazsayana.app.data.remote.SaveInventoryItemRequest
import com.markazsayana.app.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

sealed interface InventoryUiState {
    data object Loading : InventoryUiState
    data class Success(val filter: String, val data: InventoryResponse, val operationBusy: Boolean = false, val message: String? = null, val operationError: String? = null) : InventoryUiState
    data class Error(val message: String) : InventoryUiState
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Loading)
    val uiState: StateFlow<InventoryUiState> = _uiState

    private val _itemSaved = MutableStateFlow(false)
    val itemSaved: StateFlow<Boolean> = _itemSaved
    private var request: Job? = null
    private var currentFilter = "all"

    init {
        load("all")
    }

    fun load(filter: String) {
        if ((_uiState.value as? InventoryUiState.Success)?.operationBusy == true) return
        request?.cancel()
        currentFilter = filter
        _uiState.value = InventoryUiState.Loading
        request = viewModelScope.launch {
            try {
                val data = repository.items(filter)
                _uiState.value = InventoryUiState.Success(filter, data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("تعذّر تحميل المخزون")
            }
        }
    }
    fun refresh() = load(currentFilter)

    fun createPurchaseOrder(itemId: Int) {
        performOperation("تم تسجيل طلب الشراء.") { repository.createPurchaseOrder(itemId) }
    }

    fun receivePurchaseOrder(id: Int) {
        performOperation("تم تأكيد استلام القطع وتحديث المخزون.") { repository.receivePurchaseOrder(id) }
    }

    fun resetItemSaved() { _itemSaved.value = false }

    fun saveItem(id: Int?, request: SaveInventoryItemRequest) {
        performOperation("تم حفظ بيانات القطعة.") {
            repository.saveItem(id, request)
            _itemSaved.value = true
        }
    }

    private fun performOperation(message: String, operation: suspend () -> Unit) {
        val current = _uiState.value as? InventoryUiState.Success ?: return
        if (current.operationBusy) return
        _uiState.value = current.copy(operationBusy = true, operationError = null, message = null)
        viewModelScope.launch {
            try {
                operation()
                _uiState.value = current.copy(data = repository.items(current.filter), message = message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = current.copy(operationError = "تعذّر حفظ التغيير. راجع البيانات وحدّث المخزون قبل المحاولة مرة أخرى.")
            }
        }
    }
}
