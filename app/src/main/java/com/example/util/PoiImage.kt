package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Xử lý ảnh + encode BMP cho hiệu ứng POI (POV trên thanh quay).
 * Bám sát desktop Tool ARGB HSL: `ImageProcessor.rotate_resize_width`, `services/poi_polar.py`,
 * `ImageProcessor.polar_preview`. Xem thêm docs/poi-preset-workflow.md.
 */
object PoiImage {
    const val POI_MIN_W = PoiTools.PIXEL_MIN
    const val POI_MAX_W = PoiTools.PIXEL_MAX
    const val POI_RECOMMENDED_MAX_W = PoiTools.PIXEL_RECOMMENDED_MAX
    const val POI_MAX_SIZE = PoiTools.POI_MAX_BMP_BYTES
    const val POI_EFFECT_NAME = PoiTools.POI_EFFECT_NAME

    /** Ảnh nguồn đọc vào tối đa cạnh này (dư cho 145 LED, tránh tốn RAM với ảnh chụp 12 MP). */
    const val SOURCE_MAX_SIDE = 1024

    private const val SUPERSAMPLE = 3
    private const val MIN_ALPHA = 16
    private const val MIN_BRIGHTNESS = 8

    /** Giải mã ảnh, thu nhỏ khi đọc nếu cạnh dài > [maxSide]. */
    fun decodeSampled(bytes: ByteArray, maxSide: Int = SOURCE_MAX_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val side = max(bmp.width, bmp.height)
        if (side <= maxSide) return bmp
        val s = maxSide.toDouble() / side
        return smoothScale(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()))
    }

    /** Ghép ảnh lên nền đen (nền đen = LED tắt). Trả ARGB đục + mặt nạ điểm có nội dung. */
    private fun flattenOnBlack(src: Bitmap): Pair<IntArray, BooleanArray> {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val visible = BooleanArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF
            if (a < 255) {
                r = (r * a + 127) / 255
                g = (g * a + 127) / 255
                b = (b * a + 127) / 255
            }
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            visible[i] = a > MIN_ALPHA && max(r, max(g, b)) > MIN_BRIGHTNESS
        }
        return px to visible
    }

    private fun opaqueOnBlack(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val (px, _) = flattenOnBlack(src)
        return Bitmap.createBitmap(px, src.width, src.height, Bitmap.Config.ARGB_8888)
    }

    /** Thu nhỏ mịn (giảm một nửa nhiều lần rồi lọc song tuyến) — gần với BILINEAR của PIL. */
    fun smoothScale(src: Bitmap, w: Int, h: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= w && cur.height / 2 >= h && cur.width >= 4 && cur.height >= 4) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        return if (cur.width == w && cur.height == h) cur else Bitmap.createScaledBitmap(cur, w, h, true)
    }

    /** Xoay [degrees] (90 = theo chiều kim đồng hồ, -90 = ngược), chính xác từng điểm ảnh. */
    fun rotate(src: Bitmap, degrees: Float): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees) }, false)

    /**
     * Kiểu FLAT: xoay phải 90° + resize theo CHIỀU RỘNG = [targetWidth], giữ tỉ lệ.
     * Chiều cao làm tròn xuống như desktop để dung lượng BMP khớp tuyệt đối.
     */
    fun rotateResizeWidth(src: Bitmap, targetWidth: Int): Bitmap {
        val rotated = rotate(opaqueOnBlack(src), 90f)
        val newH = ((targetWidth.toDouble() / rotated.width) * rotated.height).toInt().coerceAtLeast(1)
        return smoothScale(rotated, targetWidth, newH)
    }

    /**
     * Kiểu DISC: đổi ảnh sang toạ độ cực → ảnh [pixels]×[rows], mỗi hàng là 1 góc, cột 0 = LED đầu
     * (gần tâm). [holePercent]: LED đầu cách tâm quay bao nhiêu % bán kính.
     */
    fun toPolar(src: Bitmap, pixels: Int, rows: Int, holePercent: Int): Bitmap {
        val w = src.width
        val h = src.height
        val (flat, visible) = flattenOnBlack(src)

        // Vòng tròn nhỏ nhất bao nội dung (tâm = giữa khung nội dung).
        var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (visible[y * w + x]) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val cx: Double
        val cy: Double
        var radius: Double
        if (maxX < 0) {
            cx = (w - 1) / 2.0; cy = (h - 1) / 2.0
            radius = max(1.0, hypot(w.toDouble(), h.toDouble()) / 2)
        } else {
            cx = (minX + maxX) / 2.0; cy = (minY + maxY) / 2.0
            radius = 0.0
            for (y in minY..maxY) for (x in minX..maxX) if (visible[y * w + x]) {
                val d = hypot(x - cx, y - cy)
                if (d > radius) radius = d
            }
            radius = max(1.0, radius)
        }

        val inner = holePercent.coerceIn(0, 99) / 100.0
        val samplesR = pixels * SUPERSAMPLE
        val samplesA = rows * SUPERSAMPLE
        val rho = DoubleArray(samplesR) { j -> (inner + (1.0 - inner) * ((j + 0.5) / samplesR)) * radius }
        val cosT = DoubleArray(samplesA) { i -> cos((i + 0.5) / samplesA * 2.0 * PI) }
        val sinT = DoubleArray(samplesA) { i -> sin((i + 0.5) / samplesA * 2.0 * PI) }

        val out = IntArray(pixels * rows)
        val n = SUPERSAMPLE * SUPERSAMPLE
        for (a in 0 until rows) {
            for (p in 0 until pixels) {
                var rs = 0; var gs = 0; var bs = 0
                for (di in 0 until SUPERSAMPLE) {
                    val ti = a * SUPERSAMPLE + di
                    for (dj in 0 until SUPERSAMPLE) {
                        val r = rho[p * SUPERSAMPLE + dj]
                        val x = Math.rint(cx + cosT[ti] * r).toInt()
                        val y = Math.rint(cy + sinT[ti] * r).toInt()
                        if (x in 0 until w && y in 0 until h) {
                            val c = flat[y * w + x]
                            rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
                        }
                    }
                }
                val r8 = Math.rint(rs.toDouble() / n).toInt()
                val g8 = Math.rint(gs.toDouble() / n).toInt()
                val b8 = Math.rint(bs.toDouble() / n).toInt()
                out[a * pixels + p] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
            }
        }
        return Bitmap.createBitmap(out, pixels, rows, Bitmap.Config.ARGB_8888)
    }

    /** Ảnh đúng định dạng gửi lên mạch (mỗi cột = 1 LED, mỗi hàng = 1 lần quét). */
    fun makeBmp(src: Bitmap, pixels: Int, shape: PoiTools.Shape, holePercent: Int, limit: Int): Bitmap =
        if (shape == PoiTools.Shape.DISC) toPolar(src, pixels, PoiTools.discRows(pixels, limit), holePercent)
        else rotateResizeWidth(src, pixels)

    /** BMP → ảnh để xem trước (xoay ngược 90°: cột = thời gian/góc, hàng = LED). */
    fun previewSource(bmp: Bitmap): Bitmap = rotate(bmp, -90f)

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

/** Tham số vẽ vòng quay (khớp các lời gọi `polar_preview` của desktop). */
data class SpinParams(
    val innerRatio: Double,
    val sectors: Int,
    val invertRadial: Boolean,
    val fadePower: Double,
    val angleOffset: Double,
    val sweepWidth: Double,
    val reverseAngular: Boolean
) {
    companion object {
        /** Kiểu Đúng hình: 1 hình chiếm cả vòng, hàng cuối (LED đầu) ở gần tâm. */
        fun disc(holePercent: Int) = SpinParams(
            innerRatio = holePercent / 100.0, sectors = 1, invertRadial = true, fadePower = 0.35,
            angleOffset = 0.0, sweepWidth = 2 * PI, reverseAngular = false
        )

        /** Kiểu Uốn theo vòng: ảnh lặp [sectors] lần mỗi vòng. */
        fun flat(sectors: Int) = SpinParams(
            innerRatio = 0.32, sectors = max(1, sectors), invertRadial = false, fadePower = 1.2,
            angleOffset = PI / 2, sweepWidth = 2 * PI / max(1, sectors), reverseAngular = true
        )
    }
}

/**
 * Vẽ POI đang quay (port `ImageProcessor.polar_preview`). Ánh xạ điểm ảnh tính 1 lần; mỗi khung
 * chỉ nhân độ mờ theo pha quét qua bảng tra → đủ nhanh để quay ~30 FPS trên tablet.
 * [src] là ảnh xem trước: cột = góc/thời gian, hàng = LED.
 */
class PoiSpinRenderer(src: Bitmap, val size: Int, params: SpinParams) {
    private val n = size * size
    private val color = IntArray(n)
    private val inside = BooleanArray(n)
    private val local = FloatArray(n)
    private val sector = 2 * PI / max(1, params.sectors)
    private val trail = params.sweepWidth.coerceIn(1e-6, sector)
    private val lut = IntArray(1025) { i -> (Math.pow(i / 1024.0, params.fadePower) * 256).roundToInt() }

    init {
        val w = src.width
        val h = src.height
        val sp = IntArray(w * h)
        src.getPixels(sp, 0, w, 0, 0, w, h)
        val center = (size - 1) / 2.0
        val outer = size / 2.0
        val inner = outer * params.innerRatio
        val span = max(1.0, outer - inner)
        for (y in 0 until size) {
            val dy = y - center
            for (x in 0 until size) {
                val dx = x - center
                val i = y * size + x
                val r = hypot(dx, dy)
                if (r < inner || r > outer) continue
                // clockwise = true: góc quét tăng theo chiều kim đồng hồ trên màn hình.
                var ang = -atan2(-dy, dx) + params.angleOffset
                ang = ((ang % (2 * PI)) + 2 * PI) % (2 * PI)
                var t = (r - inner) / span
                if (params.invertRadial) t = 1 - t
                t = t.coerceIn(0.0, 1.0)
                val py = floor(t * h).toInt().coerceIn(0, h - 1)
                val la = ang % sector
                var px = floor(la / sector * w).toInt().coerceIn(0, w - 1)
                if (params.reverseAngular) px = (w - 1) - px
                color[i] = sp[py * w + px]
                local[i] = la.toFloat()
                inside[i] = true
            }
        }
    }

    /** Vẽ 1 khung vào [out] (ARGB, size×size). [sweepAngle] null = phơi sáng dài (như mắt thấy khi quay nhanh). */
    fun render(sweepAngle: Double?, out: IntArray) {
        val black = 0xFF000000.toInt()
        if (sweepAngle == null) {
            for (i in 0 until n) out[i] = if (inside[i]) color[i] or black else black
            return
        }
        val s = sector
        val base = ((sweepAngle % s) + s) % s
        val invTrail = 1.0 / trail
        for (i in 0 until n) {
            if (!inside[i]) { out[i] = black; continue }
            var phase = base - local[i]
            if (phase < 0) phase += s
            val f = 1.0 - phase * invTrail
            if (f <= 0.0) { out[i] = black; continue }
            val fade = lut[min(1024, (f * 1024).toInt())]
            val c = color[i]
            val r = (((c shr 16) and 0xFF) * fade) shr 8
            val g = (((c shr 8) and 0xFF) * fade) shr 8
            val b = ((c and 0xFF) * fade) shr 8
            out[i] = black or (r shl 16) or (g shl 8) or b
        }
    }
}
