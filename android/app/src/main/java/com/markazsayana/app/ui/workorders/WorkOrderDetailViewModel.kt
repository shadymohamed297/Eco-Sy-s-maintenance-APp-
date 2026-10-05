package com.markazsayana.app.ui.workorders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkOrderDetailUiState(
    val loading: Boolean = true,
    val workOrder: WorkOrderDto? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val savedVersion: Int = 0,
)

@HiltViewModel
class WorkOrderDetailViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()
    private val _uiState = MutableStateFlow(WorkOrderDetailUiState())
    val uiState: StateFlow<WorkOrderDetailUiState> = _uiState
    private var request: Job? = null

    fun load() {
        if (_uiState.value.submitting) return
        request?.cancel()
        _uiState.update { it.copy(loading = it.workOrder == null, error = null) }
        request = viewModelScope.launch {
            try {
                val order = repository.workOrder(workOrderId)
                _uiState.update { it.copy(loading = false, workOrder = order) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل تفاصيل الطلب. حاول مرة أخرى.") }
            }
        }
    }

    fun updateDetails(issue: String, priority: String) {
        if (_uiState.value.submitting) return
        if (issue.isBlank() || issue.length > 2000) {
            _uiState.update { it.copy(error = "اكتب وصف العطل، بحد أقصى ٢٠٠٠ حرف.") }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val order = repository.updateWorkOrder(workOrderId, issue.trim(), priority)
                _uiState.update { it.copy(submitting = false, workOrder = order, savedVersion = it.savedVersion + 1) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر حفظ تعديل الطلب. راجع الحالة وحاول مرة أخرى.") }
            }
        }
    }

    fun approveQuote() = updateApproval(true, null)
    fun rejectQuote(reason: String) = updateApproval(false, reason.trim().ifBlank { null })

    private fun updateApproval(approve: Boolean, reason: String?) {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val order = if (approve) repository.approve(workOrderId) else repository.reject(workOrderId, reason)
                _uiState.update { it.copy(submitting = false, workOrder = order) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر حفظ قرار الموافقة. حدّث الطلب وحاول مرة أخرى.") }
            }
        }
    }
}
