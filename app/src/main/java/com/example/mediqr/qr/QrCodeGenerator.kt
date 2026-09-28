package com.example.mediqr.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Rendered QR bitmap size in pixels (square). */
const val QR_IMAGE_SIZE_PX = 768

/**
 * Builds the public medical-card URL that goes into the QR code:
 * `{baseUrl}/card/{cardId}`.
 *
 * This is the ONLY kind of content the QR code ever encodes - never medical
 * details, never credentials. [baseUrl] is configured at build time
 * (BuildConfig.PUBLIC_CARD_BASE_URL) and a trailing slash is tolerated.
 */
fun buildPublicCardUrl(baseUrl: String, cardId: String): String =
    "${baseUrl.trimEnd('/')}/card/$cardId"

/**
 * Encodes [content] as a QR code into a square [BitMatrix] of [sizePx].
 *
 * Pure ZXing (no Android types) so the exact production encoding can be
 * round-trip tested in plain JVM unit tests. Error correction level M and the
 * standard 4-module quiet zone keep the code scannable from a phone camera
 * even when displayed small or photographed off a screen.
 */
fun encodeQrMatrix(content: String, sizePx: Int = QR_IMAGE_SIZE_PX): BitMatrix {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 4,
    )
    return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
}
