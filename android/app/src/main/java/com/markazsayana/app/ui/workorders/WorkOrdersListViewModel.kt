package com.markazsayana.app.ui.workorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.WorkOrderSummaryDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WorkOrderFilter(val label: String, val statusQuery: String?) {
    ALL("الكل", null),
    OPEN("مفتوحة", "new,assigned,in_progress,paused,awaiting_approval"),
    AWAITING_APPROVAL("بانتظار الموافقة", "awaiting_approval"),
    CLOSED("مغلقة", "closed"),
}

data class WorkOrdersListUiState(
    val loading: Boolean = true,
    val items: List<WorkOrderSummaryDto> = emptyList(),
    val filter: WorkOrderFilter = WorkOrderFilter.OPEN,
    val search: String = "",
    val error: String? = null,
    val selected: WorkOrderSummaryDto? = null,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class WorkOrdersListViewModel @Inject constructor(
    private val repository: WorkOrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkOrdersListUiState())
    val uiState: StateFlow<WorkOrdersListUiState> = _uiState

    init {
        load()
    }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.listWorkOrders(status = s.filter.statusQuery, search = s.search.ifBlank { null })
                _uiState.update { it.copy(loading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل الطلبات") }
            }
        }
    }

    fun setFilter(filter: WorkOrderFilter) {
        _uiState.update { it.copy(filter = filter) }
        load()
    }

    fun setSearch(text: String) {
        _uiState.update { it.copy(search = text) }
    }

    fun runSearch() = load()

    fun select(item: WorkOrderSummaryDto?) = _uiState.update { it.copy(selected = item, actionError = null) }

    fun setInitialFilter(filter: WorkOrderFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        load()
    }

    fun approve(id: Int) = runAction {
        repository.approve(id)
    }

    fun reject(id: Int, reason: String?) = runAction {
        repository.reject(id, reason)
    }

    private fun runAction(action: suspend () -> Unit) {
        _uiState.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            try {
                action()
                _uiState.update { it.copy(actionInProgress = false, selected = null) }
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(actionInProgress = false, actionError = "تعذّر تنفيذ العملية، حاول مرة أخرى") }
            }
        }
    }
}
