package com.markazsayana.app.ui.technician

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Used for both the visible pad and its PNG; every stroke has its own path. */
internal fun drawSignature(canvas: Canvas, drawing: SignatureDrawing, width: Int, height: Int, strokeWidth: Float) {
    if (width <= 0 || height <= 0) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 38, 77)
        this.strokeWidth = strokeWidth
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val inset = (strokeWidth / 2f).coerceAtMost(minOf(width, height) / 2f)
    fun SignaturePoint.pixelX() = (x * width).coerceIn(inset, width - inset)
    fun SignaturePoint.pixelY() = (y * height).coerceIn(inset, height - inset)
    drawing.strokes.filter { it.isNotEmpty() }.forEach { points ->
        if (points.size == 1) {
            canvas.drawCircle(points[0].pixelX(), points[0].pixelY(), strokeWidth / 2f, Paint(paint).apply { style = Paint.Style.FILL })
        } else {
            val path = Path().apply {
                moveTo(points[0].pixelX(), points[0].pixelY())
                for (index in 1 until points.size) {
                    val previous = points[index - 1]
                    val current = points[index]
                    quadTo(previous.pixelX(), previous.pixelY(), (previous.pixelX() + current.pixelX()) / 2f, (previous.pixelY() + current.pixelY()) / 2f)
                }
                lineTo(points.last().pixelX(), points.last().pixelY())
            }
            canvas.drawPath(path, paint)
        }
    }
}

internal fun signatureBitmapBase64(drawing: SignatureDrawing, width: Int, height: Int, strokeWidth: Float): String? {
    if (drawing.isEmpty || width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawSignature(canvas, drawing, width, height, strokeWidth)
        ByteArrayOutputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) null
            else Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        bitmap.recycle()
    }
}
