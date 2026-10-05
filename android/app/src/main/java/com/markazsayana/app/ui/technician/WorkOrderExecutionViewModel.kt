package com.markazsayana.app.ui.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.InventoryItemDto
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.InventoryRepository
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class WorkOrderExecutionUiState(
    val loading: Boolean = true,
    val workOrder: WorkOrderDto? = null,
    val elapsed: String = "٠٠:٠٠:٠٠",
    val updating: Boolean = false,
    val addingPart: Boolean = false,
    val newPartName: String = "",
    val selectedInventoryId: Int? = null,
    val inventory: List<InventoryItemDto> = emptyList(),
    val loadingInventory: Boolean = false,
    val inventoryError: String? = null,
    val error: String? = null,
    val submittingQuote: Boolean = false,
    val quoteError: String? = null,
    val quoteSent: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val photoError: String? = null,
)

@HiltViewModel
class WorkOrderExecutionViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val inventoryRepository: InventoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()
    private val _uiState = MutableStateFlow(WorkOrderExecutionUiState())
    val uiState: StateFlow<WorkOrderExecutionUiState> = _uiState

    init {
        load()
        viewModelScope.launch {
            while (true) {
                val workOrder = _uiState.value.workOrder
                val elapsed = workOrder?.let { order -> order.startedAt?.let { start ->
                    runCatching {
                        val endedAt = order.closedAt ?: order.completedAt
                        com.markazsayana.app.util.elapsedSince(Instant.parse(start), endedAt?.let(Instant::parse) ?: Instant.now())
                    }.getOrNull()
                } }
                if (elapsed != null) _uiState.update { it.copy(elapsed = elapsed) }
                delay(1000)
            }
        }
    }

    fun load() {
        if (_uiState.value.updating) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // Opening a visit is read-only. The technician explicitly starts work on arrival.
                val wo = repository.workOrder(workOrderId)
                _uiState.update { it.copy(loading = false, workOrder = wo) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = technicianRequestError(e, "تعذّر تحميل الطلب. حاول مرة أخرى.")) }
            }
        }
    }

    private fun updateOrder(action: suspend () -> WorkOrderDto, message: String, onSuccess: (WorkOrderExecutionUiState) -> WorkOrderExecutionUiState = { it }) {
        if (_uiState.value.updating || _uiState.value.submittingQuote || _uiState.value.uploadingPhoto) return
        _uiState.update { it.copy(updating = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = action()
                _uiState.update { onSuccess(it.copy(workOrder = updated, updating = false)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(updating = false, error = technicianRequestError(e, message)) } }
        }
    }

    fun togglePause() {
        val status = _uiState.value.workOrder?.status ?: return
        when (status) {
            "assigned", "paused" -> updateOrder({ repository.startWorkOrder(workOrderId) }, "تعذّر بدء العمل. حاول مرة أخرى.")
            "in_progress" -> updateOrder({ repository.pauseWorkOrder(workOrderId) }, "تعذّر إيقاف العمل مؤقتًا. حاول مرة أخرى.")
        }
    }

    fun toggleChecklistItem(itemId: Int, currentStatus: String) {
        if (_uiState.value.workOrder?.status !in listOf("in_progress", "paused")) return
        val nextStatus = if (currentStatus == "done") "pending" else "done"
        updateOrder({ repository.updateChecklistItem(itemId, nextStatus) }, "تعذّر حفظ بند الفحص. حاول مرة أخرى.")
    }

    fun startAddPart() {
        _uiState.update { it.copy(addingPart = true, loadingInventory = true, inventoryError = null) }
        viewModelScope.launch {
            try {
                val inventory = inventoryRepository.items().items
                _uiState.update { it.copy(inventory = inventory, loadingInventory = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loadingInventory = false, inventoryError = technicianRequestError(e, "تعذّر تحميل المخزون. حاول مرة أخرى.")) } }
        }
    }
    fun cancelAddPart() = _uiState.update { it.copy(addingPart = false, newPartName = "", selectedInventoryId = null) }
    fun onNewPartNameChange(value: String) = _uiState.update { it.copy(newPartName = value, selectedInventoryId = null) }
    fun selectInventoryItem(item: InventoryItemDto) = _uiState.update { it.copy(newPartName = item.name, selectedInventoryId = item.id) }

    fun confirmAddPart(status: String) {
        val state = _uiState.value
        val name = state.newPartName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "اكتب اسم القطعة أو اخترها من المخزون.") }
            return
        }
        if (status == "used" && (state.selectedInventoryId == null || state.inventory.firstOrNull { it.id == state.selectedInventoryId }?.quantity?.let { it <= 0 } != false)) {
            _uiState.update { it.copy(error = "اختر قطعة متاحة من المخزون؛ سيُسجل سعرها الفعلي في الفاتورة.") }
            return
        }
        updateOrder({ repository.addPart(workOrderId, name, state.selectedInventoryId, status) }, "تعذّر حفظ القطعة. حاول مرة أخرى.") {
            it.copy(addingPart = false, newPartName = "", selectedInventoryId = null)
        }
    }

    fun useRequestedPart(partId: Int) = updateOrder({ repository.useRequestedPart(workOrderId, partId) }, "تعذّر استخدام القطعة. راجع توفرها في المخزون وحاول مرة أخرى.")

    fun photoCaptureError(message: String) = _uiState.update { it.copy(photoError = message) }

    fun removePart(partId: Int) = updateOrder({ repository.removePart(workOrderId, partId) }, "تعذّر إزالة القطعة. حاول مرة أخرى.")

    fun uploadPhoto(kind: String, imageData: String) {
        val state = _uiState.value
        if (state.uploadingPhoto || state.updating || state.submittingQuote) return
        _uiState.update { it.copy(uploadingPhoto = true, photoError = null) }
        viewModelScope.launch {
            try {
                val updated = repository.uploadPhoto(workOrderId, kind, imageData)
                _uiState.update { it.copy(uploadingPhoto = false, workOrder = updated) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(uploadingPhoto = false, photoError = technicianRequestError(e, "تعذّر حفظ الصورة. حاول مرة أخرى.")) } }
        }
    }

    fun sendQuote(estimatedCost: Double, note: String?) {
        val state = _uiState.value
        if (state.submittingQuote || state.updating || state.uploadingPhoto) return
        if (!estimatedCost.isFinite() || estimatedCost <= 0) {
            _uiState.update { it.copy(quoteError = "اكتب تكلفة صحيحة أكبر من صفر.") }
            return
        }
        _uiState.update { it.copy(submittingQuote = true, quoteError = null) }
        viewModelScope.launch {
            try {
                val updated = repository.sendQuote(workOrderId, estimatedCost, note?.trim()?.ifBlank { null })
                _uiState.update { it.copy(submittingQuote = false, workOrder = updated, quoteSent = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(submittingQuote = false, quoteError = technicianRequestError(e, "تعذّر إرسال عرض السعر. حاول مرة أخرى.")) } }
        }
    }
}
