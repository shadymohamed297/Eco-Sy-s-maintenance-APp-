package com.markazsayana.app.ui.workorders

import androidx.compose.foundation.background
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
import com.markazsayana.app.util.dateLabelCairo
import java.time.Instant

private val nav = navItemsForRole("reception")

@Composable
fun WorkOrdersListScreen(
    onNavTab: (String) -> Unit,
    initialFilter: WorkOrderFilter? = null,
    viewModel: WorkOrdersListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialFilter) {
        initialFilter?.let { viewModel.setInitialFilter(it) }
    }

    state.selected?.let { wo ->
        WorkOrderDetailDialog(
            wo = wo,
            actionInProgress = state.actionInProgress,
            actionError = state.actionError,
            onDismiss = { viewModel.select(null) },
            onApprove = { viewModel.approve(wo.id) },
            onReject = { reason -> viewModel.reject(wo.id, reason) },
        )
    }

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(nav, Routes.WORK_ORDERS_LIST, BrandNavy, onNavTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
                Text(text = "الطلبات", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
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
                        WorkOrderRow(wo, onClick = { viewModel.select(wo) })
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

@Composable
private fun WorkOrderDetailDialog(
    wo: WorkOrderSummaryDto,
    actionInProgress: Boolean,
    actionError: String?,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,
) {
    var showRejectReason by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wo.code) },
        text = {
            Column {
                DetailLine("الحالة", wo.statusLabel)
                DetailLine("الجهاز", wo.deviceType)
                DetailLine("العميل", wo.customerName)
                DetailLine("العطل", wo.issueDescription)
                DetailLine("الفرع", wo.branch ?: "—")
                DetailLine("الفني", wo.technicianName ?: "بلا فني")
                DetailLine("مصدر الطلب", if (wo.source == "online") "الموقع الإلكتروني" else "استقبال")
                DetailLine("تاريخ الإنشاء", runCatching { Instant.parse(wo.createdAt).dateLabelCairo() }.getOrDefault(wo.createdAt))
                wo.closedAt?.let { DetailLine("تاريخ الإغلاق", runCatching { Instant.parse(it).dateLabelCairo() }.getOrDefault(it)) }

                if (wo.status == "awaiting_approval") {
                    Text(
                        text = "بعد اتصالك بالعميل وتأكيد قراره بخصوص عرض السعر:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    )
                    if (actionInProgress) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandNavy)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onApprove) { Text("العميل وافق", color = BrandNavy) }
                            TextButton(onClick = { showRejectReason = true }) { Text("العميل رفض", color = UrgentRed) }
                        }
                    }
                    actionError?.let {
                        Text(text = it, color = UrgentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = BrandNavy) }
        },
    )

    if (showRejectReason) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRejectReason = false },
            title = { Text("سبب الرفض (اختياري)") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("مثال: السعر مرتفع") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { showRejectReason = false; onReject(reason.ifBlank { null }) }) {
                    Text("تأكيد الرفض", color = UrgentRed)
                }
            },
            dismissButton = { TextButton(onClick = { showRejectReason = false }) { Text("تراجع", color = TextTertiary) } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
