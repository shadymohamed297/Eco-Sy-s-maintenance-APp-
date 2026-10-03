package com.markazsayana.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.TextTertiary

data class BottomNavItem(val label: String, val route: String)

/**
 * White bottom bar with a 24x3dp active indicator, matching every screen footer.
 * Reception uses the petrol-green active color; technician/manager screens use near-black.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    activeColor: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(top = 8.dp, bottom = 4.dp)
            .then(Modifier),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (item in items) {
            val selected = item.route == selectedRoute
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clickable { onSelect(item.route) }
                    .padding(vertical = 6.dp)
                    .width(76.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) activeColor else BorderLight)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) activeColor else TextTertiary,
                )
            }
        }
    }
}
