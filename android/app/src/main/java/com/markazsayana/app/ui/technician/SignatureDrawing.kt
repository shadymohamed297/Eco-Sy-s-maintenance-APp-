package com.markazsayana.app.ui.technician

/** Coordinates are relative to the pad, so a resize never stretches the stored bounds. */
data class SignaturePoint(val x: Float, val y: Float) {
    companion object {
        fun fromPixels(x: Float, y: Float, width: Int, height: Int): SignaturePoint? {
            if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
            return SignaturePoint((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f))
        }
    }
}

data class SignatureDrawing(
    val strokes: List<List<SignaturePoint>> = emptyList(),
    val activeStroke: Int? = null,
) {
    val isEmpty: Boolean get() = strokes.all { it.isEmpty() }

    fun begin(point: SignaturePoint) = copy(strokes = strokes + listOf(listOf(point)), activeStroke = strokes.size)

    fun append(point: SignaturePoint): SignatureDrawing {
        val index = activeStroke ?: return this
        val stroke = strokes.getOrNull(index) ?: return finish()
        if (stroke.lastOrNull() == point) return this
        return copy(strokes = strokes.mapIndexed { i, points -> if (i == index) points + point else points })
    }

    /** Lifting or cancelling a pointer finishes this stroke, never connecting it to the next one. */
    fun finish() = copy(activeStroke = null)
}
