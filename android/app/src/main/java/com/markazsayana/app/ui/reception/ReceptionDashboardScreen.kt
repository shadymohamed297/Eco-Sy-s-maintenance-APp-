package com.markazsayana.app.ui.reception

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.AppointmentDto
import com.markazsayana.app.data.remote.PendingApprovals
import com.markazsayana.app.data.remote.ReceptionCounts
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.ChangePasswordIconButton
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.LogoutIconButton
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.AccentOrange
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Divider
import com.markazsayana.app.ui.theme.Headline26Bold
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeChipBorder
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.OrangeTextMuted
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.dateLabel
import com.markazsayana.app.util.periodLabelLongCairo
import com.markazsayana.app.util.timeShortCairo
import java.time.Instant
import java.time.ZoneId

private val receptionNavItems = navItemsForRole("reception")

@Composable
fun ReceptionDashboardScreen(
    onNewRequest: () -> Unit,
    onNavTab: (String) -> Unit,
    onOpenApprovals: () -> Unit,
    viewModel: ReceptionDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = {
            BottomNavBar(
                items = receptionNavItems,
                selectedRoute = Routes.RECEPTION_DASHBOARD,
                activeColor = BrandNavy,
                onSelect = onNavTab,
            )
        },
    ) { padding ->
        when (val s = state) {
            is ReceptionDashboardUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is ReceptionDashboardUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is ReceptionDashboardUiState.Success -> ReceptionDashboardContent(
                modifier = Modifier.padding(padding),
                firstName = s.user?.name?.trim()?.split(" ")?.firstOrNull() ?: "",
                initials = s.user?.initials ?: "",
                branch = s.data.branch ?: s.user?.branch ?: "",
                counts = s.data.counts,
                pendingApprovals = s.data.pendingApprovals,
                appointments = s.data.todaysAppointments,
                onNewRequest = onNewRequest,
                onLogout = viewModel::logout,
                onOpenApprovals = onOpenApprovals,
            )
        }
    }
}

@Composable
private fun ReceptionDashboardContent(
    firstName: String,
    initials: String,
    branch: String,
    counts: ReceptionCounts,
    pendingApprovals: PendingApprovals,
    appointments: List<AppointmentDto>,
    onNewRequest: () -> Unit,
    onLogout: () -> Unit,
    onOpenApprovals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayLabel = remember { Instant.now().atZone(ZoneId.of("Africa/Cairo")).dateLabel() }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandNavy)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "فرع $branch · $todayLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardWhite.copy(alpha = 0.8f),
                    )
                    Text(
                        text = "أهلاً $firstName",
                        style = MaterialTheme.typography.titleLarge,
                        color = CardWhite,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangePasswordIconButton()
                LogoutIconButton(onConfirmLogout = onLogout)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardWhite.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = initials, color = CardWhite, style = MaterialTheme.typography.labelLarge)
                }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("طلب جديد", counts.new.arabic(), TextPrimary, Modifier.weight(1f))
                    StatTile("قيد التنفيذ", counts.inProgress.arabic(), BrandNavy, Modifier.weight(1f))
                    StatTile("متأخر", counts.late.arabic(), UrgentRed, Modifier.weight(1f))
                }
            }

            if (pendingApprovals.count > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OrangeChipBg, RoundedCornerShape(14.dp))
                            .border(1.dp, OrangeChipBorder, RoundedCornerShape(14.dp))
                            .clickable { onOpenApprovals() }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(UrgentRed),
                        )
                        Column {
                            Text(
                                text = "${pendingApprovals.count.arabic()} أجهزة تنتظر موافقة العميل على عرض السعر",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrangeTextDark,
                            )
                            Text(
                                text = "تجاوزت ${pendingApprovals.oldestHours.arabic()} ساعة في المخزن",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrangeTextMuted,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }

            item {
                Text(text = "مواعيد اليوم", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }

            items(appointments, key = { it.id }) { appt ->
                AppointmentRow(appt)
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryButton(text = "+ استلام طلب صيانة", onClick = onNewRequest, backgroundColor = AccentOrange)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier, padding = PaddingValues(12.dp)) {
        Text(text = value, style = Headline26Bold, color = valueColor)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextTertiary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AppointmentRow(appt: AppointmentDto) {
    val scheduled = appt.scheduledAt?.let { Instant.parse(it) }
    OutlinedCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(52.dp)
                    .padding(end = 12.dp),
            ) {
                Text(text = scheduled?.timeShortCairo() ?: "—", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(text = scheduled?.periodLabelLongCairo() ?: "", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Divider))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = "${appt.deviceType} — ${appt.issueDescription.take(28)}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(text = "${appt.branch ?: ""} · ${appt.customerName}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            if (appt.technicianName != null) {
                StatusChip(text = "مُعيَّن", background = BrandNavyChipBg, contentColor = BrandNavy)
            } else {
                StatusChip(text = "بلا فني", background = OrangeChipBg, contentColor = OrangeTextDark)
            }
        }
    }
}
