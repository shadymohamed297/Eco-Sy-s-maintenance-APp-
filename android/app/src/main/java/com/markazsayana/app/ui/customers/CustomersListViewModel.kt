package com.markazsayana.app.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markazsayana.app.data.remote.CustomerSummaryDto
import com.markazsayana.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersListUiState(
    val loading: Boolean = true,
    val items: List<CustomerSummaryDto> = emptyList(),
    val search: String = "",
    val error: String? = null,
)

@HiltViewModel
class CustomersListViewModel @Inject constructor(
    private val repository: CustomerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersListUiState())
    val uiState: StateFlow<CustomersListUiState> = _uiState

    init {
        load()
    }

    fun load() {
        val search = _uiState.value.search
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.list(search.ifBlank { null })
                _uiState.update { it.copy(loading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "تعذّر تحميل العملاء") }
            }
        }
    }

    fun setSearch(text: String) = _uiState.update { it.copy(search = text) }
    fun runSearch() = load()
}
