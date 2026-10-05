package com.markazsayana.app.ui.manager

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.ui.workorders.DetailField
import com.markazsayana.app.ui.workorders.DetailHeader
import com.markazsayana.app.ui.workorders.SectionTitle
import com.markazsayana.app.ui.workorders.RefreshOnResume
import com.markazsayana.app.ui.workorders.displayDateTime
import com.markazsayana.app.util.arabic
import java.time.LocalDate

@Composable
fun TechnicianDetailScreen(onBack: () -> Unit, onOpenWorkOrder: (Int) -> Unit, viewModel: TechnicianDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    RefreshOnResume(viewModel::load)
    val context = LocalContext.current
    Scaffold(containerColor = SurfaceScreen, topBar = { DetailHeader("متابعة الفني", onBack, viewModel::load) }) { padding ->
        val technician = state.technician
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            technician == null -> FullScreenError(state.error ?: "تعذّر تحميل الفني", viewModel::load, Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedCard {
                        Text(technician.name, style = MaterialTheme.typography.titleLarge, color = BrandNavy)
                        DetailField("التخصص", technician.specialty)
                        DetailField("الهاتف", technician.phone)
                        DetailField("إنجاز هذا الشهر", "${technician.completedThisMonth.arabic()} طلب")
                        Text("افتح الزيارة لمراجعة العمل والصور والفاتورة.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
                item {
                    SectionTitle("خط سير الزيارات")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectDate(state.date.minusDays(1)) }) { Text("اليوم السابق") }
                        TextButton(onClick = {
                            DatePickerDialog(context, { _, year, month, day -> viewModel.selectDate(LocalDate.of(year, month + 1, day)) }, state.date.year, state.date.monthValue - 1, state.date.dayOfMonth).show()
                        }) { Text(state.date.toString()) }
                        TextButton(onClick = { viewModel.selectDate(state.date.plusDays(1)) }) { Text("اليوم التالي") }
                    }
                    if (state.itinerary.isEmpty()) Text("لا توجد زيارات مجدولة لهذا اليوم.", color = TextTertiary)
                }
                items(state.itinerary, key = { "visit-${it.id}" }) { order ->
                    OutlinedCard(modifier = Modifier.clickable { onOpenWorkOrder(order.id) }) {
                        Text("${order.code} · ${order.statusLabel}", style = MaterialTheme.typography.titleSmall, color = BrandNavy)
                        DetailField("موعد الزيارة", order.scheduledAt?.displayDateTime())
                        DetailField("العميل", order.customer.name)
                        DetailField("عنوان الزيارة", order.customer.address ?: "لم يُسجّل عنوان للزيارة")
                        DetailField("الجهاز والعطل", "${order.device.type} · ${order.issueDescription}")
                        DetailField("بداية العمل", order.startedAt?.displayDateTime())
                        DetailField("التسليم", order.closedAt?.displayDateTime())
                    }
                }
                item {
                    SectionTitle("سجل عمل الفني")
                    if (state.history.isEmpty()) Text("لا توجد طلبات في سجل هذا الفني بعد.", color = TextTertiary)
                }
                items(state.history, key = { "history-${it.id}" }) { order ->
                    OutlinedCard(modifier = Modifier.clickable { onOpenWorkOrder(order.id) }) {
                        Text("${order.code} · ${order.statusLabel}", style = MaterialTheme.typography.titleSmall, color = BrandNavy)
                        Text("${order.customerName} · ${order.deviceType}", color = TextPrimary)
                        Text(order.issueDescription, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        DetailField("الموعد", order.scheduledAt?.displayDateTime())
                        DetailField("تاريخ التسليم", order.closedAt?.displayDateTime())
                    }
                }
                item {
                    state.moreError?.let { Text(it, color = UrgentRed) }
                    if (state.nextOffset < state.historyTotal) TextButton(enabled = !state.loadingMore, onClick = viewModel::loadMore) {
                        Text(if (state.loadingMore) "جارِ التحميل…" else "عرض المزيد من سجل الفني")
                    }
                    Text("${state.history.size.arabic()} من ${state.historyTotal.arabic()} طلب", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    state.error?.let { Text(it, color = UrgentRed) }
                }
            }
        }
    }
}
