package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Logic thuần cho nút "Xóa preset + ảnh" (đồng bộ desktop `services/preset_ops.py`:
 * `plan_delete`, `orphan_images`, `delete_plan`).
 *
 * - Xoá preset TRƯỚC, ảnh SAU: không có lúc nào preset còn mà ảnh đã mất.
 * - Ảnh đi kèm chỉ bị xoá khi KHÔNG preset còn lại nào (kể cả nhóm khác, slot hệ thống)
 *   dùng nó, đang có trên mạch, và mọi preset bị xoá dùng ảnh đó đã xoá được thật.
 * - Không bao giờ xoá 100 (autosave), 248 (TIMELINE_OFF), 250 (nút bấm), 251–255.
 */
object PresetCleanup {
    val IMAGE_EXTS = listOf(".bmp", ".gif", ".jpg", ".jpeg", ".png")

    /** Slot hệ thống luôn được chừa khi dọn (249 thì xoá được nếu người dùng chọn). */
    val PROTECTED: Set<Int> = setOf(PresetSlots.AUTOSAVE, PresetSlots.OFF, PresetSlots.BUTTON)

    enum class Group { LOGO, TIMECODE, PLAYLIST, OTHER }

    fun groupOf(pid: Int): Group = when {
        pid in PresetSlots.LOGO_RANGE -> Group.LOGO
        pid == PresetSlots.PLAYLIST -> Group.PLAYLIST
        PresetSlots.isTimecodeSlot(pid) -> Group.TIMECODE
        else -> Group.OTHER
    }

    /** "logo.bmp" / "/logo.bmp" / " \\logo.bmp " → "/logo.bmp" ("" nếu rỗng). */
    fun normPath(name: String?): String {
        val t = (name ?: "").trim().replace('\\', '/').trimStart('/')
        return if (t.isEmpty()) "" else "/$t"
    }

    fun isImage(path: String?): Boolean {
        val p = normPath(path).lowercase()
        return p.isNotEmpty() && IMAGE_EXTS.any { p.endsWith(it) }
    }

    /** Ảnh 1 preset dùng (`seg[].n`; `seg` là mảng hoặc 1 object), không trùng, giữ thứ tự. */
    fun presetImages(preset: Any?): List<String> {
        if (preset !is JSONObject) return emptyList()
        val segs: List<JSONObject> = when (val s = preset.opt("seg")) {
            is JSONArray -> (0 until s.length()).mapNotNull { s.optJSONObject(it) }
            is JSONObject -> listOf(s)
            else -> emptyList()
        }
        val out = LinkedHashSet<String>()
        for (seg in segs) {
            val n = seg.opt("n") as? String ?: continue
            if (isImage(n)) out += normPath(n)
        }
        return out.toList()
    }

    /** Preset dọn được trên 1 mạch: object khác rỗng, ID > 0, không phải slot hệ thống / 251–255. */
    fun deletablePids(presets: JSONObject): List<Int> =
        presets.keys().asSequence()
            .mapNotNull { k -> k.toIntOrNull()?.takeIf { it > 0 && PresetSlots.isNonEmptyPreset(presets.opt(k)) } }
            .filter { it !in PROTECTED && it !in PresetSlots.RESERVED }
            .sorted()
            .toList()

    fun presetName(presets: JSONObject, pid: Int): String {
        val p = presets.optJSONObject(pid.toString())
        val n = p?.optString("n").orEmpty().ifBlank { p?.optString("name").orEmpty() }.trim()
        return n.ifBlank { if (pid == PresetSlots.PLAYLIST) "Playlist $pid" else "Preset $pid" }
    }

    /** (ảnh của preset bị xoá, ảnh của preset còn lại) — so không phân biệt hoa thường. */
    private fun refsSplit(presets: JSONObject, delete: Set<Int>): Pair<Set<String>, Set<String>> {
        val gone = HashSet<String>()
        val kept = HashSet<String>()
        val keys = presets.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val pid = k.toIntOrNull() ?: continue
            val refs = presetImages(presets.opt(k)).map { it.lowercase() }
            if (pid in delete) gone += refs else kept += refs
        }
        return gone to kept
    }

    class Plan(
        val pids: List<Int>,
        /** Ảnh sẽ xoá (tên đúng như trên mạch, có "/"). */
        val files: List<String>,
        /** Ảnh → preset (trong [pids]) đang dùng nó. Ảnh thừa: danh sách rỗng. */
        val filePids: Map<String, List<Int>>
    ) {
        val isEmpty: Boolean get() = pids.isEmpty() && files.isEmpty()

        /** Ảnh xoá được sau khi đã xoá thật [deleted]: pdel lỗi thì preset còn đó → giữ ảnh của nó. */
        fun filesAfter(deleted: Set<Int>): List<String> =
            files.filter { f -> filePids[f].orEmpty().all { it in deleted } }
    }

    /**
     * Kế hoạch xoá trên 1 mạch.
     * @param deviceFiles mọi file trên mạch (`/edit?list`); rỗng = không đọc được → không xoá ảnh nào.
     * @param extraFiles ảnh người dùng chọn thêm (ảnh thừa); vẫn bỏ qua nếu preset còn lại đang dùng.
     */
    fun plan(
        presets: JSONObject,
        deviceFiles: Collection<String>,
        pids: Collection<Int>,
        extraFiles: Collection<String> = emptyList()
    ): Plan {
        val existing = LinkedHashMap<String, String>()
        for (f in deviceFiles) {
            val p = normPath(f)
            if (isImage(p)) existing.putIfAbsent(p.lowercase(), p)
        }
        val allowed = deletablePids(presets).toSet()
        val del = pids.filter { it in allowed }.distinct().sorted()
        val (gone, kept) = refsSplit(presets, del.toSet())
        val wanted = gone + extraFiles.map { normPath(it).lowercase() }.filter { it.isNotEmpty() }
        val outFiles = (wanted - kept).mapNotNull { existing[it] }.sorted()
        val filePids = outFiles.associateWith { path ->
            del.filter { pid -> presetImages(presets.opt(pid.toString())).any { it.equals(path, ignoreCase = true) } }
        }
        return Plan(del, outFiles, filePids)
    }

    /** Ảnh trên mạch không preset nào dùng (rác còn sót sau khi xoá / gửi lại). */
    fun orphanImages(presets: JSONObject, deviceFiles: Collection<String>): List<String> {
        val (_, used) = refsSplit(presets, emptySet())
        return deviceFiles.map { normPath(it) }
            .filter { isImage(it) && it.lowercase() !in used }
            .distinct()
            .sorted()
    }
}
