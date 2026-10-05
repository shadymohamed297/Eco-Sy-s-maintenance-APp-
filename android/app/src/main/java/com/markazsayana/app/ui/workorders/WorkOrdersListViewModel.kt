package com.markazsayana.app.ui.workorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.WorkOrderSummaryDto
import com.markazsayana.app.data.repository.WorkOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WorkOrderFilter(val label: String, val statusQuery: String?) {
    ALL("الكل", null), OPEN("مفتوحة", "new,assigned,in_progress,paused,awaiting_approval"),
    AWAITING_APPROVAL("بانتظار الموافقة", "awaiting_approval"), CLOSED("مغلقة", "closed"),
}
data class WorkOrdersListUiState(
    val loading: Boolean = true, val items: List<WorkOrderSummaryDto> = emptyList(),
    val filter: WorkOrderFilter = WorkOrderFilter.OPEN, val search: String = "",
    val total: Int = 0, val nextOffset: Int = 0, val loadingMore: Boolean = false,
    val error: String? = null, val moreError: String? = null,
)
@HiltViewModel
class WorkOrdersListViewModel @Inject constructor(private val repository: WorkOrderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkOrdersListUiState())
    val uiState: StateFlow<WorkOrdersListUiState> = _uiState
    private var request: Job? = null
    private var appliedSearch: String? = null
    private var appliedFilter: String? = null
    init { load() }
    fun load() {
        request?.cancel()
        appliedSearch = _uiState.value.search.trim().ifBlank { null }
        appliedFilter = _uiState.value.filter.statusQuery
        _uiState.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null) }
        request = viewModelScope.launch {
            try {
                val page = repository.listWorkOrdersPage(appliedFilter, appliedSearch)
                _uiState.update { it.copy(loading = false, items = page.items, total = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loading = false, error = "تعذّر تحميل الطلبات. حاول مرة أخرى.") } }
        }
    }
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.nextOffset >= state.total) return
        _uiState.update { it.copy(loadingMore = true, moreError = null) }
        request = viewModelScope.launch {
            try {
                val page = repository.listWorkOrdersPage(appliedFilter, appliedSearch, state.nextOffset)
                _uiState.update { it.copy(loadingMore = false, items = (it.items + page.items).distinctBy { order -> order.id }, total = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loadingMore = false, moreError = "تعذّر تحميل المزيد من الطلبات.") } }
        }
    }
    fun setFilter(filter: WorkOrderFilter) { _uiState.update { it.copy(filter = filter) }; load() }
    fun setSearch(text: String) = _uiState.update { it.copy(search = text) }
    fun runSearch() = load()
    fun setInitialFilter(filter: WorkOrderFilter) { if (_uiState.value.filter != filter) setFilter(filter) }
}
