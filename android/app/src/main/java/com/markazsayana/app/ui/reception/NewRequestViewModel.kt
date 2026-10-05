package com.markazsayana.app.ui.reception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.CustomerDto
import com.markazsayana.app.data.remote.DeviceDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import com.markazsayana.app.ui.components.CENTER_LOCATION
import com.markazsayana.app.ui.components.westernDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val DEVICE_TYPES = listOf("تكييف", "غسالة", "ثلاجة", "سخان", "بوتاجاز", "أخرى")
enum class IntakeStep { CUSTOMER, DEVICE }

data class NewRequestUiState(
    val step: IntakeStep = IntakeStep.CUSTOMER,
    val phone: String = "",
    val searching: Boolean = false,
    val foundCustomer: CustomerDto? = null,
    val customerNotFound: Boolean = false,
    val newCustomerName: String = "",
    val newCustomerAddress: String = "",
    val selectedDeviceId: Int? = null,
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
class NewRequestViewModel @Inject constructor(private val repository: WorkOrderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NewRequestUiState())
    val uiState: StateFlow<NewRequestUiState> = _uiState

    fun onPhoneChange(phone: String) {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(phone = westernDigits(phone), searching = false, foundCustomer = null, customerNotFound = false, selectedDeviceId = null, error = null) }
    }
    fun onNewCustomerNameChange(name: String) = _uiState.update { if (it.submitting) it else it.copy(newCustomerName = name) }
    fun onNewCustomerAddressChange(address: String) = _uiState.update { if (it.submitting) it else it.copy(newCustomerAddress = address) }

    private fun validPhone(phone: String) = phone.matches(Regex("[+0-9\\s-]{8,20}")) && phone.count(Char::isDigit) >= 8

    fun searchCustomer() {
        val phone = _uiState.value.phone.trim()
        if (!validPhone(phone)) {
            _uiState.update { it.copy(error = "اكتب رقم هاتف صحيح للبحث عن العميل") }
            return
        }
        if (_uiState.value.searching || _uiState.value.submitting) return
        _uiState.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val customer = repository.findCustomerByPhone(phone)
                _uiState.update { if (it.phone.trim() != phone) it else it.copy(searching = false, foundCustomer = customer, newCustomerAddress = customer?.address.orEmpty(), customerNotFound = customer == null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { if (it.phone.trim() != phone) it else it.copy(searching = false, error = "تعذّر البحث عن العميل. حاول مرة أخرى") }
            }
        }
    }

    fun proceedToDeviceStep() {
        val s = _uiState.value
        if (s.submitting) return
        if (!validPhone(s.phone.trim()) || (s.foundCustomer == null && (!s.customerNotFound || s.newCustomerName.trim().length < 2))) {
            _uiState.update { it.copy(error = "راجع رقم الهاتف واسم العميل قبل المتابعة") }
            return
        }
        _uiState.update { it.copy(step = IntakeStep.DEVICE, error = null) }
    }
    fun backToCustomerStep() = _uiState.update { if (it.submitting) it else it.copy(step = IntakeStep.CUSTOMER, error = null) }

    fun chooseDevice(device: DeviceDto?) {
        _uiState.update { if (it.submitting) it else it.copy(
            selectedDeviceId = device?.id, deviceType = device?.deviceType ?: DEVICE_TYPES.first(),
            brand = device?.brand.orEmpty(), model = device?.model.orEmpty(), serialNumber = device?.serialNumber.orEmpty(),
            underWarranty = device?.underWarranty ?: false,
        ) }
    }
    fun onDeviceTypeChange(type: String) = _uiState.update { if (it.submitting) it else it.copy(deviceType = type, selectedDeviceId = null) }
    fun onBrandChange(v: String) = _uiState.update { if (it.submitting) it else it.copy(brand = v, selectedDeviceId = null) }
    fun onModelChange(v: String) = _uiState.update { if (it.submitting) it else it.copy(model = v, selectedDeviceId = null) }
    fun onSerialChange(v: String) = _uiState.update { if (it.submitting) it else it.copy(serialNumber = v, selectedDeviceId = null) }
    fun onIssueChange(v: String) = _uiState.update { if (it.submitting) it else it.copy(issueDescription = v) }
    fun onWarrantyToggle(v: Boolean) = _uiState.update { if (it.submitting) it else it.copy(underWarranty = v, selectedDeviceId = null) }

    fun submit() {
        val s = _uiState.value
        if (s.submitting || s.createdWorkOrderId != null) return
        if (!validPhone(s.phone.trim()) || (s.foundCustomer == null && s.newCustomerName.trim().length < 2)) {
            _uiState.update { it.copy(error = "راجع بيانات العميل قبل حفظ الطلب") }
            return
        }
        if (s.issueDescription.isBlank()) {
            _uiState.update { it.copy(error = "اكتب وصف العطل كما ذكره العميل") }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val customer = s.foundCustomer ?: repository.createCustomer(s.newCustomerName.trim(), s.phone.trim(), s.newCustomerAddress.trim().ifBlank { null }, CENTER_LOCATION)
                _uiState.update { it.copy(foundCustomer = customer) }
                val deviceId = s.selectedDeviceId ?: repository.createDevice(
                    customer.id, s.deviceType, s.brand.trim().ifBlank { null }, s.model.trim().ifBlank { null },
                    s.serialNumber.trim().ifBlank { null }, s.underWarranty, null,
                ).also { device ->
                    // A saved device remains selected if creating the order needs a retry.
                    _uiState.update { it.copy(selectedDeviceId = device.id, foundCustomer = customer.copy(devices = customer.devices.orEmpty() + device)) }
                }.id
                val order = repository.createWorkOrder(customer.id, deviceId, s.issueDescription.trim(), "normal", CENTER_LOCATION, serviceAddress = s.newCustomerAddress.trim().ifBlank { null })
                _uiState.update { it.copy(submitting = false, createdWorkOrderId = order.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر حفظ الطلب. راجع البيانات وحاول مرة أخرى") }
            }
        }
    }
}
