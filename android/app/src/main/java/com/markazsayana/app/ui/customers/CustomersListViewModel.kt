package com.markazsayana.app.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.CustomerSummaryDto
import com.markazsayana.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersListUiState(
    val loading: Boolean = true, val items: List<CustomerSummaryDto> = emptyList(),
    val search: String = "", val total: Int = 0, val nextOffset: Int = 0,
    val loadingMore: Boolean = false, val error: String? = null, val moreError: String? = null,
)
@HiltViewModel
class CustomersListViewModel @Inject constructor(private val repository: CustomerRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomersListUiState())
    val uiState: StateFlow<CustomersListUiState> = _uiState
    private var request: Job? = null
    private var appliedSearch: String? = null
    init { load() }
    fun load() {
        request?.cancel()
        appliedSearch = _uiState.value.search.trim().ifBlank { null }
        _uiState.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null) }
        request = viewModelScope.launch {
            try {
                val page = repository.listPage(appliedSearch)
                _uiState.update { it.copy(loading = false, items = page.items, total = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loading = false, error = "تعذّر تحميل العملاء. حاول مرة أخرى.") } }
        }
    }
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.nextOffset >= state.total) return
        _uiState.update { it.copy(loadingMore = true, moreError = null) }
        request = viewModelScope.launch {
            try {
                val page = repository.listPage(appliedSearch, state.nextOffset)
                _uiState.update { it.copy(loadingMore = false, items = (it.items + page.items).distinctBy { customer -> customer.id }, total = page.total, nextOffset = page.offset + page.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loadingMore = false, moreError = "تعذّر تحميل المزيد من العملاء.") } }
        }
    }
    fun setSearch(text: String) = _uiState.update { it.copy(search = text) }
    fun runSearch() = load()
}
