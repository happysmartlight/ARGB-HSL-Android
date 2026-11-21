package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gọi HTTP tới mạch WLED (suspend, chạy trên IO). JSON gửi là UTF-8 thật (không `\uXXXX`) vì
 * firmware lưu nguyên văn chuỗi escape. Thiết bị ảo (debug) trả dữ liệu giả, không gọi mạng.
 */
object WledHttp {
    private val json = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun clientFor(timeoutSec: Long): OkHttpClient =
        client.newBuilder().readTimeout(timeoutSec, TimeUnit.SECONDS).writeTimeout(timeoutSec, TimeUnit.SECONDS).build()

    private fun getText(ip: String, path: String, timeoutSec: Long = 8): String {
        val request = Request.Builder().url("http://$ip$path").build()
        clientFor(timeoutSec).newCall(request).execute().use { r ->
            if (r.code == 401) throw Exception("mạch đang khoá PIN")
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    /** GET /json (state + info + effects + palettes). */
    suspend fun getJson(ip: String): JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext MockDevice.cannedFullJson()
        JSONObject(getText(ip, "/json"))
    }

    /** GET /json/info. */
    suspend fun getInfo(ip: String, timeoutSec: Long = 4): JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext JSONObject(MockDevice.cannedInfoJson())
        JSONObject(getText(ip, "/json/info", timeoutSec))
    }

    /** GET /json/state. */
    suspend fun getState(ip: String, timeoutSec: Long = 3): JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext JSONObject("{}")
        JSONObject(getText(ip, "/json/state", timeoutSec))
    }

    /** GET 1 file văn bản (vd /show.jsonl); null = mạch không có file đó (HTTP 404). */
    suspend fun getTextOrNull(ip: String, path: String, timeoutSec: Long = 8): String? = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext null
        val request = Request.Builder().url("http://$ip$path").build()
        clientFor(timeoutSec).newCall(request).execute().use { r ->
            if (r.code == 404) return@withContext null
            if (r.code == 401) throw Exception("mạch đang khoá PIN")
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            r.body?.string()
        }
    }

    /** GET /json/si (state + info, nhẹ hơn nhiều so với /json). */
    suspend fun getStateInfo(ip: String): JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext MockDevice.cannedFullJson().apply { remove("effects"); remove("palettes") }
        JSONObject(getText(ip, "/json/si"))
    }

    suspend fun getPresets(ip: String): JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext JSONObject(MockDevice.cannedPresetsJson())
        val t = getText(ip, "/presets.json").trim()
        if (t.isEmpty() || t == "null") JSONObject() else JSONObject(t)
    }

    /** GET /json/cfg (null nếu không đọc được). */
    suspend fun getCfg(ip: String): JSONObject? = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext JSONObject("""{"hw":{"led":{"fps":0,"ins":[{"type":51,"len":72}]}}}""")
        runCatching { JSONObject(getText(ip, "/json/cfg")) }.getOrNull()
    }

    /** POST /json/state. */
    suspend fun postState(ip: String, body: JSONObject, timeoutSec: Long = 4) = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) { delay(40); return@withContext }
        val request = Request.Builder()
            .url("http://$ip/json/state")
            .post(PresetJson.compact(body).toRequestBody(json))
            .build()
        clientFor(timeoutSec).newCall(request).execute().use { r ->
            if (r.code == 401) throw Exception("mạch đang khoá PIN")
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
        }
    }

    /** POST /upload (multipart). Timeout co giãn theo dung lượng. */
    suspend fun uploadFile(ip: String, path: String, bytes: ByteArray, mime: String) = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) { delay(120); return@withContext }
        val name = if (path.startsWith("/")) path else "/$path"
        val timeout = (20L + bytes.size / 40000L).coerceIn(20L, 300L)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("data", name, bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .build()
        val request = Request.Builder().url("http://$ip/upload").post(body).build()
        clientFor(timeout).newCall(request).execute().use { r ->
            if (r.code == 401) throw Exception("WLED đang khoá PIN phần /upload")
            if (!r.isSuccessful) throw Exception("Upload thất bại (HTTP ${r.code})")
        }
    }

    /** Tải 1 file về (vd BMP của preset). */
    suspend fun downloadFile(ip: String, path: String): ByteArray = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) throw Exception("thiết bị ảo không có file")
        val p = if (path.startsWith("/")) path else "/$path"
        val url = "http://$ip" + p.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val request = Request.Builder().url(url).build()
        clientFor(10).newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            r.body?.bytes() ?: ByteArray(0)
        }
    }

    /** Danh sách file (tên có "/" đầu, kích thước byte). */
    suspend fun listFiles(ip: String): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext emptyList()
        val arr = JSONArray(getText(ip, "/edit?list=/"))
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("type", "file") != "dir" }
            .map { "/" + it.optString("name").trim().trimStart('/') to it.optLong("size", 0L) }
    }
}
