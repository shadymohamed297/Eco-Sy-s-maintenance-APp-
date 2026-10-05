package com.markazsayana.app.ui.technician

import org.junit.Assert.*
import org.junit.Test

class SignatureDrawingTest {
    @Test fun liftingThePenKeepsSeparateStrokesAndBothStartingPoints() {
        val first = SignaturePoint(0.1f, 0.2f)
        val second = SignaturePoint(0.8f, 0.7f)
        val drawing = SignatureDrawing().begin(first).append(SignaturePoint(0.2f, 0.3f)).finish()
            .begin(second).append(SignaturePoint(0.9f, 0.8f)).finish()
        assertEquals(2, drawing.strokes.size)
        assertEquals(first, drawing.strokes[0].first())
        assertEquals(second, drawing.strokes[1].first())
        assertNull(drawing.activeStroke)
    }

    @Test fun cancellingAStrokePreventsLaterMovementFromJoiningIt() {
        val drawing = SignatureDrawing().begin(SignaturePoint(0.2f, 0.2f)).finish()
        assertEquals(drawing, drawing.append(SignaturePoint(0.7f, 0.7f)))
        assertEquals(2, drawing.begin(SignaturePoint(0.8f, 0.8f)).strokes.size)
    }

    @Test fun singleTapIsARealDotAndClearRemovesAllStrokes() {
        assertFalse(SignatureDrawing().begin(SignaturePoint(0.5f, 0.5f)).finish().isEmpty)
        assertTrue(SignatureDrawing().isEmpty)
    }

    @Test fun draggingOutsidePadUsesTheSameClampedBoundsForDisplayAndExport() {
        assertEquals(SignaturePoint(0f, 1f), SignaturePoint.fromPixels(-20f, 300f, 200, 100))
        assertEquals(SignaturePoint(0.5f, 0.5f), SignaturePoint.fromPixels(100f, 50f, 200, 100))
        assertNull(SignaturePoint.fromPixels(10f, 20f, 0, 100))
        assertNull(SignaturePoint.fromPixels(Float.NaN, 20f, 100, 100))
    }

    @Test fun publishedDrawingRemainsImmutableWhileAppendingNewPoints() {
        val before = SignatureDrawing().begin(SignaturePoint(0.1f, 0.1f))
        val after = before.append(SignaturePoint(0.2f, 0.2f))
        assertEquals(1, before.strokes.single().size)
        assertEquals(2, after.strokes.single().size)
    }
}
