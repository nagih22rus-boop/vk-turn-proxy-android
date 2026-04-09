package com.vkturn.proxy.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object QrUtils {
    
    fun generateQrCode(text: String, size: Int = 512, context: Context? = null): Bitmap? {
        val levels = listOf(ErrorCorrectionLevel.H, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)
        
        for (level in levels) {
            try {
                val hints = mapOf(
                    EncodeHintType.MARGIN to 0,
                    EncodeHintType.ERROR_CORRECTION to level
                )
                // Encode at size 0 to get the pure, minimal QR matrix
                val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
                
                val matrixWidth = bitMatrix.width
                val matrixHeight = bitMatrix.height
                val scale = (size / matrixWidth).coerceAtLeast(1)
                val qrWidth = matrixWidth * scale
                val qrHeight = matrixHeight * scale

                // Add a uniform white padding (2 cells thick) around the QR code
                val qrPadding = scale * 2
                val finalWidth = qrWidth + qrPadding * 2
                val finalHeight = qrHeight + qrPadding * 2

                val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                for (x in 0 until qrWidth) {
                    for (y in 0 until qrHeight) {
                        if (bitMatrix.get(x / scale, y / scale)) {
                            bitmap.setPixel(x + qrPadding, y + qrPadding, Color.BLACK)
                        }
                    }
                }

                // Only embed the icon if we have high enough error correction redundancy
                if (context != null && level == ErrorCorrectionLevel.H) {
                    try {
                        val appIcon: Drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
                        
                        val iconSize = (finalWidth * 0.25f).toInt() // 25% of the entire QR size
                        val left = (finalWidth - iconSize) / 2
                        val top = (finalHeight - iconSize) / 2

                        val iconBitmap = appIcon.toBitmap(iconSize, iconSize)
                        canvas.drawBitmap(iconBitmap, left.toFloat(), top.toFloat(), null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                return bitmap
            } catch (e: com.google.zxing.WriterException) {
                if (level == levels.last()) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return null
    }

    suspend fun scanQrFromBitmap(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty() && !continuation.isCompleted) {
                        continuation.resume(barcodes[0].rawValue)
                    } else if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!continuation.isCompleted) {
                continuation.resume(null)
            }
        }
    }
}
