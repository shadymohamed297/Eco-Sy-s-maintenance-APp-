package com.markazsayana.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.TextTertiary

data class BottomNavItem(val label: String, val route: String)

/**
 * White bottom bar with a 24x3dp active indicator, matching every screen footer.
 * The width adapts to the role's tabs and respects Android's system navigation area.
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
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (item in items) {
            val selected = item.route == selectedRoute
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.route) })
                    .padding(vertical = 6.dp)
                    .padding(horizontal = 2.dp),
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
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
