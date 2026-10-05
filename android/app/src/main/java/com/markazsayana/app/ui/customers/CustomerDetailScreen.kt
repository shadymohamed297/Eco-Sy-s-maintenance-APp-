package com.markazsayana.app.ui.customers

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.CustomerDetailResponse
import com.markazsayana.app.data.remote.CustomerWorkOrderDto
import com.markazsayana.app.data.remote.DeviceDto
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.workorders.RefreshOnResume

@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onOpenWorkOrder: (Int) -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    RefreshOnResume(viewModel::load)

    Scaffold(containerColor = SurfaceScreen) { padding ->
        when (val s = state) {
            is CustomerDetailUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is CustomerDetailUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is CustomerDetailUiState.Success -> Content(s.data, onBack, onOpenWorkOrder, onSave = viewModel::updateCustomer, saving = s.saving, saveError = s.saveError, savedVersion = s.savedVersion, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun Content(
    data: CustomerDetailResponse,
    onBack: () -> Unit,
    onOpenWorkOrder: (Int) -> Unit,
    onSave: (name: String, phone: String, address: String?) -> Unit,
    saving: Boolean,
    saveError: String?,
    savedVersion: Int,
    modifier: Modifier,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    LaunchedEffect(savedVersion) { if (savedVersion > 0) showEditDialog = false }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(CardWhite).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceScreen).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text(text = "→", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = data.customer.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = data.customer.phone, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceScreen).clickable { showEditDialog = true },
                contentAlignment = Alignment.Center,
            ) { Text(text = "✎", style = MaterialTheme.typography.titleMedium, color = BrandNavy) }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (data.customer.address != null) {
                item {
                    OutlinedCard {
                        Text(text = "العنوان", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Text(text = data.customer.address, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }

            item { Text(text = "الأجهزة", style = MaterialTheme.typography.titleSmall, color = TextPrimary) }
            items(data.devices, key = { it.id }) { d -> DeviceRow(d) }

            item { Text(text = "سجل الطلبات", style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.padding(top = 6.dp)) }
            if (data.devices.isEmpty()) item { Text("لم تُسجّل أجهزة لهذا العميل بعد.", color = TextTertiary) }
            if (data.workOrders.isEmpty()) item { Text("لا توجد طلبات سابقة لهذا العميل.", color = TextTertiary) }
            items(data.workOrders, key = { it.id }) { wo -> WorkOrderHistoryRow(wo, onClick = { onOpenWorkOrder(wo.id) }) }
        }
    }

    if (showEditDialog) {
        EditCustomerDialog(
            name = data.customer.name,
            phone = data.customer.phone,
            address = data.customer.address,
            saving = saving,
            saveError = saveError,
            onDismiss = { if (!saving) showEditDialog = false },
            onSave = { name, phone, address ->
                onSave(name, phone, address)
            },
        )
    }
}

@Composable
private fun EditCustomerDialog(
    name: String,
    phone: String,
    address: String?,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String?) -> Unit,
) {
    var editedName by remember { mutableStateOf(name) }
    var editedPhone by remember { mutableStateOf(phone) }
    var editedAddress by remember { mutableStateOf(address ?: "") }
    val canSave = !saving && editedName.isNotBlank() && editedPhone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات العميل") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedName, onValueChange = { editedName = it }, label = { Text("الاسم") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                saveError?.let { Text(it, color = com.markazsayana.app.ui.theme.UrgentRed, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = editedPhone, onValueChange = { editedPhone = it }, label = { Text("رقم الهاتف") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                OutlinedTextField(
                    value = editedAddress, onValueChange = { editedAddress = it }, label = { Text("العنوان") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(editedName.trim(), editedPhone.trim(), editedAddress.trim().ifBlank { null }) },
            ) { Text(if (saving) "جارِ الحفظ…" else "حفظ", color = if (canSave) BrandNavy else TextTertiary) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("رجوع", color = TextTertiary) } },
    )
}

@Composable
private fun DeviceRow(device: DeviceDto) {
    OutlinedCard {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = device.deviceType, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    text = listOfNotNull(device.brand, device.model).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (device.underWarranty) {
                StatusChip(text = "داخل الضمان", background = BrandNavyChipBg, contentColor = BrandNavy)
            }
        }
    }
}

@Composable
private fun WorkOrderHistoryRow(wo: CustomerWorkOrderDto, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${wo.deviceType} — ${wo.issueDescription.take(28)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = "${wo.code} · ${wo.technicianName ?: "بلا فني"}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            if (wo.status == "closed") {
                StatusChip(text = "مغلقة", background = BrandNavyChipBg, contentColor = BrandNavy)
            } else {
                StatusChip(text = "مفتوحة", background = OrangeChipBg, contentColor = OrangeTextDark)
            }
        }
    }
}
