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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.TechnicianDto
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.timeWithPeriodCairo
import com.markazsayana.app.util.timeWithPeriod
import com.markazsayana.app.util.weekdayLabel
import java.time.Instant

@Composable
fun AssignTechnicianScreen(
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    viewModel: AssignTechnicianViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.confirmed) {
        if (state.confirmed) onConfirmed()
    }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null && state.workOrder == null -> FullScreenError(state.error!!, onRetry = {}, modifier = Modifier.padding(padding))
            else -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(CardWhite).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceScreen).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = "→", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
                    Column {
                        Text(text = "تعيين فني وموعد", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "أمر عمل ${state.workOrder?.code ?: ""} · ${state.workOrder?.device?.type ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "الموعد المقترح", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                state.slots.forEachIndexed { index, slot ->
                                    val selected = index == state.selectedSlotIndex
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CardShape)
                                            .background(if (selected) BrandNavy else CardWhite)
                                            .border(1.dp, if (selected) BrandNavy else BorderMedium, CardShape)
                                            .clickable { viewModel.selectSlot(index) }
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = slot.zoned.weekdayLabel(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) CardWhite.copy(alpha = 0.85f) else TextTertiary,
                                        )
                                        Text(
                                            text = slot.zoned.timeWithPeriod(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) CardWhite else TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "الفنيون المتاحون", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                            Text(text = "الأقرب للموقع", style = MaterialTheme.typography.labelMedium, color = BrandNavy)
                        }
                    }

                    items(state.technicians, key = { it.id }) { tech ->
                        TechnicianRow(
                            tech = tech,
                            selected = tech.id == state.selectedTechnicianId,
                            onSelect = { viewModel.selectTechnician(tech.id) },
                        )
                    }

                    item {
                        OutlinedCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(text = "إرسال رسالة تأكيد للعميل", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Switch(
                                    checked = state.notifyCustomer,
                                    onCheckedChange = viewModel::toggleNotify,
                                    colors = SwitchDefaults.colors(checkedTrackColor = BrandNavy),
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.background(CardWhite).padding(16.dp)) {
                    PrimaryButton(
                        text = if (state.submitting) "جارِ التأكيد…" else "تأكيد التعيين",
                        onClick = viewModel::confirm,
                        enabled = !state.submitting && state.selectedTechnicianId != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicianRow(tech: TechnicianDto, selected: Boolean, onSelect: () -> Unit) {
    val busy = tech.busyUntil != null
    OutlinedCard(
        borderColor = if (selected) BrandNavy else BorderLight,
        modifier = Modifier.clickable(enabled = !busy, onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (selected) BrandNavy else BrandNavyChipBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = tech.initials, color = if (selected) CardWhite else BrandNavy, style = MaterialTheme.typography.labelLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tech.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                if (busy) {
                    val busyLabel = runCatching { Instant.parse(tech.busyUntil).timeWithPeriodCairo() }.getOrDefault("")
                    Text(text = "مشغول حتى $busyLabel", style = MaterialTheme.typography.bodySmall, color = OrangeTextDark)
                } else {
                    val distance = tech.distanceKm?.let { "${it.toInt().arabic()} كم" } ?: ""
                    Text(
                        text = "${tech.tasksToday.arabic()} مهام اليوم · $distance · ${tech.specialty ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(BrandNavy),
                    contentAlignment = Alignment.Center,
                ) { Text(text = "✓", color = CardWhite, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
