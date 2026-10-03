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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
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
    val activeColor = if (role == "reception") BrandNavy else SurfaceDark

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(navItemsForRole(role), Routes.INVENTORY, activeColor, onNavTab) },
    ) { padding ->
        when (val s = state) {
            is InventoryUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is InventoryUiState.Error -> FullScreenError(s.message, onRetry = { viewModel.load("low") }, modifier = Modifier.padding(padding))
            is InventoryUiState.Success -> InventoryContent(s, canOrder = role == "manager" || role == "technician", onFilterChange = viewModel::load, onOrder = viewModel::createPurchaseOrder, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun InventoryContent(
    state: InventoryUiState.Success,
    canOrder: Boolean,
    onFilterChange: (String) -> Unit,
    onOrder: (Int) -> Unit,
    modifier: Modifier,
) {
    var search by remember { mutableStateOf("") }
    val visibleItems = state.data.items.filter {
        search.isBlank() || it.name.contains(search, ignoreCase = true) || it.sku.contains(search, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
            Text(text = "المخزون وقطع الغيار", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
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
                        text = "لا توجد قطع مطابقة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }

            items(visibleItems, key = { it.id }) { item ->
                InventoryRow(item, canOrder = canOrder, onOrder = { onOrder(item.id) })
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
private fun InventoryRow(item: InventoryItemDto, canOrder: Boolean, onOrder: () -> Unit) {
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
        if (item.lowStock) {
            val fraction = (item.quantity.toFloat() / item.reorderThreshold.toFloat()).coerceIn(0.05f, 1f)
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
