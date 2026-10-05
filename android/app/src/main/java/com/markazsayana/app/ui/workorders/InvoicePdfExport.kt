package com.markazsayana.app.ui.workorders

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Base64
import com.markazsayana.app.R
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.util.egp
import java.io.File

/** Exports the amounts and signature saved when this order was closed. */
internal fun exportStoredInvoice(context: Context, order: WorkOrderDto): File {
    val invoice = requireNotNull(order.invoice)
    val document = PdfDocument()
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 38, 77); textSize = 13f }
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
    var cursor = 34f

    fun finishPage() { document.finishPage(page) }
    fun nextPage() {
        finishPage()
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        cursor = 34f
    }
    fun line(text: String, heading: Boolean = false) {
        textPaint.textSize = if (heading) 18f else 13f
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, 511)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        if (cursor + layout.height + 12 > 800) nextPage()
        page.canvas.save()
        page.canvas.translate(42f, cursor)
        layout.draw(page.canvas)
        page.canvas.restore()
        cursor += layout.height + if (heading) 18 else 10
    }

    try {
        BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo_original)?.let { logo ->
            page.canvas.drawBitmap(logo, null, RectF(42f, 28f, 152f, 106.55f), null)
        }
        line("الهندسية المتحدة · الحدايق", heading = true)
        cursor = cursor.coerceAtLeast(118f)
        line("فاتورة طلب الصيانة ${order.code}", heading = true)
        line("العميل: ${order.customer.name}")
        line("الهاتف: ${order.customer.phone}")
        line("الجهاز: ${listOfNotNull(order.device.type, order.device.brand, order.device.model).joinToString(" · ")}")
        order.closedAt?.let { line("تاريخ التسليم: ${it.displayDateTime()}") }
        line("أجرة الكشف والعمالة: ${invoice.laborFee.egp()}")
        order.parts.filter { it.status == "used" }.forEach { line("${it.name}: ${it.price.egp()}") }
        line("إجمالي قطع الغيار: ${invoice.partsTotal.egp()}")
        line("خصم الضمان: ${invoice.warrantyDiscount.egp()}")
        line("المجموع قبل الضريبة: ${invoice.subtotal.egp()}")
        line("الضريبة: ${(invoice.total - invoice.subtotal).coerceAtLeast(0.0).egp()}")
        line("الإجمالي: ${invoice.total.egp()}", heading = true)
        line("طريقة الدفع: ${when (invoice.paymentMethod) { "cash" -> "نقدي"; "card" -> "بطاقة"; "account" -> "على الحساب"; else -> "لم تُسجّل" }}")
        invoice.signatureName?.let { line("توقيع العميل باستلام الجهاز: $it") }
        invoice.signedAt?.let { line("تاريخ التوقيع: ${it.displayDateTime()}") }
        invoice.signatureImage?.takeIf { it.isNotBlank() }?.let { data ->
            val bitmap = runCatching {
                val bytes = Base64.decode(data.substringAfter(',', data), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            bitmap?.let {
                if (cursor + 132 > 800) nextPage()
                val ratio = it.width.toFloat() / it.height.coerceAtLeast(1)
                val height = 120f.coerceAtMost(511f / ratio)
                page.canvas.drawBitmap(it, null, RectF(553f - height * ratio, cursor, 553f, cursor + height), null)
                cursor += height + 12
            }
        }
        finishPage()
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val filename = order.code.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { order.id.toString() }
        return File(directory, "invoice-$filename.pdf").also { file -> file.outputStream().use(document::writeTo) }
    } finally {
        document.close()
    }
}
