package com.markazsayana.app.ui.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.InvoicePreviewResponse
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceSignoffUiState(
    val loading: Boolean = true,
    val workOrder: WorkOrderDto? = null,
    val invoice: InvoicePreviewResponse? = null,
    val paymentMethod: String = "card",
    val signatureName: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val closed: Boolean = false,
)

@HiltViewModel
class InvoiceSignoffViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()
    private val laborFee = 450.0

    private val _uiState = MutableStateFlow(InvoiceSignoffUiState())
    val uiState: StateFlow<InvoiceSignoffUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val wo = repository.workOrder(workOrderId)
                val warrantyDiscount = if (wo.device.underWarranty) laborFee else 0.0
                val preview = repository.invoicePreview(workOrderId, laborFee, warrantyDiscount)
                _uiState.update {
                    it.copy(
                        loading = false,
                        workOrder = wo,
                        invoice = preview,
                        signatureName = wo.customer.name,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل الفاتورة") }
            }
        }
    }

    fun selectPaymentMethod(method: String) = _uiState.update { it.copy(paymentMethod = method) }
    fun onSignatureNameChange(v: String) = _uiState.update { it.copy(signatureName = v) }

    fun confirm() {
        val s = _uiState.value
        val invoice = s.invoice ?: return
        if (s.signatureName.isBlank()) {
            _uiState.update { it.copy(error = "من فضلك وقّع باسم العميل لإتمام التسليم") }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.closeWorkOrder(
                    workOrderId = workOrderId,
                    laborFee = invoice.laborFee,
                    warrantyDiscount = invoice.warrantyDiscount,
                    paymentMethod = s.paymentMethod,
                    signatureName = s.signatureName,
                    customerRating = null,
                )
                _uiState.update { it.copy(submitting = false, closed = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر تأكيد التسليم") }
            }
        }
    }
}
