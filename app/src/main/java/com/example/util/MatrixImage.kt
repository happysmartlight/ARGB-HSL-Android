package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.example.util.gif.AnimatedGifEncoder
import com.example.util.gif.GifDecoder
import java.io.ByteArrayOutputStream

/**
 * Xử lý ảnh + encode GIF cho hiệu ứng "Cờ LED" (ma trận W×H, fx=53).
 * Bám sát desktop tool: xem docs/poi-preset-workflow.md §11, §19.
 */
object MatrixImage {
    const val MAX_PIXELS = 256 * 256 // 65536
    const val MAX_DIM = 256
    const val FX_GIF = 53            // hiệu ứng GIF của firmware (hardcode)

    private const val KILL_DIM_THRESHOLD = 36
    private const val ENHANCE_SATURATION = 1.40f
    private const val ENHANCE_CONTRAST = 1.25f

    /** Clamp W×H về ngưỡng an toàn RAM (giữ tỉ lệ). */
    fun clampSize(w: Int, h: Int): Pair<Int, Int> {
        val ww = w.coerceAtLeast(1)
        val hh = h.coerceAtLeast(1)
        val px = ww * hh
        val maxDim = maxOf(ww, hh)
        if (px <= MAX_PIXELS && maxDim <= MAX_DIM) return ww to hh
        val sPix = Math.sqrt(MAX_PIXELS.toDouble() / px)
        val sDim = MAX_DIM.toDouble() / maxDim
        val s = minOf(sPix, sDim)
        return maxOf(1, Math.round(ww * s).toInt()) to maxOf(1, Math.round(hh * s).toInt())
    }

    /**
     * Sinh GIF từ [bytes] ảnh nguồn:
     *  - GIF động nhiều khung  → resize từng khung W×H, giữ duration/loop (animated GIF).
     *  - Ảnh tĩnh (PNG/JPG/BMP/GIF 1 khung) → enhance LED + resize + kill-dim → GIF 1 khung.
     */
    fun buildGif(bytes: ByteArray, w: Int, h: Int): ByteArray {
        val animated = tryBuildAnimatedGif(bytes, w, h)
        if (animated != null) return animated
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Không đọc được ảnh (định dạng không hỗ trợ).")
        return buildStaticGif(src, w, h)
    }

    /** Trả về GIF động nếu [bytes] là GIF nhiều khung; null nếu không phải (để rơi sang nhánh tĩnh). */
    private fun tryBuildAnimatedGif(bytes: ByteArray, w: Int, h: Int): ByteArray? {
        val decoder = GifDecoder()
        val status = try {
            decoder.read(java.io.ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            return null
        }
        if (status != GifDecoder.STATUS_OK) return null
        val frameCount = decoder.frameCount
        if (frameCount <= 1) return null

        val bos = ByteArrayOutputStream()
        val enc = AnimatedGifEncoder()
        enc.start(bos)
        enc.setRepeat(decoder.loopCount.coerceAtLeast(0)) // 0 = lặp vô hạn
        for (i in 0 until frameCount) {
            val frame = decoder.getFrame(i) ?: continue
            val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
            enc.setDelay(decoder.getDelay(i).coerceAtLeast(20))
            enc.setDispose(2)
            enc.addFrame(scaled)
        }
        enc.finish()
        return bos.toByteArray()
    }

    /** Ảnh tĩnh → GIF 1 khung W×H (enhance + kill-dim). */
    private fun buildStaticGif(src: Bitmap, w: Int, h: Int): ByteArray {
        val enhanced = enhanceForLed(src)
        val scaled = Bitmap.createScaledBitmap(enhanced, w, h, true)
        val cleaned = killDimPixels(scaled)
        val bos = ByteArrayOutputStream()
        val enc = AnimatedGifEncoder()
        enc.start(bos)
        enc.setRepeat(0)
        enc.addFrame(cleaned)
        enc.finish()
        return bos.toByteArray()
    }

    /** Tăng bão hòa + tương phản cho ảnh hiển thị rõ trên LED pitch lớn. */
    private fun enhanceForLed(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val saturation = ColorMatrix().apply { setSaturation(ENHANCE_SATURATION) }
        val c = ENHANCE_CONTRAST
        val t = (1f - c) * 127.5f
        val contrast = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        val combined = ColorMatrix().apply {
            postConcat(saturation)
            postConcat(contrast)
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(combined) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** max(R,G,B) < threshold → ép đen tuyệt đối (xóa halo viền). */
    private fun killDimPixels(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (maxOf(r, g, b) < KILL_DIM_THRESHOLD) {
                pixels[i] = 0xFF000000.toInt()
            }
        }
        val out = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
