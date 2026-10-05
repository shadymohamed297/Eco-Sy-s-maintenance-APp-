package com.markazsayana.app.ui.reception

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianDto
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import com.markazsayana.app.ui.components.westernDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
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
    val appointmentDate: String = "",
    val appointmentTime: String = "",
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

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val workOrder = repository.workOrder(workOrderId)
                val technicians = repository.availableTechnicians()
                val slots = buildSuggestedSlots()
                val existingAppointment = workOrder.scheduledAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?.takeIf { it.isAfter(Instant.now()) }?.atZone(ZoneId.of("Africa/Cairo"))
                _uiState.update {
                    it.copy(
                        loading = false,
                        workOrder = workOrder,
                        technicians = technicians,
                        slots = slots,
                        selectedSlotIndex = if (existingAppointment == null) 0 else -1,
                        appointmentDate = (existingAppointment ?: slots.first().zoned).toLocalDate().toString(),
                        appointmentTime = (existingAppointment ?: slots.first().zoned).toLocalTime().withSecond(0).withNano(0).toString(),
                        selectedTechnicianId = workOrder.technician?.id,
                    )
                }
            } catch (e: CancellationException) { throw e
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

    fun selectSlot(index: Int) = _uiState.update {
        val slot = it.slots.getOrNull(index)
        if (it.submitting || slot == null) it else it.copy(selectedSlotIndex = index, appointmentDate = slot.zoned.toLocalDate().toString(), appointmentTime = slot.zoned.toLocalTime().toString(), error = null)
    }
    fun selectTechnician(id: Int) = _uiState.update { if (it.submitting || it.technicians.none { tech -> tech.id == id }) it else it.copy(selectedTechnicianId = id, error = null) }
    fun changeDate(date: String) = _uiState.update { if (it.submitting) it else it.copy(appointmentDate = westernDigits(date), selectedSlotIndex = -1, error = null) }
    fun changeTime(time: String) = _uiState.update { if (it.submitting) it else it.copy(appointmentTime = westernDigits(time), selectedSlotIndex = -1, error = null) }

    fun confirm() {
        val s = _uiState.value
        if (s.submitting || s.confirmed) return
        val techId = s.selectedTechnicianId ?: return
        val appointment = runCatching {
            LocalDate.parse(s.appointmentDate.trim()).atTime(LocalTime.parse(s.appointmentTime.trim())).atZone(ZoneId.of("Africa/Cairo")).toInstant()
        }.getOrNull()
        if (appointment == null || !appointment.isAfter(Instant.now())) {
            _uiState.update { it.copy(error = "اختر تاريخًا ووقتًا صحيحين في المستقبل") }
            return
        }
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                repository.assignTechnician(workOrderId, techId, appointment.toString())
                _uiState.update { it.copy(submitting = false, confirmed = true) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = "تعذّر تأكيد التعيين") }
            }
        }
    }
}
