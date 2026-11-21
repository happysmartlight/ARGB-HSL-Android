package com.example.data

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Lệnh mạng tới trình phát show của firmware (usermod show_player, `/json/info` → `show.v` ≥ 1),
 * cùng cách desktop Tool ARGB HSL v3.17.11 làm (`ui/timeline_tab.py`: `_show_upload_and_load`,
 * `_show_stop_then_off`). Định dạng file: [ShowFile].
 */
object ShowDevice {
    private const val LOAD_TIMEOUT_MS = 4000L
    private const val STOP_TIMEOUT_MS = 1500L

    /** ip → `show.v` đọc được gần nhất (ping `/json/si`, `/json/info`). 0 = firmware chưa có trình phát show. */
    private val versions = ConcurrentHashMap<String, Int>()

    fun cached(ip: String): Int? = versions[ip]

    fun remember(ip: String, version: Int) {
        versions[ip] = version
    }

    /** `show.v` của mạch (đọc /json/info); đọc lỗi thì dùng giá trị đã biết (chưa biết = 0 → playlist). */
    suspend fun probeVersion(ip: String): Int = try {
        ShowFile.playerVersion(WledHttp.getInfo(ip)).also { versions[ip] = it }
    } catch (e: Exception) {
        versions[ip] ?: 0
    }

    /** Nội dung /show.jsonl trên mạch; null = không có / không đọc được. */
    suspend fun readShow(ip: String): String? = runCatching { WledHttp.getTextOrNull(ip, ShowFile.PATH) }.getOrNull()

    /**
     * Ghi /show.jsonl rồi bảo mạch nạp; chờ tới khi mạch báo đúng số clip. Lỗi (mạch không đọc được file,
     * hết giờ) → ném lỗi kèm lý do mạch báo (`show.err`).
     */
    suspend fun uploadAndLoad(ip: String, text: String) {
        WledHttp.uploadFile(ip, ShowFile.PATH, text.toByteArray(Charsets.UTF_8), "application/json")
        WledHttp.postState(ip, JSONObject().put("show", JSONObject().put("load", true)))
        if (MockDevice.isMock(ip)) return
        val want = ShowFile.clipCount(text)
        val deadline = System.currentTimeMillis() + LOAD_TIMEOUT_MS
        var last: JSONObject? = null
        while (true) {
            delay(120)
            last = runCatching { WledHttp.getInfo(ip, 2).optJSONObject("show") }.getOrNull() ?: last
            val err = last?.optString("err").orEmpty()
            if (err.isNotEmpty()) throw Exception("mạch không đọc được file show: $err")
            if (last?.optInt("clips", -1) == want) return
            if (System.currentTimeMillis() > deadline) {
                throw Exception("mạch chưa nạp xong file show (${last?.optInt("clips", -1)}/$want clip)")
            }
        }
    }

    /**
     * Dừng show (mạch trả bố cục + hình ảnh như trước khi phát) và tắt đèn. Trình phát v7: 1 lệnh, mạch
     * khôi phục lúc đèn đã tắt, không nháy. Mạch cũ: dừng RỒI mới tắt (tắt trước thì lúc khôi phục mạch
     * bật đèn lại); giữa 2 lệnh hình cũ có thể thoáng hiện, nên hỏi dày và tắt ngay (`tt` 0).
     * Dùng [firstSent] = lệnh dừng đã được gửi (vd cùng lúc mọi mạch) → chỉ làm bước sau.
     */
    suspend fun stopAndOff(ip: String, version: Int, firstSent: Boolean = false) {
        if (version >= ShowFile.STOP_OFF_PLAYER_VERSION) {
            if (!firstSent) WledHttp.postState(ip, ShowFile.stopOffCommand())
            return
        }
        if (!firstSent) WledHttp.postState(ip, stopCommand())
        val deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(30)
            val st = runCatching { WledHttp.getState(ip, 2).optJSONObject("show")?.optInt("st", 0) }.getOrNull() ?: continue
            if (st == ShowFile.ST_IDLE) break
        }
        WledHttp.postState(ip, JSONObject().put("on", false).put("tt", 0))
    }

    /** Lệnh dừng đầu tiên của [stopAndOff] theo phiên bản (để gửi cùng lúc nhiều mạch). */
    fun firstStopCommand(version: Int): JSONObject =
        if (version >= ShowFile.STOP_OFF_PLAYER_VERSION) ShowFile.stopOffCommand() else stopCommand()

    private fun stopCommand(): JSONObject = JSONObject().put("show", JSONObject().put("stop", true))

    /** Phát show từ [posMs] (cũng dùng để Tiếp tục / Tua: mạch đang tạm dừng hay đang phát đều nhận). */
    fun playCommand(posMs: Long): JSONObject =
        JSONObject().put("on", true).put("show", JSONObject().put("play", posMs))

    fun pauseCommand(posMs: Long): JSONObject = JSONObject().put("show", JSONObject().put("pause", posMs))

    fun syncCommand(posMs: Long): JSONObject = JSONObject().put("show", JSONObject().put("sync", posMs))
}
