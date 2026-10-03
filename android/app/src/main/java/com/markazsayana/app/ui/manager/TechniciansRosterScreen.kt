package com.markazsayana.app.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.TechnicianRosterDto
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.arabicDecimal

private val nav = navItemsForRole("manager")

@Composable
fun TechniciansRosterScreen(
    onNavTab: (String) -> Unit,
    viewModel: TechniciansRosterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(nav, Routes.TECHNICIANS_ROSTER, SurfaceDark, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is TechniciansRosterUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is TechniciansRosterUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is TechniciansRosterUiState.Success -> Content(s.technicians, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Content(technicians: List<TechnicianRosterDto>, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp)) {
            Text(text = "الفنيون", style = MaterialTheme.typography.titleLarge, color = CardWhite)
            Text(text = "${technicians.size.arabic()} فني", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(technicians, key = { it.id }) { tech -> TechnicianRow(tech) }
        }
    }
}

@Composable
private fun TechnicianRow(tech: TechnicianRosterDto) {
    OutlinedCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(BrandNavyChipBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = tech.initials, color = BrandNavy, style = MaterialTheme.typography.labelLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tech.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(
                    text = "${tech.specialty ?: ""} · ${tech.branch ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    MiniStat("اليوم", tech.tasksToday.arabic())
                    MiniStat("هذا الشهر", tech.completedThisMonth.arabic())
                    tech.avgRating?.let { MiniStat("التقييم", "${it.arabicDecimal(1)}★") }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}
