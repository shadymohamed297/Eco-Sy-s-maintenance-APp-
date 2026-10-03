package com.markazsayana.app.ui.technician

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.components.ChangePasswordIconButton
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.LogoutIconButton
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.AccentOrange
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Divider
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.timeShortCairo
import java.time.Instant

private val technicianNavItems = navItemsForRole("technician")

@Composable
fun TechnicianTasksScreen(
    onOpenWorkOrder: (Int) -> Unit,
    onNavTab: (String) -> Unit,
    viewModel: TechnicianTasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = {
            BottomNavBar(items = technicianNavItems, selectedRoute = Routes.TECHNICIAN_TASKS, activeColor = SurfaceDark, onSelect = onNavTab)
        },
    ) { padding ->
        when (val s = state) {
            is TechnicianTasksUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is TechnicianTasksUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is TechnicianTasksUiState.Success -> TasksContent(s, onOpenWorkOrder, viewModel::logout, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TasksContent(state: TechnicianTasksUiState.Success, onOpenWorkOrder: (Int) -> Unit, onLogout: () -> Unit, modifier: Modifier) {
    val current = state.items.firstOrNull { it.status == "in_progress" || it.status == "paused" }
    val upcoming = state.items.filter { it.status == "assigned" }
    val completed = state.items.filter { it.status == "closed" }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "${state.user?.name ?: ""} · فني أجهزة منزلية", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
                    Text(text = "مهام اليوم", style = MaterialTheme.typography.titleLarge, color = CardWhite, modifier = Modifier.padding(top = 3.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChangePasswordIconButton()
                    LogoutIconButton(onConfirmLogout = onLogout)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CardWhite.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${state.progress.completed.arabic()} / ${state.progress.total.arabic()}", style = MaterialTheme.typography.titleMedium, color = CardWhite)
                            Text(text = "مكتملة", style = MaterialTheme.typography.labelSmall, color = TextOnDarkMuted)
                        }
                    }
                }
            }
            val fraction = if (state.progress.total > 0) state.progress.completed.toFloat() / state.progress.total else 0f
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CardWhite.copy(alpha = 0.15f))) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AccentOrange))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current != null) {
                item { CurrentVisitCard(current, onClick = { onOpenWorkOrder(current.id) }) }
                item { Text(text = "التالي", style = MaterialTheme.typography.labelLarge, color = TextTertiary, modifier = Modifier.padding(top = 4.dp)) }
            }
            items(upcoming, key = { it.id }) { wo -> UpcomingRow(wo, onClick = { onOpenWorkOrder(wo.id) }) }
            items(completed, key = { it.id }) { wo -> CompletedRow(wo) }
        }
    }
}

@Composable
private fun CurrentVisitCard(wo: WorkOrderDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite, RoundedCornerShape(16.dp))
            .padding(top = 4.dp)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            StatusChip(text = "الزيارة الحالية", background = OrangeChipBg, contentColor = OrangeTextDark)
            val scheduled = wo.scheduledAt?.let { Instant.parse(it) }
            Text(text = scheduled?.timeShortCairo() ?: "", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Text(
            text = "${wo.device.type} — ${wo.issueDescription.take(30)}",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "${wo.customer.name} · ${wo.customer.address ?: ""}\n${wo.code} · ${if (wo.device.underWarranty) "داخل الضمان" else "خارج الضمان"}",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.padding(top = 5.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
            PrimaryButton(text = "بدء العمل", onClick = onClick, backgroundColor = SurfaceDark, modifier = Modifier.weight(1f))
            SecondaryButton(text = "اتصال", onClick = {}, modifier = Modifier.weight(0.55f))
            SecondaryButton(text = "الموقع", onClick = {}, modifier = Modifier.weight(0.55f))
        }
    }
}

@Composable
private fun UpcomingRow(wo: WorkOrderDto, onClick: () -> Unit) {
    val scheduled = wo.scheduledAt?.let { Instant.parse(it) }
    OutlinedCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = scheduled?.timeShortCairo() ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = BrandNavy,
                modifier = Modifier.width(48.dp),
            )
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Divider))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = "${wo.device.type} — ${wo.issueDescription.take(24)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = wo.customer.name, style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun CompletedRow(wo: WorkOrderDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceScreen, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "✓", style = MaterialTheme.typography.titleSmall, color = SuccessGreen, modifier = Modifier.width(48.dp))
        Box(modifier = Modifier.width(1.dp).height(36.dp).background(Divider))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "${wo.device.type} — ${wo.issueDescription.take(24)}",
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                color = TextTertiary,
            )
        }
    }
}
