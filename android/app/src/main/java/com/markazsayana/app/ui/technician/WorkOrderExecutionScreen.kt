package com.markazsayana.app.ui.technician

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.ChecklistItemDto
import com.markazsayana.app.data.remote.PartUsedDto
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.egp

@Composable
fun WorkOrderExecutionScreen(
    onBack: () -> Unit,
    onFinish: (workOrderId: Int) -> Unit,
    viewModel: WorkOrderExecutionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showQuoteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.quoteSent) {
        if (state.quoteSent) onBack()
    }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.workOrder == null -> FullScreenError(state.error ?: "خطأ", onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> {
                val wo = state.workOrder!!
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(CardWhite.copy(alpha = 0.12f)).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { Text(text = "→", color = CardWhite, style = MaterialTheme.typography.titleMedium) }
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(text = "${wo.code} · ${wo.statusLabel}", style = MaterialTheme.typography.titleSmall, color = CardWhite)
                            Text(text = "الوقت المنقضي ${state.elapsed}", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(com.markazsayana.app.ui.theme.AccentOrange)
                                .clickable(onClick = viewModel::togglePause)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = if (wo.status == "paused") "استئناف" else "إيقاف مؤقت", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "قائمة الفحص — ${wo.device.type}", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardWhite, CardShape)
                                        .border(1.dp, BorderLight, CardShape)
                                        .clip(CardShape),
                                ) {
                                    wo.checklist.forEachIndexed { idx, item ->
                                        ChecklistRow(item, onToggle = { viewModel.toggleChecklistItem(item.id, item.status) })
                                        if (idx != wo.checklist.lastIndex) {
                                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(com.markazsayana.app.ui.theme.Divider))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "قطع الغيار المستخدمة", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                    Text(
                                        text = "+ إضافة",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = BrandNavy,
                                        modifier = Modifier.clickable(onClick = viewModel::startAddPart),
                                    )
                                }
                                wo.parts.forEach { part -> PartRow(part) }

                                if (state.addingPart) {
                                    AddPartInline(
                                        name = state.newPartName,
                                        onNameChange = viewModel::onNewPartNameChange,
                                        onUse = { viewModel.confirmAddPart("used") },
                                        onRequest = { viewModel.confirmAddPart("requested") },
                                        onCancel = viewModel::cancelAddPart,
                                    )
                                }
                            }
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardWhite, CardShape)
                                    .border(1.dp, BorderMedium, CardShape)
                                    .padding(18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "إضافة صور قبل / بعد", style = MaterialTheme.typography.labelLarge, color = BrandNavy, textAlign = TextAlign.Center)
                            }
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OrangeChipBg, CardShape)
                                    .clickable { showQuoteDialog = true }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "العطل يحتاج قطعة خارج الضمان — إرسال عرض سعر لموافقة العميل",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OrangeTextDark,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.background(CardWhite).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryButton(text = "تعليق الزيارة", onClick = viewModel::togglePause, modifier = Modifier.weight(1f))
                        PrimaryButton(text = "إنهاء وتسليم", onClick = { onFinish(wo.id) }, backgroundColor = SuccessGreen, modifier = Modifier.weight(1.3f))
                    }
                }
            }
        }
    }

    if (showQuoteDialog) {
        SendQuoteDialog(
            submitting = state.submittingQuote,
            error = state.quoteError,
            onDismiss = { showQuoteDialog = false },
            onSend = { cost, note -> viewModel.sendQuote(cost, note) },
        )
    }
}

@Composable
private fun SendQuoteDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (cost: Double, note: String?) -> Unit,
) {
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsedCost = cost.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال عرض سعر للعميل") },
        text = {
            Column {
                Text(
                    text = "سيُنقل الطلب إلى \"بانتظار موافقة العميل\" حتى يتصل الاستقبال بالعميل ويسجّل قراره.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("التكلفة التقديرية (ج.م)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                error?.let {
                    Text(text = it, color = UrgentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedCost != null && parsedCost > 0 && !submitting,
                onClick = { onSend(parsedCost!!, note.trim().ifBlank { null }) },
            ) { Text(if (submitting) "جارٍ الإرسال…" else "إرسال", color = BrandNavy) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = TextTertiary) } },
    )
}

@Composable
private fun ChecklistRow(item: ChecklistItemDto, onToggle: () -> Unit) {
    val isIssue = item.status == "issue"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isIssue) OrangeChipBg else CardWhite)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    when (item.status) {
                        "done" -> Modifier.background(SuccessGreen)
                        "issue" -> Modifier.background(UrgentRed)
                        else -> Modifier.border(2.dp, BorderMedium, RoundedCornerShape(6.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (item.status) {
                "done" -> Text(text = "✓", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                "issue" -> Text(text = "!", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                else -> {}
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isIssue) OrangeTextDark else TextPrimary,
            modifier = Modifier.weight(1f),
        )
        item.note?.let {
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = TextTertiary)
        }
    }
}

@Composable
private fun PartRow(part: PartUsedDto) {
    val requested = part.status != "used"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(CardWhite, CardShape)
            .then(
                if (requested) Modifier.border(1.dp, BorderMedium, CardShape) else Modifier.border(1.dp, BorderLight, CardShape),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = part.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = if (requested) "غير متوفر — طلب من المخزن الرئيسي" else "من مخزون المركبة",
                style = MaterialTheme.typography.bodySmall,
                color = if (requested) OrangeTextDark else TextTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (requested) {
            Box(modifier = Modifier.border(1.dp, BrandNavy, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(text = "بانتظار التوريد", style = MaterialTheme.typography.labelMedium, color = BrandNavy)
            }
        } else {
            Text(text = part.price.egp(), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun AddPartInline(name: String, onNameChange: (String) -> Unit, onUse: () -> Unit, onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(CardWhite, CardShape)
            .border(1.dp, BrandNavyChipBg, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("اسم القطعة") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = "إلغاء", onClick = onCancel, modifier = Modifier.weight(1f))
            SecondaryButton(text = "طلب من المخزن", onClick = onRequest, modifier = Modifier.weight(1f))
            PrimaryButton(text = "استخدام", onClick = onUse, modifier = Modifier.weight(1f))
        }
    }
}
