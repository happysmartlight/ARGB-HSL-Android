package com.example.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Logic thuần (không mạng) cho file timeline desktop v3.17+ và quy tắc an toàn khi ghi
 * preset lên mạch. Spec: `docs/android-timecode-recipe-task.md` (§ trích dẫn trong comment).
 */

// =============================================================================================
// Dải slot preset (desktop: services/preset_groups.py)
// =============================================================================================
object PresetSlots {
    const val LEGACY_PMAX = 250
    const val EXT_START = 256
    const val AUTOSAVE = 100
    const val OFF = 248
    const val PLAYLIST = 249
    const val BUTTON = 250

    /** Slot hệ thống: không bao giờ cấp cho clip. */
    val SYSTEM: Set<Int> = setOf(AUTOSAVE, OFF, PLAYLIST, BUTTON)

    /** 251–255: firmware giữ riêng, psave/pdel bị bỏ qua im lặng — không bao giờ ghi/xoá/tham chiếu. */
    val RESERVED: IntRange = 251..255

    val LOGO_RANGE: IntRange = 1..59
    val TIMECODE_RANGE: IntRange = 60..240

    /** `fs.pmax` → pmax thật; thiếu/không hợp lệ = 250 (KHÔNG suy từ vid). */
    fun pmaxOrLegacy(pmax: Int?): Int = if (pmax != null && pmax > 0) pmax else LEGACY_PMAX

    /** pmax để hiện sức chứa: từ info nếu có, không thì suy từ preset ≥ 256 đang có trên mạch. */
    fun pmaxHint(pmax: Int?, presets: JSONObject?): Int {
        var p = pmaxOrLegacy(pmax)
        if (presets != null) {
            val keys = presets.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toIntOrNull() ?: continue
                if (id > p && id !in RESERVED && isNonEmptyPreset(presets.opt(k))) p = id
            }
        }
        return p
    }

    fun isValidId(pid: Int, pmax: Int): Boolean = pid in 1..pmax && pid !in RESERVED

    /** Slot thuộc nhóm Timecode (60–240 hoặc ≥ 256), đã trừ slot hệ thống. */
    fun isTimecodeSlot(pid: Int): Boolean =
        (pid in TIMECODE_RANGE || pid >= EXT_START) && pid !in SYSTEM && pid !in RESERVED

    /** Thứ tự cấp slot Timecode (§4.6): 60..240 rồi 256..pmax (chỉ khi pmax ≥ 256). */
    fun timecodeSlots(pmax: Int): List<Int> {
        val out = ArrayList<Int>()
        for (s in TIMECODE_RANGE) if (s !in SYSTEM) out += s
        if (pmax >= EXT_START) for (s in EXT_START..pmax) out += s
        return out
    }

    fun timecodeRangeLabel(pmax: Int): String =
        if (pmax >= EXT_START) "60–240, $EXT_START–$pmax" else "60–240"

    fun validRangeLabel(pmax: Int): String =
        if (pmax >= EXT_START) "1–$LEGACY_PMAX, $EXT_START–$pmax" else "1–${minOf(pmax, LEGACY_PMAX)}"

    fun isNonEmptyPreset(v: Any?): Boolean = v is JSONObject && v.length() > 0

    /** Slot trống đầu tiên (§4.6): bỏ slot hệ thống, 251–255 và slot đang giữ object khác rỗng. */
    fun findFreeSlot(presets: JSONObject, pmax: Int): Int? =
        timecodeSlots(pmax).firstOrNull { !isNonEmptyPreset(presets.opt(it.toString())) }
}

// =============================================================================================
// JSON: dạng gọn để upload, dạng chuẩn để so sánh nội dung preset
// =============================================================================================
object PresetJson {
    private val IMAGE_EXTS = listOf(".gif", ".bmp", ".png", ".jpg", ".jpeg")

    /** JSON gọn, UTF-8 thật (không `\uXXXX` cho ký tự có dấu, không `\/`), giữ thứ tự key. */
    fun compact(value: Any?): String = StringBuilder().also { write(value, it, sortKeys = false) }.toString()

    /** Dạng chuẩn: key sắp xếp, số nguyên viết không ".0" — dùng làm chữ ký so sánh. */
    fun canonical(value: Any?): String = StringBuilder().also { write(value, it, sortKeys = true) }.toString()

    /**
     * Nội dung presets.json để upload (§5.2): `"0":{}` đầu tiên, key số tăng dần, không dấu cách.
     * Firmware sửa file tại chỗ; file có indent sẽ hỏng sau một lần `pdel`.
     */
    fun presetsJsonText(presets: JSONObject): String {
        val sb = StringBuilder("{")
        val zero = presets.opt("0")
        sb.append("\"0\":")
        if (zero is JSONObject) write(zero, sb, sortKeys = false) else sb.append("{}")
        val numeric = ArrayList<Pair<Int, String>>()
        val other = ArrayList<String>()
        val keys = presets.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "0") continue
            val n = if (k.isNotEmpty() && k.all { it.isDigit() }) k.toIntOrNull() else null
            if (n != null) numeric += n to k else other += k
        }
        for (k in numeric.sortedBy { it.first }.map { it.second } + other) {
            sb.append(',')
            writeString(k, sb)
            sb.append(':')
            write(presets.opt(k), sb, sortKeys = false)
        }
        return sb.append('}').toString()
    }

    /** Chữ ký nội dung preset, bỏ qua `n` / `name` cấp ngoài cùng (§4.3). Null = rỗng / chỉ có tên. */
    fun contentSignature(preset: Any?): String? {
        if (preset !is JSONObject) return null
        val sb = StringBuilder("{")
        var first = true
        for (k in preset.keys().asSequence().filterNot { it == "n" || it == "name" }.sorted()) {
            if (!first) sb.append(',')
            first = false
            writeString(k, sb)
            sb.append(':')
            write(preset.opt(k), sb, sortKeys = true)
        }
        if (first) return null
        return sb.append('}').toString()
    }

    fun samePresetContent(a: Any?, b: Any?): Boolean {
        val sa = contentSignature(a) ?: return false
        return sa == contentSignature(b)
    }

    /** Chữ ký nội dung → slot đầu tiên giữ nội dung đó (key tăng dần, bỏ 100 autosave). */
    fun contentIndex(presets: JSONObject): MutableMap<String, Int> {
        val ids = presets.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
        val out = LinkedHashMap<String, Int>()
        for (id in ids) {
            if (id == PresetSlots.AUTOSAVE || id in PresetSlots.RESERVED) continue
            val sig = contentSignature(presets.opt(id.toString())) ?: continue
            if (!out.containsKey(sig)) out[sig] = id
        }
        return out
    }

    /** Đường dẫn ảnh mà preset tham chiếu (`seg[].n = "/name.bmp"`). */
    fun fileRefs(payload: JSONObject?): List<String> {
        val out = LinkedHashSet<String>()
        val segs = payload?.optJSONArray("seg") ?: return emptyList()
        for (i in 0 until segs.length()) {
            val n = segs.optJSONObject(i)?.optString("n").orEmpty().trim()
            if (n.isEmpty()) continue
            val path = if (n.startsWith("/")) n else "/$n"
            if (IMAGE_EXTS.any { path.lowercase().endsWith(it) }) out += path
        }
        return out.toList()
    }

    private fun write(v: Any?, sb: StringBuilder, sortKeys: Boolean) {
        when (v) {
            null, JSONObject.NULL -> sb.append("null")
            is JSONObject -> {
                sb.append('{')
                val keys = v.keys().asSequence().toList().let { if (sortKeys) it.sorted() else it }
                keys.forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    writeString(k, sb)
                    sb.append(':')
                    write(v.opt(k), sb, sortKeys)
                }
                sb.append('}')
            }
            is JSONArray -> {
                sb.append('[')
                for (i in 0 until v.length()) {
                    if (i > 0) sb.append(',')
                    write(v.opt(i), sb, sortKeys)
                }
                sb.append(']')
            }
            is Boolean -> sb.append(if (v) "true" else "false")
            is Number -> sb.append(numberText(v))
            is String -> writeString(v, sb)
            else -> writeString(v.toString(), sb)
        }
    }

    private fun numberText(n: Number): String {
        if (n is Int || n is Long || n is Short || n is Byte) return n.toLong().toString()
        val d = n.toDouble()
        if (d.isNaN() || d.isInfinite()) return "null"
        if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) return d.toLong().toString()
        return d.toString()
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}

// =============================================================================================
// MAC, SHA-256, recipe
// =============================================================================================

/** "AA:bb-cc…" → "aabbcc…" (12 hex thường). Không hợp lệ / toàn 0 → "" (coi như không có MAC). */
fun normMac(mac: String?): String {
    val hex = (mac ?: "").filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
    return if (hex.length == 12 && hex != "000000000000") hex else ""
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class RecipeFile(val path: String, val bytes: ByteArray)

/**
 * Nội dung preset mà clip cần (từ `recipe` hoặc `bake_snapshot`).
 * [missingFiles]: ảnh payload tham chiếu nhưng file timeline không kèm byte — mạch đích có thể đã có sẵn.
 */
class Recipe(
    val kind: String,
    val payload: JSONObject,
    val files: List<RecipeFile>,
    val missingFiles: List<String>
)

object RecipeCodec {
    private fun decodeB64(b64: String): ByteArray? =
        if (b64.isEmpty()) null else runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()

    private fun normPath(p: String): String = if (p.startsWith("/")) p else "/$p"

    /** `clips[].recipe` (§4.1): ảnh tra theo sha256 trong `recipe_files`; [cache] để ảnh dùng chung chỉ giải mã 1 lần. */
    fun parseRecipe(entry: JSONObject?, table: JSONObject?, cache: MutableMap<String, ByteArray>): Recipe? {
        if (entry == null) return null
        val payload = entry.optJSONObject("payload") ?: return null
        val files = mutableListOf<RecipeFile>()
        val missing = mutableListOf<String>()
        val arr = entry.optJSONArray("files") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val raw = f.optString("path")
            if (raw.isEmpty()) continue
            val path = normPath(raw)
            val sha = f.optString("sha256").lowercase()
            var bytes: ByteArray? = if (sha.isNotEmpty()) cache[sha] else null
            if (bytes == null) {
                var b64 = if (sha.isNotEmpty() && table != null && table.has(sha)) table.optString(sha) else ""
                if (b64.isEmpty()) b64 = f.optString("content_b64")          // fallback được chấp nhận
                bytes = decodeB64(b64)
                if (bytes != null && sha.isNotEmpty()) {
                    if (sha256Hex(bytes) != sha) bytes = null else cache[sha] = bytes
                }
            }
            if (bytes == null) { missing += path; continue }
            files += RecipeFile(path, bytes)
        }
        addUnlistedRefs(payload, files, missing)
        return Recipe(entry.optString("kind", "preset"), payload, files, missing)
    }

    /** `bake_snapshot`: ảnh inline `files[].content_b64`. */
    fun parseSnapshot(entry: JSONObject?): Recipe? {
        if (entry == null) return null
        val payload = entry.optJSONObject("payload") ?: return null
        val files = mutableListOf<RecipeFile>()
        val missing = mutableListOf<String>()
        val arr = entry.optJSONArray("files") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val raw = f.optString("path")
            if (raw.isEmpty()) continue
            val bytes = decodeB64(f.optString("content_b64"))
            if (bytes == null) missing += normPath(raw) else files += RecipeFile(normPath(raw), bytes)
        }
        addUnlistedRefs(payload, files, missing)
        return Recipe(entry.optString("kind", "preset"), payload, files, missing)
    }

    private fun addUnlistedRefs(payload: JSONObject, files: List<RecipeFile>, missing: MutableList<String>) {
        for (ref in PresetJson.fileRefs(payload)) {
            if (files.none { it.path.equals(ref, true) } && missing.none { it.equals(ref, true) }) missing += ref
        }
    }
}

// =============================================================================================
// File timeline (đã giải mã) → track
// =============================================================================================

/** Một track trong file timeline (mock hoặc mạch thật). [clips] trỏ vào JSONObject gốc. */
class TimelineTrack(
    val index: Int,
    val ip: String,
    val name: String,
    val isMock: Boolean,
    /** MAC chuẩn hoá ("" nếu không có / mock). */
    val mac: String,
    val clips: List<JSONObject>
) {
    val recipeCount: Int get() = clips.count { it.optJSONObject("recipe") != null }
    val pendingBakeCount: Int get() = clips.count { it.optJSONObject("bake_snapshot") != null }
    val endSeconds: Double get() = clips.maxOfOrNull { it.optDouble("start", 0.0) + it.optDouble("duration", 0.0) } ?: 0.0
}

class TimecodeFile private constructor(
    val config: JSONObject,
    val tracks: List<TimelineTrack>
) {
    val recipeTable: JSONObject? = config.optJSONObject("recipe_files")
    /** sha256 → byte ảnh; nhiều mạch được nạp song song nên dùng map an toàn đa luồng. */
    val recipeCache: MutableMap<String, ByteArray> = java.util.concurrent.ConcurrentHashMap()
    val loop: Boolean = config.optJSONObject("defaults")?.optBoolean("loop", false) ?: false
    val audioPath: String? = config.optString("audio_path").takeIf { it.isNotBlank() && it != "null" }
    val audioRel: String? = config.optString("audio_rel").takeIf { it.isNotBlank() && it != "null" }

    /** Tên file nhạc (đoạn cuối của audio_rel / audio_path, tách theo cả `/` và `\`). */
    val audioFileName: String?
        get() = (audioRel ?: audioPath)?.split('/', '\\')?.lastOrNull { it.isNotBlank() }

    companion object {
        fun isMockIp(ip: String): Boolean = ip.startsWith("mock:")

        fun parse(config: JSONObject): TimecodeFile {
            val mockNames = HashMap<String, String>()
            config.optJSONArray("mock_devices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id")
                    if (id.isNotBlank()) mockNames[id] = m.optString("name", id).ifBlank { id }
                }
            }
            val tracks = ArrayList<TimelineTrack>()
            val arr = config.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val ip = t.optString("ip")
                val isMock = t.optBoolean("is_mock", false) || isMockIp(ip)
                val clips = ArrayList<JSONObject>()
                t.optJSONArray("clips")?.let { cs -> for (j in 0 until cs.length()) cs.optJSONObject(j)?.let { clips += it } }
                val name = t.optString("name").ifBlank { mockNames[ip] ?: ip }
                tracks += TimelineTrack(
                    index = i,
                    ip = ip,
                    name = if (isMock) (mockNames[ip] ?: name) else name,
                    isMock = isMock,
                    mac = if (isMock) "" else normMac(t.optString("mac")),
                    clips = clips
                )
            }
            return TimecodeFile(config, tracks)
        }
    }
}

// =============================================================================================
// Tự gán track thật → thiết bị đã lưu (§4.2)
// =============================================================================================
object TrackMatcher {
    enum class Kind { MAC, MAC_NEW_IP, IP, NONE, IP_OTHER_DEVICE }

    data class Candidate(val ip: String, val mac: String?)
    data class Match(val ip: String?, val kind: Kind)

    /**
     * MAC thắng IP; một thiết bị không bao giờ gán cho 2 track. IP cũ trả lời bằng MAC khác →
     * IP đó thuộc mạch khác, không dùng cho track này.
     */
    fun autoMatch(tracks: List<TimelineTrack>, devices: List<Candidate>): Map<Int, Match> {
        val out = HashMap<Int, Match>()
        val taken = HashSet<String>()
        val real = tracks.filter { !it.isMock }
        // Lượt 1: theo MAC.
        for (t in real) {
            if (t.mac.isEmpty()) continue
            val dev = devices.firstOrNull { normMac(it.mac) == t.mac && it.ip !in taken } ?: continue
            taken += dev.ip
            out[t.index] = Match(dev.ip, if (dev.ip == t.ip) Kind.MAC else Kind.MAC_NEW_IP)
        }
        // Lượt 2: theo IP.
        for (t in real) {
            if (out.containsKey(t.index)) continue
            val dev = devices.firstOrNull { it.ip == t.ip }
            out[t.index] = when {
                dev == null -> Match(null, Kind.NONE)
                dev.ip in taken -> Match(null, Kind.NONE)
                t.mac.isNotEmpty() && normMac(dev.mac).isNotEmpty() && normMac(dev.mac) != t.mac ->
                    Match(null, Kind.IP_OTHER_DEVICE)
                else -> { taken += dev.ip; Match(dev.ip, Kind.IP) }
            }
        }
        return out
    }
}

// =============================================================================================
// Lập kế hoạch ghi 1 track lên 1 mạch (§4.4 đối chiếu + §4.5 bake) và biên dịch playlist 249
// =============================================================================================
object TimecodePlanner {
    const val PLAYLIST_NAME_IMPORT = "Timecode 249"
    /** Khoảng trống giữa 2 clip nhỏ hơn mức này (giây) không chèn OFF 248. */
    const val GAP_TOLERANCE = 0.05

    enum class Action {
        /** Slot đang đúng nội dung công thức. */
        OK,
        /** Nội dung nằm ở slot khác trên mạch → trỏ lại. */
        REPOINTED,
        /** Clip chưa ghi (bake_snapshot) / mất preset / khác mạch → ghi preset mới. */
        BAKE,
        /** Không có công thức, dùng preset đang có ở preset_id. */
        EXISTING,
        /** preset_id ≤ 0 → giữ chỗ bằng OFF 248. */
        OFF,
        /** Không thể phát đúng (thiếu preset, thiếu ảnh). */
        UNRESOLVED
    }

    class ClipPlan(
        val name: String,
        val start: Double,
        val duration: Double,
        val transition: Double,
        val action: Action,
        var slot: Int?,
        val payload: JSONObject? = null,
        val note: String? = null
    )

    class DevicePlan(
        val clips: List<ClipPlan>,
        /** Ảnh cần upload trước presets.json (path → byte), đã khử trùng theo path. */
        val uploads: Map<String, ByteArray>,
        val problems: List<String>,
        val warnings: List<String>,
        /** Có clip cần ghi mà thiếu byte ảnh → phải đọc `/edit?list` rồi lập lại kế hoạch. */
        val needsDeviceFiles: Boolean
    ) {
        fun count(a: Action) = clips.count { it.action == a }
    }

    /**
     * @param sameDevice track thật đang ghi lại đúng mạch của nó (chỉ ảnh hưởng nhãn/cảnh báo).
     * @param deviceFiles đường dẫn ảnh (chữ thường) đang có trên mạch; null = chưa đọc.
     */
    fun plan(
        clips: List<JSONObject>,
        recipeTable: JSONObject?,
        cache: MutableMap<String, ByteArray>,
        presets: JSONObject,
        pmax: Int,
        sameDevice: Boolean,
        deviceFiles: Set<String>?
    ): DevicePlan {
        val index = PresetJson.contentIndex(presets)
        val uploads = LinkedHashMap<String, ByteArray>()
        val problems = ArrayList<String>()
        val warnings = ArrayList<String>()
        var needsFiles = false
        val out = ArrayList<ClipPlan>()

        clips.sortedBy { it.optDouble("start", 0.0) }.forEachIndexed { i, clip ->
            val name = clip.optString("name").ifBlank { "Clip ${i + 1}" }
            val start = clip.optDouble("start", 0.0)
            val duration = clip.optDouble("duration", 0.0)
            val transition = clip.optDouble("transition", 0.0)
            val pid = clip.optInt("preset_id", -1)
            fun mk(action: Action, slot: Int?, payload: JSONObject? = null, note: String? = null) =
                ClipPlan(name, start, duration, transition, action, slot, payload, note)

            val cur = presets.opt(pid.toString()).takeIf { PresetSlots.isValidId(pid, pmax) && PresetSlots.isNonEmptyPreset(it) }

            // bake_snapshot (mock, hoặc clip trên track thật chưa kịp ghi): preset_id chỉ là slot tạm.
            val snapshot = RecipeCodec.parseSnapshot(clip.optJSONObject("bake_snapshot"))
            val recipe = snapshot ?: RecipeCodec.parseRecipe(clip.optJSONObject("recipe"), recipeTable, cache)

            if (recipe == null) {
                when {
                    pid <= 0 -> out += mk(Action.OFF, PresetSlots.OFF)
                    cur != null -> {
                        out += mk(Action.EXISTING, pid)
                        if (!sameDevice) warnings += "\"$name\": dùng preset $pid có sẵn trên mạch (file không kèm nội dung để kiểm tra)."
                    }
                    else -> {
                        out += mk(Action.UNRESOLVED, null)
                        problems += "\"$name\": preset $pid không có trên mạch và file không kèm nội dung để ghi lại."
                    }
                }
                return@forEachIndexed
            }

            fun queueFiles() {
                for (f in recipe.files) {
                    val prev = uploads[f.path]
                    if (prev == null) uploads[f.path] = f.bytes
                    else if (!prev.contentEquals(f.bytes)) warnings += "Ảnh ${f.path} xuất hiện 2 lần với nội dung khác nhau — dùng bản đầu tiên."
                }
            }

            val sig = PresetJson.contentSignature(recipe.payload)
            if (snapshot == null && sig != null) {
                if (cur != null && PresetJson.contentSignature(cur) == sig) { out += mk(Action.OK, pid); return@forEachIndexed }
                val slot = index[sig]
                if (slot != null) {
                    out += mk(Action.REPOINTED, slot, note = if (sameDevice) "preset $pid → $slot" else null)
                    return@forEachIndexed
                }
            } else if (snapshot != null && sig != null) {
                // Nội dung y hệt đã có trên mạch (vd nạp lại cùng file) → dùng lại, không tốn slot.
                // Vẫn gửi lại ảnh kèm theo để mạch không giữ ảnh cũ trùng tên.
                val slot = index[sig]
                if (slot != null) {
                    queueFiles()
                    out += mk(Action.REPOINTED, slot)
                    return@forEachIndexed
                }
            }

            // Cần ghi preset mới: kiểm ảnh.
            val lacking = ArrayList<String>()
            for (path in recipe.missingFiles) {
                if (deviceFiles == null) needsFiles = true
                else if (path.lowercase() !in deviceFiles) lacking += path
            }
            if (lacking.isNotEmpty()) {
                out += mk(Action.UNRESOLVED, null)
                problems += "\"$name\": mạch thiếu ảnh ${lacking.joinToString()} và file không kèm ảnh này."
                return@forEachIndexed
            }
            queueFiles()
            out += mk(
                Action.BAKE, null, payload = recipe.payload,
                note = when {
                    snapshot != null -> null
                    sameDevice -> "mất preset $pid trên mạch → ghi lại"
                    else -> null
                }
            )
        }
        return DevicePlan(out, uploads, problems, warnings, needsFiles)
    }

    class Applied(val writtenSlots: List<Int>, val playlist: JSONObject, val ps: List<Int>, val clipCount: Int)

    class NoFreeSlotException(pmax: Int) :
        Exception("Hết slot trống (${PresetSlots.timecodeRangeLabel(pmax)}) trên mạch — hãy dọn bớt preset Timecode.")

    /**
     * Ghi các clip BAKE vào slot trống, thêm 248 (nếu có khoảng trống) và 249 vào [presets].
     * Gọi SAU khi đã kiểm [DevicePlan.problems] rỗng.
     */
    fun apply(
        plan: DevicePlan,
        presets: JSONObject,
        pmax: Int,
        timelineSeconds: Double,
        loop: Boolean,
        playlistName: String = PLAYLIST_NAME_IMPORT
    ): Applied {
        val written = ArrayList<Int>()
        val bakedBySig = HashMap<String, Int>()
        for (c in plan.clips) {
            if (c.action != Action.BAKE) continue
            val payload = c.payload ?: continue
            val sig = PresetJson.contentSignature(payload)
            val slot = sig?.let { bakedBySig[it] }
                ?: PresetSlots.findFreeSlot(presets, pmax)
                ?: throw NoFreeSlotException(pmax)
            if (slot !in written) {
                presets.put(slot.toString(), payload)
                written += slot
            }
            if (sig != null) bakedBySig[sig] = slot
            c.slot = slot
        }
        val entries = plan.clips.mapNotNull { c -> c.slot?.let { PlaylistEntry(it, c.start, c.duration, c.transition) } }
        val compiled = compilePlaylist(entries, timelineSeconds, loop)
        if (compiled.ps.contains(PresetSlots.OFF)) {
            presets.put(PresetSlots.OFF.toString(), timelineOffPreset())
            written += PresetSlots.OFF
        }
        presets.put(PresetSlots.PLAYLIST.toString(), playlistPreset(compiled.playlist, playlistName))
        written += PresetSlots.PLAYLIST
        return Applied(written, compiled.playlist, compiled.ps, plan.clips.count { it.action != Action.OFF && it.slot != null })
    }

    class PlaylistEntry(val slot: Int, val start: Double, val duration: Double, val transition: Double)
    class Compiled(val playlist: JSONObject, val ps: List<Int>)

    /**
     * Biên dịch clip → playlist WLED (đơn vị decisecond). Mốc đầu/cuối được làm tròn theo thời
     * gian TUYỆT ĐỐI nên tổng thời lượng không bị trôi dần qua nhiều clip. Khoảng trống → 248 OFF.
     */
    fun compilePlaylist(entries: List<PlaylistEntry>, timelineSeconds: Double, loop: Boolean): Compiled {
        val ps = ArrayList<Int>()
        val dur = ArrayList<Int>()
        val tr = ArrayList<Int>()
        var cursor = 0L          // decisecond đã phủ
        var prevEnd = 0.0        // giây (chưa làm tròn) — so khoảng trống với GAP_TOLERANCE
        for (e in entries.sortedBy { it.start }) {
            // Khoảng trống ≤ 0.05 s coi như liền nhau (tránh OFF 0.1 s giả do làm tròn số thực).
            var s = if (e.start - prevEnd <= GAP_TOLERANCE) cursor else Math.round(e.start * 10)
            val end = Math.round((e.start + e.duration) * 10)
            if (s > cursor) {
                ps += PresetSlots.OFF; dur += (s - cursor).toInt(); tr += 0
                cursor = s
            }
            if (s < cursor) s = cursor
            val d = maxOf(1L, end - s)
            ps += e.slot; dur += d.toInt(); tr += maxOf(0L, Math.round(e.transition * 10)).toInt()
            cursor = s + d
            prevEnd = maxOf(prevEnd, e.start + e.duration)
        }
        val total = Math.round(timelineSeconds * 10)
        if (timelineSeconds - prevEnd > GAP_TOLERANCE && total > cursor && ps.isNotEmpty()) {
            ps += PresetSlots.OFF; dur += (total - cursor).toInt(); tr += 0
        }
        val playlist = JSONObject().apply {
            put("ps", JSONArray(ps))
            put("dur", JSONArray(dur))
            put("transition", JSONArray(tr))
            put("repeat", if (loop) 0 else 1)
            put("end", 0)
            put("r", 0)
        }
        return Compiled(playlist, ps)
    }

    fun timelineOffPreset(): JSONObject = JSONObject().apply {
        put("n", "TIMELINE_OFF")
        put("seg", JSONArray().put(JSONObject().apply {
            put("id", 0)
            put("fx", 0)
            put("col", JSONArray().put(JSONArray().put(0).put(0).put(0)))
        }))
    }

    fun playlistPreset(playlist: JSONObject, name: String): JSONObject = JSONObject().apply {
        put("playlist", playlist)
        put("on", true)
        put("n", name)
    }

    /** ID trong `ps` không tồn tại (object khác rỗng, ID hợp lệ) trong [presets] — §5.3. */
    fun missingPlaylistRefs(presets: JSONObject, ps: List<Int>, pmax: Int): List<Int> =
        ps.distinct().filter { !PresetSlots.isValidId(it, pmax) || !PresetSlots.isNonEmptyPreset(presets.opt(it.toString())) }

    /** ID `ps` của playlist ở [slot] (rỗng nếu không có). */
    fun playlistIds(presets: JSONObject, slot: Int = PresetSlots.PLAYLIST): List<Int>? {
        val pl = presets.optJSONObject(slot.toString())?.optJSONObject("playlist") ?: return null
        val arr = pl.optJSONArray("ps") ?: return emptyList()
        return (0 until arr.length()).map { arr.optInt(it, -1) }
    }
}
