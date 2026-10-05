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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.UserDto
import com.markazsayana.app.data.local.normalizeServerBaseUrl
import com.markazsayana.app.ui.components.BrandLogo
import com.markazsayana.app.ui.components.CENTER_NAME
import com.markazsayana.app.ui.components.CENTER_LOCATION
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
            onSave = viewModel::setServerUrl,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePage)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(modifier = Modifier.height(136.dp).fillMaxWidth().padding(top = 16.dp))
            Text(
                text = CENTER_NAME,
                style = MaterialTheme.typography.titleLarge,
                color = BrandNavy,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(text = "مركز صيانة الأجهزة المنزلية · $CENTER_LOCATION", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                enabled = state !is LoginUiState.Loading,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.login(identifier, password) }),
                enabled = state !is LoginUiState.Loading,
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
                PrimaryButton(text = "دخول", onClick = { viewModel.login(identifier, password) }, enabled = identifier.isNotBlank() && password.isNotBlank())
            }
        }

        Text(
            text = "إعداد الاتصال بالنظام",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 18.dp, bottom = 24.dp)
                .fillMaxWidth()
                .clickable(enabled = state !is LoginUiState.Loading) { showServerDialog = true },
        )
    }
}

@Composable
private fun ServerUrlDialog(currentUrl: String, onDismiss: () -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("الاتصال بالنظام") },
        text = {
            Column {
                Text(
                    text = "يحدد مسؤول المركز رابط النظام عند التجهيز. تغيير الرابط يتطلب تسجيل الدخول من جديد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    label = { Text("رابط النظام") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !saving,
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = UrgentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val normalized = runCatching { normalizeServerBaseUrl(text) }.getOrElse {
                    error = it.message
                    return@TextButton
                }
                saving = true
                onSave(normalized) { message ->
                    saving = false
                    if (message == null) onDismiss() else error = message
                }
            }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ", color = BrandNavy) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء", color = TextTertiary) }
        },
    )
}
