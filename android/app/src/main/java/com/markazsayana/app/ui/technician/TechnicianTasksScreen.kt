package com.markazsayana.app.ui.technician

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.BrandHeader
import com.markazsayana.app.ui.components.ChangePasswordIconButton
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.LogoutIconButton
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.AccentOrange
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.dateLabelCairo
import com.markazsayana.app.util.timeWithPeriodCairo
import java.time.Instant

private val technicianNavItems = navItemsForRole("technician")

@Composable
fun TechnicianTasksScreen(
    onOpenWorkOrder: (Int) -> Unit,
    onNavTab: (String) -> Unit,
    viewModel: TechnicianTasksViewModel = hiltViewModel(),
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
        bottomBar = { BottomNavBar(technicianNavItems, Routes.TECHNICIAN_TASKS, SurfaceDark, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is TechnicianTasksUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is TechnicianTasksUiState.Error -> FullScreenError(s.message, viewModel::load, Modifier.padding(padding))
            is TechnicianTasksUiState.Success -> TasksContent(s, onOpenWorkOrder, viewModel::logout, viewModel::load, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TasksContent(state: TechnicianTasksUiState.Success, onOpenWorkOrder: (Int) -> Unit, onLogout: () -> Unit, onRefresh: () -> Unit, modifier: Modifier) {
    val active = state.items.filter { it.status in listOf("in_progress", "paused") }
    val upcoming = state.items.filter { it.status == "assigned" }
    val waiting = state.items.filter { it.status == "awaiting_approval" }
    val completed = state.items.filter { it.status in listOf("completed", "closed") }
    val other = state.items.filter { it.status !in listOf("in_progress", "paused", "assigned", "awaiting_approval", "completed", "closed") }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                BrandHeader(compact = true, onDark = true, modifier = Modifier.weight(1f))
                ChangePasswordIconButton()
                LogoutIconButton(onConfirmLogout = onLogout)
            }
            Text("زيارات اليوم والمتابعة", style = MaterialTheme.typography.titleLarge, color = CardWhite, modifier = Modifier.padding(top = 12.dp))
            Text("${state.user?.name.orEmpty()} · ${state.progress.completed.arabic()} طلبات تم تسليمها", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
            val fraction = if (state.progress.total > 0) (state.progress.completed.toFloat() / state.progress.total).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CardWhite.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(AccentOrange))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton(onClick = onRefresh) { Text("تحديث الزيارات", color = BrandNavy) } }
            if (state.items.isEmpty()) item {
                OutlinedCard {
                    Text("لا توجد زيارات مسندة إليك اليوم.", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text("الزيارات الجديدة ستظهر هنا بعد إسنادها من الاستقبال.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (active.isNotEmpty()) item { SectionTitle("العمل الجاري") }
            items(active, key = { it.id }) { wo -> VisitCard(wo, { onOpenWorkOrder(wo.id) }, expanded = true) }
            if (upcoming.isNotEmpty()) item { SectionTitle("الزيارات المسندة") }
            items(upcoming, key = { it.id }) { wo -> VisitCard(wo, { onOpenWorkOrder(wo.id) }) }
            if (waiting.isNotEmpty()) item { SectionTitle("بانتظار موافقة العميل") }
            items(waiting, key = { it.id }) { wo -> VisitCard(wo, { onOpenWorkOrder(wo.id) }) }
            if (completed.isNotEmpty()) item { SectionTitle("الطلبات المكتملة والفواتير") }
            items(completed, key = { it.id }) { wo -> VisitCard(wo, { onOpenWorkOrder(wo.id) }) }
            if (other.isNotEmpty()) item { SectionTitle("طلبات أخرى") }
            items(other, key = { it.id }) { wo -> VisitCard(wo, { onOpenWorkOrder(wo.id) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, color = TextTertiary, style = MaterialTheme.typography.labelLarge)

@Composable
private fun VisitCard(wo: WorkOrderDto, onOpen: () -> Unit, expanded: Boolean = false) {
    val context = LocalContext.current
    val scheduled = wo.scheduledAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val closed = wo.status == "closed"
    OutlinedCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(wo.code, color = BrandNavy, style = MaterialTheme.typography.labelLarge)
            StatusChip(wo.statusLabel, if (closed) SuccessGreen.copy(alpha = 0.12f) else OrangeChipBg, if (closed) SuccessGreen else OrangeTextDark)
        }
        Text("${wo.device.type} · ${wo.customer.name}", color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(wo.issueDescription, color = TextTertiary, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) 4 else 2)
        if (scheduled != null) Text("${scheduled.dateLabelCairo()} · ${scheduled.timeWithPeriodCairo()}", color = TextTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        if (!wo.customer.address.isNullOrBlank()) Text(wo.customer.address, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            PrimaryButton("فتح الزيارة", onOpen, backgroundColor = SurfaceDark, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SecondaryButton("اتصال بالعميل", onClick = {
                    openTaskIntent(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", wo.customer.phone, null)))
                }, modifier = Modifier.weight(1f))
                if (!wo.customer.address.isNullOrBlank()) SecondaryButton("عنوان الزيارة", onClick = {
                    openTaskIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(wo.customer.address)}")))
                }, modifier = Modifier.weight(1f))
            }
        } else if (closed) Text("افتح الطلب لعرض الفاتورة والتوقيع", color = SuccessGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun openTaskIntent(context: android.content.Context, intent: Intent) {
    try { context.startActivity(intent) }
    catch (e: ActivityNotFoundException) { Toast.makeText(context, "لا يوجد تطبيق متاح لفتح هذا الإجراء.", Toast.LENGTH_SHORT).show() }
}
