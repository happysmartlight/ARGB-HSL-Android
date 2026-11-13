package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Loại preset hiện ở tab Biên Tập Timeline (lọc nhanh khi mạch có hàng trăm preset — pmax 1024). */
enum class PresetKind { POI, FLAG, EFFECT, PLAYLIST }

/** Logic thuần của tab Biên Tập Timeline (có test). */
object TimelineEditing {
    /** Chuyển cảnh mặc định cho clip mới — như desktop (`default_transition` 0.7 s). */
    const val DEFAULT_TRANSITION_SEC = 0.7f

    private fun segments(preset: JSONObject): List<JSONObject> = when (val s = preset.opt("seg")) {
        is JSONArray -> (0 until s.length()).mapNotNull { s.optJSONObject(it) }
        is JSONObject -> listOf(s)
        else -> emptyList()
    }

    fun isPlaylist(preset: JSONObject?): Boolean =
        (preset?.optJSONObject("playlist")?.optJSONArray("ps")?.length() ?: 0) > 0

    fun kindOf(preset: JSONObject): PresetKind {
        if (isPlaylist(preset)) return PresetKind.PLAYLIST
        val images = PresetCleanup.presetImages(preset).map { it.lowercase() }
        if (images.any { it.endsWith(".bmp") }) return PresetKind.POI
        if (images.any { it.endsWith(".gif") }) return PresetKind.FLAG
        val fx = segments(preset).map { it.optInt("fx", -1) }
        return when {
            PoiFx in fx -> PresetKind.POI
            FlagFx in fx -> PresetKind.FLAG
            else -> PresetKind.EFFECT
        }
    }

    private const val PoiFx = 142   // "Poi HSL" trên FW ARGB HSL
    private const val FlagFx = 53   // GIF ma trận

    /**
     * Preset kéo được vào timeline: ID > 0, không phải slot hệ thống (100/248/249/250) hay 251–255,
     * KHÔNG phải playlist (playlist lồng sẽ thay playlist đang chạy → phần sau timeline không phát).
     */
    fun editablePresets(presets: JSONObject): List<Triple<Int, String, PresetKind>> =
        presets.keys().asSequence()
            .mapNotNull { k -> k.toIntOrNull()?.let { it to presets.optJSONObject(k) } }
            .filter { (pid, obj) ->
                pid > 0 && pid !in PresetSlots.SYSTEM && pid !in PresetSlots.RESERVED && obj != null && obj.length() > 0
            }
            .map { (pid, obj) -> Triple(pid, PresetCleanup.presetName(presets, pid), kindOf(obj!!)) }
            .filter { it.third != PresetKind.PLAYLIST }
            .sortedBy { it.first }
            .toList()

    /** Preset trong playlist sắp ghi mà bản thân là playlist (ghi vào sẽ phá timeline). */
    fun nestedPlaylistRefs(presets: JSONObject, ids: Collection<Int>): List<Int> =
        ids.filter { it != PresetSlots.OFF }.distinct().filter { isPlaylist(presets.optJSONObject(it.toString())) }

    /** Tìm không dấu, theo tên hoặc số ID. */
    fun matches(id: Int, name: String, query: String): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        return id.toString() == q || fold(name).contains(q)
    }

    fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd')
}
