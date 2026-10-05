package com.markazsayana.app.ui.manager

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.markazsayana.app.data.remote.InventoryItemDto
import com.markazsayana.app.data.remote.SaveInventoryItemRequest
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.workorders.RefreshOnResume
import com.markazsayana.app.ui.theme.AccentOrange
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Divider
import com.markazsayana.app.ui.theme.OrangeChipBorder
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.arabic
import com.markazsayana.app.util.egp
import com.markazsayana.app.util.egpCompact

@Composable
fun InventoryScreen(
    role: String,
    onNavTab: (String) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val itemSaved by viewModel.itemSaved.collectAsState()
    var showItemEditor by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemDto?>(null) }
    RefreshOnResume { if (!showItemEditor) viewModel.refresh() }
    val success = state as? InventoryUiState.Success
    LaunchedEffect(itemSaved) {
        if (itemSaved) {
            showItemEditor = false
            viewModel.resetItemSaved()
        }
    }
    val activeColor = if (role == "reception") BrandNavy else SurfaceDark

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(navItemsForRole(role), Routes.INVENTORY, activeColor, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is InventoryUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is InventoryUiState.Error -> FullScreenError(s.message, onRetry = { viewModel.load("all") }, modifier = Modifier.padding(padding))
            is InventoryUiState.Success -> InventoryContent(
                s, canOrder = role == "manager", canManage = role == "manager",
                onFilterChange = viewModel::load, onOrder = viewModel::createPurchaseOrder,
                onCreate = { editingItem = null; viewModel.resetItemSaved(); showItemEditor = true },
                onEdit = { editingItem = it; viewModel.resetItemSaved(); showItemEditor = true },
                onReceive = viewModel::receivePurchaseOrder, modifier = Modifier.padding(padding),
            )
        }
    }
    if (showItemEditor && role == "manager") InventoryItemDialog(
        item = editingItem, busy = success?.operationBusy == true, serverError = success?.operationError,
        onDismiss = { if (success?.operationBusy != true) showItemEditor = false },
        onSave = { viewModel.saveItem(editingItem?.id, it) },
    )
}

@Composable
private fun InventoryContent(
    state: InventoryUiState.Success,
    canOrder: Boolean,
    canManage: Boolean,
    onFilterChange: (String) -> Unit,
    onOrder: (Int) -> Unit,
    onCreate: () -> Unit,
    onEdit: (InventoryItemDto) -> Unit,
    onReceive: (Int) -> Unit,
    modifier: Modifier,
) {
    var search by remember { mutableStateOf("") }
    val visibleItems = state.data.items.filter {
        search.isBlank() || it.name.contains(search, ignoreCase = true) || it.sku.contains(search, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
            Text(text = "المخزون وقطع الغيار", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (canManage) TextButton(enabled = !state.operationBusy, onClick = onCreate) { Text("إضافة قطعة للمخزون") }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("بحث باسم القطعة أو الرقم…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.message?.let { item { Text(it, color = BrandNavy, style = MaterialTheme.typography.bodySmall) } }
            state.operationError?.let { item { Text(it, color = UrgentRed, style = MaterialTheme.typography.bodySmall) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("تحت الحد", state.filter == "low") { onFilterChange("low") }
                    FilterChip("الكل", state.filter == "all") { onFilterChange("all") }
                    FilterChip("طلبات معلّقة ${state.data.pendingPurchaseOrders.arabic()}", state.filter == "pending") { onFilterChange("pending") }
                }
            }

            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        text = if (search.isNotBlank()) "لا توجد قطع مطابقة للبحث." else if (canManage) "لا توجد قطع في هذه القائمة. يمكنك إضافة القطع من زر إضافة قطعة للمخزون." else "لا توجد قطع في هذه القائمة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }

            items(visibleItems, key = { it.id }) { item ->
                InventoryRow(item, canOrder = canOrder && !state.operationBusy, onOrder = { onOrder(item.id) }, onEdit = if (canManage) ({ onEdit(item) }) else null)
            }

            if (canManage && state.data.purchaseOrders.isNotEmpty()) {
                item { Text("طلبات الشراء قيد الاستلام", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
                items(state.data.purchaseOrders, key = { "purchase-${it.id}" }) { purchase ->
                    OutlinedCard {
                        Text(purchase.itemName ?: "طلب شراء رقم ${purchase.id.arabic()}", style = MaterialTheme.typography.titleSmall)
                        Text("الكمية: ${purchase.quantity.arabic()}", style = MaterialTheme.typography.bodyMedium)
                        purchase.workOrderCode?.let { Text("لطلب الصيانة $it", style = MaterialTheme.typography.bodySmall, color = TextTertiary) }
                        Button(enabled = !state.operationBusy, onClick = { onReceive(purchase.id) }) { Text("تأكيد وصول القطع للمخزون") }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(text = "استهلاك القطع هذا الشهر", style = MaterialTheme.typography.bodyMedium, color = CardWhite)
                    Text(
                        text = state.data.consumptionThisMonth.egpCompact(),
                        style = com.markazsayana.app.ui.theme.Title22Bold,
                        color = CardWhite,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) SurfaceDark else CardWhite)
            .border(1.dp, if (selected) SurfaceDark else BorderMedium, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = if (selected) CardWhite else TextPrimary)
    }
}

@Composable
private fun InventoryRow(item: InventoryItemDto, canOrder: Boolean, onOrder: () -> Unit, onEdit: (() -> Unit)? = null) {
    OutlinedCard(borderColor = if (item.lowStock) OrangeChipBorder else com.markazsayana.app.ui.theme.BorderLight) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(text = "${item.sku} · ${item.price.egp()}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.quantity.arabic(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.lowStock) UrgentRed else TextPrimary,
                )
                Text(text = "الحد ${item.reorderThreshold.arabic()}", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
        if (onEdit != null) TextButton(onClick = onEdit) { Text("تعديل بيانات القطعة والمخزون") }
        if (item.lowStock) {
            val fraction = (item.quantity.toFloat() / item.reorderThreshold.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 1f)
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Divider)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(UrgentRed))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    text = item.reservedForCode?.let { "محجوزة لأمر $it" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = OrangeTextDark,
                )
                if (canOrder) {
                    Box(
                        modifier = Modifier
                            .clip(CardShape)
                            .background(AccentOrange)
                            .clickable(onClick = onOrder)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(text = "أمر شراء", style = MaterialTheme.typography.labelLarge, color = CardWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryItemDialog(
    item: InventoryItemDto?, busy: Boolean, serverError: String?, onDismiss: () -> Unit,
    onSave: (SaveInventoryItemRequest) -> Unit,
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var sku by remember(item?.id) { mutableStateOf(item?.sku.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category.orEmpty()) }
    var price by remember(item?.id) { mutableStateOf(item?.price?.toString() ?: "") }
    var quantity by remember(item?.id) { mutableStateOf(item?.quantity?.toString() ?: "0") }
    var threshold by remember(item?.id) { mutableStateOf(item?.reorderThreshold?.toString() ?: "0") }
    var validationError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (item == null) "إضافة قطعة غيار" else "تعديل قطعة غيار") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم القطعة") }, enabled = !busy)
                OutlinedTextField(sku, { sku = it }, label = { Text("كود القطعة") }, enabled = !busy)
                OutlinedTextField(category, { category = it }, label = { Text("التصنيف (اختياري)") }, enabled = !busy)
                OutlinedTextField(price, { price = it }, label = { Text("سعر الوحدة بالجنيه") }, enabled = !busy)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("الكمية الفعلية بالمخزون") }, enabled = !busy)
                OutlinedTextField(threshold, { threshold = it }, label = { Text("حد التنبيه لإعادة الطلب") }, enabled = !busy)
                (validationError ?: serverError)?.let { Text(it, color = UrgentRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val unitPrice = price.normalizedNumber().toDoubleOrNull()
            val stock = quantity.normalizedNumber().toIntOrNull()
            val reorder = threshold.normalizedNumber().toIntOrNull()
            if (name.isBlank() || sku.isBlank() || unitPrice == null || !unitPrice.isFinite() || unitPrice < 0 || stock == null || stock < 0 || reorder == null || reorder < 0) {
                validationError = "أدخل اسمًا وكودًا للقطعة، وسعرًا وكميات صحيحة لا تقل عن صفر."
            } else {
                validationError = null
                onSave(SaveInventoryItemRequest(name.trim(), sku.trim(), category.trim().ifBlank { null }, unitPrice, stock, reorder))
            }
        }) { Text(if (busy) "جارِ الحفظ…" else "حفظ") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("رجوع") } },
    )
}

private fun String.normalizedNumber(): String = trim().map { char ->
    when (char) {
        in '٠'..'٩' -> '0' + (char - '٠')
        in '۰'..'۹' -> '0' + (char - '۰')
        '٫' -> '.'
        else -> char
    }
}.joinToString("")
