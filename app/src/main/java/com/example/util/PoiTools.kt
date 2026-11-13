package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Logic thuần của Công cụ POI (không Android UI) — port từ desktop Tool ARGB HSL:
 * `services/poi_upload.py`, `services/poi_fleet.py`, `services/brightness.py`.
 */
object PoiTools {
    /** Dòng mạch duy nhất dùng được POI (`/json/info` → `product`). */
    const val SUPPORTED_PRODUCT = "ARGB HSL Controller 2X Pro"
    /** Firmware đầu tiên quét theo RPM (`vid`): ix = RPM, c3 = số ảnh/vòng, o3 = đảo chiều. */
    const val FW_BUILD_FIXED_RPM = 2611050

    const val POI_MAX_BMP_BYTES = 63 * 1024
    const val BMP_LIMIT_PSRAM = 512 * 1024
    const val BMP_HEADER_BYTES = 54

    const val RPM_AUTO = 0
    const val RPM_MAX = 255
    const val RPM_DEFAULT = 120
    const val IMAGES_PER_TURN_MAX = 31
    /** 3 ảnh/vòng: ảnh vuông gần như không méo ở giữa thanh LED. */
    const val IMAGES_PER_TURN_DEFAULT = 3
    const val DISC_HOLE_DEFAULT = 15
    const val DISC_HOLE_MAX = 60

    const val PIXEL_MIN = 15
    const val PIXEL_MAX = 145
    const val PIXEL_RECOMMENDED_MAX = 72
    const val PIXEL_DEFAULT = 72
    const val PRESET_NAME_MAX = 20

    /** POI chạy pin: `bri` tối đa 101 (cao hơn dễ sụt áp), giao diện hiện 0–100%. */
    const val BRI_MIN = 1
    const val POI_BRI_MAX = 101

    const val POI_EFFECT_NAME = "Poi HSL"
    private const val DISC_SUFFIX = "_disc"

    private const val MAX_SCAN_ROWS = 2000
    private const val NEOPIXEL_US_PER_LED = 30
    private const val NEOPIXEL_RESET_US = 300
    private val TYPE_DIGITAL_RANGE = 16..39

    const val IDENTIFY_FLASHES = 3
    const val IDENTIFY_BRI = 40
    const val IDENTIFY_STEP_MS = 250L

    /** Kiểu hình khi quay. FLAT: quét thẳng (ảnh uốn theo vòng). DISC: toạ độ cực (đúng hình). */
    enum class Shape(val id: String) {
        FLAT("flat"), DISC("disc");

        companion object {
            fun of(id: String?): Shape = if (id == DISC.id) DISC else FLAT
        }
    }

    enum class Block { PRODUCT, FIRMWARE }

    // ── Khả năng của mạch ────────────────────────────────────────────────

    fun supportsFixedRpm(vid: Int?): Boolean = vid != null && vid >= FW_BUILD_FIXED_RPM

    fun isSupportedProduct(product: String?): Boolean =
        product?.trim()?.equals(SUPPORTED_PRODUCT, ignoreCase = true) == true

    /** Lý do mạch không dùng được POI; null = dùng được. */
    fun blockReason(product: String?, vid: Int?): Block? = when {
        !isSupportedProduct(product) -> Block.PRODUCT
        !supportsFixedRpm(vid) -> Block.FIRMWARE
        else -> null
    }

    fun blockMessage(name: String, product: String?, vid: Int?): String = when (blockReason(product, vid)) {
        null -> ""
        Block.PRODUCT -> "$name (${product?.trim().orEmpty().ifEmpty { "dòng mạch không xác định" }}): POI chỉ hỗ trợ $SUPPORTED_PRODUCT."
        Block.FIRMWARE -> "$name đang chạy firmware ${vid?.let { "build $it" } ?: "phiên bản không xác định"}. " +
            "POI cần build $FW_BUILD_FIXED_RPM trở lên — vui lòng nâng cấp firmware."
    }

    /** Dung lượng BMP tối đa: chỉ firmware mới + có PSRAM mới vượt 63 KB. */
    fun deviceBmpLimit(vid: Int?, psram: Long?): Int =
        if (supportsFixedRpm(vid) && psram != null && psram > 0) BMP_LIMIT_PSRAM else POI_MAX_BMP_BYTES

    /** Hàng/giây tối đa mạch quét được; null = chưa biết. [ledIns]: (type, len) của `hw.led.ins`. */
    fun maxScanRows(vid: Int?, ledIns: List<Pair<Int, Int>>?): Int? {
        if (!supportsFixedRpm(vid) || ledIns.isNullOrEmpty()) return null
        var best = MAX_SCAN_ROWS
        for ((type, len) in ledIns) {
            if (len > 0 && type in TYPE_DIGITAL_RANGE) {
                best = min(best, 1_000_000 / (NEOPIXEL_US_PER_LED * len + NEOPIXEL_RESET_US))
            }
        }
        return best
    }

    // ── Dung lượng BMP ───────────────────────────────────────────────────

    /** Số hàng BMP kiểu FLAT: ảnh xoay 90° rồi chiều rộng về [pixels] (khớp ImageProcessor desktop). */
    fun poiBmpHeight(pixels: Int, imgW: Int, imgH: Int): Int {
        val rotW = imgH
        val rotH = imgW
        if (rotW <= 0) return 0
        return ((pixels.toDouble() / rotW) * rotH).toInt()
    }

    fun bmpSizeBytes(width: Int, height: Int): Int = BMP_HEADER_BYTES + (width * 3 + 3) / 4 * 4 * max(0, height)

    fun poiBmpSize(pixels: Int, imgW: Int, imgH: Int): Int = bmpSizeBytes(pixels, poiBmpHeight(pixels, imgW, imgH))

    fun fits(size: Int, limit: Int): Boolean = size <= limit

    fun maxFittingPixels(imgW: Int, imgH: Int, upper: Int = PIXEL_MAX, limit: Int = POI_MAX_BMP_BYTES): Int? {
        for (px in upper downTo PIXEL_MIN) {
            if (poiBmpHeight(px, imgW, imgH) >= 1 && fits(poiBmpSize(px, imgW, imgH), limit)) return px
        }
        return null
    }

    /** Số hàng BMP kiểu DISC (1 vòng = 1 ảnh): 2π × số LED, giảm cho vừa [limit]. */
    fun discRows(pixels: Int, limit: Int = POI_MAX_BMP_BYTES): Int {
        val stride = (pixels * 3 + 3) / 4 * 4
        return min((2 * PI * pixels).roundToInt(), (limit - BMP_HEADER_BYTES) / stride)
    }

    fun limitLabel(limit: Int): String = "${limit / 1024} KB"

    fun humanSize(size: Int): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", size / 1024.0)
        else -> String.format(java.util.Locale.US, "%.2f MB", size / 1024.0 / 1024.0)
    }

    // ── Tốc độ quay + payload preset ─────────────────────────────────────

    fun scanRowsPerSecond(rows: Int, rpm: Int, imagesPerTurn: Int): Int = rows * rpm * max(1, imagesPerTurn) / 60

    fun speedFields(rpm: Int, imagesPerTurn: Int, reverse: Boolean): JSONObject =
        JSONObject().put("ix", rpm).put("c3", imagesPerTurn).put("o3", reverse)

    fun speedLabel(rpm: Int, imagesPerTurn: Int, reverse: Boolean): String =
        if (rpm <= 0) "Auto" else "$rpm RPM · $imagesPerTurn ảnh/vòng" + if (reverse) " · đảo chiều" else ""

    /**
     * Payload `POST /json/state` lưu 1 preset ảnh POI. Ghi rõ cả ix/c3/o3 (thiếu field nào thì
     * preset thừa hưởng giá trị bất kỳ từ hiệu ứng đang chạy); `ib`/`sb` giữ độ sáng và dải LED.
     */
    fun imagePresetPayload(
        filename: String, presetId: Int, presetName: String, fxId: Int, bri: Int,
        pixels: Int, rpm: Int, imagesPerTurn: Int, reverse: Boolean
    ): JSONObject {
        val seg = JSONObject()
            .put("id", 0).put("on", true).put("bri", 255).put("n", "/$filename").put("fx", fxId)
            .put("start", 0).put("stop", pixels)
            .put("ix", rpm).put("c3", imagesPerTurn).put("o3", reverse)
        return JSONObject()
            .put("on", true).put("bri", bri).put("seg", JSONArray().put(seg))
            .put("psave", presetId).put("n", presetName).put("ib", true).put("sb", true)
    }

    /** Preset trên mạch đã mang đúng nội dung [payload] chưa (so tên + n/fx/ix/c3/o3 của seg 0)? */
    fun presetSaved(presets: JSONObject?, presetId: Int, payload: JSONObject): Boolean {
        val saved = presets?.optJSONObject(presetId.toString()) ?: return false
        if (saved.opt("n") != payload.opt("n")) return false
        val seg = when (val s = saved.opt("seg")) {
            is JSONArray -> s.optJSONObject(0)
            is JSONObject -> s
            else -> null
        } ?: return false
        val want = payload.optJSONArray("seg")?.optJSONObject(0) ?: return false
        for (key in listOf("n", "fx", "ix", "c3", "o3")) {
            if (!want.has(key)) continue
            if (com.example.data.PresetJson.canonical(seg.opt(key)) != com.example.data.PresetJson.canonical(want.opt(key))) return false
        }
        return true
    }

    // ── Tên preset / file BMP ────────────────────────────────────────────

    /** Bỏ dấu tiếng Việt → ASCII; ký tự ngoài [A-Za-z0-9_-] thành '_'. */
    fun asciiSlug(text: String?): String {
        var t = (text ?: "").replace('đ', 'd').replace('Đ', 'D')
        t = Normalizer.normalize(t, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
        t = t.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return t.replace(Regex("_+"), "_").trim('_')
    }

    fun fileStem(name: String?): String {
        val base = (name ?: "").substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        return if (dot > 0) base.substring(0, dot) else base
    }

    /** Tên preset (= tên BMP không đuôi), không trùng nhau và không trùng [reserved] (không phân biệt hoa thường). */
    fun uniquePresetNames(stems: List<String>, reserved: Collection<String> = emptyList(), maxLen: Int = PRESET_NAME_MAX): List<String> {
        val taken = reserved.map { it.lowercase() }.toMutableSet()
        return stems.mapIndexed { i, stem ->
            val base = asciiSlug(stem).take(maxLen).ifEmpty { "Preset_${i + 1}" }
            var name = base
            var n = 2
            while (name.lowercase() in taken) {
                val suffix = "_$n"
                name = base.take(maxLen - suffix.length) + suffix
                n++
            }
            taken += name.lowercase()
            name
        }
    }

    /** Tên BMP kiểu DISC: giữ hậu tố `_disc` kể cả khi phải cắt tên. */
    fun discStem(stem: String, maxLen: Int = PRESET_NAME_MAX): String =
        asciiSlug(stem).take(maxLen - DISC_SUFFIX.length).trimEnd('_').ifEmpty { "Preset" } + DISC_SUFFIX

    /** BMP là ảnh cực? (tên là dấu hiệu duy nhất; `uniquePresetNames` có thể thêm `_2`). */
    fun isDiscStem(stem: String?): Boolean = Regex("${DISC_SUFFIX}(_\\d+)?$", RegexOption.IGNORE_CASE).containsMatchIn(stem ?: "")

    /** Tên BMP (không "/" và ".bmp") mà preset dùng ở seg 0. */
    fun presetBmpStem(preset: JSONObject?): String? {
        val seg = when (val s = preset?.opt("seg")) {
            is JSONArray -> s.optJSONObject(0)
            is JSONObject -> s
            else -> null
        } ?: return null
        val n = seg.optString("n").trim().trimStart('/')
        return if (n.lowercase().endsWith(".bmp")) n.dropLast(4) else null
    }

    /** Tên BMP mà preset NGOÀI [targetSlots] đang dùng → không được đè. */
    fun reservedBmpStems(presets: JSONObject, targetSlots: Collection<Int>): Set<String> {
        val targets = targetSlots.toSet()
        val out = HashSet<String>()
        val keys = presets.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = k.toIntOrNull() ?: continue
            if (id in targets) continue
            presetBmpStem(presets.optJSONObject(k))?.let { out += it }
        }
        return out
    }

    /** Slot logo 1–59 để ghi [count] ảnh: ghi đè từ ID 1, hoặc ID trống kế tiếp. */
    fun logoSlots(presets: JSONObject, count: Int, overwrite: Boolean): List<Int> {
        val candidates = (1..59).toList()
        if (overwrite) return candidates.take(count)
        return candidates.filter { !com.example.data.PresetSlots.isNonEmptyPreset(presets.opt(it.toString())) }.take(count)
    }

    /** [1,2,3,7,9,10] → "ID 1-3, 7, 9-10". */
    fun formatSlots(slots: Collection<Int>): String {
        val nums = slots.toSortedSet().toList()
        if (nums.isEmpty()) return "—"
        val parts = ArrayList<String>()
        var start = nums[0]
        var prev = nums[0]
        for (n in nums.drop(1) + listOf(Int.MIN_VALUE)) {
            if (n != Int.MIN_VALUE && n == prev + 1) { prev = n; continue }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = n; prev = n
        }
        return "ID " + parts.joinToString(", ")
    }

    // ── Độ sáng ──────────────────────────────────────────────────────────

    fun clampBri(bri: Int, briMax: Int = POI_BRI_MAX): Int = bri.coerceIn(BRI_MIN, briMax)
    fun briToPercent(bri: Int, briMax: Int = POI_BRI_MAX): Int = (clampBri(bri, briMax) - BRI_MIN) * 100 / max(1, briMax - BRI_MIN)
    fun defaultBri(briMax: Int = POI_BRI_MAX): Int = BRI_MIN + (briMax - BRI_MIN) / 2

    // ── Số liệu mạch ─────────────────────────────────────────────────────

    /** Nhiệt độ chip (°C) từ `info.sensor` / `info.u` dạng {"tên": [59.2, "°C"]}. */
    fun tempCelsius(info: JSONObject?): Double? {
        for (key in listOf("sensor", "u")) {
            val block = info?.optJSONObject(key) ?: continue
            val it = block.keys()
            while (it.hasNext()) {
                val v = block.optJSONArray(it.next()) ?: continue
                if (v.length() >= 2 && v.opt(0) is Number && (v.opt(1) as? String)?.contains("°") == true) {
                    return (v.opt(0) as Number).toDouble()
                }
            }
        }
        return null
    }

    fun fsPercent(info: JSONObject?): Int? {
        val fs = info?.optJSONObject("fs") ?: return null
        val u = fs.optDouble("u", Double.NaN)
        val t = fs.optDouble("t", Double.NaN)
        return if (!u.isNaN() && !t.isNaN() && t > 0) (u * 100 / t).toInt() else null
    }

    fun fwVersion(info: JSONObject?): String? {
        val ver = info?.optString("ver").orEmpty().ifBlank { return null }
        val vid = info?.opt("vid") as? Int
        return if (vid != null) "$ver ($vid)" else ver
    }

    /**
     * Nháy trắng 3 lần ở độ sáng thấp rồi trả mạch về như cũ: playlist/preset đang chạy → chạy
     * lại; không có → khôi phục fx/col của seg 0; cuối cùng đặt lại on/bri.
     * @return (payload /json/state, nghỉ ms).
     */
    fun identifyPlan(state: JSONObject?): List<Pair<JSONObject, Long>> {
        val st = state ?: JSONObject()
        val segs = st.optJSONArray("seg")
        var seg0: JSONObject? = null
        if (segs != null) {
            for (i in 0 until segs.length()) {
                val s = segs.optJSONObject(i) ?: continue
                if (seg0 == null) seg0 = s
                if (s.optInt("id", 0) == 0) { seg0 = s; break }
            }
        }
        val steps = ArrayList<Pair<JSONObject, Long>>()
        fun col(r: Int, g: Int, b: Int) = JSONArray().put(JSONArray().put(r).put(g).put(b))
        for (i in 0 until IDENTIFY_FLASHES) {
            val onSeg = JSONObject().put("id", 0).put("col", col(255, 255, 255))
            val on = JSONObject().put("tt", 0)
            if (i == 0) {
                onSeg.put("fx", 0)
                on.put("on", true).put("bri", IDENTIFY_BRI)
            }
            on.put("seg", JSONArray().put(onSeg))
            steps += on to IDENTIFY_STEP_MS
            steps += JSONObject().put("tt", 0).put("seg", JSONArray().put(JSONObject().put("id", 0).put("col", col(0, 0, 0)))) to IDENTIFY_STEP_MS
        }
        val basic = JSONObject()
        (st.opt("on") as? Boolean)?.let { basic.put("on", it) }
        (st.opt("bri") as? Int)?.let { basic.put("bri", it) }
        val pl = st.opt("pl") as? Int
        val ps = st.opt("ps") as? Int
        val resume = when {
            pl != null && pl > 0 -> pl
            ps != null && ps > 0 -> ps
            else -> null
        }
        if (resume != null) {
            steps += JSONObject().put("ps", resume) to 300L
            if (basic.length() > 0) steps += basic to 0L
        } else {
            val seg = JSONObject().put("id", 0)
            seg0?.let { s -> for (k in listOf("fx", "col")) if (s.has(k)) seg.put(k, s.opt(k)) }
            val last = JSONObject(basic.toString()).put("seg", JSONArray().put(seg))
            steps += last to 0L
        }
        return steps
    }
}
