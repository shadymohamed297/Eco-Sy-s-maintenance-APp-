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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.CustomerSummaryDto
import com.markazsayana.app.ui.components.AvatarCircle
import com.markazsayana.app.ui.components.BottomNavBar
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.navItemsForRole
import com.markazsayana.app.ui.navigation.Routes
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.util.arabic

private val nav = navItemsForRole("reception")

@Composable
fun CustomersListScreen(
    onOpenCustomer: (Int) -> Unit,
    onNavTab: (String) -> Unit,
    viewModel: CustomersListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceScreen,
        bottomBar = { BottomNavBar(nav, Routes.CUSTOMERS_LIST, BrandNavy, onNavTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
                Text(text = "العملاء", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text("بحث بالاسم أو رقم الهاتف…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            when {
                state.loading -> FullScreenLoading()
                state.error != null -> FullScreenError(state.error!!, onRetry = viewModel::load)
                state.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.search.isNotBlank()) "لا يوجد عملاء مطابقون للبحث" else "لا يوجد عملاء بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.id }) { c -> CustomerRow(c, onClick = { onOpenCustomer(c.id) }) }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(customer: CustomerSummaryDto, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarCircle(initials = customer.name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.take(1) })
            Column(modifier = Modifier.weight(1f)) {
                Text(text = customer.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(text = customer.phone, style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${customer.workOrderCount.arabic()} طلب", style = MaterialTheme.typography.labelMedium, color = BrandNavy)
                Text(text = "${customer.deviceCount.arabic()} جهاز", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
    }
}
