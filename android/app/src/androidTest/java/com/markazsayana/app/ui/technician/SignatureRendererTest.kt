package com.markazsayana.app.ui.technician

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignatureRendererTest {
    @Test fun exportedPngHasNoBridgeBetweenSeparatePenStrokes() {
        val drawing = SignatureDrawing()
            .begin(SignaturePoint(0.1f, 0.25f)).append(SignaturePoint(0.3f, 0.25f)).finish()
            .begin(SignaturePoint(0.7f, 0.75f)).append(SignaturePoint(0.9f, 0.75f)).finish()
        val png = Base64.decode(signatureBitmapBase64(drawing, 100, 100, 4f), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        try {
            assertEquals(100, bitmap.width)
            assertEquals(100, bitmap.height)
            assertNotEquals(Color.WHITE, bitmap.getPixel(20, 25))
            assertNotEquals(Color.WHITE, bitmap.getPixel(80, 75))
            assertEquals("A pen lift must leave the space between strokes empty", Color.WHITE, bitmap.getPixel(50, 50))
        } finally { bitmap.recycle() }
    }

    @Test fun singleTapExportsAsADotAndClearExportsNothing() {
        val drawing = SignatureDrawing().begin(SignaturePoint(0.5f, 0.5f)).finish()
        val png = Base64.decode(signatureBitmapBase64(drawing, 100, 100, 4f), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        try {
            assertNotEquals(Color.WHITE, bitmap.getPixel(50, 50))
            assertEquals(Color.WHITE, bitmap.getPixel(10, 10))
            assertNull(signatureBitmapBase64(SignatureDrawing(), 100, 100, 4f))
        } finally { bitmap.recycle() }
    }
}
