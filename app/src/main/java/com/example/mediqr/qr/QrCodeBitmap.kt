package com.example.mediqr.qr

import android.graphics.Bitmap
import com.google.zxing.common.BitMatrix

/**
 * Renders this matrix as an ARGB [Bitmap]: black modules on a white background
 * (the white also forms the quiet zone produced by the encoder).
 *
 * Kept separate from [encodeQrMatrix] so the encoder stays free of Android
 * types and remains unit-testable on the JVM.
 */
fun BitMatrix.toQrBitmap(): Bitmap {
    val matrixWidth = width
    val matrixHeight = height
    val pixels = IntArray(matrixWidth * matrixHeight) { index ->
        val x = index % matrixWidth
        val y = index / matrixWidth
        if (get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
}
