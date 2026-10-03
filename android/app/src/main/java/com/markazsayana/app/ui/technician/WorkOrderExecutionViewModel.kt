package com.markazsayana.app.ui.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val addingPart: Boolean = false,
    val newPartName: String = "",
    val error: String? = null,
    val readyToFinish: Boolean = false,
    val submittingQuote: Boolean = false,
    val quoteError: String? = null,
    val quoteSent: Boolean = false,
)

@HiltViewModel
class WorkOrderExecutionViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()

    private val _uiState = MutableStateFlow(WorkOrderExecutionUiState())
    val uiState: StateFlow<WorkOrderExecutionUiState> = _uiState

    init {
        load()
        tickElapsed()
    }

    fun load() {
        viewModelScope.launch {
            try {
                var wo = repository.workOrder(workOrderId)
                if (wo.status == "assigned") {
                    wo = repository.startWorkOrder(workOrderId)
                }
                _uiState.update { it.copy(loading = false, workOrder = wo) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل أمر العمل") }
            }
        }
    }

    private fun tickElapsed() {
        viewModelScope.launch {
            while (true) {
                val startedAt = _uiState.value.workOrder?.startedAt
                if (startedAt != null) {
                    val elapsed = com.markazsayana.app.util.elapsedSince(Instant.parse(startedAt))
                    _uiState.update { it.copy(elapsed = elapsed) }
                }
                delay(1000)
            }
        }
    }

    fun togglePause() {
        val wo = _uiState.value.workOrder ?: return
        viewModelScope.launch {
            try {
                val updated = if (wo.status == "paused") repository.startWorkOrder(workOrderId) else repository.pauseWorkOrder(workOrderId)
                _uiState.update { it.copy(workOrder = updated) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "تعذّر تحديث الحالة") }
            }
        }
    }

    fun toggleChecklistItem(itemId: Int, currentStatus: String) {
        val nextStatus = if (currentStatus == "done") "pending" else "done"
        viewModelScope.launch {
            try {
                val updated = repository.updateChecklistItem(itemId, nextStatus)
                _uiState.update { it.copy(workOrder = updated) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "تعذّر تحديث بند الفحص") }
            }
        }
    }

    fun startAddPart() = _uiState.update { it.copy(addingPart = true) }
    fun cancelAddPart() = _uiState.update { it.copy(addingPart = false, newPartName = "") }
    fun onNewPartNameChange(v: String) = _uiState.update { it.copy(newPartName = v) }

    fun confirmAddPart(status: String) {
        val name = _uiState.value.newPartName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val updated = repository.addPart(workOrderId, name, null, status)
                _uiState.update { it.copy(workOrder = updated, addingPart = false, newPartName = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "تعذّر إضافة القطعة") }
            }
        }
    }

    fun sendQuote(estimatedCost: Double, note: String?) {
        _uiState.update { it.copy(submittingQuote = true, quoteError = null) }
        viewModelScope.launch {
            try {
                val updated = repository.sendQuote(workOrderId, estimatedCost, note)
                _uiState.update { it.copy(submittingQuote = false, workOrder = updated, quoteSent = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submittingQuote = false, quoteError = "تعذّر إرسال عرض السعر") }
            }
        }
    }
}
