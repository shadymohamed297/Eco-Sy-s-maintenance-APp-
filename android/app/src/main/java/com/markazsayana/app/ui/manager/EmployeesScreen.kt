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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.EmployeeDto
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.StatusChip
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
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
fun EmployeesScreen(
    onBack: () -> Unit,
    viewModel: EmployeesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val temporaryPassword by viewModel.temporaryPassword.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeDto?>(null) }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp),
            ) {
                Text(text = "◀", color = CardWhite, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
                Column {
                    Text(text = "إدارة الموظفين", style = MaterialTheme.typography.titleLarge, color = CardWhite)
                    Text(text = "إضافة موظفين جدد وإيقاف حسابات من غادروا", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
                }
            }

            when (val s = state) {
                is EmployeesUiState.Loading -> FullScreenLoading(Modifier.weight(1f))
                is EmployeesUiState.Error -> FullScreenError(s.message, onRetry = viewModel::load, modifier = Modifier.weight(1f))
                is EmployeesUiState.Success -> {
                    if (s.employees.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(text = "لا يوجد موظفون بعد", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(s.employees, key = { it.id }) { emp ->
                                EmployeeRow(emp, onClick = { editingEmployee = emp })
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PrimaryButton(text = "+ إضافة موظف جديد", onClick = { showAddDialog = true })
            }
        }
    }

    if (showAddDialog) {
        AddEmployeeDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, role, email, phone, branch, title, specialty ->
                viewModel.createEmployee(name, role, email, phone, branch, title, specialty)
                showAddDialog = false
            },
        )
    }

    editingEmployee?.let { emp ->
        EditEmployeeDialog(
            employee = emp,
            isSelf = emp.id == viewModel.currentUserId,
            onDismiss = { editingEmployee = null },
            onSave = { name, branch, title, specialty ->
                viewModel.updateEmployee(emp.id, name, branch, title, specialty)
                editingEmployee = null
            },
            onToggleActive = { active ->
                viewModel.setActive(emp.id, active)
                editingEmployee = null
            },
            onResetPassword = {
                viewModel.resetPassword(emp.id, emp.name)
                editingEmployee = null
            },
        )
    }

    temporaryPassword?.let { (name, password) ->
        TemporaryPasswordDialog(name = name, password = password, onDismiss = viewModel::dismissTemporaryPassword)
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissActionError,
            title = { Text("حدث خطأ") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissActionError) { Text("حسناً", color = BrandNavy) } },
        )
    }
}

@Composable
private fun EmployeeRow(emp: EmployeeDto, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = emp.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    StatusChip(text = roleLabel(emp.role), background = BrandNavyChipBg, contentColor = BrandNavy)
                    if (!emp.active) {
                        StatusChip(text = "موقوف", background = OrangeChipBg, contentColor = OrangeTextDark)
                    }
                }
                Text(
                    text = listOfNotNull(emp.branch, emp.email ?: emp.phone).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AddEmployeeDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, role: String, email: String?, phone: String?, branch: String?, title: String?, specialty: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("reception") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    val canSubmit = name.isNotBlank() && (email.isNotBlank() || phone.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة موظف جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        value = roleLabel(role), onValueChange = {}, readOnly = true, label = { Text("الوظيفة") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                        modifier = Modifier.fillMaxWidth().clickable { roleMenuExpanded = true },
                    )
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        listOf("reception", "technician", "manager").forEach { r ->
                            DropdownMenuItem(text = { Text(roleLabel(r)) }, onClick = { role = r; roleMenuExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني (اختياري)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف (اختياري)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = branch, onValueChange = { branch = it }, label = { Text("الفرع") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                if (role == "technician") {
                    OutlinedTextField(
                        value = specialty, onValueChange = { specialty = it }, label = { Text("التخصص") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onCreate(name.trim(), role, email.trim(), phone.trim(), branch.trim(), null, specialty.trim()) },
            ) { Text("إضافة", color = if (canSubmit) BrandNavy else TextTertiary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = TextTertiary) } },
    )
}

@Composable
private fun EditEmployeeDialog(
    employee: EmployeeDto,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, branch: String?, title: String?, specialty: String?) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onResetPassword: () -> Unit,
) {
    var name by remember { mutableStateOf(employee.name) }
    var branch by remember { mutableStateOf(employee.branch ?: "") }
    var specialty by remember { mutableStateOf(employee.specialty ?: "") }
    var confirmDeactivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(roleLabel(employee.role)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = branch, onValueChange = { branch = it }, label = { Text("الفرع") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                )
                if (employee.role == "technician") {
                    OutlinedTextField(
                        value = specialty, onValueChange = { specialty = it }, label = { Text("التخصص") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = onResetPassword) { Text("إعادة تعيين كلمة المرور", color = BrandNavy) }
                if (!isSelf) {
                    if (employee.active) {
                        TextButton(onClick = { confirmDeactivate = true }) { Text("إيقاف الحساب", color = UrgentRed) }
                    } else {
                        TextButton(onClick = { onToggleActive(true) }) { Text("إعادة تفعيل الحساب", color = BrandNavy) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), branch.trim(), null, specialty.trim()) }) { Text("حفظ", color = BrandNavy) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = TextTertiary) } },
    )

    if (confirmDeactivate) {
        AlertDialog(
            onDismissRequest = { confirmDeactivate = false },
            title = { Text("إيقاف الحساب؟") },
            text = { Text("${employee.name} لن يستطيع تسجيل الدخول بعد الآن حتى تُعيد تفعيل حسابه.") },
            confirmButton = {
                TextButton(onClick = { confirmDeactivate = false; onToggleActive(false) }) { Text("إيقاف", color = UrgentRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDeactivate = false }) { Text("تراجع", color = TextTertiary) } },
        )
    }
}

@Composable
private fun TemporaryPasswordDialog(name: String, password: String, onDismiss: () -> Unit) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("كلمة مرور $name") },
        text = {
            Column {
                Text(
                    text = "شارك كلمة المرور هذه مع الموظف الآن — لن تظهر مرة أخرى بعد إغلاق هذه الرسالة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandNavyChipBg)
                        .clickable { clipboard.setText(AnnotatedString(password)) }
                        .padding(12.dp),
                ) {
                    Text(text = password, style = MaterialTheme.typography.titleMedium, color = BrandNavy)
                }
                Text(
                    text = "اضغط لنسخ كلمة المرور",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم", color = BrandNavy) } },
    )
}
