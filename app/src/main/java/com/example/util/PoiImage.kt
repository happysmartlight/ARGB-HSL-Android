package com.example.util

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Xử lý ảnh + encode BMP cho hiệu ứng POI (POV trên thanh quay).
 * Bám sát desktop tool: xem docs/poi-preset-workflow.md §3, §15, §16.
 */
object PoiImage {
    const val POI_MIN_W = 15
    const val POI_MAX_W = 145
    const val POI_RECOMMENDED_MAX_W = 72
    const val POI_MAX_SIZE = 63 * 1024 // 63 KB cho BMP POI
    const val POI_EFFECT_NAME = "Poi HSL"

    /**
     * Xoay phải 90° + resize theo CHIỀU RỘNG = [targetWidth], giữ tỉ lệ (bilinear).
     * Tương đương ImageProcessor.rotate_resize_width() bên desktop.
     */
    fun rotateResizeWidth(src: Bitmap, targetWidth: Int): Bitmap {
        // 1) xoay phải 90° (clockwise) — khớp PIL img.rotate(-90)
        val m = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)

        // 2) resize theo chiều rộng, giữ tỉ lệ
        val newH = Math.round(targetWidth.toFloat() / rotated.width * rotated.height)
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(rotated, targetWidth, newH, true)
    }

    /**
     * Encode Bitmap -> BMP 24-bit (bottom-up, BGR, pad 4 byte/hàng).
     * Output BYTE-IDENTICAL với BMP do PIL.save(.., "BMP") tạo ra.
     */
    fun encodeBmp24(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val rowSize = (w * 3 + 3) / 4 * 4          // pad bội số 4
        val pixelArraySize = rowSize * h
        val fileSize = 54 + pixelArraySize

        val out = ByteArray(fileSize)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        // --- BITMAPFILEHEADER (14 byte) ---
        buf.put('B'.code.toByte()); buf.put('M'.code.toByte())
        buf.putInt(fileSize)        // file size
        buf.putInt(0)               // reserved
        buf.putInt(54)              // offset tới pixel data

        // --- BITMAPINFOHEADER (40 byte) ---
        buf.putInt(40)              // header size
        buf.putInt(w)               // width
        buf.putInt(h)               // height DƯƠNG => bottom-up
        buf.putShort(1)             // planes
        buf.putShort(24)            // bits/pixel
        buf.putInt(0)               // compression = BI_RGB
        buf.putInt(pixelArraySize)
        buf.putInt(3780)            // X ppm (96 DPI) — khớp PIL để byte-identical
        buf.putInt(3780)            // Y ppm
        buf.putInt(0)               // colors used
        buf.putInt(0)               // important colors

        // --- Pixel data: bottom-up, BGR ---
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)    // ARGB, top-down
        var pos = 54
        for (y in h - 1 downTo 0) {                // bottom-up
            var rowPos = pos
            val base = y * w
            for (x in 0 until w) {
                val c = pixels[base + x]
                out[rowPos++] = (c and 0xFF).toByte()          // B
                out[rowPos++] = ((c shr 8) and 0xFF).toByte()  // G
                out[rowPos++] = ((c shr 16) and 0xFF).toByte() // R
            }
            pos += rowSize                          // bytes pad đã = 0 sẵn
        }
        return out
    }

    /** Kiểm tra giới hạn 63 KB trước khi upload. */
    fun bmpFitsLimit(bmpBytes: ByteArray) = bmpBytes.size < POI_MAX_SIZE
}
