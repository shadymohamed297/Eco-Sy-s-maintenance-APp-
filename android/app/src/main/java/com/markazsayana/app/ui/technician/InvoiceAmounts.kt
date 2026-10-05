package com.markazsayana.app.ui.technician

/** Accepts Egyptian Arabic digits and decimal separators; rejects NaN, exponents and negatives. */
internal fun parseMoney(input: String): Double? {
    val normalized = buildString {
        input.trim().forEach { char ->
            append(when (char) {
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                '٫', ',' -> '.'
                else -> char
            })
        }
    }
    if (!Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(normalized)) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

internal fun invoiceAmountError(labor: String, discount: String, partsTotal: Double): String? {
    val laborFee = parseMoney(labor) ?: return "اكتب المصنعية الفعلية بالجنيه، أو ٠ إذا لم توجد مصنعية."
    val warrantyDiscount = parseMoney(discount) ?: return "اكتب الخصم بالجنيه، أو ٠ إذا لم يوجد خصم."
    if (warrantyDiscount > laborFee + partsTotal) return "الخصم لا يمكن أن يزيد على إجمالي المصنعية وقطع الغيار."
    return null
}
