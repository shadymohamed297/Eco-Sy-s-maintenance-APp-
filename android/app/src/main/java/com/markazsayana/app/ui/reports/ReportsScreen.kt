package com.markazsayana.app.ui.reports

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.ReportsResponse
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.workorders.RefreshOnResume
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Headline26Bold
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.egpCompact

private val nav = navItemsForRole("manager")
private val periodTabs = listOf("month" to "هذا الشهر", "quarter" to "الربع", "year" to "السنة")

@Composable
fun ReportsScreen(
    onNavTab: (String) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    RefreshOnResume(viewModel::refresh)
    val exporting by viewModel.exporting.collectAsState()
    val exportError by viewModel.exportError.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(exportedFile) {
        val file = exportedFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(Intent.createChooser(intent, "مشاركة التقرير")) }
        catch (e: android.content.ActivityNotFoundException) { viewModel.reportShareFailure() }
        viewModel.consumeExportedFile()
    }

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(nav, Routes.REPORTS, SurfaceDark, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is ReportsUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is ReportsUiState.Error -> FullScreenError(s.message, onRetry = { viewModel.load("month") }, modifier = Modifier.padding(padding))
            is ReportsUiState.Success -> Content(
                state = s,
                exporting = exporting,
                exportError = exportError,
                onPeriodChange = viewModel::load,
                onExport = viewModel::export,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun Content(
    state: ReportsUiState.Success,
    exporting: Boolean,
    exportError: String?,
    onPeriodChange: (String) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier,
) {
    val data = state.data
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp)) {
            Text(text = "التقارير", style = MaterialTheme.typography.titleLarge, color = CardWhite)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                periodTabs.forEach { (key, label) ->
                    val selected = key == state.period
                    Box(
                        modifier = Modifier
                            .background(if (selected) CardWhite else CardWhite.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                            .clickable { onPeriodChange(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodySmall, color = if (selected) SurfaceDark else CardWhite)
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { TotalsCard(data) }
            item { ExportRow(exporting = exporting, onExport = onExport) }
            exportError?.let { item { Text(it, color = UrgentRed, style = MaterialTheme.typography.bodySmall) } }
            item { BreakdownCard(title = "حسب الفني", rows = data.byTechnician.map { Triple(it.name, it.completedCount, it.revenue) }) }
            item { BreakdownCard(title = "حسب نوع الجهاز", rows = data.byDeviceType.map { Triple(it.deviceType, it.completedCount, it.revenue) }) }
        }
    }
}

@Composable
private fun TotalsCard(data: ReportsResponse) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
            Text(text = "أوامر مكتملة", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            Text(text = data.totals.completedCount.arabic(), style = Headline26Bold, color = TextPrimary, modifier = Modifier.padding(top = 5.dp))
        }
        OutlinedCard(modifier = Modifier.weight(1f), padding = PaddingValues(14.dp)) {
            Text(text = "الإيراد الكلي", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            Text(text = data.totals.totalRevenue.toDouble().egpCompact(), style = Headline26Bold, color = BrandNavy, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun ExportRow(exporting: Boolean, onExport: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.clickable(enabled = !exporting, onClick = onExport),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (exporting) "جارِ التجهيز…" else "تصدير التقرير CSV", style = MaterialTheme.typography.bodyMedium, color = BrandNavy)
            Text(text = "⬇", style = MaterialTheme.typography.bodyMedium, color = BrandNavy)
        }
    }
}

@Composable
private fun BreakdownCard(title: String, rows: List<Triple<String, Int, Double>>) {
    if (rows.isEmpty()) return
    OutlinedCard {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Column(modifier = Modifier.padding(top = 10.dp)) {
            rows.forEach { (label, count, revenue) ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "${count.arabic()} أمر", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Text(text = revenue.toDouble().egpCompact(), style = MaterialTheme.typography.bodySmall, color = BrandNavy)
                    }
                }
            }
        }
    }
}
