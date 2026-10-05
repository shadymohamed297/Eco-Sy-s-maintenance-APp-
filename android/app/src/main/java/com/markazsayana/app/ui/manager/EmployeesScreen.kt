package com.markazsayana.app.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.EmployeeDto
import com.markazsayana.app.ui.components.BrandHeader
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.components.westernDigits
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed

private fun roleLabel(role: String): String = when (role) {
    "reception" -> "استقبال"
    "technician" -> "فني"
    "manager" -> "مدير"
    else -> role
}

@Composable
fun EmployeesScreen(onBack: () -> Unit, viewModel: EmployeesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val temporaryPassword by viewModel.temporaryPassword.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val busy by viewModel.actionInProgress.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeDto?>(null) }
    var search by remember { mutableStateOf("") }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(BrandNavy).padding(16.dp)) {
                BrandHeader(compact = true, onDark = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("رجوع", color = CardWhite) }
                    Column {
                        Text("حسابات الفريق", style = MaterialTheme.typography.titleLarge, color = CardWhite)
                        Text("بيانات الموظفين وصلاحياتهم وحالة حساباتهم", style = MaterialTheme.typography.bodySmall, color = CardWhite.copy(alpha = 0.8f))
                    }
                }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it }, label = { Text("ابحث بالاسم أو الهاتف أو الوظيفة") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val s = state) {
                EmployeesUiState.Loading -> FullScreenLoading(Modifier.weight(1f))
                is EmployeesUiState.Error -> FullScreenError(s.message, viewModel::load, Modifier.weight(1f))
                is EmployeesUiState.Success -> {
                    val filtered = s.employees.filter { employee ->
                        search.isBlank() || listOf(employee.name, employee.phone.orEmpty(), employee.email.orEmpty(), roleLabel(employee.role)).any { it.contains(search.trim(), ignoreCase = true) }
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (search.isBlank()) "لا توجد حسابات مسجلة" else "لا توجد نتائج لهذا البحث", color = TextTertiary)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filtered, key = { it.id }) { employee ->
                                OutlinedCard(modifier = Modifier.clickable(enabled = !busy) { editingEmployee = employee }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(employee.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                                        StatusChip(roleLabel(employee.role), BrandNavyChipBg, BrandNavy)
                                        if (!employee.active) StatusChip("موقوف", OrangeChipBg, OrangeTextDark)
                                    }
                                    Text(listOfNotNull(employee.phone, employee.email).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(top = 6.dp))
                                    employee.specialty?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextTertiary) }
                                }
                            }
                        }
                    }
                }
            }
            PrimaryButton("إضافة موظف", { showAddDialog = true }, enabled = !busy, modifier = Modifier.padding(16.dp))
        }
    }
    if (showAddDialog) {
        EmployeeFormDialog(
            employee = null, isSelf = false, busy = busy,
            onDismiss = { if (!busy) showAddDialog = false },
            onSave = { name, role, email, phone, title, specialty ->
                viewModel.createEmployee(name, role, email, phone, title, specialty) { showAddDialog = false }
            },
        )
    }
    editingEmployee?.let { employee ->
        EmployeeFormDialog(
            employee = employee, isSelf = employee.id == viewModel.currentUserId, busy = busy,
            onDismiss = { if (!busy) editingEmployee = null },
            onSave = { name, role, email, phone, title, specialty ->
                viewModel.updateEmployee(employee.id, name, role, email, phone, title, specialty) { editingEmployee = null }
            },
            onToggleActive = { active -> viewModel.setActive(employee.id, active) { editingEmployee = null } },
            onResetPassword = { viewModel.resetPassword(employee.id, employee.name) { editingEmployee = null } },
        )
    }
    temporaryPassword?.let { (name, password) -> TemporaryPasswordDialog(name, password, viewModel::dismissTemporaryPassword) }
    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissActionError, title = { Text("تعذّر تنفيذ التغيير") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissActionError) { Text("حسنًا") } },
        )
    }
}

@Composable
private fun RolePicker(role: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column {
        Text("صلاحية الحساب", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("reception", "technician", "manager").forEach { option ->
                TextButton(enabled = enabled, onClick = { onSelect(option) }, modifier = Modifier.weight(1f)) {
                    Text(roleLabel(option), color = if (role == option) BrandNavy else TextTertiary,
                        style = if (role == option) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmployeeFormDialog(
    employee: EmployeeDto?, isSelf: Boolean, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onToggleActive: (Boolean) -> Unit = {}, onResetPassword: () -> Unit = {},
) {
    var name by remember(employee?.id) { mutableStateOf(employee?.name.orEmpty()) }
    var role by remember(employee?.id) { mutableStateOf(employee?.role ?: "reception") }
    var email by remember(employee?.id) { mutableStateOf(employee?.email.orEmpty()) }
    var phone by remember(employee?.id) { mutableStateOf(employee?.phone.orEmpty()) }
    var title by remember(employee?.id) { mutableStateOf(employee?.title.orEmpty()) }
    var specialty by remember(employee?.id) { mutableStateOf(employee?.specialty.orEmpty()) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val emailValid = email.isBlank() || email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    val phoneValid = phone.isBlank() || (phone.trim().matches(Regex("[+0-9\\s-]{8,20}")) && phone.count(Char::isDigit) >= 8)
    val canSubmit = name.trim().length >= 2 && (email.isNotBlank() || phone.isNotBlank()) && emailValid && phoneValid && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (employee == null) "إضافة موظف" else "بيانات ${employee.name}") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الموظف") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                RolePicker(role, enabled = !busy && !isSelf) { role = it }
                Text("أضف رقم الهاتف أو البريد الإلكتروني الذي سيستخدمه الموظف للدخول", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                OutlinedTextField(phone, { phone = westernDigits(it) }, label = { Text("رقم الهاتف") }, isError = !phoneValid, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, isError = !emailValid, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("المسمى الوظيفي (اختياري)") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (role == "technician") OutlinedTextField(specialty, { specialty = it }, label = { Text("تخصص الفني") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (employee != null && !isSelf) {
                    TextButton(enabled = !busy, onClick = { confirming = "password" }) { Text("إعادة تعيين كلمة المرور") }
                    TextButton(enabled = !busy, onClick = { if (employee.active) confirming = "deactivate" else onToggleActive(true) }) {
                        Text(if (employee.active) "إيقاف الحساب" else "تفعيل الحساب", color = if (employee.active) UrgentRed else BrandNavy)
                    }
                }
                if (busy) CircularProgressIndicator(color = BrandNavy)
            }
        },
        confirmButton = { TextButton(enabled = canSubmit, onClick = { onSave(name, role, email, phone, title, if (role == "technician") specialty else "") }) { Text(if (busy) "جارٍ الحفظ…" else "حفظ") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") } },
    )
    confirming?.let { action ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (action == "password") "تغيير كلمة مرور الموظف؟" else "إيقاف حساب الموظف؟") },
            text = { Text(if (action == "password") "سيتم إلغاء كلمة المرور القديمة وجلسات الدخول الحالية، وستظهر كلمة مرور جديدة لتسليمها للموظف." else "لن يتمكن ${employee?.name} من الدخول إلى النظام حتى تفعّل حسابه مرة أخرى.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirming = null; if (action == "password") onResetPassword() else onToggleActive(false) }) { Text("تأكيد", color = UrgentRed) } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun TemporaryPasswordDialog(name: String, password: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("كلمة مرور $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("سلّم كلمة المرور للموظف بشكل خاص، واطلب منه تغييرها بعد الدخول. لن تظهر مرة أخرى بعد إغلاق هذه الرسالة.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(password, style = MaterialTheme.typography.titleMedium, color = BrandNavy, modifier = Modifier.fillMaxWidth().background(BrandNavyChipBg).padding(12.dp))
                TextButton(onClick = { clipboard.setText(AnnotatedString(password)); copied = true }) { Text(if (copied) "تم النسخ" else "نسخ كلمة المرور") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
    )
}
