package com.markazsayana.app.ui.reception

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianDto
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class TimeSlot(val instant: Instant) {
    val zoned: ZonedDateTime = instant.atZone(ZoneId.of("Africa/Cairo"))
}

data class AssignTechnicianUiState(
    val loading: Boolean = true,
    val workOrder: WorkOrderDto? = null,
    val technicians: List<TechnicianDto> = emptyList(),
    val slots: List<TimeSlot> = emptyList(),
    val selectedSlotIndex: Int = 0,
    val selectedTechnicianId: Int? = null,
    val notifyCustomer: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val confirmed: Boolean = false,
)

@HiltViewModel
class AssignTechnicianViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workOrderId: Int = checkNotNull(savedStateHandle.get<String>("workOrderId")).toInt()

    private val _uiState = MutableStateFlow(AssignTechnicianUiState())
    val uiState: StateFlow<AssignTechnicianUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val workOrder = repository.workOrder(workOrderId)
                val technicians = repository.availableTechnicians()
                _uiState.update {
                    it.copy(
                        loading = false,
                        workOrder = workOrder,
                        technicians = technicians,
                        slots = buildSuggestedSlots(),
                        selectedTechnicianId = technicians.firstOrNull()?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل بيانات التعيين") }
            }
        }
    }

    private fun buildSuggestedSlots(): List<TimeSlot> {
        val zone = ZoneId.of("Africa/Cairo")
        val now = ZonedDateTime.now(zone)
        val tomorrow = now.plusDays(1).withHour(10).withMinute(30).withSecond(0).withNano(0)
        val tomorrowAfternoon = tomorrow.withHour(14).withMinute(0)
        val dayAfter = now.plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0)
        return listOf(tomorrow, tomorrowAfternoon, dayAfter).map { TimeSlot(it.toInstant()) }
    }

    fun selectSlot(index: Int) = _uiState.update { it.copy(selectedSlotIndex = index) }
    fun selectTechnician(id: Int) = _uiState.update { it.copy(selectedTechnicianId = id) }
    fun toggleNotify(value: Boolean) = _uiState.update { it.copy(notifyCustomer = value) }

    fun confirm() {
        val s = _uiState.value
        val techId = s.selectedTechnicianId ?: return
        val slot = s.slots.getOrNull(s.selectedSlotIndex) ?: return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.assignTechnician(workOrderId, techId, slot.instant.toString())
                _uiState.update { it.copy(submitting = false, confirmed = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر تأكيد التعيين") }
            }
        }
    }
}
