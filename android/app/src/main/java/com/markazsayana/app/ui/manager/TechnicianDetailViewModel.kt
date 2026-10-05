package com.markazsayana.app.ui.manager

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.TechnicianRosterDto
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.remote.WorkOrderSummaryDto
import com.markazsayana.app.data.repository.TechnicianRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TechnicianDetailUiState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Africa/Cairo")),
    val loading: Boolean = true, val technician: TechnicianRosterDto? = null,
    val itinerary: List<WorkOrderDto> = emptyList(), val history: List<WorkOrderSummaryDto> = emptyList(),
    val historyTotal: Int = 0, val nextOffset: Int = 0, val loadingMore: Boolean = false,
    val error: String? = null, val moreError: String? = null,
)
@HiltViewModel
class TechnicianDetailViewModel @Inject constructor(private val repository: TechnicianRepository, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val id = checkNotNull(savedStateHandle.get<String>("technicianId")).toInt()
    private val _uiState = MutableStateFlow(TechnicianDetailUiState())
    val uiState: StateFlow<TechnicianDetailUiState> = _uiState
    private var request: Job? = null
    private var historyRequest: Job? = null
    init { load() }
    fun load() {
        request?.cancel(); historyRequest?.cancel()
        val date = _uiState.value.date.toString()
        _uiState.update { it.copy(loading = true, error = null, moreError = null, loadingMore = false) }
        request = viewModelScope.launch {
            try {
                val scheduleRequest = async { repository.itinerary(id, date) }
                val history = async { repository.history(id) }
                val schedule = scheduleRequest.await()
                val page = history.await()
                _uiState.update { it.copy(loading = false, technician = schedule.technician, itinerary = schedule.items, history = page.items, historyTotal = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loading = false, error = "تعذّر تحميل بيانات الفني. حاول مرة أخرى.") } }
        }
    }
    fun selectDate(date: LocalDate) { _uiState.update { it.copy(date = date) }; load() }
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.nextOffset >= state.historyTotal) return
        _uiState.update { it.copy(loadingMore = true, moreError = null) }
        historyRequest = viewModelScope.launch {
            try {
                val page = repository.history(id, state.nextOffset)
                _uiState.update { it.copy(loadingMore = false, history = (it.history + page.items).distinctBy { order -> order.id }, historyTotal = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loadingMore = false, moreError = "تعذّر تحميل المزيد من سجل الفني.") } }
        }
    }
}
