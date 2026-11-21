package com.example.data

import org.json.JSONObject

/**
 * Lập kế hoạch ghi 1 track của file timeline desktop lên mạch có trình phát show (logic thuần, có test).
 *
 * Khác playlist 249 ([TimecodePlanner]): không cần ghi preset cho từng clip — hình ảnh nằm ngay trong file
 * show. Chỉ cần ảnh (GIF/BMP) mà hình ảnh tham chiếu có trên mạch. Clip không kèm công thức (file cũ) lấy
 * hình ảnh từ preset đang có trên mạch theo `preset_id`, như desktop `_show_text_for`.
 */
object ShowPlanner {
    class Plan(
        val clips: List<ShowFile.Clip>,
        /** Ảnh cần upload trước file show (path → byte), đã khử trùng theo path. */
        val uploads: Map<String, ByteArray>,
        val problems: List<String>,
        val warnings: List<String>,
        /** Có clip thiếu byte ảnh → phải đọc `/edit?list` rồi lập lại kế hoạch. */
        val needsDeviceFiles: Boolean
    ) {
        val segmentClipCount: Int get() = clips.count { it.segment != null }
        val layeredClipCount: Int get() = clips.count { it.segment == null && (it.looks?.size ?: 0) > 1 }
    }

    /**
     * @param presets presets.json của mạch (cho clip không kèm công thức).
     * @param deviceFiles đường dẫn ảnh (chữ thường) đang có trên mạch; null = chưa đọc.
     * @param playerVersion `show.v` của mạch.
     */
    fun plan(
        track: TimelineTrack,
        recipeTable: JSONObject?,
        cache: MutableMap<String, ByteArray>,
        presets: JSONObject,
        deviceFiles: Set<String>?,
        playerVersion: Int
    ): Plan {
        val keys = track.segments.map { it.key }.toSet()
        val uploads = LinkedHashMap<String, ByteArray>()
        val problems = ArrayList<String>()
        val warnings = ArrayList<String>()
        var needsFiles = false
        val out = ArrayList<ShowFile.Clip>()

        if (playerVersion < ShowFile.LED_ORDER_PLAYER_VERSION && track.segments.any { it.ledOrder }) {
            problems += "Phân đoạn \"Thứ tự LED\" cần firmware ${ShowFile.LED_ORDER_FIRMWARE} trở lên — cập nhật firmware cho mạch này."
        }

        track.clips.sortedBy { it.optDouble("start", 0.0) }.forEachIndexed { i, clip ->
            val name = clip.optString("name").ifBlank { "Clip ${i + 1}" }
            val pid = clip.optInt("preset_id", -1)
            // như desktop khi mở file: clip trỏ tới đoạn không còn → về hàng toàn mạch
            val segment = clip.optString("segment").takeIf { it.isNotEmpty() && it != "null" && it in keys }
            val snapshot = RecipeCodec.parseSnapshot(clip.optJSONObject("bake_snapshot"))
            val recipe = snapshot ?: RecipeCodec.parseRecipe(clip.optJSONObject("recipe"), recipeTable, cache)
            val payload = recipe?.payload ?: presets.optJSONObject(pid.toString())?.takeIf {
                pid > 0 && pid !in PresetSlots.RESERVED && it.length() > 0
            }
            if (payload == null) {
                if (pid > 0) problems += "\"$name\": preset $pid không có trên mạch và file không kèm nội dung để ghi lại."
                return@forEachIndexed            // clip trống (preset_id ≤ 0) = khoảng tối
            }
            val look = ShowFile.clipLook(payload)
            if (look == null) {
                problems += "\"$name\": preset không có hiệu ứng nào."
                return@forEachIndexed
            }
            if (recipe != null) {
                val lacking = ArrayList<String>()
                for (path in recipe.missingFiles) {
                    if (deviceFiles == null) needsFiles = true
                    else if (path.lowercase() !in deviceFiles) lacking += path
                }
                if (lacking.isNotEmpty()) {
                    problems += "\"$name\": mạch thiếu ảnh ${lacking.joinToString()} và file không kèm ảnh này."
                    return@forEachIndexed
                }
                for (f in recipe.files) {
                    val prev = uploads[f.path]
                    if (prev == null) uploads[f.path] = f.bytes
                    else if (!prev.contentEquals(f.bytes)) warnings += "Ảnh ${f.path} xuất hiện 2 lần với nội dung khác nhau — dùng bản đầu tiên."
                }
            }
            out += ShowFile.Clip(
                segment = segment,
                start = clip.optDouble("start", 0.0),
                duration = clip.optDouble("duration", 0.0),
                transition = clip.optDouble("transition", 0.0),
                name = name,
                look = look,
                looks = ShowFile.clipLooks(payload)
            )
        }
        return Plan(out, uploads, problems, warnings, needsFiles)
    }

    /** Chữ ký hình ảnh của 1 preset / clip: các lớp (preset nhiều phân đoạn) hoặc lớp đầu. */
    private fun lookSignature(look: JSONObject?, looks: List<JSONObject>?): String? = when {
        !looks.isNullOrEmpty() && looks.size > 1 -> looks.joinToString("|") { lk ->
            PresetJson.canonical(JSONObject().also { o -> for (k in lk.keys()) if (k != "area") o.put(k, lk.opt(k)) })
        }
        look != null -> PresetJson.canonical(look)
        else -> null
    }

    /**
     * Preset trên mạch có cùng hình ảnh với clip của file show (tab Biên Tập dựng lại clip từ file show: clip
     * trỏ preset). Bỏ slot hệ thống, 251–255, playlist và lệnh phát show. Null = không preset nào khớp.
     */
    fun presetForClip(presets: JSONObject, clip: ShowFile.Clip): Int? {
        val want = lookSignature(clip.look, clip.looks) ?: return null
        val candidates = presets.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().mapNotNull { id ->
            if (id <= 0 || id in PresetSlots.SYSTEM || id in PresetSlots.RESERVED) return@mapNotNull null
            val p = presets.optJSONObject(id.toString()) ?: return@mapNotNull null
            if (p.has("playlist") || ShowFile.isPlayPreset(p)) null else id to p
        }.toList()
        candidates.firstOrNull { (_, p) -> lookSignature(ShowFile.clipLook(p), ShowFile.clipLooks(p)) == want }?.let { return it.first }
        // Show ghi cho trình phát cũ (trước v6) chỉ chở lớp đầu của preset nhiều lớp → so lớp đầu.
        if (clip.looks.isNullOrEmpty()) {
            candidates.firstOrNull { (_, p) -> ShowFile.clipLook(p)?.let { PresetJson.canonical(it) } == want }?.let { return it.first }
        }
        return null
    }

    /**
     * Clip cho mạch firmware cũ (playlist 249): chỉ hàng toàn mạch. Clip theo phân đoạn không biên dịch
     * được thành playlist (chúng chạy cùng lúc trên các phần khác nhau của mạch) → bỏ, kèm số lượng để báo.
     */
    fun boardRowClips(track: TimelineTrack): Pair<List<JSONObject>, Int> {
        val keys = track.segments.map { it.key }.toSet()
        val (seg, board) = track.clips.partition { c -> c.optString("segment").let { it.isNotEmpty() && it != "null" && it in keys } }
        return board to seg.size
    }
}
