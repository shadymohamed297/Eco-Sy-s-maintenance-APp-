package com.markazsayana.app.ui.workorders

import android.content.Intent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.markazsayana.app.ui.components.FullScreenError
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.components.OutlinedCard
import com.markazsayana.app.ui.theme.BrandNavy
import com.markazsayana.app.ui.theme.SurfaceScreen
import com.markazsayana.app.ui.theme.TextTertiary
import com.markazsayana.app.ui.theme.UrgentRed
import com.markazsayana.app.util.egp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkOrderInvoiceScreen(onBack: () -> Unit, viewModel: WorkOrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    RefreshOnResume(viewModel::load)
    Scaffold(containerColor = SurfaceScreen, topBar = { DetailHeader("الفاتورة المسجّلة", onBack, viewModel::load) }) { padding ->
        val order = state.workOrder
        val invoice = order?.invoice
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            order == null -> FullScreenError(state.error ?: "تعذّر تحميل الفاتورة", viewModel::load, Modifier.padding(padding))
            invoice == null -> FullScreenError("لم تُصدر فاتورة لهذا الطلب بعد.", viewModel::load, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedCard {
                        Text(order.code, style = MaterialTheme.typography.titleLarge, color = BrandNavy)
                        DetailField("العميل", order.customer.name)
                        DetailField("الجهاز", listOfNotNull(order.device.type, order.device.brand, order.device.model).joinToString(" · "))
                        DetailField("تاريخ التسليم", order.closedAt?.displayDateTime())
                    }
                }
                item {
                    Button(enabled = !sharing, onClick = {
                        sharing = true
                        shareError = null
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) { exportStoredInvoice(context, order) }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "مشاركة فاتورة ${order.code}"))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                shareError = "تعذّر تجهيز الفاتورة للمشاركة. حاول مرة أخرى."
                            } finally {
                                sharing = false
                            }
                        }
                    }) { Text(if (sharing) "جارِ تجهيز الفاتورة…" else "مشاركة الفاتورة PDF") }
                    shareError?.let { Text(it, color = UrgentRed) }
                }
                item {
                    OutlinedCard {
                        SectionTitle("تفاصيل الفاتورة")
                        DetailField("أجرة الكشف والعمالة", invoice.laborFee.egp())
                        order.parts.filter { it.status == "used" }.forEach { DetailField(it.name, it.price.egp()) }
                        DetailField("إجمالي قطع الغيار", invoice.partsTotal.egp())
                        DetailField("خصم الضمان", invoice.warrantyDiscount.egp())
                        DetailField("المجموع قبل الضريبة", invoice.subtotal.egp())
                        DetailField("الضريبة", (invoice.total - invoice.subtotal).coerceAtLeast(0.0).egp())
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text("الإجمالي: ${invoice.total.egp()}", style = MaterialTheme.typography.titleLarge, color = BrandNavy)
                        DetailField("طريقة الدفع", when (invoice.paymentMethod) { "card" -> "بطاقة"; "cash" -> "نقدي"; "account" -> "على الحساب"; else -> "لم تُسجّل" })
                    }
                }
                item {
                    OutlinedCard {
                        SectionTitle("توقيع العميل على الاستلام")
                        DetailField("اسم العميل", invoice.signatureName)
                        DetailField("تاريخ التوقيع", invoice.signedAt?.displayDateTime())
                        if (!invoice.signatureImage.isNullOrBlank()) StoredImage(invoice.signatureImage, "توقيع العميل على استلام الجهاز")
                        else Text("لا توجد صورة توقيع مسجّلة لهذا الطلب.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        invoice.customerRating?.let { DetailField("تقييم العميل", "$it من ٥") }
                    }
                }
            }
        }
    }
}
