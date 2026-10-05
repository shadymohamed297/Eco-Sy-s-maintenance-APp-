package com.markazsayana.app.ui.reception

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.ui.components.AvatarCircle
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.ControlShape
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextSecondary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed

@Composable
fun NewRequestScreen(
    onBack: () -> Unit,
    onNext: (workOrderId: Int) -> Unit,
    viewModel: NewRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdWorkOrderId) {
        state.createdWorkOrderId?.let(onNext)
    }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TopBarWithProgress(
                title = "طلب صيانة جديد",
                subtitle = if (state.step == IntakeStep.CUSTOMER) "الخطوة ١ من ٣ · بيانات العميل" else "الخطوة ٢ من ٣ · بيانات الجهاز",
                progress = if (state.step == IntakeStep.CUSTOMER) 0.33f else 0.66f,
                onBack = { if (state.step == IntakeStep.DEVICE) viewModel.backToCustomerStep() else onBack() },
            )

            if (state.step == IntakeStep.CUSTOMER) {
                CustomerStep(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
            } else {
                DeviceStep(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TopBarWithProgress(title: String, subtitle: String, progress: Float, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceScreen)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "→", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(BorderLight)) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(BrandNavy))
        }
    }
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun CustomerStep(state: NewRequestUiState, viewModel: NewRequestViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "رقم هاتف العميل", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("رقم الهاتف") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(text = "بحث", onClick = viewModel::searchCustomer, modifier = Modifier.weight(0.35f))
        }

        if (state.searching) {
            FullScreenLoading(Modifier.height(80.dp))
        }

        state.foundCustomer?.let { customer ->
            OutlinedCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarCircle(initials = customer.name.trim().split(" ").take(2).joinToString(" ") { it.take(1) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = customer.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text(text = customer.phone, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
            OutlinedTextField(
                value = state.newCustomerAddress,
                onValueChange = viewModel::onNewCustomerAddressChange,
                label = { Text("عنوان الزيارة") },
                supportingText = { Text("يمكن تحديد عنوان مختلف لهذا الطلب") },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(text = "متابعة إلى بيانات الجهاز", onClick = viewModel::proceedToDeviceStep)
        }

        if (state.customerNotFound) {
            Text(text = "هذا الرقم غير مسجل. أضف اسم العميل وعنوانه", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            OutlinedTextField(
                value = state.newCustomerName,
                onValueChange = viewModel::onNewCustomerNameChange,
                label = { Text("اسم العميل") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.newCustomerAddress,
                onValueChange = viewModel::onNewCustomerAddressChange,
                label = { Text("عنوان الزيارة") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "متابعة إلى بيانات الجهاز",
                onClick = viewModel::proceedToDeviceStep,
                enabled = state.newCustomerName.isNotBlank(),
            )
        }

        state.error?.let {
            Text(text = it, color = UrgentRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceStep(state: NewRequestUiState, viewModel: NewRequestViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val registeredDevices = state.foundCustomer?.devices.orEmpty()
        if (registeredDevices.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أجهزة العميل المسجلة", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    registeredDevices.forEach { device ->
                        OutlinedCard(
                            borderColor = if (state.selectedDeviceId == device.id) BrandNavy else BorderLight,
                            modifier = Modifier.clickable(enabled = !state.submitting) { viewModel.chooseDevice(device) },
                        ) {
                            Text(listOfNotNull(device.deviceType, device.brand, device.model).joinToString(" · "), color = TextPrimary)
                            device.serialNumber?.let { Text("رقم تسلسلي: $it", style = MaterialTheme.typography.bodySmall, color = TextTertiary) }
                            if (state.selectedDeviceId == device.id) Text("الجهاز المحدد لهذا الطلب", style = MaterialTheme.typography.labelMedium, color = BrandNavy)
                        }
                    }
                    SecondaryButton(text = "إضافة جهاز آخر", onClick = { viewModel.chooseDevice(null) })
                }
            }
        }
        item {
            val displayName = state.foundCustomer?.name ?: state.newCustomerName
            val displayPhone = state.foundCustomer?.phone ?: state.phone
            OutlinedCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarCircle(initials = displayName.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.take(1) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = displayName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text(text = displayPhone, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        state.newCustomerAddress.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextTertiary) }
                    }
                    Text(text = "تعديل", style = MaterialTheme.typography.labelLarge, color = BrandNavy, modifier = Modifier.clickableNoIndication(viewModel::backToCustomerStep))
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "نوع الجهاز", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DEVICE_TYPES.forEach { type ->
                        val selected = type == state.deviceType
                        Box(
                            modifier = Modifier
                                .clip(ControlShape)
                                .background(if (selected) BrandNavy else CardWhite)
                                .border(1.dp, if (selected) BrandNavy else BorderMedium, ControlShape)
                                .clickable(enabled = !state.submitting && state.selectedDeviceId == null) { viewModel.onDeviceTypeChange(type) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text(text = type, color = if (selected) CardWhite else TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.brand,
                    onValueChange = viewModel::onBrandChange,
                    label = { Text("الماركة") },
                    singleLine = true,
                    enabled = !state.submitting && state.selectedDeviceId == null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.model,
                    onValueChange = viewModel::onModelChange,
                    label = { Text("الموديل") },
                    singleLine = true,
                    enabled = !state.submitting && state.selectedDeviceId == null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.serialNumber,
                onValueChange = viewModel::onSerialChange,
                label = { Text("الرقم التسلسلي") },
                singleLine = true,
                enabled = !state.submitting && state.selectedDeviceId == null,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = state.issueDescription,
                onValueChange = viewModel::onIssueChange,
                label = { Text("وصف العطل") },
                minLines = 3,
                enabled = !state.submitting,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "داخل الضمان", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Switch(
                        checked = state.underWarranty,
                        onCheckedChange = viewModel::onWarrantyToggle,
                        enabled = !state.submitting && state.selectedDeviceId == null,
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandNavy),
                    )
                }
            }
        }

        state.error?.let {
            item { Text(text = it, color = UrgentRed, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            PrimaryButton(
                    text = if (state.submitting) "جارٍ حفظ الطلب…" else "حفظ الطلب واختيار الفني",
                    onClick = viewModel::submit,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
        }
    }
}
