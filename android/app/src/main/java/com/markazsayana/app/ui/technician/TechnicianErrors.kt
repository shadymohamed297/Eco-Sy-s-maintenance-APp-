package com.markazsayana.app.ui.technician

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

internal fun technicianRequestError(error: Exception, fallback: String): String {
    if (error is IOException) return "تعذّر الاتصال بالسيرفر. تأكد من الإنترنت وحاول مرة أخرى."
    if (error !is HttpException) return fallback
    val message = runCatching {
        Json.parseToJsonElement(error.response()?.errorBody()?.string().orEmpty()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull().orEmpty()
    if (message.any { it in '\u0600'..'\u06ff' }) return message
    return when {
        "checklist" in message.lowercase() -> "أكمل بنود الفحص قبل تسليم الطلب."
        "part" in message.lowercase() -> "راجع قطع الغيار المطلوبة قبل تسليم الطلب."
        "discount" in message.lowercase() -> "راجع قيمة الخصم؛ يجب ألا تزيد على إجمالي الفاتورة."
        error.code() == 401 -> "انتهت الجلسة. سجّل الدخول مرة أخرى."
        error.code() == 403 -> "لا تملك صلاحية تنفيذ هذا الإجراء على الطلب."
        error.code() == 404 -> "الطلب غير موجود. ارجع إلى قائمة الطلبات وحدّثها."
        error.code() == 409 -> "تغيّرت حالة الطلب. حدّثه وراجع بياناته قبل المتابعة."
        else -> fallback
    }
}
