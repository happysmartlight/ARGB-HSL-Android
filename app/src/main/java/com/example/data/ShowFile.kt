package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * File `/show.jsonl` cho trình phát show của firmware (usermod show_player) — port của desktop
 * `services/show_file.py` (Tool ARGB HSL v3.17.11). Logic thuần, có unit test (`ShowFileTest`).
 *
 * Dòng đầu = header (`v`, `name`, `dur` ms, `loop`, `segs` phân đoạn, `marks` vạch đánh dấu);
 * mỗi dòng sau = 1 clip `{"s": chỉ số đoạn hoặc -1 = cả mạch, "t", "d", "tr" (ms), "name", "seg": look}`.
 * Mạch có trình phát show (`/json/info` → `show.v` ≥ 1) phát Timecode từ file này, không cần preset
 * từng clip: preset 249 = lệnh phát show, 248 = dừng show + tắt đèn.
 */
object ShowFile {
    const val PATH = "/show.jsonl"
    const val FORMAT_VERSION = 1

    /** Trình phát show có từ firmware 2611163. */
    const val MIN_PLAYER_VERSION = 1
    /** v2: header đọc có lọc → header dài (nhiều phân đoạn, vạch đánh dấu) vẫn nạp được. */
    const val MARKS_PLAYER_VERSION = 2
    /** v3: đoạn không có clip thì tắt (đoạn chồng nhau đúng), clip cả mạch chạy trên lớp phủ toàn khung. */
    const val LAYER_PLAYER_VERSION = 3
    /** v5 (firmware 2611211+): phân đoạn "Thứ tự LED" trên ma trận (`lo`). */
    const val LED_ORDER_PLAYER_VERSION = 5
    /** v6 (firmware 2611213+): clip cả mạch chở đủ các lớp của preset nhiều phân đoạn. */
    const val LAYERS_PLAYER_VERSION = 6
    /** v7 (firmware 2611214+): `{"show":{"stop":true,"off":true}}` trả trạng thái cũ với đèn tắt, không nháy. */
    const val STOP_OFF_PLAYER_VERSION = 7

    const val LED_ORDER_FIRMWARE = "2611211"
    const val LAYERS_FIRMWARE = "2611213"
    const val STOP_OFF_FIRMWARE = "2611214"

    /** Lớp tối đa / clip (mạch còn giới hạn theo `leds.maxseg`). */
    const val LOOKS_MAX = 16
    /** Firmware giữ 47 byte tên show. */
    const val NAME_MAX = 47

    /** `/json/state` → `show.st`. */
    const val ST_IDLE = 0
    const val ST_PLAYING = 1
    const val ST_PAUSED = 2
    const val ST_ENDED = 3

    /** Khoá làm dời / đổi kích thước phân đoạn: clip chỉ đổi hình ảnh (firmware cũng bỏ qua). */
    val LAYOUT_KEYS = setOf("id", "start", "stop", "len", "startY", "stopY", "lo", "grp", "spc", "of", "rpt", "sel", "on")

    const val OFF_PRESET_NAME = "TIMELINE_OFF"
    const val OFF_PRESET_SEGMENTS = 64

    /**
     * Một phân đoạn của mạch (desktop `SegmentDef`): khoảng LED đặt tên tự do. Ma trận: [start]/[stop]
     * là cột, [startY]/[stopY] là hàng; [ledOrder] ("Thứ tự LED"): [start]/[stop] là số LED theo dây.
     * [key] cố định trong file timeline — clip trỏ tới đoạn bằng key.
     */
    data class Segment(
        val name: String,
        val start: Int,
        val stop: Int,
        val startY: Int = 0,
        val stopY: Int = 1,
        val color: String = "",
        val key: String = newKey(),
        val ledOrder: Boolean = false
    ) {
        /** Phần tử `segs` trong header file show (`k` / `c` để Tool đọc lại). */
        fun toHeader(): JSONObject = JSONObject().apply {
            put("n", name)
            put("start", start)
            put("stop", stop)
            if (ledOrder) put("lo", true)
            else if (startY != 0 || stopY != 1) { put("startY", startY); put("stopY", stopY) }
            put("k", key)
            if (color.isNotEmpty()) put("c", color)
        }

        companion object {
            /** `tracks[].segments[]` của file timeline desktop (`SegmentDef.to_dict`). */
            fun fromDict(d: JSONObject?): Segment? = d?.let {
                make(it.optString("name"), it.opt("start"), it.opt("stop"), it.opt("start_y"), it.opt("stop_y"),
                    it.optString("color"), it.optString("key"), it.optBoolean("led_order", false))
            }

            /** Phần tử `segs` của header file show. */
            fun fromHeader(h: JSONObject?): Segment? = h?.let {
                make(it.optString("n"), it.opt("start"), it.opt("stop"), it.opt("startY"), it.opt("stopY"),
                    it.optString("c"), it.optString("k"), it.optBoolean("lo", false))
            }

            private fun make(
                name: String, start: Any?, stop: Any?, startY: Any?, stopY: Any?, color: String, key: String, lo: Boolean
            ): Segment? {
                val s = intOf(start, 0) ?: return null
                val e = intOf(stop, 0) ?: return null
                var sy = intOf(startY, 0) ?: return null
                var ey = intOf(stopY, 1) ?: return null
                if (lo) { sy = 0; ey = 1 }                     // đoạn Thứ tự LED luôn 1 hàng
                if (e <= s || ey <= sy) return null
                return Segment(
                    name = name.trim().ifEmpty { "Phân đoạn" }, start = s, stop = e, startY = sy, stopY = ey,
                    color = color.takeIf { it != "null" }.orEmpty(), key = key.takeIf { it.isNotBlank() && it != "null" } ?: newKey(),
                    ledOrder = lo
                )
            }
        }
    }

    /**
     * Clip để dựng file show. [segment] = key của phân đoạn (null = cả mạch). [look] = hình ảnh
     * (phần tử `seg` của preset); [looks] = các lớp của preset nhiều phân đoạn ([clipLooks]).
     */
    data class Clip(
        val segment: String?,
        val start: Double,
        val duration: Double,
        val transition: Double,
        val name: String,
        val look: JSONObject?,
        val looks: List<JSONObject>? = null
    )

    data class Parsed(
        val name: String,
        val duration: Double,
        val loop: Boolean,
        val marks: List<Double>,
        val segments: List<Segment>,
        val clips: List<Clip>
    )

    class Built(val text: String, val warnings: List<String>)

    private fun newKey(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(8)

    private fun intOf(v: Any?, default: Int): Int? = when (v) {
        null, JSONObject.NULL -> default
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    private fun ms(seconds: Double): Long = maxOf(0L, Math.round(seconds * 1000))

    /** Phiên bản trình phát show của mạch (`/json/info` → `show.v`); 0 = không có. */
    fun playerVersion(info: JSONObject?): Int = info?.optJSONObject("show")?.optInt("v", 0) ?: 0

    private fun withoutLayout(o: JSONObject): JSONObject {
        val out = JSONObject()
        for (k in o.keys()) if (k !in LAYOUT_KEYS) out.put(k, o.opt(k))
        return out
    }

    private fun payloadSegs(payload: JSONObject?): List<JSONObject> = when (val s = payload?.opt("seg")) {
        is JSONArray -> (0 until s.length()).mapNotNull { s.optJSONObject(it) }.filter { it.length() > 0 }
        is JSONObject -> listOf(s).filter { it.length() > 0 }
        else -> emptyList()
    }

    /**
     * Hình ảnh của 1 clip từ nội dung preset (presets.json hoặc `recipe.payload`): phần tử `seg` của
     * phân đoạn 0 (hoặc phần tử đầu), bỏ khoá bố cục. Null = rỗng.
     */
    fun clipLook(payload: JSONObject?): JSONObject? {
        val segs = payloadSegs(payload)
        if (segs.isEmpty()) return null
        val first = segs.firstOrNull { it.has("id") && it.optInt("id", -1) == 0 } ?: segs[0]
        return withoutLayout(JSONObject(first.toString())).takeIf { it.length() > 0 }
    }

    /**
     * Chỗ của 1 phân đoạn preset cho trình phát v6: `[start, stop]` = khoảng LED (dải, hoặc số LED của
     * đoạn Thứ tự LED), `[start, stop, startY, stopY]` = vùng ma trận; null = cả mạch.
     */
    private fun segArea(s: JSONObject): JSONArray? {
        if (!s.has("stop")) return null
        val start = intOf(s.opt("start"), 0) ?: return null
        val stop = intOf(s.opt("stop"), 0) ?: return null
        if (s.optBoolean("lo", false)) return JSONArray().put(start).put(stop)
        if (s.has("startY") || s.has("stopY")) {
            val sy = intOf(s.opt("startY"), 0) ?: return null
            val ey = intOf(s.opt("stopY"), 1) ?: return null
            return JSONArray().put(start).put(stop).put(sy).put(ey)
        }
        return JSONArray().put(start).put(stop)
    }

    /**
     * Các lớp của preset nhiều phân đoạn (vd chữ chạy + hiệu ứng hoà trộn lên trên), từ lớp dưới lên theo
     * thứ tự phân đoạn: mỗi lớp = hình ảnh (bỏ khoá bố cục) + `area`. Bỏ đoạn đã xoá (stop ≤ start) và đoạn
     * tắt. Preset 1 phân đoạn → null (dùng [clipLook]).
     */
    fun clipLooks(payload: JSONObject?): List<JSONObject>? {
        val arr = payload?.optJSONArray("seg") ?: return null
        val segs = ArrayList<Triple<Int, Int, JSONObject>>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            if (s.has("on") && s.opt("on") == false) continue
            if (s.has("stop")) {
                val stop = intOf(s.opt("stop"), 0) ?: continue
                val start = intOf(s.opt("start"), 0) ?: continue
                if (stop <= start) continue
            }
            if (s.keys().asSequence().any { it !in LAYOUT_KEYS }) {
                segs += Triple(if (s.has("id")) s.optInt("id", i) else i, i, s)
            }
        }
        if (segs.size < 2) return null
        return segs.sortedWith(compareBy({ it.first }, { it.second })).map { (_, _, s) ->
            withoutLayout(JSONObject(s.toString())).also { look -> segArea(s)?.let { look.put("area", it) } }
        }
    }

    /** Ngược lại [clipLooks]: các lớp đọc từ file show → danh sách `seg` của preset. */
    fun lookSegments(looks: List<JSONObject>?): JSONArray {
        val out = JSONArray()
        (looks ?: emptyList()).forEachIndexed { i, look ->
            val s = JSONObject().put("id", i)
            for (k in look.keys()) if (k != "area") s.put(k, look.opt(k))
            val area = look.optJSONArray("area")
            if (area != null && area.length() >= 2) {
                s.put("start", area.optInt(0)); s.put("stop", area.optInt(1))
                if (area.length() >= 4) { s.put("startY", area.optInt(2)); s.put("stopY", area.optInt(3)) }
                else s.put("lo", true)
            }
            out.put(s)
        }
        return out
    }

    /**
     * Nội dung /show.jsonl + cảnh báo (desktop `build_show`).
     *
     * [segments] rỗng = show chỉ có lớp toàn mạch. Clip thiếu `look` hoặc trỏ tới đoạn không còn thì bỏ qua và
     * báo trong cảnh báo. [marks] (giây) ghi vào header để "Đọc từ mạch" dựng lại; firmware bỏ qua. [layers] =
     * mạch có trình phát v6: clip hàng tên mạch của preset nhiều phân đoạn ghi đủ lớp, không thì chỉ lớp đầu.
     */
    fun buildShow(
        name: String,
        segments: List<Segment>,
        clips: List<Clip>,
        duration: Double,
        loop: Boolean,
        marks: List<Double> = emptyList(),
        layers: Boolean = false
    ): Built {
        val index = segments.withIndex().associate { (i, s) -> s.key to i }
        val head = JSONObject().apply {
            put("v", FORMAT_VERSION)
            put("name", name.take(NAME_MAX))
            put("dur", ms(duration))
            put("loop", loop)
            if (segments.isNotEmpty()) put("segs", JSONArray().also { a -> segments.forEach { a.put(it.toHeader()) } })
            val msMarks = marks.filter { it >= 0 }.map { ms(it) }.distinct().sorted()
            if (msMarks.isNotEmpty()) put("marks", JSONArray(msMarks))
        }
        val lines = arrayListOf(PresetJson.compact(head))
        val warnings = ArrayList<String>()
        for (c in clips.sortedWith(compareBy({ it.start }, { it.name }))) {
            val look = c.look
            if (look == null || look.length() == 0) {
                warnings += "clip '${c.name}' không có nội dung hiệu ứng, bỏ qua"
                continue
            }
            val s = when (val key = c.segment) {
                null -> -1
                in index -> index.getValue(key)
                else -> { warnings += "clip '${c.name}' thuộc phân đoạn đã xoá, bỏ qua"; continue }
            }
            val d = ms(c.duration)
            if (d <= 0) continue
            var seg: Any = withoutLayout(look)
            val looks = c.looks.orEmpty()
            if (looks.size > 1) {
                when {
                    s >= 0 -> warnings += "clip '${c.name}' trên phân đoạn: preset nhiều lớp chỉ chạy lớp đầu"
                    !layers -> warnings += "clip '${c.name}': preset nhiều lớp, mạch cần firmware $LAYERS_FIRMWARE trở lên để chạy đủ lớp (đang chạy lớp đầu)"
                    else -> {
                        if (looks.size > LOOKS_MAX) warnings += "clip '${c.name}': preset ${looks.size} lớp, chỉ chạy $LOOKS_MAX lớp dưới cùng"
                        seg = JSONArray().also { a -> looks.take(LOOKS_MAX).forEach { a.put(withoutLayout(it)) } }
                    }
                }
            }
            val line = JSONObject().apply {
                put("s", s)
                put("t", ms(c.start))
                put("d", d)
                put("tr", ms(c.transition))
                put("name", c.name)
                put("seg", seg)
            }
            lines += PresetJson.compact(line)
        }
        return Built(lines.joinToString("\n") + "\n", warnings)
    }

    /** Số clip firmware sẽ nạp (dòng clip, bỏ dòng đầu). */
    fun clipCount(text: String): Int = maxOf(0, text.lines().count { it.isNotBlank() } - 1)

    /** Đọc lại /show.jsonl. Null = không phải file show hợp lệ. */
    fun parseShow(text: String?): Parsed? {
        val lines = (text ?: "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val head = runCatching { JSONObject(lines[0]) }.getOrNull() ?: return null
        if (head.optInt("v", -1) != FORMAT_VERSION) return null
        val segments = head.optJSONArray("segs")?.let { a ->
            (0 until a.length()).mapNotNull { Segment.fromHeader(a.optJSONObject(it)) }
        }.orEmpty()
        val clips = ArrayList<Clip>()
        for (line in lines.drop(1)) {
            val c = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val s = c.optInt("s", -1)
            var looks: List<JSONObject>? = null
            val look: JSONObject = when (val seg = c.opt("seg")) {
                is JSONObject -> seg
                is JSONArray -> {
                    val all = (0 until seg.length()).mapNotNull { seg.optJSONObject(it) }
                    if (all.isEmpty()) continue
                    if (all.size > 1) looks = all
                    JSONObject().also { o -> for (k in all[0].keys()) if (k != "area") o.put(k, all[0].opt(k)) }
                }
                else -> continue
            }
            clips += Clip(
                segment = if (s in segments.indices) segments[s].key else null,
                start = c.optLong("t", 0) / 1000.0,
                duration = c.optLong("d", 0) / 1000.0,
                transition = c.optLong("tr", 0) / 1000.0,
                name = c.optString("name"),
                look = look,
                looks = looks
            )
        }
        val marks = head.optJSONArray("marks")?.let { a ->
            (0 until a.length()).mapNotNull { a.opt(it) as? Number }.map { it.toLong() / 1000.0 }
        }.orEmpty().distinct().sorted()
        return Parsed(
            name = head.optString("name"),
            duration = head.optLong("dur", 0) / 1000.0,
            loop = head.optBoolean("loop", false),
            marks = marks,
            segments = segments,
            clips = clips
        )
    }

    /** Preset (slot 249) để mạch tự phát show đã ghi: nút bấm, khởi động, app. */
    fun playPreset(name: String): JSONObject = JSONObject().apply {
        put("n", name)
        put("on", true)
        put("show", JSONObject().put("load", true).put("play", 0))
    }

    fun isPlayPreset(preset: Any?): Boolean =
        (preset as? JSONObject)?.optJSONObject("show")?.has("play") == true

    /**
     * Slot OFF 248 (desktop `preset_groups.off_preset_payload`): MỌI segment = Solid đen, bỏ đóng băng, kèm
     * `show.stop` (restore false) để mạch có trình phát show dừng show; firmware cũ bỏ qua khoá này. Không
     * dùng `on:false` (playlist đứng yên khi bri = 0, clip kế tiếp không bật lại đèn).
     */
    fun offPreset(): JSONObject = JSONObject().apply {
        put("n", OFF_PRESET_NAME)
        put("seg", JSONArray().also { a ->
            for (i in 0 until OFF_PRESET_SEGMENTS) {
                a.put(JSONObject().put("id", i).put("fx", 0)
                    .put("col", JSONArray().put(JSONArray().put(0).put(0).put(0))).put("frz", false))
            }
        })
        put("show", JSONObject().put("stop", true).put("restore", false))
    }

    /** Lệnh dừng show + tắt đèn (Dừng / hết bài). v7: 1 lệnh, mạch khôi phục lúc đèn đã tắt. */
    fun stopOffCommand(): JSONObject = JSONObject().put("show", JSONObject().put("stop", true).put("off", true))
}
