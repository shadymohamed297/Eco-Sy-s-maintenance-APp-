package com.markazsayana.app.ui.reception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.CustomerDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val DEVICE_TYPES = listOf("مكيف", "غسالة", "ثلاجة", "سخان", "بوتاجاز", "أخرى")

enum class IntakeStep { CUSTOMER, DEVICE }

data class NewRequestUiState(
    val step: IntakeStep = IntakeStep.CUSTOMER,
    val phone: String = "",
    val searching: Boolean = false,
    val foundCustomer: CustomerDto? = null,
    val customerNotFound: Boolean = false,
    val newCustomerName: String = "",
    val newCustomerAddress: String = "",
    val deviceType: String = DEVICE_TYPES.first(),
    val brand: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val issueDescription: String = "",
    val underWarranty: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val createdWorkOrderId: Int? = null,
)

@HiltViewModel
class NewRequestViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewRequestUiState())
    val uiState: StateFlow<NewRequestUiState> = _uiState

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phone = phone, foundCustomer = null, customerNotFound = false, error = null) }
    }

    fun onNewCustomerNameChange(name: String) = _uiState.update { it.copy(newCustomerName = name) }
    fun onNewCustomerAddressChange(address: String) = _uiState.update { it.copy(newCustomerAddress = address) }

    fun searchCustomer() {
        val phone = _uiState.value.phone.trim()
        if (phone.isBlank()) return
        _uiState.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val customer = repository.findCustomerByPhone(phone)
                _uiState.update { it.copy(searching = false, foundCustomer = customer, customerNotFound = customer == null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(searching = false, error = "تعذّر البحث، حاول مرة أخرى") }
            }
        }
    }

    fun proceedToDeviceStep() {
        _uiState.update { it.copy(step = IntakeStep.DEVICE) }
    }

    fun backToCustomerStep() {
        _uiState.update { it.copy(step = IntakeStep.CUSTOMER) }
    }

    fun onDeviceTypeChange(type: String) = _uiState.update { it.copy(deviceType = type) }
    fun onBrandChange(v: String) = _uiState.update { it.copy(brand = v) }
    fun onModelChange(v: String) = _uiState.update { it.copy(model = v) }
    fun onSerialChange(v: String) = _uiState.update { it.copy(serialNumber = v) }
    fun onIssueChange(v: String) = _uiState.update { it.copy(issueDescription = v) }
    fun onWarrantyToggle(v: Boolean) = _uiState.update { it.copy(underWarranty = v) }

    fun submit() {
        val s = _uiState.value
        if (s.issueDescription.isBlank()) {
            _uiState.update { it.copy(error = "من فضلك أدخل وصف العطل") }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val customer = s.foundCustomer ?: repository.createCustomer(
                    name = s.newCustomerName.ifBlank { "عميل جديد" },
                    phone = s.phone,
                    address = s.newCustomerAddress.ifBlank { null },
                    branch = null,
                )
                val device = repository.createDevice(
                    customerId = customer.id,
                    deviceType = s.deviceType,
                    brand = s.brand.ifBlank { null },
                    model = s.model.ifBlank { null },
                    serialNumber = s.serialNumber.ifBlank { null },
                    underWarranty = s.underWarranty,
                    warrantyEnd = null,
                )
                val workOrder = repository.createWorkOrder(
                    customerId = customer.id,
                    deviceId = device.id,
                    issueDescription = s.issueDescription,
                    priority = "normal",
                    branch = customer.branch,
                )
                _uiState.update { it.copy(submitting = false, createdWorkOrderId = workOrder.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر حفظ الطلب، حاول مرة أخرى") }
            }
        }
    }
}
