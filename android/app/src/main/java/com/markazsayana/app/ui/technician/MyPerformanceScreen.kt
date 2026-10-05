package com.markazsayana.app.ui.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.markazsayana.app.data.remote.PerformanceWorkOrderDto
import com.markazsayana.app.data.remote.TechnicianPerformanceResponse
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Headline26Bold
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.arabicDecimal
import com.markazsayana.app.util.dateLabelCairo
import java.time.Instant

private val nav = navItemsForRole("technician")

@Composable
fun MyPerformanceScreen(
    onNavTab: (String) -> Unit,
    onOpenWorkOrder: (Int) -> Unit = {},
    viewModel: MyPerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.load() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(nav, Routes.MY_PERFORMANCE, SurfaceDark, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is MyPerformanceUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is MyPerformanceUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is MyPerformanceUiState.Success -> Content(s.data, onOpenWorkOrder, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Content(data: TechnicianPerformanceResponse, onOpenWorkOrder: (Int) -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp)) {
            Text(text = "شغلي وتقارير الأداء", style = MaterialTheme.typography.titleLarge, color = CardWhite)
            Text(text = "الطلبات التي تم تسليمها خلال آخر ٩٠ يوم", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("تم تسليمها هذا الشهر", data.completedThisMonth.arabic(), TextPrimary, Modifier.weight(1f))
                    StatTile("تقييمات العملاء", data.avgRating?.let { "${it.arabicDecimal(1)} / ٥" } ?: "—", SuccessGreen, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("متوسط مدة الطلب", data.avgRepairDays?.let { "${it.arabicDecimal(1)} يوم" } ?: "—", TextPrimary, Modifier.weight(1f))
                    StatTile(
                        "طلبات متابعة الإصلاح", "${data.reopenRatePct.arabicDecimal(1)}٪",
                        if (data.reopenRatePct > 5) UrgentRed else TextPrimary, Modifier.weight(1f),
                    )
                }
            }
            item {
                StatTile("إجمالي الطلبات خلال ٩٠ يوم", data.completedLast90Days.arabic(), TextPrimary, Modifier.fillMaxWidth())
            }
            item {
                Text(text = "آخر الطلبات التي تم تسليمها", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            if (data.recentWorkOrders.isEmpty()) item {
                Text("لم تُسجل طلبات تم تسليمها بعد. سيظهر سجل شغلك هنا بعد أول تسليم.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            items(data.recentWorkOrders, key = { it.id }) { wo -> RecentRow(wo, { onOpenWorkOrder(wo.id) }) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    OutlinedCard(modifier = modifier, padding = PaddingValues(14.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(text = value, style = Headline26Bold, color = valueColor, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun RecentRow(wo: PerformanceWorkOrderDto, onOpen: () -> Unit) {
    OutlinedCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = "${wo.deviceType} — ${wo.customerName}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = listOfNotNull(wo.code, wo.closedAt?.let { runCatching { Instant.parse(it).dateLabelCairo() }.getOrNull() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            if (wo.customerRating != null) {
                Text(text = "★ ${wo.customerRating.arabic()}", style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
            }
        }
    }
}
