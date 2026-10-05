package com.markazsayana.app.ui.workorders

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.WorkOrderSummaryDto
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import java.time.Instant


@Composable
fun WorkOrdersListScreen(
    onNavTab: (String) -> Unit,
    role: String = "reception",
    onOpenWorkOrder: (Int) -> Unit,
    onNewRequest: () -> Unit,
    initialFilter: WorkOrderFilter? = null,
    viewModel: WorkOrdersListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    RefreshOnResume(viewModel::load)

    LaunchedEffect(initialFilter) {
        initialFilter?.let { viewModel.setInitialFilter(it) }
    }

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(navItemsForRole(role), Routes.WORK_ORDERS_LIST, BrandNavy, onNavTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "الطلبات", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    TextButton(onClick = onNewRequest) { Text("طلب جديد") }
                }
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text("بحث برقم الطلب أو اسم العميل…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::runSearch) { Text("بحث") }
                    TextButton(onClick = viewModel::load) { Text("تحديث") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())) {
                    WorkOrderFilter.entries.forEach { filter ->
                        val selected = filter == state.filter
                        Box(
                            modifier = Modifier
                                .background(if (selected) BrandNavy else CardWhite, RoundedCornerShape(9.dp))
                                .border(1.dp, if (selected) BrandNavy else BorderMedium, RoundedCornerShape(9.dp))
                                .clickable { viewModel.setFilter(filter) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(text = filter.label, style = MaterialTheme.typography.bodySmall, color = if (selected) CardWhite else TextPrimary)
                        }
                    }
                }
            }

            when {
                state.loading -> FullScreenLoading()
                state.error != null -> FullScreenError(state.error!!, onRetry = viewModel::load)
                state.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.search.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لا توجد طلبات في هذا التصنيف",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.id }) { wo ->
                        WorkOrderRow(wo, onClick = { onOpenWorkOrder(wo.id) })
                    }
                    item {
                        Text("${state.items.size.arabic()} من ${state.total.arabic()} طلب", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        state.moreError?.let { Text(it, color = UrgentRed) }
                        if (state.nextOffset < state.total) TextButton(enabled = !state.loadingMore, onClick = viewModel::loadMore) {
                            Text(if (state.loadingMore) "جارِ التحميل…" else "عرض المزيد من الطلبات")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkOrderRow(wo: WorkOrderSummaryDto, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "${wo.deviceType} — ${wo.issueDescription.take(30)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    if (wo.source == "online") {
                        StatusChip(text = "من الموقع", background = BrandNavyChipBg, contentColor = BrandNavy)
                    }
                }
                Text(text = "${wo.code} · ${wo.customerName}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            if (wo.technicianName != null) {
                StatusChip(text = wo.statusLabel, background = BrandNavyChipBg, contentColor = BrandNavy)
            } else {
                StatusChip(text = wo.statusLabel, background = OrangeChipBg, contentColor = OrangeTextDark)
            }
        }
    }
}
