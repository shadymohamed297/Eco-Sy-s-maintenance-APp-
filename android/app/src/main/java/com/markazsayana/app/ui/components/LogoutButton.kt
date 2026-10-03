package com.markazsayana.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.markazsayana.app.ui.theme.UrgentRed

/**
 * Small circular logout affordance for a dashboard header. Confirms before actually
 * logging out since it immediately drops the whole session.
 */
@Composable
fun LogoutIconButton(
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.18f),
    contentColor: Color = Color.White,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(36.dp)
            .background(background, CircleShape)
            .clickable { showConfirm = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "⎋", color = contentColor, style = MaterialTheme.typography.titleMedium)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("تسجيل الخروج") },
            text = { Text("هل تريد تسجيل الخروج من حسابك؟") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onConfirmLogout() }) {
                    Text("تسجيل الخروج", color = UrgentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("إلغاء") }
            },
        )
    }
}
