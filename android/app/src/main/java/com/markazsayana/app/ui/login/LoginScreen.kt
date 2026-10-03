package com.markazsayana.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.R
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfacePage
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed

@Composable
fun LoginScreen(
    onLoggedIn: (UserDto) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val success = state as? LoginUiState.Success
        if (success != null) onLoggedIn(success.user)
    }

    if (showServerDialog) {
        ServerUrlDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = { url ->
                viewModel.setServerUrl(url)
                showServerDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePage)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = null,
                modifier = Modifier.height(72.dp),
            )
            Text(
                text = "الهندسة المتحدة",
                style = MaterialTheme.typography.titleLarge,
                color = BrandNavy,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(text = "صيانة الأجهزة المنزلية وأجهزة الأسواق الحرة", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "تسجيل الدخول", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("البريد الإلكتروني أو رقم الهاتف") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state is LoginUiState.Error) {
                Text(
                    text = (state as LoginUiState.Error).message,
                    color = UrgentRed,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state is LoginUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandNavy)
                }
            } else {
                PrimaryButton(text = "دخول", onClick = { viewModel.login(identifier, password) })
            }
        }

        Text(
            text = "عنوان السيرفر: ${serverUrl.ifBlank { "…" }}",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .clickable { showServerDialog = true },
        )
    }
}

@Composable
private fun ServerUrlDialog(currentUrl: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عنوان سيرفر النظام") },
        text = {
            Column {
                Text(
                    text = "الصق هنا رابط الباك إند بعد نشره (مثلاً من Render)، مرة واحدة بس.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("https://markaz-sayana-api.onrender.com") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("حفظ", color = BrandNavy) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextTertiary) }
        },
    )
}
