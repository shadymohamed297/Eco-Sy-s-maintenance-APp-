package com.markazsayana.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed

/** Small circular key affordance for a dashboard header, next to the logout button. */
@Composable
fun ChangePasswordIconButton(
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.18f),
    contentColor: Color = Color.White,
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(36.dp)
            .background(background, CircleShape)
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "🔑", color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
    if (showDialog) {
        ChangePasswordDialog(onDismiss = { showDialog = false })
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.reset() }

    val mismatch = confirmPassword.isNotBlank() && newPassword != confirmPassword
    val canSubmit = currentPassword.isNotBlank() && newPassword.length >= 8 && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير كلمة المرور") },
        text = {
            Column {
                if (state is ChangePasswordUiState.Success) {
                    Text(text = "تم تغيير كلمة المرور بنجاح.", color = BrandNavy, style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("كلمة المرور الحالية") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("كلمة المرور الجديدة (٨ أحرف على الأقل)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("تأكيد كلمة المرور الجديدة") },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    if (mismatch) {
                        Text(text = "كلمتا المرور غير متطابقتين", color = UrgentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state is ChangePasswordUiState.Error) {
                        Text(
                            text = (state as ChangePasswordUiState.Error).message,
                            color = UrgentRed,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (state is ChangePasswordUiState.Loading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandNavy)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is ChangePasswordUiState.Success) {
                TextButton(onClick = onDismiss) { Text("تم", color = BrandNavy) }
            } else {
                TextButton(enabled = canSubmit, onClick = { viewModel.submit(currentPassword, newPassword) }) {
                    Text("حفظ", color = if (canSubmit) BrandNavy else TextTertiary)
                }
            }
        },
        dismissButton = {
            if (state !is ChangePasswordUiState.Success) {
                TextButton(onClick = onDismiss) { Text("إلغاء", color = TextTertiary) }
            }
        },
    )
}
