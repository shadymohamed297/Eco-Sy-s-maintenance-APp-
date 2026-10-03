package com.markazsayana.app.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.FaultDistributionEntry
import com.markazsayana.app.data.remote.ManagerKpis
import com.markazsayana.app.data.remote.WeeklyRevenuePoint
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.components.ChangePasswordIconButton
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.LogoutIconButton
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.AccentOrange
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Headline26Bold
import com.markazsayana.app.ui.theme.LargeCardShape
import com.markazsayana.app.ui.theme.OrangeAmber
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyDark
import com.markazsayana.app.ui.theme.RevenueBarLight
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.currentMonthYearLabelCairo
import com.markazsayana.app.util.egpCompact
import java.time.Instant
import java.time.ZoneId

private val managerNavItems = navItemsForRole("manager")

private val periodTabs = listOf("month" to "هذا الشهر", "quarter" to "الربع", "year" to "السنة")
private val faultColors = listOf(BrandNavy, AccentOrange, BrandNavyDark, OrangeAmber, UrgentRed)

@Composable
fun ManagerDashboardScreen(
    onNavTab: (String) -> Unit,
    onManageEmployees: () -> Unit,
    viewModel: ManagerDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(managerNavItems, Routes.MANAGER_DASHBOARD, SurfaceDark, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is ManagerDashboardUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is ManagerDashboardUiState.Error -> FullScreenError(s.message, onRetry = { viewModel.load("month") }, modifier = Modifier.padding(padding))
            is ManagerDashboardUiState.Success -> DashboardContent(
                s,
                onPeriodChange = viewModel::load,
                onLogout = viewModel::logout,
                onManageEmployees = onManageEmployees,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: ManagerDashboardUiState.Success,
    onPeriodChange: (String) -> Unit,
    onLogout: () -> Unit,
    onManageEmployees: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "لوحة الأداء · ${currentMonthYearLabelCairo()}", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
                    Text(text = "الهندسة المتحدة", style = MaterialTheme.typography.titleLarge, color = CardWhite, modifier = Modifier.padding(top = 3.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CardWhite.copy(alpha = 0.18f))
                            .clickable { onManageEmployees() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "👥", style = MaterialTheme.typography.titleMedium)
                    }
                    ChangePasswordIconButton()
                    LogoutIconButton(onConfirmLogout = onLogout)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                periodTabs.forEach { (key, label) ->
                    val selected = key == state.period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) CardWhite else CardWhite.copy(alpha = 0.12f))
                            .clickable { onPeriodChange(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodySmall, color = if (selected) SurfaceDark else CardWhite)
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { KpiGrid(state.data.kpis) }
            item { WeeklyRevenueCard(state.data.weeklyRevenue) }
            item { FaultDistributionCard(state.data.faultDistribution) }
        }
    }
}

@Composable
private fun KpiGrid(kpis: ManagerKpis) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KpiTile(
                "أوامر مكتملة", kpis.completedCount.arabic(),
                delta = kpis.completedDeltaPct?.let { "${if (it >= 0) "▲" else "▼"} ${kotlin.math.abs(it).arabicPct()} عن الفترة السابقة" },
                deltaColor = SuccessGreen,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                "متوسط زمن الإصلاح", kpis.avgRepairDays?.let { "${it.arabicOneDecimal()} يوم" } ?: "—",
                delta = null, deltaColor = SuccessGreen, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KpiTile(
                "إعادة فتح (تكرار عطل)", "${kpis.reopenRatePct.arabicOneDecimal()}٪",
                delta = if (kpis.reopenRatePct > 5) "يحتاج مراجعة" else null,
                deltaColor = OrangeTextDark, valueColor = if (kpis.reopenRatePct > 5) UrgentRed else TextPrimary,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                "رضا العملاء", kpis.avgRating?.let { "${it.arabicOneDecimal()} / ٥" } ?: "—",
                delta = "${kpis.ratingCount.arabic()} تقييماً", deltaColor = TextTertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, delta: String?, deltaColor: Color, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    OutlinedCard(modifier = modifier, padding = PaddingValues(14.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(text = value, style = Headline26Bold, color = valueColor, modifier = Modifier.padding(top = 5.dp))
        delta?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = deltaColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun WeeklyRevenueCard(points: List<WeeklyRevenuePoint>) {
    val maxRevenue = (points.maxOfOrNull { it.revenue } ?: 1).coerceAtLeast(1)
    val total = points.sumOf { it.revenue }
    OutlinedCard(shape = LargeCardShape, padding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "الإيراد الأسبوعي", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = total.toDouble().egpCompact(), style = MaterialTheme.typography.bodyMedium, color = BrandNavy)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(78.dp).padding(top = 12.dp),
        ) {
            points.forEachIndexed { index, point ->
                val fraction = point.revenue.toFloat() / maxRevenue
                val isLast = index == points.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((78 * fraction).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (isLast) BrandNavy else RevenueBarLight),
                        )
                    }
                    Text(text = "أ${(index + 1).arabic()}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun FaultDistributionCard(entries: List<FaultDistributionEntry>) {
    OutlinedCard(shape = LargeCardShape, padding = PaddingValues(16.dp)) {
        Text(text = "توزيع الأعطال", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = deviceLabel(entry.deviceType), style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.width(92.dp))
                    Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(com.markazsayana.app.ui.theme.Divider)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (entry.pct / 100.0).toFloat().coerceIn(0f, 1f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(faultColors[index % faultColors.size]),
                        )
                    }
                    Text(text = "${entry.pct.arabicOneDecimal()}٪", style = MaterialTheme.typography.labelMedium, color = TextPrimary, modifier = Modifier.width(40.dp))
                }
            }
        }
    }
}

private fun deviceLabel(type: String): String = when (type) {
    "مكيف" -> "مكيفات"
    "غسالة" -> "غسالات"
    "ثلاجة" -> "ثلاجات"
    "سخان" -> "سخانات"
    "بوتاجاز" -> "بوتاجاز"
    else -> type
}

private fun Double.arabicOneDecimal(): String = "%.1f".format(java.util.Locale.US, this).replace(".", "٫").let { arabicizeDigits(it) }
private fun Double.arabicPct(): String = "${arabicOneDecimal()}٪"

private fun arabicizeDigits(s: String): String {
    val digits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return buildString { for (c in s) append(if (c in '0'..'9') digits[c - '0'] else c) }
}
