package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.MockDevice
import com.example.data.PoiGallery
import com.example.data.PoiGalleryItem
import com.example.data.WledDevice
import com.example.data.WledHttp
import com.example.util.PoiImage
import com.example.util.PoiTools
import com.example.util.PoiTools.Shape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Thông số POI (lưu lại cho lần sau). [imagesPerTurn] là giá trị của kiểu Uốn theo vòng. */
data class PoiParams(
    val pixels: Int = PoiTools.PIXEL_DEFAULT,
    val bri: Int = PoiTools.defaultBri(),
    val shape: Shape = Shape.FLAT,
    val hole: Int = PoiTools.DISC_HOLE_DEFAULT,
    val rpm: Int = PoiTools.RPM_DEFAULT,
    val imagesPerTurn: Int = PoiTools.IMAGES_PER_TURN_DEFAULT,
    val reverse: Boolean = false
)

enum class PoiLevel { INFO, OK, WARN, ERR }

data class PoiDeviceRow(
    val id: Int,
    val name: String,
    val ip: String,
    val online: Boolean,
    val block: PoiTools.Block?,
    val fw: String?,
    val wifi: Int?,
    val fsPct: Int?,
    val tempC: Double?,
    val logoCount: Int?,
    val bmpLimit: Int,
    val loading: Boolean,
    val identifying: Boolean
) {
    val usable: Boolean get() = online && block == null
}

data class PoiPresetItem(val id: Int, val name: String, val isPoi: Boolean, val speed: String?)

data class PoiImageInfo(
    val label: String,
    val width: Int,
    val height: Int,
    /** Ảnh lấy từ preset trên mạch: đã là BMP cuối cùng, không đổi kiểu hình được. */
    val fromDevice: Boolean,
    val galleryKey: String?
)

data class PoiSizeInfo(
    val bytes: Int = 0,
    val limit: Int = PoiTools.POI_MAX_BMP_BYTES,
    val rows: Int = 0,
    val ok: Boolean = false,
    val hint: String = "",
    val level: PoiLevel = PoiLevel.INFO
)

data class PoiProgress(val done: Int, val total: Int, val note: String)

data class PoiUiState(
    val rows: List<PoiDeviceRow> = emptyList(),
    /** Mạch online không phải 2X Pro (không hiện trong danh sách POI). */
    val hiddenNames: List<String> = emptyList(),
    val currentId: Int? = null,
    val targetIds: Set<Int> = emptySet(),
    val params: PoiParams = PoiParams(),
    val image: PoiImageInfo? = null,
    val size: PoiSizeInfo = PoiSizeInfo(),
    val speedHint: String = "",
    val speedHintLevel: PoiLevel = PoiLevel.INFO,
    val presets: List<PoiPresetItem> = emptyList(),
    val presetsLoading: Boolean = false,
    val showAllPresets: Boolean = false,
    val runningPresetId: Int? = null,
    /** Preset đang chạy là ảnh Poi HSL → chỉnh tốc độ trực tiếp + nút Lưu. */
    val runningPoi: Boolean = false,
    val speedUnsaved: Boolean = false,
    val busy: Boolean = false,
    val progress: PoiProgress? = null,
    val status: String = "",
    val statusLevel: PoiLevel = PoiLevel.INFO,
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList()
) {
    val current: PoiDeviceRow? get() = rows.firstOrNull { it.id == currentId }
    val usableTargets: List<PoiDeviceRow> get() = rows.filter { it.usable && it.id in targetIds }
    /** Kiểu chuyển đổi khi gửi: ảnh lấy từ mạch đã là BMP cuối → luôn FLAT. */
    val sendShape: Shape get() = if (image?.fromDevice == true) Shape.FLAT else params.shape
    val effectiveImagesPerTurn: Int get() = if (sendShape == Shape.DISC) 1 else params.imagesPerTurn
}

/** Ảnh xem trước (BMP xoay lại) + cách vẽ; tách khỏi [PoiUiState] để không so sánh Bitmap. */
data class PoiPreview(val bitmap: Bitmap, val disc: Boolean, val hole: Int, val sectors: Int, val version: Long)

/**
 * Bộ điều khiển Công cụ POI (tab "Upload POI & Cờ LED", chế độ POI). Port từ desktop
 * `ui/poi_tab.py`: chỉ mạch 2X Pro firmware ≥ 2611050; kiểu hình Uốn theo vòng / Đúng hình;
 * tốc độ quay RPM + số ảnh/vòng + đảo chiều (chỉnh trực tiếp khi preset đang chạy, bấm Lưu mới ghi);
 * thư viện mẫu; gửi ảnh có đọc lại xác nhận. Số liệu mạch chỉ đọc theo sự kiện (mở tab, làm mới,
 * sau khi ghi) — không poll: request HTTP làm LED đang quay bị giật.
 */
class PoiStudio(
    private val app: Application,
    private val scope: CoroutineScope,
    private val devices: StateFlow<List<WledDevice>>,
    private val log: (String, String) -> Unit,
    private val afterWrite: (List<Int>) -> Unit
) {
    val gallery: PoiGallery by lazy { PoiGallery.load(app) }
    private val prefs = app.getSharedPreferences("poi_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(PoiUiState(params = loadParams(), favorites = loadList(KEY_FAV), recents = loadList(KEY_RECENT)))
    val state: StateFlow<PoiUiState> = _state.asStateFlow()

    private val _preview = MutableStateFlow<PoiPreview?>(null)
    val preview: StateFlow<PoiPreview?> = _preview.asStateFlow()

    private class DevCache(
        val info: JSONObject?,
        val fxId: Int?,
        val presets: JSONObject?,
        val ledIns: List<Pair<Int, Int>>?,
        val readAt: Long
    )

    private val cache = ConcurrentHashMap<Int, DevCache>()
    private val loading = ConcurrentHashMap.newKeySet<Int>()
    private val identifying = ConcurrentHashMap.newKeySet<Int>()
    private var targetsTouched = false

    private var source: Bitmap? = null
    private var sourceStem: String = ""
    private var deviceImageDisc = false
    /** deviceId → (presetId, tên) preset POI đang chạy (bấm trong danh sách preset). */
    private var running: Triple<Int, Int, String>? = null

    private var previewJob: Job? = null
    private var liveSpeedJob: Job? = null
    private val commandMutex = Mutex()
    private var previewVersion = 0L

    init {
        scope.launch { devices.collect { rebuildRows() } }
    }

    // ── Thiết bị ─────────────────────────────────────────────────────────

    /** Tab POI vừa hiện: đọc số liệu mạch nếu chưa có / đã cũ (> 60 s). */
    fun onShown() {
        val stale = state.value.rows.filter { r ->
            r.online && (cache[r.id]?.let { System.currentTimeMillis() - it.readAt > 60_000 } ?: true)
        }.map { it.id }
        if (stale.isNotEmpty()) refreshFleet(stale)
    }

    fun refreshAll() = refreshFleet(state.value.rows.filter { it.online }.map { it.id })

    /** Mạch vừa bị sửa preset ở chỗ khác (vd dialog Xóa preset + ảnh). */
    fun onPresetsChangedExternally(deviceIds: Collection<Int>) {
        val ids = deviceIds.filter { id -> state.value.rows.any { it.id == id } }
        if (ids.isNotEmpty()) refreshFleet(ids)
    }

    private fun refreshFleet(ids: Collection<Int>) {
        val targets = devices.value.filter { it.id in ids && it.isOnline }
        if (targets.isEmpty()) return
        targets.forEach { loading += it.id }
        if (targets.any { it.id == state.value.currentId }) _state.update { it.copy(presetsLoading = true) }
        rebuildRows()
        scope.launch(Dispatchers.IO) {
            targets.map { d -> async { readDevice(d) } }.awaitAll()
            rebuildRows()
            _state.update { it.copy(presetsLoading = false) }
            rebuildPresets()
            recomputeSize()
            rebuildPreview() // giới hạn BMP (PSRAM) có thể vừa đổi
        }
    }

    private suspend fun readDevice(d: WledDevice) {
        try {
            val json = WledHttp.getJson(d.ipAddress)
            val info = json.optJSONObject("info")
            val effects = json.optJSONArray("effects")
            val fx = effects?.let { arr -> (0 until arr.length()).firstOrNull { arr.optString(it) == PoiTools.POI_EFFECT_NAME } }
            val presets = runCatching { WledHttp.getPresets(d.ipAddress) }.getOrNull()
            val ledIns = cache[d.id]?.ledIns ?: WledHttp.getCfg(d.ipAddress)?.optJSONObject("hw")?.optJSONObject("led")
                ?.optJSONArray("ins")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optInt("type", -1) to it.optInt("len", 0) }
                }
            cache[d.id] = DevCache(info, fx, presets, ledIns, System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.w("PoiStudio", "read ${d.ipAddress} failed", e)
        } finally {
            loading -= d.id
        }
    }

    private fun rebuildRows() {
        val all = devices.value
        val rows = ArrayList<PoiDeviceRow>()
        val hidden = ArrayList<String>()
        for (d in all) {
            val info = cache[d.id]?.info
            val product = info?.optString("product")?.takeIf { it.isNotBlank() } ?: d.product
            if (product == null) continue
            if (!PoiTools.isSupportedProduct(product)) {
                if (d.isOnline) hidden += d.name
                continue
            }
            val vid = (info?.opt("vid") as? Int) ?: d.vid
            val psram = (info?.opt("psram") as? Number)?.toLong()
            val presets = cache[d.id]?.presets
            rows += PoiDeviceRow(
                id = d.id,
                name = d.name,
                ip = d.ipAddress,
                online = d.isOnline,
                block = PoiTools.blockReason(product, vid),
                fw = PoiTools.fwVersion(info) ?: d.fwVersion?.let { v -> vid?.let { "$v ($it)" } ?: v },
                wifi = info?.optJSONObject("wifi")?.opt("signal")?.let { (it as? Number)?.toInt() } ?: d.wifiSignal,
                fsPct = PoiTools.fsPercent(info),
                tempC = PoiTools.tempCelsius(info),
                logoCount = presets?.let { p -> (1..59).count { com.example.data.PresetSlots.isNonEmptyPreset(p.opt(it.toString())) } },
                bmpLimit = PoiTools.deviceBmpLimit(vid, psram),
                loading = d.id in loading,
                identifying = d.id in identifying
            )
        }
        rows.sortWith(compareBy({ !it.usable }, { it.name.lowercase() }))
        _state.update { s ->
            val usableIds = rows.filter { it.usable }.map { it.id }.toSet()
            val targets = if (!targetsTouched) usableIds else s.targetIds.filter { it in usableIds }.toSet()
            val current = s.currentId?.takeIf { id -> rows.any { it.id == id } }
                ?: rows.firstOrNull { it.usable }?.id ?: rows.firstOrNull()?.id
            s.copy(rows = rows, hiddenNames = hidden, targetIds = targets, currentId = current)
        }
        if (state.value.presets.isEmpty()) rebuildPresets()
    }

    fun selectDevice(id: Int) {
        if (state.value.busy || state.value.currentId == id) return
        _state.update { it.copy(currentId = id, runningPresetId = null, runningPoi = false, speedUnsaved = false) }
        if (running?.first != id) running = null
        rebuildPresets()
        recomputeSize()
        rebuildPreview()
        if (cache[id] == null) refreshFleet(listOf(id))
    }

    fun toggleTarget(id: Int) {
        val row = state.value.rows.firstOrNull { it.id == id } ?: return
        if (!row.usable || state.value.busy) return
        targetsTouched = true
        _state.update { s -> s.copy(targetIds = if (id in s.targetIds) s.targetIds - id else s.targetIds + id) }
    }

    fun setAllTargets(on: Boolean) {
        targetsTouched = true
        _state.update { s -> s.copy(targetIds = if (on) s.rows.filter { it.usable }.map { it.id }.toSet() else emptySet()) }
    }

    fun setShowAllPresets(all: Boolean) {
        _state.update { it.copy(showAllPresets = all) }
        rebuildPresets()
    }

    private fun rebuildPresets() {
        val s = state.value
        val id = s.currentId ?: return _state.update { it.copy(presets = emptyList()) }
        val c = cache[id]
        val presets = c?.presets ?: return _state.update { it.copy(presets = emptyList()) }
        val items = presets.keys().asSequence().mapNotNull { it.toIntOrNull() }
            .filter { it > 0 && com.example.data.PresetSlots.isNonEmptyPreset(presets.opt(it.toString())) }
            .filter { s.showAllPresets || it in 1..59 }
            .sorted()
            .map { pid ->
                val p = presets.optJSONObject(pid.toString())!!
                val seg = segZero(p)
                val isPoi = seg != null && c.fxId != null && seg.optInt("fx", -1) == c.fxId && PoiTools.presetBmpStem(p) != null
                val speed = if (isPoi) {
                    val ix = seg.optInt("ix", 0)
                    PoiTools.speedLabel(ix, seg.optInt("c3", 1), seg.optBoolean("o3", false))
                } else null
                PoiPresetItem(pid, p.optString("n").ifBlank { "Preset $pid" }, isPoi, speed)
            }.toList()
        _state.update { it.copy(presets = items) }
    }

    private fun segZero(p: JSONObject?): JSONObject? = when (val s = p?.opt("seg")) {
        is JSONArray -> s.optJSONObject(0)
        is JSONObject -> s
        else -> null
    }

    // ── Thông số ─────────────────────────────────────────────────────────

    private fun updateParams(transform: (PoiParams) -> PoiParams, speedChanged: Boolean = false, rebuild: Boolean = true) {
        _state.update { it.copy(params = transform(it.params)) }
        saveParams(state.value.params)
        recomputeSize()
        if (rebuild) rebuildPreview()
        if (speedChanged) scheduleLiveSpeed()
    }

    fun setPixels(v: Int) = updateParams({ it.copy(pixels = v.coerceIn(PoiTools.PIXEL_MIN, PoiTools.PIXEL_MAX)) })

    /** Kéo thanh độ sáng (chỉ cập nhật giao diện); thả tay → [applyBrightness]. */
    fun setBri(v: Int) = updateParams({ it.copy(bri = PoiTools.clampBri(v)) }, rebuild = false)

    fun setShape(shape: Shape) {
        if (state.value.image?.fromDevice == true) return
        // Đúng hình chỉ khớp 1 vòng khi quét theo RPM.
        updateParams({ p -> p.copy(shape = shape, rpm = if (shape == Shape.DISC && p.rpm == PoiTools.RPM_AUTO) PoiTools.RPM_DEFAULT else p.rpm) }, speedChanged = true)
    }

    fun setHole(v: Int) = updateParams({ it.copy(hole = v.coerceIn(0, PoiTools.DISC_HOLE_MAX)) })

    fun setRpm(v: Int) = updateParams({ it.copy(rpm = v.coerceIn(PoiTools.RPM_AUTO, PoiTools.RPM_MAX)) }, speedChanged = true)

    fun setImagesPerTurn(v: Int) = updateParams({ it.copy(imagesPerTurn = v.coerceIn(1, PoiTools.IMAGES_PER_TURN_MAX)) }, speedChanged = true)

    fun setReverse(v: Boolean) = updateParams({ it.copy(reverse = v) }, speedChanged = true, rebuild = false)

    fun applyBrightness() {
        val row = state.value.current ?: return status("⚠ Chưa chọn mạch để chỉnh độ sáng.", PoiLevel.WARN)
        if (!row.usable) return
        val bri = state.value.params.bri
        scope.launch {
            try {
                commandMutex.withLock { WledHttp.postState(row.ip, JSONObject().put("on", true).put("bri", bri), 3) }
                status("💡 Độ sáng ${row.name} → ${PoiTools.briToPercent(bri)}%", PoiLevel.OK)
            } catch (e: Exception) {
                status("❌ Không chỉnh được độ sáng ${row.name}: ${e.message}", PoiLevel.ERR)
            }
        }
    }

    private fun currentLimit(): Int = state.value.current?.bmpLimit ?: PoiTools.POI_MAX_BMP_BYTES

    private fun recomputeSize() {
        val s = state.value
        val img = source
        val limit = currentLimit()
        val size = if (img == null) {
            PoiSizeInfo(limit = limit, hint = "Chọn ảnh hoặc mẫu trong thư viện để xem dung lượng BMP.")
        } else {
            val px = s.params.pixels
            val rows: Int
            val bytes: Int
            if (s.sendShape == Shape.DISC) {
                rows = PoiTools.discRows(px, limit)
                bytes = PoiTools.bmpSizeBytes(px, rows)
            } else {
                rows = PoiTools.poiBmpHeight(px, img.width, img.height)
                bytes = PoiTools.poiBmpSize(px, img.width, img.height)
            }
            when {
                rows < 1 -> PoiSizeInfo(bytes, limit, rows, false, "⚠ Ảnh quá cao/hẹp — không tạo được BMP. Hãy cắt lại ảnh.", PoiLevel.ERR)
                PoiTools.fits(bytes, limit) -> PoiSizeInfo(
                    bytes, limit, rows, true, "✓ Dùng tốt cho POI · BMP $px×$rows",
                    if (bytes > 0.9 * limit) PoiLevel.WARN else PoiLevel.OK
                )
                else -> {
                    val maxPx = PoiTools.maxFittingPixels(img.width, img.height, px, limit)
                    PoiSizeInfo(
                        bytes, limit, rows, false,
                        if (maxPx != null) "⚠ Vượt ${PoiTools.limitLabel(limit)} — giảm còn ≤ $maxPx pixel hoặc chọn ảnh khác."
                        else "⚠ Vượt ${PoiTools.limitLabel(limit)} kể cả ở ${PoiTools.PIXEL_MIN} pixel — hãy cắt ngắn ảnh.",
                        PoiLevel.ERR
                    )
                }
            }
        }
        val (hint, level) = speedHint(size)
        _state.update { it.copy(size = size, speedHint = hint, speedHintLevel = level) }
    }

    private fun speedHint(size: PoiSizeInfo): Pair<String, PoiLevel> {
        val s = state.value
        val row = s.current
        when (row?.block) {
            PoiTools.Block.PRODUCT -> return "⚠ POI chỉ hỗ trợ ${PoiTools.SUPPORTED_PRODUCT}." to PoiLevel.WARN
            PoiTools.Block.FIRMWARE -> return "⚠ Firmware chưa hỗ trợ tốc độ quay. Cần nâng cấp firmware ≥ ${PoiTools.FW_BUILD_FIXED_RPM}." to PoiLevel.WARN
            null -> {}
        }
        val rpm = s.params.rpm
        if (rpm <= 0) {
            return if (previewIsDisc()) "⚠ Kiểu Đúng hình cần đặt RPM, Auto sẽ làm hình bị vỡ." to PoiLevel.WARN
            else "Auto: quét nhanh nhất có thể, hình có thể lặp và trôi." to PoiLevel.INFO
        }
        if (source == null) return "Đặt bằng tốc độ quay thật để hình đứng gần như yên. Lệch 5 RPM là hình trôi 30°/giây." to PoiLevel.INFO
        val needed = PoiTools.scanRowsPerSecond(size.rows, rpm, s.effectiveImagesPerTurn)
        val c = row?.let { cache[it.id] }
        val maximum = PoiTools.maxScanRows(c?.info?.opt("vid") as? Int, c?.ledIns)
        if (maximum != null && needed > maximum) {
            return "⚠ Cần $needed hàng/s, mạch đạt khoảng $maximum: sẽ bỏ bớt hàng, hình vẫn đúng tỉ lệ nhưng thô hơn." to PoiLevel.WARN
        }
        return ("✓ Quét $needed hàng/s" + (maximum?.let { " (mạch đạt khoảng $it)" } ?: "")) to PoiLevel.OK
    }

    private fun previewIsDisc(): Boolean =
        if (state.value.image?.fromDevice == true) deviceImageDisc else state.value.params.shape == Shape.DISC

    // ── Ảnh nguồn ────────────────────────────────────────────────────────

    fun loadImage(uri: Uri) {
        if (state.value.busy) return
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("không đọc được file")
                    val bmp = PoiImage.decodeSampled(bytes) ?: error("không phải ảnh hợp lệ")
                    bmp to displayName(uri)
                }
            }
            loaded.onSuccess { (bmp, name) ->
                setSource(bmp, label = name, stem = PoiTools.fileStem(name), fromDevice = false, galleryKey = null)
                status("🖼 Đã mở $name — ${bmp.width}×${bmp.height} px.", PoiLevel.OK)
            }.onFailure { status("❌ Không mở được ảnh: ${it.message}", PoiLevel.ERR) }
        }
    }

    /** Nạp mẫu thư viện và đặt luôn kiểu hình hợp với ảnh. */
    fun useGalleryItem(item: PoiGalleryItem) {
        if (state.value.busy) return
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadAsset(item.file) }
                ?: return@launch status("❌ Không mở được mẫu ${item.title}.", PoiLevel.ERR)
            _state.update { s ->
                val p = s.params.copy(
                    shape = item.shape,
                    rpm = if (item.shape == Shape.DISC && s.params.rpm == PoiTools.RPM_AUTO) PoiTools.RPM_DEFAULT else s.params.rpm
                )
                s.copy(params = p, recents = PoiGallery.pushRecent(s.recents, item.key))
            }
            saveParams(state.value.params)
            saveList(KEY_RECENT, state.value.recents)
            setSource(bmp, label = item.title, stem = item.key, fromDevice = false, galleryKey = item.key)
            status("🎨 Mẫu \"${item.title}\" · ${if (item.shape == Shape.DISC) "Đúng hình" else "Uốn theo vòng"}", PoiLevel.OK)
        }
    }

    fun toggleFavorite(key: String) {
        _state.update { it.copy(favorites = PoiGallery.toggleFavorite(it.favorites, key)) }
        saveList(KEY_FAV, state.value.favorites)
    }

    fun loadAsset(path: String): Bitmap? = runCatching {
        app.assets.open("${PoiGallery.DIR}/$path").use { PoiImage.decodeSampled(it.readBytes()) }
    }.getOrNull()

    fun clearImage() {
        if (state.value.busy) return
        source = null
        sourceStem = ""
        deviceImageDisc = false
        _state.update { it.copy(image = null) }
        _preview.value = null
        recomputeSize()
    }

    private fun setSource(bmp: Bitmap, label: String, stem: String, fromDevice: Boolean, galleryKey: String?, deviceDisc: Boolean = false) {
        source = bmp
        sourceStem = stem
        deviceImageDisc = fromDevice && deviceDisc
        _state.update { it.copy(image = PoiImageInfo(label, bmp.width, bmp.height, fromDevice, galleryKey)) }
        recomputeSize()
        rebuildPreview()
    }

    private fun rebuildPreview() {
        val img = source ?: run { _preview.value = null; return }
        val s = state.value
        val pixels = s.params.pixels
        val shape = s.sendShape
        val hole = s.params.hole
        val limit = currentLimit()
        val disc = previewIsDisc()
        val sectors = if (disc) 1 else if (s.params.rpm > 0) s.params.imagesPerTurn else PoiTools.IMAGES_PER_TURN_DEFAULT
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.Default) {
            delay(60) // gộp các lần kéo thanh liên tiếp
            val out = runCatching { PoiImage.previewSource(PoiImage.makeBmp(img, pixels, shape, hole, limit)) }.getOrNull()
            if (out != null) _preview.value = PoiPreview(out, disc, hole, sectors, ++previewVersion)
        }
    }

    // ── Preset trên mạch + tốc độ trực tiếp ──────────────────────────────

    /** Bấm 1 preset → chạy trên mạch; nếu là ảnh Poi HSL thì tải BMP về xem trước + hiện tốc độ. */
    fun playPreset(presetId: Int) {
        val row = state.value.current ?: return
        if (!row.usable || state.value.busy) return
        scope.launch {
            try {
                commandMutex.withLock { WledHttp.postState(row.ip, JSONObject().put("on", true).put("ps", presetId), 3) }
            } catch (e: Exception) {
                return@launch status("❌ ${row.name} không phản hồi.", PoiLevel.WARN)
            }
            val c = cache[row.id]
            val preset = c?.presets?.optJSONObject(presetId.toString())
            val name = preset?.optString("n").orEmpty().ifBlank { "Preset $presetId" }
            val seg = segZero(preset)
            val stem = PoiTools.presetBmpStem(preset)
            val isPoi = c?.fxId != null && seg?.optInt("fx", -1) == c.fxId && stem != null
            if (!isPoi) {
                running = null
                _state.update { it.copy(runningPresetId = presetId, runningPoi = false, speedUnsaved = false) }
                return@launch status("▶ Đang chạy ID $presetId — $name (không phải ảnh POI).")
            }
            running = Triple(row.id, presetId, name)
            // Hiện tốc độ đang lưu trong preset (không gửi gì lên mạch).
            val ix = seg!!.optInt("ix", 0).coerceIn(PoiTools.RPM_AUTO, PoiTools.RPM_MAX)
            _state.update { s ->
                var p = s.params.copy(rpm = ix)
                if (ix > 0) {
                    val c3 = seg.optInt("c3", 0)
                    if (c3 > 0) p = p.copy(imagesPerTurn = c3.coerceAtMost(PoiTools.IMAGES_PER_TURN_MAX))
                    p = p.copy(reverse = seg.optBoolean("o3", false))
                }
                s.copy(params = p, runningPresetId = presetId, runningPoi = true, speedUnsaved = false)
            }
            saveParams(state.value.params)
            status("▶ Đang chạy ID $presetId — $name", PoiLevel.OK)
            val bmp = withContext(Dispatchers.IO) {
                runCatching { WledHttp.downloadFile(row.ip, "/$stem.bmp") }.getOrNull()
                    ?.let { PoiImage.decodeSampled(it) }?.let { PoiImage.rotate(it, -90f) }
            }
            if (bmp != null && running?.second == presetId) {
                setSource(bmp, label = "ID $presetId · $name", stem = stem, fromDevice = true, galleryKey = null, deviceDisc = PoiTools.isDiscStem(stem))
            } else {
                recomputeSize()
            }
        }
    }

    private fun scheduleLiveSpeed() {
        val run = running ?: return
        if (run.first != state.value.currentId) return
        liveSpeedJob?.cancel()
        liveSpeedJob = scope.launch {
            delay(LIVE_SPEED_DELAY_MS)
            val row = state.value.current ?: return@launch
            val s = state.value
            val fields = PoiTools.speedFields(s.params.rpm, s.effectiveImagesPerTurn, s.params.reverse)
            _state.update { it.copy(speedUnsaved = true) }
            try {
                commandMutex.withLock {
                    WledHttp.postState(row.ip, JSONObject().put("seg", JSONArray().put(JSONObject(fields.toString()).put("id", 0))), 3)
                }
                status("🌀 ${row.name}: đang thử ${PoiTools.speedLabel(s.params.rpm, s.effectiveImagesPerTurn, s.params.reverse)} — chưa lưu vào preset.")
            } catch (e: Exception) {
                status("❌ Không chỉnh được tốc độ ${row.name}: ${e.message}", PoiLevel.ERR)
            }
        }
    }

    /** Ghi tốc độ đang thử vào preset đang chạy (đọc lại xác nhận). */
    fun saveSpeedToPreset() {
        val run = running ?: return
        val row = state.value.rows.firstOrNull { it.id == run.first } ?: return
        if (state.value.busy) return
        liveSpeedJob?.cancel()
        val s = state.value
        val (_, presetId, name) = run
        val payload = JSONObject().put("psave", presetId).put("n", name).put("ib", true).put("sb", true)
            .put("seg", JSONArray().put(PoiTools.speedFields(s.params.rpm, s.effectiveImagesPerTurn, s.params.reverse).put("id", 0)))
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                saveVerified(row.ip, payload, presetId)
                _state.update { it.copy(speedUnsaved = false) }
                status("✔ ${row.name} ID $presetId: đã lưu ${PoiTools.speedLabel(s.params.rpm, s.effectiveImagesPerTurn, s.params.reverse)}.", PoiLevel.OK)
                log("POI: lưu tốc độ ID $presetId trên ${row.name}", "INFO")
                refreshFleet(listOf(row.id))
            } catch (e: Exception) {
                status("❌ Không lưu được tốc độ vào ID $presetId: ${e.message}", PoiLevel.ERR)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Gửi lệnh lưu preset rồi đọc lại tới khi preset thật sự có trên mạch (3 lần). HTTP 200 chỉ là
     * firmware đặt cờ lưu; autosave có thể đè mất lệnh.
     */
    private suspend fun saveVerified(ip: String, payload: JSONObject, presetId: Int) {
        var lastErr: String? = null
        repeat(SAVE_ATTEMPTS) { attempt ->
            try {
                WledHttp.postState(ip, payload, 5)
            } catch (e: Exception) {
                lastErr = e.message
                delay(300)
                return@repeat
            }
            if (MockDevice.isMock(ip)) return
            val deadline = System.currentTimeMillis() + SAVE_WAIT_MS
            while (true) {
                val presets = runCatching { WledHttp.getPresets(ip) }.getOrNull()
                if (PoiTools.presetSaved(presets, presetId, payload)) return
                lastErr = "chưa thấy preset trên mạch"
                if (System.currentTimeMillis() >= deadline) break
                delay(SAVE_POLL_MS)
            }
            android.util.Log.w("PoiStudio", "ID $presetId chưa lưu, lần ${attempt + 1}/$SAVE_ATTEMPTS — $lastErr")
        }
        throw Exception("lưu preset thất bại sau $SAVE_ATTEMPTS lần ($lastErr)")
    }

    // ── Nháy / Tắt LED ───────────────────────────────────────────────────

    fun identify(id: Int) {
        val row = state.value.rows.firstOrNull { it.id == id } ?: return
        if (!row.online || id in identifying) return
        identifying += id
        rebuildRows()
        status("💡 Đang nháy ${row.name}…")
        scope.launch {
            try {
                val st = WledHttp.getStateInfo(row.ip).optJSONObject("state")
                commandMutex.withLock {
                    for ((payload, pause) in PoiTools.identifyPlan(st)) {
                        WledHttp.postState(row.ip, payload, 2)
                        if (pause > 0) delay(pause)
                    }
                }
                status("💡 Đã nháy ${row.name} — mạch đã trở lại trạng thái cũ.", PoiLevel.OK)
            } catch (e: Exception) {
                status("❌ Không nháy được ${row.name}: ${e.message}", PoiLevel.ERR)
            } finally {
                identifying -= id
                rebuildRows()
            }
        }
    }

    fun allOff() {
        val targets = state.value.rows.filter { it.online && it.id in state.value.targetIds }
        if (targets.isEmpty()) return status("⚠ Chưa tick mạch nào để tắt LED.", PoiLevel.WARN)
        scope.launch {
            val results = targets.map { r ->
                async { runCatching { WledHttp.postState(r.ip, JSONObject().put("on", false), 2) }.isSuccess }
            }.awaitAll()
            val ok = results.count { it }
            running = null
            _state.update { it.copy(runningPresetId = null, runningPoi = false, speedUnsaved = false) }
            status("⏻ Đã tắt LED $ok/${targets.size} mạch" + if (ok < targets.size) " — ${targets.size - ok} mạch không phản hồi." else ".",
                if (ok < targets.size) PoiLevel.WARN else PoiLevel.OK)
        }
    }

    // ── Gửi ảnh ──────────────────────────────────────────────────────────

    /** Gửi ảnh đang xem trước tới các mạch đã tick (ghi vào ID trống kế tiếp 1–59). */
    fun sendCurrent() {
        val img = source ?: return status("⚠ Chưa có ảnh — chọn ảnh hoặc mẫu trong thư viện trước.", PoiLevel.WARN)
        val s = state.value
        if (s.busy) return
        if (!s.size.ok) return status("⚠ BMP ở ${s.params.pixels} pixel vượt ${PoiTools.limitLabel(s.size.limit)} — giảm Pixel LED hoặc chọn ảnh khác.", PoiLevel.WARN)
        val targets = s.usableTargets
        if (targets.isEmpty()) return status("⚠ Chưa tick mạch POI nào để gửi.", PoiLevel.WARN)
        runUpload(listOf((sourceStem.ifBlank { "POI" }) to img), targets, overwrite = false, shape = s.sendShape)
    }

    /** Gửi nhiều ảnh (theo kiểu hình đang chọn) tới các mạch đã tick. */
    fun sendMany(uris: List<Uri>, overwrite: Boolean) {
        val s = state.value
        if (s.busy || uris.isEmpty()) return
        val targets = s.usableTargets
        if (targets.isEmpty()) return status("⚠ Chưa tick mạch POI nào để gửi.", PoiLevel.WARN)
        val shape = s.params.shape
        val px = s.params.pixels
        val limit = targets.minOf { it.bmpLimit }
        scope.launch {
            val images = ArrayList<Pair<String, Bitmap>>()
            val skipped = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = displayName(uri)
                    val bmp = runCatching { app.contentResolver.openInputStream(uri)?.use { PoiImage.decodeSampled(it.readBytes()) } }.getOrNull()
                    if (bmp == null) { skipped += "$name (không mở được)"; continue }
                    if (shape != Shape.DISC && (PoiTools.poiBmpHeight(px, bmp.width, bmp.height) < 1 ||
                            !PoiTools.fits(PoiTools.poiBmpSize(px, bmp.width, bmp.height), limit))) {
                        skipped += "$name (vượt ${PoiTools.limitLabel(limit)})"
                        continue
                    }
                    images += PoiTools.fileStem(name) to bmp
                }
            }
            if (skipped.isNotEmpty()) log("POI: bỏ qua ${skipped.size} ảnh: ${skipped.joinToString()}", "WARN")
            if (images.isEmpty()) return@launch status("⚠ Không có ảnh nào gửi được: ${skipped.joinToString()}", PoiLevel.WARN)
            runUpload(images, targets, overwrite, shape, skippedNote = skipped)
        }
    }

    private fun runUpload(
        images: List<Pair<String, Bitmap>>,
        targets: List<PoiDeviceRow>,
        overwrite: Boolean,
        shape: Shape,
        skippedNote: List<String> = emptyList()
    ) {
        val p = state.value.params
        val ipt = if (shape == Shape.DISC) 1 else p.imagesPerTurn
        val total = images.size * targets.size
        val done = AtomicInteger(0)
        _state.update { it.copy(busy = true, progress = PoiProgress(0, total, "Đang chuẩn bị…")) }
        log("POI: gửi ${images.size} ảnh × ${targets.size} mạch · ${p.pixels}px · ${if (shape == Shape.DISC) "Đúng hình" else "Uốn theo vòng"} · ${PoiTools.speedLabel(p.rpm, ipt, p.reverse)}", "INFO")

        scope.launch(Dispatchers.IO) {
            val lines = targets.map { row ->
                async {
                    fun step(note: String) {
                        val n = done.incrementAndGet()
                        _state.update { it.copy(progress = PoiProgress(n, total, "${row.name}: $note")) }
                    }
                    val ok = ArrayList<Int>()
                    var fail = 0
                    try {
                        val json = WledHttp.getJson(row.ip)
                        val info = json.optJSONObject("info")
                        val block = PoiTools.blockReason(info?.optString("product"), info?.opt("vid") as? Int)
                        if (block != null) throw Exception(PoiTools.blockMessage(row.name, info?.optString("product"), info?.opt("vid") as? Int))
                        val effects = json.optJSONArray("effects")
                        val fx = effects?.let { arr -> (0 until arr.length()).firstOrNull { arr.optString(it) == PoiTools.POI_EFFECT_NAME } }
                            ?: throw Exception("firmware không có hiệu ứng 'Poi HSL'")
                        val presets = WledHttp.getPresets(row.ip)
                        val slots = PoiTools.logoSlots(presets, images.size, overwrite)
                        if (slots.isEmpty()) throw Exception("hết ID trống trong vùng logo 1–59")
                        val batch = images.take(slots.size)
                        val names = PoiTools.uniquePresetNames(
                            batch.map { (stem, _) -> if (shape == Shape.DISC) PoiTools.discStem(stem) else stem },
                            PoiTools.reservedBmpStems(presets, slots)
                        )
                        val limit = PoiTools.deviceBmpLimit(info?.opt("vid") as? Int, (info?.opt("psram") as? Number)?.toLong())
                        if (running?.first == row.id) running = null
                        // Đưa mạch về trạng thái an toàn trước khi ghi flash.
                        runCatching { WledHttp.postState(row.ip, JSONObject().put("ps", 0), 1) }
                        runCatching { WledHttp.postState(row.ip, JSONObject().put("on", false), 1) }
                        for ((i, pair) in batch.withIndex()) {
                            val (_, img) = pair
                            val slot = slots[i]
                            val name = names[i]
                            try {
                                val bmp = PoiImage.makeBmp(img, p.pixels, shape, p.hole, limit)
                                val bytes = PoiImage.encodeBmp24(bmp)
                                if (!PoiTools.fits(bytes.size, limit)) throw Exception("BMP ${PoiTools.humanSize(bytes.size)} vượt ${PoiTools.limitLabel(limit)}")
                                WledHttp.uploadFile(row.ip, "/$name.bmp", bytes, "image/bmp")
                                delay(200) // cho ESP ghi flash xong
                                val payload = PoiTools.imagePresetPayload(
                                    "$name.bmp", slot, name, fx, p.bri, bmp.width, p.rpm, ipt, p.reverse
                                )
                                saveVerified(row.ip, payload, slot)
                                delay(150)
                                runCatching { WledHttp.postState(row.ip, JSONObject().put("on", false), 3) }
                                ok += slot
                            } catch (e: Exception) {
                                fail++
                                log("POI ${row.name}: ❌ ID $slot '$name' — ${e.message}", "ERROR")
                            }
                            step("ID $slot · $name")
                        }
                        repeat(images.size - batch.size) { step("hết ID trống") }
                        val skippedDev = images.size - batch.size
                        "${row.name}: ✔ ${ok.size} ảnh (${PoiTools.formatSlots(ok)})" +
                            (if (fail > 0) " · ❌ $fail lỗi" else "") +
                            (if (skippedDev > 0) " · bỏ qua $skippedDev (hết ID trống)" else "")
                    } catch (e: Exception) {
                        repeat(images.size - ok.size - fail) { step("lỗi") }
                        "${row.name}: ❌ ${e.message}"
                    }
                }
            }.awaitAll()

            val anyErr = lines.any { "❌" in it }
            lines.forEach { log("POI: $it", if ("❌" in it) "ERROR" else "INFO") }
            val note = if (skippedNote.isNotEmpty()) " · bỏ qua ${skippedNote.size} ảnh không hợp lệ" else ""
            _state.update { it.copy(busy = false, progress = null, runningPresetId = null, runningPoi = false, speedUnsaved = false) }
            status(lines.joinToString("\n") + note, if (anyErr) PoiLevel.WARN else PoiLevel.OK)
            refreshFleet(targets.map { it.id })
            withContext(Dispatchers.Main) { afterWrite(targets.map { it.id }) }
        }
    }

    // ── Tiện ích ─────────────────────────────────────────────────────────

    private fun status(text: String, level: PoiLevel = PoiLevel.INFO) {
        _state.update { it.copy(status = text, statusLevel = level) }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "POI"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "POI"
    }

    private fun loadParams(): PoiParams = PoiParams(
        pixels = prefs.getInt("pixels", PoiTools.PIXEL_DEFAULT).coerceIn(PoiTools.PIXEL_MIN, PoiTools.PIXEL_MAX),
        bri = PoiTools.clampBri(prefs.getInt("bri", PoiTools.defaultBri())),
        shape = Shape.of(prefs.getString("shape", Shape.FLAT.id)),
        hole = prefs.getInt("hole", PoiTools.DISC_HOLE_DEFAULT).coerceIn(0, PoiTools.DISC_HOLE_MAX),
        rpm = prefs.getInt("rpm", PoiTools.RPM_DEFAULT).coerceIn(PoiTools.RPM_AUTO, PoiTools.RPM_MAX),
        imagesPerTurn = prefs.getInt("images_per_turn", PoiTools.IMAGES_PER_TURN_DEFAULT).coerceIn(1, PoiTools.IMAGES_PER_TURN_MAX),
        reverse = prefs.getBoolean("reverse", false)
    )

    private fun saveParams(p: PoiParams) {
        prefs.edit()
            .putInt("pixels", p.pixels).putInt("bri", p.bri).putString("shape", p.shape.id)
            .putInt("hole", p.hole).putInt("rpm", p.rpm).putInt("images_per_turn", p.imagesPerTurn)
            .putBoolean("reverse", p.reverse)
            .apply()
    }

    private fun loadList(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    companion object {
        private const val KEY_FAV = "gallery_favorites"
        private const val KEY_RECENT = "gallery_recent"
        private const val SAVE_ATTEMPTS = 3
        private const val SAVE_WAIT_MS = 5_000L
        private const val SAVE_POLL_MS = 400L
        private const val LIVE_SPEED_DELAY_MS = 250L
    }
}
