package com.markazsayana.app.ui.technician

import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.InventoryItemDto
import com.markazsayana.app.data.remote.ChecklistItemDto
import com.markazsayana.app.data.remote.PartUsedDto
import com.markazsayana.app.data.remote.PhotoDto
import java.io.ByteArrayOutputStream
import java.io.File
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.theme.BorderLight
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.OrangeChipBg
import com.markazsayana.app.ui.theme.OrangeTextDark
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.BrandNavyChipBg
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceDark
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextOnDarkMuted
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.egp
import com.markazsayana.app.util.dateLabelCairo
import com.markazsayana.app.util.timeWithPeriodCairo
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun createPhotoCaptureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

// Decode at a bounded size first; phone camera files can otherwise exhaust memory.
private fun compressCapturedPhoto(context: android.content.Context, uri: Uri): String? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val maxDimension = 1280
    var sampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / sampleSize > maxDimension * 2) sampleSize *= 2
    val original = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null
    val scale = minOf(1f, maxDimension.toFloat() / maxOf(original.width, original.height))
    val resized = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true) else original
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull()
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    val upright = if (matrix.isIdentity) resized else Bitmap.createBitmap(resized, 0, 0, resized.width, resized.height, matrix, true)
    return try {
        ByteArrayOutputStream().use { stream ->
            if (!upright.compress(Bitmap.CompressFormat.JPEG, 75, stream)) null
            else Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        if (upright !== resized) upright.recycle()
        if (resized !== original) resized.recycle()
        original.recycle()
    }
}

@Composable
fun WorkOrderExecutionScreen(
    onBack: () -> Unit,
    onFinish: (workOrderId: Int) -> Unit,
    viewModel: WorkOrderExecutionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showQuoteDialog by remember { mutableStateOf(false) }
    var removingPart by remember { mutableStateOf<PartUsedDto?>(null) }
    val context = LocalContext.current

    LaunchedEffect(state.quoteSent) {
        if (state.quoteSent) onBack()
    }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.workOrder == null -> FullScreenError(state.error ?: "خطأ", onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> {
                val wo = state.workOrder!!
                val busy = state.updating || state.uploadingPhoto || state.submittingQuote
                val editable = wo.status in listOf("in_progress", "paused")
                val canTakePhotos = wo.status in listOf("assigned", "in_progress", "paused")
                val canFinish = wo.status in listOf("in_progress", "completed") && wo.checklist.all { it.status == "done" } && wo.parts.all { it.status == "used" }
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(CardWhite.copy(alpha = 0.12f)).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { Text(text = "→", color = CardWhite, style = MaterialTheme.typography.titleMedium) }
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(text = "${wo.code} · ${wo.statusLabel}", style = MaterialTheme.typography.titleSmall, color = CardWhite)
                            Text(text = if (wo.startedAt == null) "لم يبدأ العمل بعد" else "منذ بدء العمل ${state.elapsed}", style = MaterialTheme.typography.bodySmall, color = TextOnDarkMuted)
                        }
                        if (editable) Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(com.markazsayana.app.ui.theme.AccentOrange)
                                .clickable(enabled = !busy, onClick = viewModel::togglePause)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = if (wo.status == "paused") "استئناف" else "إيقاف مؤقت", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            OutlinedCard {
                                Text(wo.customer.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(wo.customer.phone, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                                Text(wo.customer.address ?: "لم يُسجل عنوان العميل", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                Text("${wo.device.type} · ${wo.device.brand.orEmpty()} ${wo.device.model.orEmpty()}".trim(), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 8.dp))
                                Text(wo.issueDescription, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                                Text(if (wo.device.underWarranty) "الجهاز داخل الضمان" else "الجهاز خارج الضمان", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                    SecondaryButton("اتصال بالعميل", onClick = {
                                        openVisitIntent(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", wo.customer.phone, null)))
                                    }, modifier = Modifier.weight(1f))
                                    if (!wo.customer.address.isNullOrBlank()) SecondaryButton("عنوان الزيارة", onClick = {
                                        openVisitIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(wo.customer.address)}")))
                                    }, modifier = Modifier.weight(1f))
                                }
                                TextButton(onClick = viewModel::load, enabled = !busy) { Text("تحديث بيانات الطلب") }
                            }
                        }
                        if (wo.status == "awaiting_approval") item {
                            OutlinedCard {
                                Text("بانتظار موافقة العميل", color = OrangeTextDark, style = MaterialTheme.typography.titleSmall)
                                wo.estimatedCost?.let { Text("عرض السعر: ${it.egp()}", color = TextPrimary) }
                                wo.quoteNote?.let { Text(it, color = TextTertiary) }
                                Text("الاستقبال يسجّل موافقة العميل أو رفضه. حدّث الطلب لمتابعة الحالة.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                                SecondaryButton("تحديث الطلب", viewModel::load, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "قائمة الفحص — ${wo.device.type}", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardWhite, CardShape)
                                        .border(1.dp, BorderLight, CardShape)
                                        .clip(CardShape),
                                ) {
                                    wo.checklist.forEachIndexed { idx, item ->
                                        ChecklistRow(item, enabled = editable && !busy, onToggle = { viewModel.toggleChecklistItem(item.id, item.status) })
                                        if (idx != wo.checklist.lastIndex) {
                                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(com.markazsayana.app.ui.theme.Divider))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "قطع الغيار المستخدمة", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                    if (editable) Text(
                                        text = "+ إضافة قطعة",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = BrandNavy,
                                        modifier = Modifier.clickable(enabled = !busy, onClick = viewModel::startAddPart),
                                    )
                                }
                                if (wo.parts.isEmpty()) Text("لم تُسجل قطع غيار بعد.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                                wo.parts.forEach { part ->
                                    PartRow(part, editable = editable && !busy,
                                        onRemove = { removingPart = part },
                                        onUseRequested = { viewModel.useRequestedPart(part.id) },
                                    )
                                }

                                if (state.addingPart) {
                                    AddPartInline(
                                        name = state.newPartName,
                                        inventory = state.inventory,
                                        selectedInventoryId = state.selectedInventoryId,
                                        loading = state.loadingInventory,
                                        error = state.inventoryError,
                                        busy = busy,
                                        onSelect = viewModel::selectInventoryItem,
                                        onRetry = viewModel::startAddPart,
                                        onNameChange = viewModel::onNewPartNameChange,
                                        onUse = { viewModel.confirmAddPart("used") },
                                        onRequest = { viewModel.confirmAddPart("requested") },
                                        onCancel = viewModel::cancelAddPart,
                                    )
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "صور قبل / بعد", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                PhotoCaptureSection(
                                    photos = wo.photos,
                                    uploading = busy,
                                    enabled = canTakePhotos,
                                    onCaptureError = viewModel::photoCaptureError,
                                    onPhotoCaptured = viewModel::uploadPhoto,
                                )
                                state.photoError?.let {
                                    Text(text = it, color = UrgentRed, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (editable) item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OrangeChipBg, CardShape)
                                    .clickable(enabled = !busy) { showQuoteDialog = true }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "إرسال عرض سعر لموافقة العميل",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OrangeTextDark,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        state.error?.let { error -> item { Text(error, color = UrgentRed, style = MaterialTheme.typography.bodySmall) } }
                        if (wo.timeline.isNotEmpty()) {
                            item { Text("سجل العمل على الطلب", color = TextPrimary, style = MaterialTheme.typography.titleSmall) }
                            items(wo.timeline, key = { it.id }) { event ->
                                OutlinedCard {
                                    Text(event.action, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    val time = runCatching { Instant.parse(event.createdAt).let { "${it.dateLabelCairo()} · ${it.timeWithPeriodCairo()}" } }.getOrDefault(event.createdAt)
                                    Text(listOfNotNull(event.actorName, time).joinToString(" · "), color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.background(CardWhite).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (wo.status) {
                            "assigned", "paused" -> PrimaryButton(
                                if (state.updating) "جارٍ بدء العمل…" else if (wo.status == "paused") "استئناف العمل" else "وصلت للعميل · بدء العمل",
                                viewModel::togglePause, enabled = !busy,
                            )
                            "in_progress", "completed" -> Column {
                                if (!canFinish) Text("أكمل الفحص وراجع قطع الغيار المطلوبة قبل التسليم.", color = TextTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                                PrimaryButton("مراجعة الفاتورة والتسليم", { onFinish(wo.id) }, backgroundColor = SuccessGreen, enabled = canFinish && !busy)
                            }
                            "closed" -> PrimaryButton("عرض الفاتورة والتوقيع", { onFinish(wo.id) })
                            else -> PrimaryButton("العودة إلى الطلبات", onBack)
                        }
                    }
                }
            }
        }
    }

    removingPart?.let { part ->
        AlertDialog(
            onDismissRequest = { removingPart = null },
            title = { Text("إزالة قطعة من الطلب") },
            text = { Text(if (part.status == "used") "إزالة ${part.name} من الفاتورة وإعادة كميتها إلى المخزون؟" else "إلغاء طلب ${part.name}؟") },
            confirmButton = { TextButton(onClick = { viewModel.removePart(part.id); removingPart = null }) { Text("إزالة") } },
            dismissButton = { TextButton(onClick = { removingPart = null }) { Text("رجوع") } },
        )
    }
    if (showQuoteDialog) {
        SendQuoteDialog(
            submitting = state.submittingQuote,
            error = state.quoteError,
            onDismiss = { showQuoteDialog = false },
            onSend = { cost, note -> viewModel.sendQuote(cost, note) },
        )
    }
}

@Composable
private fun SendQuoteDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (cost: Double, note: String?) -> Unit,
) {
    var cost by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val parsedCost = parseMoney(cost)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("إرسال عرض سعر للعميل") },
        text = {
            Column {
                Text(
                    text = "الاستقبال سيتواصل مع العميل لتأكيد التكلفة ويسجّل قراره قبل استكمال العمل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("التكلفة المتوقعة (ج.م)") },
                    singleLine = true, enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("تفاصيل العرض (اختياري)") }, enabled = !submitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                error?.let {
                    Text(text = it, color = UrgentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedCost != null && parsedCost > 0 && !submitting,
                onClick = { onSend(parsedCost!!, note.trim().ifBlank { null }) },
            ) { Text(if (submitting) "جارٍ الإرسال…" else "إرسال", color = BrandNavy) }
        },
        dismissButton = { TextButton(enabled = !submitting, onClick = onDismiss) { Text("رجوع", color = TextTertiary) } },
    )
}

@Composable
private fun PhotoCaptureSection(
    photos: List<PhotoDto>,
    uploading: Boolean,
    enabled: Boolean,
    onCaptureError: (String) -> Unit,
    onPhotoCaptured: (kind: String, imageData: String) -> Unit,
) {
    val context = LocalContext.current
    var pendingKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri
        val kind = pendingKind
        pendingUri = null
        pendingKind = null
        if (success && uri != null && kind != null) {
            preparing = true
            scope.launch {
                try {
                    val image = withContext(Dispatchers.IO) { runCatching { compressCapturedPhoto(context, uri) }.getOrNull() }
                    if (image != null) onPhotoCaptured(kind, image)
                    else onCaptureError("تعذّر تجهيز الصورة. التقطها مرة أخرى.")
                } finally {
                    preparing = false
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    fun launchCapture() {
        val result = runCatching {
            val uri = createPhotoCaptureUri(context)
            pendingUri = uri
            takePicture.launch(uri)
        }
        if (result.isFailure) {
            pendingUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            pendingUri = null
            pendingKind = null
            onCaptureError("تعذّر فتح الكاميرا. تأكد من وجود تطبيق كاميرا وصلاحية استخدامه.")
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingKind
        if (granted && kind != null) {
            launchCapture()
        } else {
            pendingKind = null
            onCaptureError("اسمح باستخدام الكاميرا من إعدادات التطبيق لالتقاط صور الإصلاح.")
        }
    }

    fun startCapture(kind: String) {
        pendingKind = kind
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCapture()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (enabled) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoCaptureButton(
                label = if (uploading || preparing) "جارٍ الحفظ…" else "صورة قبل الإصلاح",
                enabled = !uploading && !preparing,
                onClick = { startCapture("before") },
                modifier = Modifier.weight(1f),
            )
            PhotoCaptureButton(
                label = if (uploading || preparing) "جارٍ الحفظ…" else "صورة بعد الإصلاح",
                enabled = !uploading && !preparing,
                onClick = { startCapture("after") },
                modifier = Modifier.weight(1f),
            )
        }
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                photos.forEach { photo -> PhotoThumbnail(photo) }
            }
        }
    }
}

@Composable
private fun PhotoCaptureButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CardWhite, CardShape)
            .border(1.dp, BorderMedium, CardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = BrandNavy, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhotoThumbnail(photo: PhotoDto) {
    val bitmap = remember(photo.id) {
        runCatching {
            val bytes = Base64.decode(photo.imageData, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    val label = if (photo.kind == "before") "قبل" else "بعد"
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderMedium, RoundedCornerShape(8.dp)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(vertical = 2.dp),
        ) {
            Text(text = label, color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItemDto, enabled: Boolean, onToggle: () -> Unit) {
    val isIssue = item.status == "issue"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isIssue) OrangeChipBg else CardWhite)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    when (item.status) {
                        "done" -> Modifier.background(SuccessGreen)
                        "issue" -> Modifier.background(UrgentRed)
                        else -> Modifier.border(2.dp, BorderMedium, RoundedCornerShape(6.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (item.status) {
                "done" -> Text(text = "✓", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                "issue" -> Text(text = "!", color = CardWhite, style = MaterialTheme.typography.labelMedium)
                else -> {}
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isIssue) OrangeTextDark else TextPrimary,
            modifier = Modifier.weight(1f),
        )
        item.note?.let {
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = TextTertiary)
        }
    }
}

private fun openVisitIntent(context: android.content.Context, intent: Intent) {
    try { context.startActivity(intent) }
    catch (error: ActivityNotFoundException) { Toast.makeText(context, "لا يوجد تطبيق متاح لفتح هذا الإجراء.", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun PartRow(part: PartUsedDto, editable: Boolean, onRemove: () -> Unit, onUseRequested: () -> Unit) {
    val pending = part.status != "used"
    OutlinedCard(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(part.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(if (pending) "بانتظار توفر القطعة" else "تم استخدامها في الإصلاح", color = if (pending) OrangeTextDark else TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            if (!pending) Text(part.price.egp(), color = TextPrimary)
        }
        if (editable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pending && part.inventoryItemId != null) TextButton(onClick = onUseRequested) { Text("استخدام القطعة") }
            TextButton(onClick = onRemove) { Text(if (pending) "إلغاء الطلب" else "إزالة من الفاتورة", color = UrgentRed) }
        }
    }
}

@Composable
private fun AddPartInline(
    name: String,
    inventory: List<InventoryItemDto>,
    selectedInventoryId: Int?,
    loading: Boolean,
    error: String?,
    busy: Boolean,
    onSelect: (InventoryItemDto) -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onUse: () -> Unit,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.padding(top = 10.dp)) {
        Text("اختر القطعة لتسجيل سعرها وخصم كميتها من المخزون. يمكنك طلب قطعة غير متاحة باسمها.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = name, onValueChange = onNameChange, label = { Text("اسم القطعة أو كودها") }, enabled = !busy,
            singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
        )
        if (loading) Text("جارٍ تحميل المخزون…", color = TextTertiary)
        if (error != null) {
            Text(error, color = UrgentRed)
            TextButton(onClick = onRetry) { Text("إعادة تحميل المخزون") }
        }
        val matches = inventory.filter { name.isBlank() || it.name.contains(name, ignoreCase = true) || it.sku.contains(name, ignoreCase = true) }
        Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            matches.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(if (item.id == selectedInventoryId) BrandNavyChipBg else CardWhite)
                        .clickable(enabled = !busy) { onSelect(item) }.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("${item.sku} · المتاح: ${item.quantity}", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(item.price.egp(), color = BrandNavy, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!loading && matches.isEmpty()) Text("لا توجد قطعة مطابقة في المخزون. يمكنك إرسال طلب باسمها.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PrimaryButton("تسجيل الاستخدام", onUse, enabled = selectedInventoryId != null && inventory.any { it.id == selectedInventoryId && it.quantity > 0 } && !busy, modifier = Modifier.weight(1f))
            PrimaryButton("طلب القطعة", onRequest, enabled = name.isNotBlank() && !busy, backgroundColor = SurfaceDark, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onCancel, enabled = !busy) { Text("رجوع") }
    }
}
