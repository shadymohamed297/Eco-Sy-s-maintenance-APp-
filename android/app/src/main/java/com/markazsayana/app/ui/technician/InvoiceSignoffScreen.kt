package com.markazsayana.app.ui.technician

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.data.remote.InvoicePreviewResponse
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.components.PrimaryButton
import com.markazsayana.app.ui.components.SecondaryButton
import com.markazsayana.app.ui.theme.BorderMedium
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.CardShape
import com.markazsayana.app.ui.theme.CardWhite
import com.markazsayana.app.ui.theme.Divider
import com.markazsayana.app.ui.theme.SuccessGreen
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextPrimary
import com.markazsayana.app.ui.theme.TextSecondary
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.egp
import com.markazsayana.app.util.arabic

private val paymentMethods = listOf("cash" to "نقدي", "card" to "بطاقة", "account" to "على الحساب")

private fun invoiceShareText(wo: WorkOrderDto, invoice: InvoicePreviewResponse, paymentMethod: String): String = buildString {
    appendLine("الهندسية المتحدة · الحدايق")
    appendLine("فاتورة صيانة ${wo.code}")
    appendLine("العميل: ${wo.customer.name}")
    appendLine("الجهاز: ${wo.device.type} ${wo.device.brand.orEmpty()} ${wo.device.model.orEmpty()}".trim())
    appendLine("المصنعية: ${invoice.laborFee.egp()}")
    invoice.parts.forEach { appendLine("${it.name}: ${it.price.egp()}") }
    appendLine("قطع الغيار: ${invoice.partsTotal.egp()}")
    if (invoice.warrantyDiscount > 0) appendLine("الخصم: ${invoice.warrantyDiscount.egp()}")
    appendLine("قبل الضريبة: ${invoice.subtotal.egp()}")
    appendLine("الضريبة: ${(invoice.total - invoice.subtotal).egp()}")
    appendLine("الإجمالي: ${invoice.total.egp()}")
    append("طريقة الدفع: ${paymentMethods.firstOrNull { it.first == paymentMethod }?.second ?: paymentMethod}")
}

@Composable
fun InvoiceSignoffScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    viewModel: InvoiceSignoffViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var signatureSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

    Scaffold(containerColor = SurfaceScreen) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.workOrder == null || state.invoice == null -> FullScreenError(
                state.error ?: "تعذّر تحميل بيانات الفاتورة.", onRetry = viewModel::load, modifier = Modifier.padding(padding),
            )
            else -> {
                val wo = state.workOrder!!
                val invoice = state.invoice!!
                val amountError = invoiceAmountError(state.laborFee, state.warrantyDiscount, invoice.partsTotal)
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(CardWhite).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceScreen).clickable(enabled = !state.submitting, onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { Text("→", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
                        Column {
                            Text(if (state.closed) "فاتورة الطلب" else "مراجعة الفاتورة والتسليم", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("الهندسية المتحدة · الحدايق", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            OutlinedCard {
                                Text(wo.code, style = MaterialTheme.typography.titleSmall, color = BrandNavy)
                                Text(wo.customer.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text("${wo.device.type} · ${wo.device.brand.orEmpty()} ${wo.device.model.orEmpty()}".trim(), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                if (state.closed) Text("تم تسليم الطلب وحفظ الفاتورة", color = SuccessGreen, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        if (!state.closed) {
                            item {
                                OutlinedCard {
                                    Text("المبالغ الفعلية", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("اكتب المصنعية المتفق عليها. إذا كانت الزيارة بدون مصنعية، اكتب ٠.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                    MoneyField(state.laborFee, viewModel::onLaborFeeChange, "المصنعية (ج.م)", !state.submitting)
                                    MoneyField(state.warrantyDiscount, viewModel::onWarrantyDiscountChange, "الخصم المعتمد (ج.م)", !state.submitting)
                                    if (amountError != null) Text(amountError, style = MaterialTheme.typography.bodySmall, color = UrgentRed, modifier = Modifier.padding(top = 8.dp))
                                    if (state.previewing) Text("جارٍ حساب الفاتورة…", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                    if (state.error != null && amountError == null && !state.invoiceMatchesAmounts && !state.previewing) {
                                        SecondaryButton("إعادة حساب الفاتورة", viewModel::refreshPreview, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedCard {
                                Text(if (state.closed || state.invoiceMatchesAmounts) "تفاصيل الفاتورة" else "تفاصيل قطع الغيار · أدخل المبالغ لإظهار الإجمالي", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                invoice.parts.forEach { InvoiceLine(it.name, it.price.egp()) }
                                if (invoice.parts.isEmpty()) Text("لم تُستخدم قطع غيار في هذا الطلب.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                InvoiceLine("إجمالي قطع الغيار", invoice.partsTotal.egp())
                                if (state.closed || state.invoiceMatchesAmounts) {
                                    InvoiceLine("المصنعية", invoice.laborFee.egp())
                                    if (invoice.warrantyDiscount > 0) InvoiceLine("الخصم", "−${invoice.warrantyDiscount.egp()}", SuccessGreen)
                                    InvoiceLine("الإجمالي قبل الضريبة", invoice.subtotal.egp())
                                    InvoiceLine("الضريبة", (invoice.total - invoice.subtotal).egp())
                                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Divider))
                                    InvoiceLine("المبلغ المطلوب", invoice.total.egp(), BrandNavy)
                                }
                            }
                        }
                        item {
                            Text("طريقة الدفع", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            if (state.closed) {
                                Text(paymentMethods.firstOrNull { it.first == state.paymentMethod }?.second ?: state.paymentMethod, color = TextPrimary)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    paymentMethods.forEach { (key, label) ->
                                        val selected = key == state.paymentMethod
                                        Box(
                                            modifier = Modifier.weight(1f).clip(CardShape)
                                                .background(if (selected) BrandNavy else CardWhite)
                                                .border(1.dp, if (selected) BrandNavy else BorderMedium, CardShape)
                                                .clickable(enabled = !state.submitting) { viewModel.selectPaymentMethod(key) }.padding(vertical = 13.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(label, color = if (selected) CardWhite else TextPrimary, style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("توقيع العميل باستلام الجهاز", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                if (state.closed) {
                                    SavedSignature(wo.invoice?.signatureImage)
                                    Text("المستلم: ${wo.invoice?.signatureName ?: state.signatureName}", color = TextPrimary)
                                } else {
                                    SignaturePad(
                                        drawing = state.signature, strokeWidth = strokeWidth, enabled = !state.submitting,
                                        onBegin = viewModel::beginStroke, onPoint = viewModel::appendStroke,
                                        onFinish = viewModel::finishStroke, onSizeChanged = { signatureSize = it },
                                    )
                                    TextButton(onClick = viewModel::clearSignature, enabled = !state.submitting && !state.signature.isEmpty) { Text("مسح التوقيع وإعادته") }
                                    OutlinedTextField(
                                        value = state.signatureName, onValueChange = viewModel::onSignatureNameChange,
                                        label = { Text("اسم المستلم") }, singleLine = true, enabled = !state.submitting,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy), modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        item {
                            Text("تقييم العميل للخدمة", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            if (state.closed) {
                                Text(state.customerRating?.let { "${it.arabic()} من ٥" } ?: "لم يسجّل العميل تقييمًا.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("اختياري · سجّل رأي العميل إذا أراد تقييم الخدمة.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    (1..5).forEach { rating ->
                                        TextButton(onClick = { viewModel.selectCustomerRating(rating) }, enabled = !state.submitting) {
                                            Text("${rating.arabic()} ★", color = if (state.customerRating == rating) BrandNavy else TextTertiary)
                                        }
                                    }
                                }
                                if (state.customerRating != null) TextButton(onClick = { viewModel.selectCustomerRating(null) }, enabled = !state.submitting) { Text("بدون تقييم") }
                            }
                        }
                        state.error?.let { error -> item { Text(error, color = UrgentRed, style = MaterialTheme.typography.bodySmall) } }
                        if (state.closed) {
                            item {
                                SecondaryButton("مشاركة الفاتورة", onClick = {
                                    try {
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, invoiceShareText(wo, invoice, state.paymentMethod))
                                        }, "مشاركة الفاتورة"))
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "لا يوجد تطبيق متاح لمشاركة الفاتورة.", Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().background(CardWhite).padding(16.dp)) {
                        if (state.closed) {
                            PrimaryButton("العودة إلى الطلبات", onClosed, backgroundColor = SuccessGreen)
                        } else {
                            PrimaryButton(
                                text = if (state.submitting) "جارٍ حفظ التسليم…" else "تأكيد التسليم وحفظ الفاتورة",
                                onClick = {
                                    val image = signatureBitmapBase64(state.signature, signatureSize.width, signatureSize.height, strokeWidth)
                                    viewModel.confirm(image)
                                }, backgroundColor = SuccessGreen,
                                enabled = !state.submitting && !state.previewing && state.signature.activeStroke == null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandNavy),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun InvoiceLine(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun SavedSignature(encoded: String?) {
    val bitmap = remember(encoded) {
        runCatching {
            if (encoded.isNullOrBlank()) null else {
                val bytes = Base64.decode(encoded.substringAfter(",", encoded), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap == null) Text("لا يوجد توقيع مصوّر محفوظ.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
    else Image(bitmap, "توقيع العميل المحفوظ", modifier = Modifier.fillMaxWidth().height(180.dp).background(CardWhite), contentScale = ContentScale.Fit)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignaturePad(
    drawing: SignatureDrawing,
    strokeWidth: Float,
    enabled: Boolean,
    onBegin: (SignaturePoint) -> Unit,
    onPoint: (SignaturePoint) -> Unit,
    onFinish: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
) {
    val begin by rememberUpdatedState(onBegin)
    val point by rememberUpdatedState(onPoint)
    val finish by rememberUpdatedState(onFinish)
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(CardShape).background(CardWhite)
            .border(1.dp, BorderMedium, CardShape).onSizeChanged(onSizeChanged)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    SignaturePoint.fromPixels(down.position.x, down.position.y, size.width, size.height)?.let(begin)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.historical.forEach { sample ->
                                SignaturePoint.fromPixels(sample.position.x, sample.position.y, size.width, size.height)?.let(point)
                            }
                            SignaturePoint.fromPixels(change.position.x, change.position.y, size.width, size.height)?.let(point)
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        finish()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas -> drawSignature(canvas.nativeCanvas, drawing, size.width.toInt(), size.height.toInt(), strokeWidth) }
        }
        if (drawing.isEmpty) Text("وقّع هنا", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.align(Alignment.Center))
    }
}
