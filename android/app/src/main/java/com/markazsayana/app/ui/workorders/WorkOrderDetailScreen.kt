package com.markazsayana.app.ui.workorders

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.ui.components.BrandHeader
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.dateLabelCairo
import com.markazsayana.app.util.egp
import com.markazsayana.app.util.timeWithPeriodCairo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun WorkOrderDetailScreen(
    role: String,
    onBack: () -> Unit,
    onAssign: (Int) -> Unit,
    onInvoice: (Int) -> Unit,
    onCustomer: (Int) -> Unit,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showEdit by remember { mutableStateOf(false) }
    var editIssue by remember { mutableStateOf("") }
    var editPriority by remember { mutableStateOf("normal") }
    LaunchedEffect(state.savedVersion) { if (state.savedVersion > 0) showEdit = false }
    var showReject by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    val context = LocalContext.current
    RefreshOnResume(viewModel::load)

    Scaffold(containerColor = SurfaceScreen, topBar = {
        DetailHeader("تفاصيل طلب الصيانة", onBack, viewModel::load)
    }) { padding ->
        val order = state.workOrder
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            order == null -> FullScreenError(state.error ?: "تعذّر تحميل الطلب", viewModel::load, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedCard {
                        Text(order.code, style = MaterialTheme.typography.titleLarge, color = BrandNavy)
                        Text(order.statusLabel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        DetailField("الأولوية", when (order.priority) { "urgent" -> "عاجل"; "high" -> "مرتفعة"; "low" -> "منخفضة"; else -> "عادية" })
                        DetailField("وصف العطل", order.issueDescription)
                        DetailField("تاريخ التسجيل", order.createdAt.displayDateTime())
                        DetailField("موعد الزيارة", order.scheduledAt?.displayDateTime() ?: "لم يُحدد بعد")
                        DetailField("الفني", order.technician?.name ?: "لم يُسند بعد")
                        DetailField("بداية العمل", order.startedAt?.displayDateTime())
                        DetailField("إتمام العمل", order.completedAt?.displayDateTime())
                        DetailField("التسليم", order.closedAt?.displayDateTime())
                        if (role in listOf("manager", "reception") && order.status in listOf("new", "assigned", "paused")) {
                            Button(onClick = { onAssign(order.id) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(if (order.technician == null) "إسناد الطلب وتحديد موعد" else "تغيير الفني أو الموعد")
                            }
                        }
                        if (role in listOf("manager", "reception") && order.status in listOf("new", "assigned", "in_progress", "paused", "awaiting_approval")) {
                            TextButton(enabled = !state.submitting, onClick = {
                                editIssue = order.issueDescription
                                editPriority = order.priority
                                showEdit = true
                            }) { Text("تعديل وصف العطل والأولوية") }
                        }
                        if (order.invoice != null) {
                            TextButton(onClick = { onInvoice(order.id) }) { Text("عرض الفاتورة وتوقيع العميل") }
                        }
                    }
                }
                item {
                    OutlinedCard {
                        SectionTitle("العميل والجهاز")
                        DetailField("العميل", order.customer.name)
                        DetailField("الهاتف", order.customer.phone)
                        DetailField("العنوان", order.customer.address ?: "لم يُسجّل عنوان")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onCustomer(order.customer.id) }) { Text("ملف العميل") }
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customer.phone}"))
                                if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                            }) { Text("اتصال") }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DetailField("الجهاز", order.device.type)
                        DetailField("العلامة التجارية", order.device.brand)
                        DetailField("الموديل", order.device.model)
                        DetailField("الرقم المسلسل", order.device.serialNumber)
                        DetailField("الضمان", if (order.device.underWarranty) "داخل الضمان" else "خارج الضمان")
                    }
                }
                if (order.estimatedCost != null || order.status == "awaiting_approval") item {
                    OutlinedCard {
                        SectionTitle("عرض السعر والموافقة")
                        DetailField("التكلفة المقدرة", order.estimatedCost?.egp())
                        DetailField("ملاحظات الفني", order.quoteNote)
                        DetailField("تاريخ العرض", order.quotedAt?.displayDateTime())
                        DetailField("تاريخ الموافقة", order.approvedAt?.displayDateTime())
                        DetailField("سبب الرفض", order.rejectReason)
                        if (order.status == "awaiting_approval" && role in listOf("manager", "reception")) {
                            Text("سجّل قرار العميل بعد التواصل معه.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !state.submitting, onClick = viewModel::approveQuote) { Text("تسجيل الموافقة") }
                                TextButton(enabled = !state.submitting, onClick = { showReject = true }) { Text("رفض العرض") }
                            }
                        }
                    }
                }
                item {
                    OutlinedCard {
                        SectionTitle("قائمة الفحص")
                        if (order.checklist.isEmpty()) Text("لم تُسجّل بنود فحص لهذا الطلب بعد.", color = TextTertiary)
                        order.checklist.sortedBy { it.orderIndex }.forEach { item ->
                            DetailField(item.label, when (item.status) { "done" -> "مكتمل"; "issue" -> "تحتاج متابعة"; else -> "قيد الانتظار" })
                            item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextTertiary) }
                        }
                    }
                }
                item {
                    OutlinedCard {
                        SectionTitle("قطع الغيار")
                        if (order.parts.isEmpty()) Text("لم تُضف قطع غيار لهذا الطلب.", color = TextTertiary)
                        order.parts.forEach { part ->
                            DetailField(part.name, "${part.price.egp()} · ${when (part.status) { "used" -> "مستخدمة"; "unavailable" -> "غير متوفرة"; else -> "مطلوبة" }}")
                        }
                    }
                }
                item {
                    SectionTitle("صور الصيانة")
                    if (order.photos.isEmpty()) Text("لم تُرفع صور لهذا الطلب بعد.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
                order.photos.forEach { photo -> item(key = "photo-${photo.id}") {
                    OutlinedCard {
                        Text(if (photo.kind == "before") "قبل الصيانة" else "بعد الصيانة", style = MaterialTheme.typography.titleSmall)
                        Text(photo.createdAt.displayDateTime(), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        StoredImage(photo.imageData, "صورة ${if (photo.kind == "before") "قبل" else "بعد"} الصيانة")
                    }
                } }
                item {
                    OutlinedCard {
                        SectionTitle("سجل الطلب")
                        if (order.timeline.isEmpty()) Text("لم يُسجّل نشاط إضافي لهذا الطلب بعد.", color = TextTertiary)
                        order.timeline.forEach { activity ->
                            Text(activity.action, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
                            Text(listOfNotNull(activity.actorName, activity.createdAt.displayDateTime()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    }
                }
                state.error?.let { error -> item { Text(error, color = UrgentRed) } }
            }
        }
    }
    if (showEdit) AlertDialog(
        onDismissRequest = { if (!state.submitting) showEdit = false },
        title = { Text("تعديل بيانات الطلب") },
        text = {
            Column {
                OutlinedTextField(value = editIssue, onValueChange = { editIssue = it }, label = { Text("وصف العطل") }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
                Text("الأولوية", modifier = Modifier.padding(top = 12.dp))
                Row {
                    TextButton(enabled = !state.submitting, onClick = { editPriority = "normal" }) { Text(if (editPriority == "normal") "✓ عادية" else "عادية") }
                    TextButton(enabled = !state.submitting, onClick = { editPriority = "urgent" }) { Text(if (editPriority == "urgent") "✓ عاجل" else "عاجل") }
                }
                state.error?.let { Text(it, color = UrgentRed) }
            }
        },
        confirmButton = { TextButton(enabled = !state.submitting, onClick = { viewModel.updateDetails(editIssue, editPriority) }) { Text(if (state.submitting) "جارٍ الحفظ…" else "حفظ التعديل") } },
        dismissButton = { TextButton(enabled = !state.submitting, onClick = { showEdit = false }) { Text("رجوع") } },
    )
    if (showReject) AlertDialog(
        onDismissRequest = { if (!state.submitting) showReject = false },
        title = { Text("تسجيل رفض عرض السعر") },
        text = { OutlinedTextField(value = rejectReason, onValueChange = { rejectReason = it }, label = { Text("سبب الرفض (اختياري)") }) },
        confirmButton = { TextButton(enabled = !state.submitting, onClick = { viewModel.rejectQuote(rejectReason); showReject = false }) { Text("تأكيد الرفض") } },
        dismissButton = { TextButton(onClick = { showReject = false }) { Text("رجوع") } },
    )
}

@Composable
internal fun DetailHeader(title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(CardWhite).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("رجوع") }
            if (onRefresh != null) TextButton(onClick = onRefresh) { Text("تحديث") }
        }
        BrandHeader(compact = true)
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = BrandNavy, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun DetailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

internal fun String.displayDateTime(): String = runCatching {
    val instant = Instant.parse(this)
    "${instant.dateLabelCairo()} · ${instant.timeWithPeriodCairo()}"
}.getOrDefault(this)

@Composable
internal fun StoredImage(data: String, description: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = data) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(data.substringAfter(',', data), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1200).coerceAtLeast(1)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!, description, Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 280.dp).padding(top = 8.dp), contentScale = ContentScale.Fit)
    else Text("تعذّر عرض الصورة.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
}
