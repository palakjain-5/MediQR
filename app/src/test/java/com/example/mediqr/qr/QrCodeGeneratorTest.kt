package com.example.mediqr.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the production QR encoding: whatever the app puts on
 * screen must decode (as a phone camera would) back to exactly the public card
 * URL - and to nothing else.
 */
class QrCodeGeneratorTest {

    private val baseUrl = "https://your-domain.example.com"
    private val cardId = "6f1d9a4e-2b7c-4e8a-9d3f-51a2b3c4d5e6"

    @Test
    fun `public card url follows the expected structure`() {
        assertEquals(
            "https://your-domain.example.com/card/6f1d9a4e-2b7c-4e8a-9d3f-51a2b3c4d5e6",
            buildPublicCardUrl(baseUrl, cardId),
        )
    }

    @Test
    fun `trailing slash on the base url does not create a double slash`() {
        assertEquals(
            "https://your-domain.example.com/card/abc",
            buildPublicCardUrl("https://your-domain.example.com/", "abc"),
        )
    }

    @Test
    fun `qr code decodes back to exactly the public card url`() {
        val url = buildPublicCardUrl(baseUrl, cardId)

        val decoded = decodeQr(encodeQrMatrix(url))

        assertEquals(url, decoded)
    }

    @Test
    fun `qr code contains only a public url - no medical data`() {
        val url = buildPublicCardUrl(baseUrl, cardId)

        val decoded = decodeQr(encodeQrMatrix(url))

        // The payload must be exactly one https URL to /card/{id}.
        assertTrue(decoded.startsWith("https://"))
        assertTrue(decoded.contains("/card/$cardId"))
        assertEquals(url, decoded)
    }

    @Test
    fun `the same card id always encodes to the same public url`() {
        // Stability requirement: opening the screen repeatedly must not change
        // the QR content; only an intentional id rotation may do that.
        val first = buildPublicCardUrl(baseUrl, cardId)
        val second = buildPublicCardUrl(baseUrl, cardId)

        assertEquals(first, second)
        assertEquals(decodeQr(encodeQrMatrix(first)), decodeQr(encodeQrMatrix(second)))
    }

    /** Decodes a matrix the way a scanner would: pixels -> binarizer -> text. */
    private fun decodeQr(matrix: BitMatrix): String {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader()
            .decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
            .text
    }
}
