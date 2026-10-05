package com.markazsayana.app.ui.technician

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.InvoicePreviewResponse
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceSignoffUiState(
    val loading: Boolean = true,
    val workOrder: WorkOrderDto? = null,
    val invoice: InvoicePreviewResponse? = null,
    val laborFee: String = "",
    val warrantyDiscount: String = "0",
    val previewing: Boolean = false,
    val paymentMethod: String = "cash",
    val signatureName: String = "",
    val customerRating: Int? = null,
    val signature: SignatureDrawing = SignatureDrawing(),
    val submitting: Boolean = false,
    val error: String? = null,
    val closed: Boolean = false,
) {
    val invoiceMatchesAmounts: Boolean get() = invoice?.let {
        parseMoney(laborFee) == it.laborFee && parseMoney(warrantyDiscount) == it.warrantyDiscount
    } == true
}

@HiltViewModel
class InvoiceSignoffViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()
    private var previewJob: Job? = null
    private val _uiState = MutableStateFlow(InvoiceSignoffUiState(
        laborFee = savedStateHandle["laborFee"] ?: "",
        warrantyDiscount = savedStateHandle["warrantyDiscount"] ?: "0",
        signatureName = savedStateHandle["signatureName"] ?: "",
        paymentMethod = savedStateHandle["paymentMethod"] ?: "cash",
        customerRating = savedStateHandle["customerRating"],
        signature = SignatureDrawing(savedStateHandle.get<ArrayList<FloatArray>>("signatureStrokes")?.map { stroke ->
            stroke.toList().chunked(2).filter { it.size == 2 }.map { SignaturePoint(it[0], it[1]) }
        } ?: emptyList()),
    ))
    val uiState: StateFlow<InvoiceSignoffUiState> = _uiState

    init { load() }

    fun load() {
        if (_uiState.value.submitting) return
        previewJob?.cancel()
        _uiState.update { it.copy(loading = true, error = null, previewing = false) }
        viewModelScope.launch {
            try {
                val wo = repository.workOrder(workOrderId)
                val previous = _uiState.value
                val saved = wo.invoice
                val labor = saved?.laborFee?.toString() ?: previous.laborFee
                val discount = saved?.warrantyDiscount?.toString() ?: previous.warrantyDiscount
                val previewLabor = parseMoney(labor) ?: 0.0
                val usedPartsTotal = wo.parts.filter { it.status == "used" }.sumOf { it.price }
                val previewDiscount = parseMoney(discount)?.takeIf { it <= previewLabor + usedPartsTotal } ?: 0.0
                val preview = repository.invoicePreview(workOrderId, previewLabor, previewDiscount)
                _uiState.update { it.copy(
                    loading = false, workOrder = wo, invoice = preview,
                    laborFee = labor, warrantyDiscount = discount,
                    signatureName = saved?.signatureName ?: it.signatureName.ifBlank { wo.customer.name },
                    paymentMethod = saved?.paymentMethod ?: it.paymentMethod,
                    customerRating = saved?.customerRating ?: it.customerRating,
                    closed = wo.status == "closed",
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = technicianRequestError(e, "تعذّر تحميل الفاتورة. حاول مرة أخرى.")) }
            }
        }
    }

    fun onLaborFeeChange(value: String) {
        if (_uiState.value.submitting || _uiState.value.closed) return
        savedStateHandle["laborFee"] = value
        _uiState.update { it.copy(laborFee = value, error = null) }
        refreshPreview()
    }

    fun onWarrantyDiscountChange(value: String) {
        if (_uiState.value.submitting || _uiState.value.closed) return
        savedStateHandle["warrantyDiscount"] = value
        _uiState.update { it.copy(warrantyDiscount = value, error = null) }
        refreshPreview()
    }

    fun refreshPreview() {
        previewJob?.cancel()
        val state = _uiState.value
        if (state.closed || state.submitting) return
        val error = invoiceAmountError(state.laborFee, state.warrantyDiscount, state.invoice?.partsTotal ?: 0.0)
        if (error != null) {
            _uiState.update { it.copy(previewing = false) }
            return
        }
        _uiState.update { it.copy(previewing = true, error = null) }
        previewJob = viewModelScope.launch {
            delay(350)
            try {
                val preview = repository.invoicePreview(workOrderId, parseMoney(state.laborFee)!!, parseMoney(state.warrantyDiscount)!!)
                _uiState.update {
                    if (it.laborFee == state.laborFee && it.warrantyDiscount == state.warrantyDiscount)
                        it.copy(invoice = preview, previewing = false)
                    else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(previewing = false, error = technicianRequestError(e, "تعذّر حساب الفاتورة. حاول مرة أخرى.")) }
            }
        }
    }

    fun selectPaymentMethod(method: String) {
        if (!_uiState.value.submitting && !_uiState.value.closed && method in listOf("cash", "card", "account")) {
            savedStateHandle["paymentMethod"] = method
            _uiState.update { it.copy(paymentMethod = method) }
        }
    }
    fun selectCustomerRating(rating: Int?) {
        if (!_uiState.value.submitting && !_uiState.value.closed && (rating == null || rating in 1..5)) {
            savedStateHandle["customerRating"] = rating
            _uiState.update { it.copy(customerRating = rating) }
        }
    }
    fun onSignatureNameChange(value: String) {
        if (!_uiState.value.submitting && !_uiState.value.closed) {
            savedStateHandle["signatureName"] = value
            _uiState.update { it.copy(signatureName = value, error = null) }
        }
    }
    fun beginStroke(point: SignaturePoint) {
        if (!_uiState.value.submitting) _uiState.update { it.copy(signature = it.signature.begin(point), error = null) }
    }
    fun appendStroke(point: SignaturePoint) {
        if (!_uiState.value.submitting) _uiState.update { it.copy(signature = it.signature.append(point)) }
    }
    fun finishStroke() {
        if (_uiState.value.closed) return
        _uiState.update { it.copy(signature = it.signature.finish()) }
        savedStateHandle["signatureStrokes"] = ArrayList(_uiState.value.signature.strokes.map { stroke ->
            stroke.flatMap { listOf(it.x, it.y) }.toFloatArray()
        })
    }
    fun clearSignature() {
        if (!_uiState.value.submitting && !_uiState.value.closed) {
            _uiState.update { it.copy(signature = SignatureDrawing()) }
            savedStateHandle.remove<ArrayList<FloatArray>>("signatureStrokes")
        }
    }

    fun confirm(signatureImage: String?) {
        val state = _uiState.value
        if (state.submitting || state.closed || state.signature.activeStroke != null) return
        val invoice = state.invoice ?: return
        val wo = state.workOrder ?: return
        val error = invoiceAmountError(state.laborFee, state.warrantyDiscount, invoice.partsTotal)
            ?: when {
                state.previewing || !state.invoiceMatchesAmounts -> "انتظر حساب الفاتورة بعد تعديل المبالغ."
                wo.status !in listOf("in_progress", "completed") -> "استأنف العمل على الطلب قبل تأكيد التسليم."
                wo.checklist.any { it.status != "done" } -> "أكمل بنود الفحص قبل تسليم الطلب."
                wo.parts.any { it.status != "used" } -> "راجع قطع الغيار المطلوبة قبل تسليم الطلب."
                state.signatureName.isBlank() -> "اكتب اسم العميل الذي يستلم الجهاز."
                signatureImage.isNullOrBlank() || state.signature.isEmpty -> "اطلب من العميل التوقيع داخل المربع قبل تأكيد التسليم."
                else -> null
            }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val closed = repository.closeWorkOrder(
                    workOrderId, invoice.laborFee, invoice.warrantyDiscount,
                    state.paymentMethod, state.signatureName.trim(), signatureImage, state.customerRating,
                )
                // Keep the drawing through failures; only successful persistence clears it.
                val saved = closed.invoice
                val finalInvoice = if (saved != null) invoice.copy(
                    laborFee = saved.laborFee, warrantyDiscount = saved.warrantyDiscount, taxRate = saved.taxRate,
                    partsTotal = saved.partsTotal, subtotal = saved.subtotal, total = saved.total,
                    parts = closed.parts.filter { it.status == "used" },
                ) else invoice
                savedStateHandle.remove<ArrayList<FloatArray>>("signatureStrokes")
                _uiState.update { it.copy(
                    submitting = false, closed = true, workOrder = closed, invoice = finalInvoice,
                    paymentMethod = saved?.paymentMethod ?: it.paymentMethod,
                    signatureName = saved?.signatureName ?: it.signatureName,
                    customerRating = saved?.customerRating,
                    signature = SignatureDrawing(),
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = technicianRequestError(e, "تعذّر حفظ التسليم. التوقيع محفوظ هنا؛ حاول مرة أخرى.")) }
            }
        }
    }
}
