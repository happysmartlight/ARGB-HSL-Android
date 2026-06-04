package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class WledStateRequest(
    @Json(name = "on") val on: Boolean? = null,
    @Json(name = "bri") val bri: Int? = null,
    @Json(name = "ps") val ps: Int? = null,
    @Json(name = "pdel") val pdel: Int? = null,
    @Json(name = "seg") val seg: List<WledSegmentRequest>? = null
)

@JsonClass(generateAdapter = true)
data class WledSegmentRequest(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "on") val on: Boolean? = null,
    @Json(name = "bri") val bri: Int? = null,
    @Json(name = "col") val col: List<List<Int>>? = null, // e.g., [[R, G, B]]
    @Json(name = "fx") val fx: Int? = null,
    @Json(name = "sx") val sx: Int? = null,
    @Json(name = "ix") val ix: Int? = null,
    @Json(name = "pal") val pal: Int? = null,
    @Json(name = "frz") val frz: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class WledState(
    @Json(name = "on") val on: Boolean? = null,
    @Json(name = "bri") val bri: Int? = null,
    @Json(name = "transition") val transition: Int? = null,
    @Json(name = "ps") val ps: Int? = null,
    @Json(name = "seg") val seg: List<WledSegment>? = null
)

@JsonClass(generateAdapter = true)
data class WledSegment(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "on") val on: Boolean? = null,
    @Json(name = "bri") val bri: Int? = null,
    @Json(name = "col") val col: List<List<Int>>? = null,
    @Json(name = "fx") val fx: Int? = null,
    @Json(name = "sx") val sx: Int? = null,
    @Json(name = "ix") val ix: Int? = null,
    @Json(name = "pal") val pal: Int? = null,
    @Json(name = "frz") val frz: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class WledWifiInfo(
    @Json(name = "signal") val signal: Int? = null,
    @Json(name = "rssi") val rssi: Int? = null
)

@JsonClass(generateAdapter = true)
data class WledLedsInfo(
    @Json(name = "count") val count: Int? = null,
    @Json(name = "pwr") val pwr: Int? = null,
    @Json(name = "fps") val fps: Int? = null,
    @Json(name = "maxpwr") val maxpwr: Int? = null
)

@JsonClass(generateAdapter = true)
// Lưu ý: WLED trả `u`/`t` theo đơn vị KILOBYTE (KB), không phải byte.
// (giữ tên cũ usedBytes/totalBytes để tránh đổi nhiều chỗ; hãy coi như "KB").
data class WledFilesystemInfo(
    @Json(name = "u") val usedBytes: Long? = null,   // dung lượng đã dùng (KB)
    @Json(name = "t") val totalBytes: Long? = null   // tổng dung lượng (KB)
)

@JsonClass(generateAdapter = true)
data class WledInfo(
    @Json(name = "ver") val ver: String? = null,
    @Json(name = "vid") val vid: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "live") val live: Boolean? = null,
    @Json(name = "fxcount") val fxcount: Int? = null,
    @Json(name = "palcount") val palcount: Int? = null,
    @Json(name = "wifi") val wifi: WledWifiInfo? = null,
    @Json(name = "product") val product: String? = null,
    @Json(name = "cn") val cn: String? = null,
    @Json(name = "leds") val leds: WledLedsInfo? = null,
    @Json(name = "brand") val brand: String? = null,
    @Json(name = "fs") val fs: WledFilesystemInfo? = null
)

@JsonClass(generateAdapter = true)
data class WledResponse(
    @Json(name = "state") val state: WledState? = null,
    @Json(name = "info") val info: WledInfo? = null,
    @Json(name = "effects") val effects: List<String>? = null,
    @Json(name = "palettes") val palettes: List<String>? = null
)

interface WledApi {
    @GET
    suspend fun getCompleteApi(@Url url: String): WledResponse

    @GET
    suspend fun getState(@Url url: String): WledState

    @POST
    suspend fun updateState(@Url url: String, @Body stateRequest: WledStateRequest): WledState

    companion object {
        fun create(): WledApi {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("http://localhost/") // Placeholder, since we use @Url dynamic endpoints
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(WledApi::class.java)
        }
    }
}
