package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Thời gian của playlist WLED (logic thuần, có test — port desktop `TimelineView._build_track_entries`,
 * `_slice_entries_at_offset`, `_apply_loaded_slot`).
 *
 * Firmware (đo trên 2X Pro FW 2611103): mỗi mục chạy đúng `dur` (decisecond); `transition` là thời
 * gian chuyển cảnh NẰM TRONG `dur`, không cộng thêm. `repeat` 0 = lặp mãi, 1 = 1 lần.
 */
object PlaylistTiming {
    const val GAP_TOLERANCE = 0.05
    private const val DEFAULT_DUR_DS = 100

    data class Entry(val ps: Int, val durDs: Int, val trDs: Int)

    data class Parsed(val entries: List<Entry>, val repeat: Int, val end: Int) {
        val loop: Boolean get() = repeat == 0
        val cycleSeconds: Double get() = entries.sumOf { it.durDs } / 10.0

        /** (bắt đầu, kết thúc) theo giây của từng mục trong 1 vòng. */
        fun spans(): List<Pair<Double, Double>> {
            var t = 0
            return entries.map { e -> val s = t; t += e.durDs; s / 10.0 to t / 10.0 }
        }
    }

    /** `dur` / `transition` có thể là mảng hoặc 1 số dùng chung cho mọi mục. */
    private fun valueAt(raw: Any?, index: Int, default: Int): Int = when (raw) {
        is JSONArray -> (raw.opt(index) as? Number)?.toInt() ?: default
        is Number -> raw.toInt()
        else -> default
    }

    /** Đọc block `playlist` của 1 preset. Null nếu không có mục nào. */
    fun parse(playlist: JSONObject?): Parsed? {
        val ps = playlist?.optJSONArray("ps") ?: return null
        val entries = (0 until ps.length()).mapNotNull { i ->
            val id = (ps.opt(i) as? Number)?.toInt() ?: return@mapNotNull null
            Entry(
                ps = id,
                durDs = max(1, valueAt(playlist.opt("dur"), i, DEFAULT_DUR_DS)),
                trDs = max(0, valueAt(playlist.opt("transition"), i, 0))
            )
        }
        if (entries.isEmpty()) return null
        val repeat = (playlist.opt("repeat") as? Number)?.toInt() ?: (playlist.opt("r") as? Number)?.toInt() ?: 0
        return Parsed(entries, max(0, repeat), (playlist.opt("end") as? Number)?.toInt() ?: 0)
    }

    /**
     * Cắt playlist tại [offset] giây (phát tiếp / tua): mục chứa offset bị rút ngắn (transition = 0 vì
     * đã ở giữa preset). [loop] và offset > 0: nối thêm phần 0 → offset ở cuối để mỗi vòng vẫn đủ dài.
     * Offset ở cuối → phát lại từ đầu.
     */
    fun slice(parsed: Parsed, offsetSec: Double, loop: Boolean): List<Entry> {
        var offset = max(0.0, offsetSec)
        if (offset >= parsed.cycleSeconds - GAP_TOLERANCE) offset = 0.0
        val spans = parsed.spans()
        val out = ArrayList<Entry>()
        fun push(ps: Int, durSec: Double, trSec: Double) {
            out += Entry(ps, max(1, (durSec * 10).roundToInt()), max(0, (trSec * 10).roundToInt()))
        }
        parsed.entries.forEachIndexed { i, e ->
            val (start, end) = spans[i]
            if (end <= offset + GAP_TOLERANCE) return@forEachIndexed
            if (start < offset - GAP_TOLERANCE) push(e.ps, end - offset, 0.0)
            else out += e
        }
        if (loop && offset > GAP_TOLERANCE) {
            for ((i, e) in parsed.entries.withIndex()) {
                val (start, end) = spans[i]
                if (start >= offset - GAP_TOLERANCE) break
                if (end <= offset + GAP_TOLERANCE) out += e
                else push(e.ps, offset - start, e.trDs / 10.0)
            }
        }
        return out
    }

    /**
     * Mục cần gửi để mạch chạy tiếp từ [offsetSec] (giây tính từ lúc bắt đầu phát).
     * Lặp → offset quay vòng theo chiều dài 1 vòng. Chạy 1 lần mà offset đã qua cuối → null (mạch đứng ở mục cuối).
     */
    fun resumeEntries(parsed: Parsed, offsetSec: Double): List<Entry>? {
        val cycle = parsed.cycleSeconds
        if (parsed.loop) return slice(parsed, if (cycle > 0) offsetSec % cycle else 0.0, loop = true)
        if (offsetSec >= cycle - GAP_TOLERANCE) return null
        return slice(parsed, offsetSec, loop = false)
    }

    /** `repeat` N > 1 (playlist tạo trên web WLED) → trải thành N vòng chạy 1 lần để tính giờ/cắt đúng. */
    fun expandRepeats(parsed: Parsed, maxRepeat: Int = 20): Parsed {
        if (parsed.repeat <= 1) return parsed
        val n = parsed.repeat.coerceAtMost(maxRepeat)
        return Parsed(List(n) { parsed.entries }.flatten(), repeat = 1, end = parsed.end)
    }

    /** 1 giờ (decisecond) — vừa `uint16` của firmware cũ (2503271). KHÔNG dùng 0: FW cũ coi 0 là nhảy mục ngay. */
    const val HOLD_DS = 36000

    /**
     * Playlist "giữ": 1 mục preset 0 (firmware bỏ qua, không đổi khung hình), `repeat` 0.
     * FW mới (≥ 2512040) có playlist lồng: nạp playlist khi đang chạy playlist khác thì cái cũ thành "cha",
     * playlist chạy 1 lần hết sẽ NẠP LẠI cha ở chỗ dở; chỉ playlist `repeat` 0 mới xoá thông tin cha.
     * Dùng để dừng playlist (tạm dừng / tắt) mà không để lại cha.
     */
    fun holdPlaylistJson(): JSONObject = toJson(listOf(Entry(0, HOLD_DS, 0)), loop = true)

    /**
     * Playlist inline gửi khi phát tiếp / tua: luôn `repeat` 0 (xem [holdPlaylistJson] — chạy 1 lần sẽ quay về
     * playlist 249 ở giữa chừng khi hết). Chạy 1 lần → mục cuối giữ [HOLD_DS] (mạch đứng ở mục cuối như khi
     * chạy thật; app tự tắt LED khi hết giờ).
     */
    fun resumeJson(entries: List<Entry>, loop: Boolean): JSONObject {
        if (loop || entries.isEmpty()) return toJson(entries, loop = true)
        val held = entries.dropLast(1) + entries.last().let { it.copy(durDs = maxOf(it.durDs, HOLD_DS)) }
        return toJson(held, loop = true)
    }

    fun toJson(entries: List<Entry>, loop: Boolean): JSONObject = JSONObject().apply {
        put("ps", JSONArray(entries.map { it.ps }))
        put("dur", JSONArray(entries.map { it.durDs }))
        put("transition", JSONArray(entries.map { it.trDs }))
        put("repeat", if (loop) 0 else 1)
        put("end", 0)
        put("r", 0)
    }

    data class LoadedClip(val presetId: Int, val startSec: Double, val durationSec: Double, val transitionSec: Double)

    /** Dựng lại clip từ playlist (Đọc từ mạch): mục [offSlot] (248) là khoảng trống, chỉ dời mốc thời gian. */
    fun clipsFromPlaylist(parsed: Parsed, offSlot: Int = PresetSlots.OFF): List<LoadedClip> {
        var t = 0
        val out = ArrayList<LoadedClip>()
        for (e in parsed.entries) {
            if (e.ps != offSlot && e.ps > 0) {
                out += LoadedClip(e.ps, t / 10.0, e.durDs / 10.0, e.trDs / 10.0)
            }
            t += e.durDs
        }
        return out
    }
}
