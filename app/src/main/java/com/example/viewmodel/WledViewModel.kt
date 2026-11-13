package com.example.viewmodel

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.billing.ProSubscriptionManager
import com.example.data.*
import com.example.util.MatrixImage
import com.example.util.PoiImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

sealed class AddDeviceState {
    object Idle : AddDeviceState()
    object Validating : AddDeviceState()
    object Success : AddDeviceState()
    data class InvalidDevice(val ip: String, val message: String) : AddDeviceState()
    data class ConnectionError(val ip: String, val message: String) : AddDeviceState()
    data class DuplicateDevice(val ip: String, val message: String) : AddDeviceState()
}

enum class PlaylistPlaybackState {
    Idle,
    Running,
    Paused,
    Completed
}

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val api = WledApi.create()
    private val repository = WledRepository(db.wledDao(), api)
    private val discoveryManager = WledDiscoveryManager(application)
    private val proSubscriptionManager = ProSubscriptionManager(application)

    val proSubscriptionState = proSubscriptionManager.state

    fun refreshProSubscription() {
        proSubscriptionManager.refresh()
    }

    fun restoreProSubscription() {
        proSubscriptionManager.restorePurchases()
    }

    fun launchProPurchase(activity: Activity) {
        proSubscriptionManager.launchPurchase(activity)
    }

    fun selectProPlan(basePlanId: String) {
        proSubscriptionManager.selectPlan(basePlanId)
    }

    fun setDebugProEntitlement(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            proSubscriptionManager.setDebugEntitlement(enabled)
        }
    }

    private val _addDeviceState = MutableStateFlow<AddDeviceState>(AddDeviceState.Idle)
    val addDeviceState: StateFlow<AddDeviceState> = _addDeviceState.asStateFlow()

    fun resetAddDeviceState() {
        _addDeviceState.value = AddDeviceState.Idle
    }

    // ---- Cài đặt hệ thống app (ngôn ngữ...) ----
    private val appSettingsPrefs by lazy {
        getApplication<Application>()
            .getSharedPreferences("wled_app_settings", android.content.Context.MODE_PRIVATE)
    }

    /** Mã ngôn ngữ app: "vi" (mặc định) hoặc "en". Tạm thời demo — mới áp dụng cho màn Cài đặt. */
    private val _appLanguage = MutableStateFlow("vi")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(code: String) {
        _appLanguage.value = code
        appSettingsPrefs.edit().putString("language", code).apply()
    }

    // ---- Nút bấm Bluetooth (HID) điều khiển Play từ xa ----
    private val btRemotePrefs by lazy {
        getApplication<Application>()
            .getSharedPreferences("wled_bt_remote_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val _btRemoteEnabled = MutableStateFlow(false)
    val btRemoteEnabled: StateFlow<Boolean> = _btRemoteEnabled.asStateFlow()

    /** Mã phím (KeyEvent keyCode) đã gán; -1 = chưa gán. */
    private val _btRemoteKeyCode = MutableStateFlow(-1)
    val btRemoteKeyCode: StateFlow<Int> = _btRemoteKeyCode.asStateFlow()

    /** Đang ở chế độ "Học phím": chờ người dùng bấm remote để ghi mã phím. */
    private val _btRemoteLearning = MutableStateFlow(false)
    val btRemoteLearning: StateFlow<Boolean> = _btRemoteLearning.asStateFlow()

    init {
        _appLanguage.value = appSettingsPrefs.getString("language", "vi") ?: "vi"
        _btRemoteEnabled.value = btRemotePrefs.getBoolean("enabled", false)
        _btRemoteKeyCode.value = btRemotePrefs.getInt("keycode", -1)
    }

    fun setBtRemoteEnabled(enabled: Boolean) {
        _btRemoteEnabled.value = enabled
        btRemotePrefs.edit().putBoolean("enabled", enabled).apply()
        if (!enabled) _btRemoteLearning.value = false
    }

    fun startBtRemoteLearning() {
        _btRemoteLearning.value = true
    }

    fun cancelBtRemoteLearning() {
        _btRemoteLearning.value = false
    }

    /**
     * MainActivity gọi khi đang ở chế độ học và nhận được một phím.
     * Trả về true nếu đã ghi nhận (đang học), để Activity nuốt sự kiện.
     */
    fun onBtRemoteKeyLearned(keyCode: Int): Boolean {
        if (!_btRemoteLearning.value) return false
        _btRemoteKeyCode.value = keyCode
        _btRemoteLearning.value = false
        btRemotePrefs.edit().putInt("keycode", keyCode).apply()
        log("Đã gán nút Bluetooth: phím \"${btKeyLabel(keyCode)}\" (code $keyCode).", "INFO")
        return true
    }

    fun clearBtRemoteKey() {
        _btRemoteKeyCode.value = -1
        btRemotePrefs.edit().remove("keycode").apply()
    }

    /** Tên hiển thị thân thiện cho một keyCode. */
    fun btKeyLabel(code: Int): String {
        if (code < 0) return "Chưa gán"
        val raw = android.view.KeyEvent.keyCodeToString(code) // ví dụ "KEYCODE_VOLUME_UP"
        return raw.removePrefix("KEYCODE_").replace('_', ' ')
            .lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * Hành động "1 nút thông minh" cho nút bấm từ xa:
     * đang chạy → Tạm dừng; đang pause → Tiếp tục; còn lại → Phát từ đầu.
     * Trả về mô tả ngắn để Activity hiển thị toast.
     */
    fun triggerRemoteAction(): String {
        return when (_playlistPlaybackState.value) {
            PlaylistPlaybackState.Running -> {
                pausePlaylistTimeline(); "Tạm dừng"
            }
            PlaylistPlaybackState.Paused -> {
                resumePlaylistTimeline(); "Tiếp tục"
            }
            else -> {
                startPlaylistAllWithTimeline(playlistPresetSlot, forceRestart = true); "Phát từ đầu"
            }
        }.also { log("Nút Bluetooth: $it.", "INFO") }
    }

    /** Cử chỉ trên 1 nút A/B Shutter. */
    enum class RemoteGesture { SINGLE, DOUBLE, LONG }

    /**
     * Ánh xạ cử chỉ → hành động:
     * - SINGLE: Phát / Tạm dừng / Tiếp tục (thông minh theo trạng thái)
     * - DOUBLE: Dừng hẳn (tắt LED)
     * - LONG:   Chạy lại từ đầu
     * Trả về mô tả ngắn để Activity hiển thị toast.
     */
    fun triggerRemoteGesture(gesture: RemoteGesture): String = when (gesture) {
        RemoteGesture.SINGLE -> triggerRemoteAction()
        RemoteGesture.DOUBLE -> {
            stopPlaylistTimeline()
            "Dừng hẳn".also { log("Nút Bluetooth (nhấn đúp): $it.", "INFO") }
        }
        RemoteGesture.LONG -> {
            startPlaylistAllWithTimeline(playlistPresetSlot, forceRestart = true)
            "Chạy lại từ đầu".also { log("Nút Bluetooth (giữ lâu): $it.", "INFO") }
        }
    }

    private val logDao = db.systemLogDao()
    val systemLogs: StateFlow<List<SystemLog>> = logDao.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun log(message: String, level: String = "INFO") {
        viewModelScope.launch(Dispatchers.IO) {
            logDao.insertLog(SystemLog(message = message, level = level))
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            logDao.clearAllLogs()
            log("Đã xóa toàn bộ nhật ký hệ thống.", "WARN")
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.InetAddress) {
                        val ip = inetAddress.hostAddress
                        if (ip != null && !ip.contains(":")) { // IPv4 check
                            return ip
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("WledViewModel", "Error getting local IP Address", ex)
        }
        return "127.0.0.1"
    }

    fun getWifiSsid(): String {
        try {
            val context = getApplication<Application>()
            val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            if (info != null) {
                val ssid = info.ssid
                if (ssid != null && ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                    return ssid.replace("\"", "") // Clear surrounding quotes
                }
            }
            val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = connManager?.activeNetwork
            val capabilities = connManager?.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                return "Mạng Wi-Fi (Đang kết nối)"
            } else if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "Sóng Di Động (3G/4G/5G)"
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Error getting Wifi SSID", e)
        }
        return "Kết nối Local LAN/Wi-Fi"
    }

    fun getWifiSignalStrength(): String {
        try {
            val context = getApplication<Application>()
            val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            if (info != null) {
                val rssi = info.rssi
                if (rssi != -127) {
                    val level = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5) // 0 to 4
                    return when (level) {
                        4 -> "Rất Mạnh (4/4)"
                        3 -> "Mạnh (3/4)"
                        2 -> "Trung Bình (2/4)"
                        1 -> "Yếu (1/4)"
                        else -> "Rất Yếu (0/4)"
                    }
                }
            }
            val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = connManager?.activeNetwork
            val capabilities = connManager?.getNetworkCapabilities(network)
            if (capabilities != null) {
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                    return "Ổn Định (Wi-Fi 2.4/5GHz)"
                } else if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "Tốt (Mạng di động)"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Error getting Wifi signal level", e)
        }
        return "Tốt"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
            logDao.deleteLogsOlderThan(cutoff)
            log("Khởi động ứng dụng ARGB HSL. Hệ thống sẵn sàng.", "INFO")
        }
    }

    // Expose all devices
    val devices: StateFlow<List<WledDevice>> = repository.allDevices
        .map { list ->
            list.filter { device ->
                device.product == null || WledDevice.isValidProduct(device.product)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Expose unlinked discovered devices (filtered to only show devices not yet registered in DB)
    val unlinkedDiscoveredDevices: StateFlow<List<WledDiscoveryManager.DiscoveredDevice>> = 
        discoveryManager.discoveredDevices.combine(devices) { discovered, registered ->
            discovered.filter { disc -> registered.none { reg -> reg.ipAddress == disc.ipAddress } }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedDevice = MutableStateFlow<WledDevice?>(null)
    val selectedDevice: StateFlow<WledDevice?> = _selectedDevice.asStateFlow()

    private val _selectedTab = MutableStateFlow(1) // Default to 1 (Đồng loạt All)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _activeDeviceDetails = MutableStateFlow<WledResponse?>(null)
    val activeDeviceDetails: StateFlow<WledResponse?> = _activeDeviceDetails.asStateFlow()

    private val _activeDevicePresetStats = MutableStateFlow(DevicePresetStorageStats())
    val activeDevicePresetStats: StateFlow<DevicePresetStorageStats> = _activeDevicePresetStats.asStateFlow()


    private val _presetPurgeState = MutableStateFlow(PresetPurgeUiState())
    val presetPurgeState: StateFlow<PresetPurgeUiState> = _presetPurgeState.asStateFlow()

    private val _fileCleanupState = MutableStateFlow(FileCleanupUiState())
    val fileCleanupState: StateFlow<FileCleanupUiState> = _fileCleanupState.asStateFlow()

    private val _onlineDevicePresetStats = MutableStateFlow<Map<Int, DevicePresetStorageStats>>(emptyMap())
    val onlineDevicePresetStats: StateFlow<Map<Int, DevicePresetStorageStats>> = _onlineDevicePresetStats.asStateFlow()

    // Trạng thái upload ảnh → preset (tab "Upload POI & Cờ LED").
    private val _imageUploadState = MutableStateFlow(ImageUploadUiState())
    val imageUploadState: StateFlow<ImageUploadUiState> = _imageUploadState.asStateFlow()

    // ---- Tab "Biên Tập Timeline" ----
    private val timelineClipDao = db.timelineClipDao()

    private val _editorClips = MutableStateFlow<Map<Int, List<TimelineClip>>>(emptyMap())
    val editorClips: StateFlow<Map<Int, List<TimelineClip>>> = _editorClips.asStateFlow()

    private val _editorPresets = MutableStateFlow<Map<Int, List<EditablePreset>>>(emptyMap())
    val editorPresets: StateFlow<Map<Int, List<EditablePreset>>> = _editorPresets.asStateFlow()

    private val _editorSelectedDeviceId = MutableStateFlow<Int?>(null)
    val editorSelectedDeviceId: StateFlow<Int?> = _editorSelectedDeviceId.asStateFlow()

    private val _editorTotalSeconds = MutableStateFlow(60)
    val editorTotalSeconds: StateFlow<Int> = _editorTotalSeconds.asStateFlow()

    private val _editorUploadState = MutableStateFlow(EditorUploadUiState())
    val editorUploadState: StateFlow<EditorUploadUiState> = _editorUploadState.asStateFlow()

    private var nextEditorClipId = 1L
    // Thời lượng clip tối thiểu trên timeline (giây) — cho phép thu nhỏ "hết cỡ".
    private val EDITOR_MIN_CLIP_SEC = 0.2f

    init {
        // Start background polling to check online status
        startPolling()

        // Start Network Service Discovery
        discoveryManager.startDiscovery()

        // Listen for discovered devices to automatically ping any registered ones to quickly mark online
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { discoveredList ->
                val currentRegDevices = devices.value
                discoveredList.forEach { discovered ->
                    val matched = currentRegDevices.find { it.ipAddress == discovered.ipAddress }
                    if (matched != null && !matched.isOnline) {
                        launch {
                            repository.pingDevice(matched)
                        }
                    }
                }
            }
        }
        loadAudioPreferences()
        loadEditorClipsFromDb()
        seedMockDeviceForDebug()
    }

    /** DEV ONLY: seed thiết bị ảo + mở Pro tạm thời để thử nghiệm khi không có mạch thật. */
    private fun seedMockDeviceForDebug() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val seeded = repository.ensureMockDeviceSeeded()
            if (seeded) {
                // Lần đầu tạo mock → bật Pro debug để mở các tab thử nghiệm.
                proSubscriptionManager.setDebugEntitlement(true)
            }
        }
    }

    /** Nạp clip timeline đã lưu (Room) vào working state lúc khởi động. */
    private fun loadEditorClipsFromDb() {
        viewModelScope.launch {
            timelineClipDao.getAllClips().collect { entities ->
                // Bỏ qua đồng bộ ngược: chỉ nạp lần đầu (khi working state còn rỗng) để
                // tránh ghi đè thao tác đang chỉnh. Sau đó UI là nguồn sự thật.
                if (_editorClips.value.isNotEmpty()) return@collect
                if (entities.isEmpty()) return@collect
                val grouped = entities.groupBy { it.deviceId }.mapValues { (_, list) ->
                    list.map { e ->
                        TimelineClip(
                            id = nextEditorClipId++,
                            deviceId = e.deviceId,
                            presetId = e.presetId,
                            presetName = e.presetName,
                            startSec = e.startDeciseconds / 10f,
                            durationSec = e.durationDeciseconds / 10f,
                            transitionSec = e.transitionDeciseconds / 10f
                        )
                    }
                }
                _editorClips.value = grouped
                recomputeEditorTotalSeconds()
            }
        }
    }

    // Default static effect list as fallback
    val defaultEffects = listOf(
        WledEffect(0, "Solid", "Đơn Sắc"),
        WledEffect(1, "Blink", "Nhấp Nháy"),
        WledEffect(2, "Breathe", "Nhịp Thở"),
        WledEffect(3, "Wipe", "Quét Màu"),
        WledEffect(4, "Wipe Random", "Miết Ngẫu Nhiên"),
        WledEffect(5, "Random Colors", "Màu Ngẫu Nhiên"),
        WledEffect(6, "Sweep", "Quét Quạt Tròn"),
        WledEffect(7, "Dynamic", "Pha Màu Động"),
        WledEffect(8, "Colorloop", "Vòng Lặp Sắc Cầu Vồng"),
        WledEffect(9, "Rainbow", "Dải Cầu Vồng Cuộn"),
        WledEffect(10, "Scan", "Đuổi Điểm Sáng Trơn"),
        WledEffect(11, "Dual Scan", "Đuổi Bóng Kép Hai Chiều"),
        WledEffect(12, "Fade", "Phai Dần Màu Sắc"),
        WledEffect(15, "Theater", "H hiệu ứng Rạp Hát"),
        WledEffect(20, "Saw", "Lượn Màu Răng Cưa"),
        WledEffect(24, "Fire Flicker", "Lửa Bếp Tiêu Tiêu"),
        WledEffect(38, "Fireworks", "Pháo Hoa Đầy Trời"),
        WledEffect(46, "Fire 2012", "Huyết Lửa Bập Bùng"),
        WledEffect(51, "Aurora", "Dải Cực Quang Huyền Ảo"),
        WledEffect(54, "Lightning", "Đớp Sấm Chớp Giông Bão"),
        WledEffect(87, "Noise", "Nhiễu Vân Sáng Động")
    )

    // Default static palette list as fallback
    val defaultPalettes = listOf(
        WledPalette(0, "Default", "Mặc Định"),
        WledPalette(1, "Random Cycle", "Vòng Lặp Ngẫu Nhiên"),
        WledPalette(2, "Primary Color", "Chỉ Màu Chính"),
        WledPalette(3, "Based on Primary", "Đồng Bộ Màu Chủ Đạo"),
        WledPalette(4, "Set Colors", "Bộ Màu Thiết Lập Sẵn"),
        WledPalette(35, "Forest", "Rừng Nhiệt Đới (Xanh lá)"),
        WledPalette(36, "Rainbow", "Cầu Vồng Lục Tự"),
        WledPalette(38, "Sunset", "Hoàng Hôn Ấm Áp (Màu ấm)"),
        WledPalette(42, "Ocean", "Đại Dương Sâu Thẳm (Màu lạnh)")
    )

    fun selectDevice(device: WledDevice?) {
        _selectedDevice.value = device
        _activeDeviceDetails.value = null
        _activeDevicePresetStats.value = DevicePresetStorageStats()
        if (device != null) {
            viewModelScope.launch {
                // Ping to update latest state
                val updated = repository.pingDevice(device)
                if (updated.id == _selectedDevice.value?.id) {
                    _selectedDevice.value = updated
                }
                // Fetch complete details if possible
                val details = repository.fetchDeviceDetails(updated)
                if (details != null && _selectedDevice.value?.id == device.id) {
                    _activeDeviceDetails.value = details
                }
                refreshPresetStatsForDevice(updated)
            }
        }
    }

    fun addDevice(name: String, ipAddress: String) {
        viewModelScope.launch {
            _addDeviceState.value = AddDeviceState.Validating
            val cleanedIp = WledRepository.cleanIpAddress(ipAddress)
            
            // Duplication check across existing devices
            val isDuplicate = devices.value.any {
                WledRepository.cleanIpAddress(it.ipAddress) == cleanedIp
            }
            if (isDuplicate) {
                val dupMsg = "Thiết bị với địa chỉ IP $cleanedIp đã tồn tại trong hệ thống quản lý."
                log("Phát hiện cố gắng thêm thiết bị trùng lặp: $name ($cleanedIp)", "WARN")
                _addDeviceState.value = AddDeviceState.DuplicateDevice(cleanedIp, dupMsg)
                return@launch
            }
            
            val url = "http://$cleanedIp/json"
            
            try {
                // Fetch device info directly from the API before adding
                val response = withContext(Dispatchers.IO) {
                    api.getCompleteApi(url)
                }
                val product = response.info?.product
                if (product != null && WledDevice.isValidProduct(product)) {
                    // Valid ARGB HSL product! Add it to the DB
                    val id = repository.addDevice(name, cleanedIp)
                    log("Đã thêm thiết bị mới: $name ($cleanedIp)", "INFO")
                    _addDeviceState.value = AddDeviceState.Success
                    // Instantly ping it to cache other states
                    val newDevice = repository.getDeviceById(id.toInt())
                    if (newDevice != null) {
                        launch {
                            repository.pingDevice(newDevice)
                        }
                    }
                } else {
                    // Invalid product or not an ARGB HSL Controller!
                    val invalidMsg = "Thiết bị không chính hãng hoặc phần mềm mạch quá cũ, liên hệ nhà cung cấp để nâng cấp mới"
                    log("$invalidMsg (${cleanedIp})", "ERROR")
                    _addDeviceState.value = AddDeviceState.InvalidDevice(cleanedIp, invalidMsg)
                }
            } catch (e: Exception) {
                // Connection or offline discovery error
                val offlineMsg = "Không thể kết nối đến địa chỉ IP $cleanedIp để xác nhận thiết bị chính hãng. Vui lòng đảm bảo thiết bị đã bật nguồn và kết nối cùng mạng Wi-Fi."
                _addDeviceState.value = AddDeviceState.ConnectionError(cleanedIp, offlineMsg)
            }
        }
    }

    fun deleteDevice(device: WledDevice) {
        viewModelScope.launch {
            log("Đã xóa thiết bị: ${device.name} (${device.ipAddress})", "WARN")
            if (_selectedDevice.value?.id == device.id) {
                _selectedDevice.value = null
                _activeDeviceDetails.value = null
            }
            repository.deleteDevice(device)
        }
    }

    fun refreshAllDevices() {
        // Quét lại danh sách → mọi mạch phải đối chiếu preset lại trước lần Phát sau (§5.1).
        verifiedIps.clear()
        viewModelScope.launch {
            _isRefreshing.value = true
            devices.value.forEach { device ->
                repository.pingDevice(device)
            }
            // Update selected device if active
            _selectedDevice.value?.let { current ->
                val updated = repository.getDeviceById(current.id)
                if (updated != null) {
                    _selectedDevice.value = updated
                    val details = repository.fetchDeviceDetails(updated)
                    _activeDeviceDetails.value = details
                    refreshPresetStatsForDevice(updated)
                }
            }
            _isRefreshing.value = false
        }
    }

    fun togglePower(device: WledDevice, turnOn: Boolean) {
        viewModelScope.launch {
            val success = repository.togglePower(device, turnOn)
            if (success) {
                log("Thiết bị ${device.name}: ${if (turnOn) "BẬT nguồn" else "TẮT nguồn"}", "INFO")
                updateLocalDeviceInMemory { it.copy(isOn = turnOn, isOnline = true) }
            } else {
                log("Không thể kết nối để thay đổi nguồn thiết bị ${device.name}", "ERROR")
            }
        }
    }

    fun updateBrightness(device: WledDevice, bri: Int) {
        viewModelScope.launch {
            val success = repository.updateBrightness(device, bri)
            if (success) {
                log("Độ sáng thiết bị ${device.name} thiết lập thành: ${Math.round(bri / 2.55f)}%", "INFO")
                updateLocalDeviceInMemory { it.copy(brightness = bri, isOnline = true) }
            }
        }
    }

    fun updateColor(device: WledDevice, hexColor: String) {
        viewModelScope.launch {
            val success = repository.updateColor(device, hexColor)
            if (success) {
                log("Đổi màu thiết bị ${device.name} sang: $hexColor", "INFO")
                updateLocalDeviceInMemory { it.copy(hexColor = hexColor, isOnline = true) }
            }
        }
    }

    fun updateEffect(device: WledDevice, effectId: Int) {
        viewModelScope.launch {
            val success = repository.updateEffect(device, effectId)
            if (success) {
                updateLocalDeviceInMemory { it.copy(effectId = effectId, isOnline = true) }
            }
        }
    }

    fun updateEffectParams(device: WledDevice, speed: Int?, intensity: Int?) {
        viewModelScope.launch {
            repository.updateEffect(device, device.effectId, speed, intensity)
        }
    }

    fun updatePalette(device: WledDevice, paletteId: Int) {
        viewModelScope.launch {
            val segReq = WledSegmentRequest(id = 0, pal = paletteId)
            val stateReq = WledStateRequest(seg = listOf(segReq))
            val url = "http://${device.ipAddress}/json/state"
            try {
                val api = WledApi.create()
                api.updateState(url, stateReq)
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Error updating palette", e)
            }
        }
    }

    private fun updateLocalDeviceInMemory(transformer: (WledDevice) -> WledDevice) {
        _selectedDevice.value?.let { current ->
            val updated = transformer(current)
            _selectedDevice.value = updated
        }
    }

    fun refreshActiveDeviceMemoryAndPresetStats(device: WledDevice? = _selectedDevice.value) {
        if (device == null) return
        viewModelScope.launch {
            val details = repository.fetchDeviceDetails(device)
            if (_selectedDevice.value?.id == device.id) {
                _activeDeviceDetails.value = details
            }
            refreshPresetStatsForDevice(device)
        }
    }

    fun refreshOnlineDevicePresetStats() {
        viewModelScope.launch {
            val onlineDevices = devices.value.filter { it.isOnline }
            if (onlineDevices.isEmpty()) {
                _onlineDevicePresetStats.value = emptyMap()
                return@launch
            }

            _onlineDevicePresetStats.value = onlineDevices.associate { device ->
                device.id to DevicePresetStorageStats(deviceId = device.id, isLoading = true)
            }

            val updatedStats = mutableMapOf<Int, DevicePresetStorageStats>()
            for (device in onlineDevices) {
                val stats = try {
                    val presets = fetchPresetsJsonObject(device.ipAddress)
                    buildPresetStats(device.id, presets)
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Error fetching online preset stats", e)
                    DevicePresetStorageStats(
                        deviceId = device.id,
                        error = e.message ?: "Không đọc được presets.json"
                    )
                }
                updatedStats[device.id] = stats
                _onlineDevicePresetStats.value = _onlineDevicePresetStats.value + (device.id to stats)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // DỌN PRESET — 1 nút "Xóa preset + ảnh" (tab ALL: mọi mạch online; tab Cấu Hình Mạch:
    // mạch đang chọn). Đồng bộ desktop v3.14+.
    // Quét → người dùng tích chọn preset → mỗi mạch (song song): đọc lại, sao lưu,
    // xoá preset, đọc lại xác nhận, rồi mới xoá ảnh mà chỉ preset đã xoá dùng.
    // ---------------------------------------------------------------------------

    private class PurgeScan(
        val device: WledDevice,
        val presets: org.json.JSONObject?,
        val files: List<DeviceImageFile>?,
        val error: String?
    )

    private class PurgeOutcome(
        val device: WledDevice,
        val presetsOk: Int = 0,
        val presetsFail: Int = 0,
        val filesOk: Int = 0,
        val filesFail: Int = 0,
        val imagesKeptNoList: Boolean = false,
        val deletedPlaylist: Boolean = false,
        val error: String? = null
    )

    /** Tab Đồng Loạt (ALL): dọn trên mọi mạch online. */
    fun openPresetPurgeForOnlineDevices() {
        val online = devices.value.filter { it.isOnline }
        openPresetPurge(online, "${online.size} mạch online", emptyMessage = "Không có thiết bị online để dọn preset.")
    }

    /** Tab Cấu Hình Mạch: chỉ dọn trên mạch đang chọn. */
    fun openPresetPurgeForDevice(device: WledDevice) {
        val current = devices.value.find { it.id == device.id } ?: device
        openPresetPurge(
            if (current.isOnline) listOf(current) else emptyList(),
            "mạch ${current.name}",
            emptyMessage = "Thiết bị ${current.name} đang ngoại tuyến."
        )
    }

    /** Mở dialog chọn preset: đọc presets.json + danh sách file của các mạch [online] (song song). */
    private fun openPresetPurge(online: List<WledDevice>, scopeLabel: String, emptyMessage: String) {
        if (_presetPurgeState.value.isBusy) return
        if (online.isEmpty()) {
            _presetPurgeState.value = PresetPurgeUiState(isVisible = true, error = emptyMessage)
            return
        }
        _presetPurgeState.value = PresetPurgeUiState(
            isVisible = true, isScanning = true, deviceCount = online.size, scopeLabel = scopeLabel
        )
        viewModelScope.launch {
            val scans = withContext(Dispatchers.IO) {
                online.map { d -> async { scanDeviceForPurge(d) } }.awaitAll()
            }
            val ok = scans.filter { it.presets != null }
            val byPid = java.util.TreeMap<Int, PresetPurgeItem>()
            var orphanCount = 0
            var orphanBytes = 0L
            var orphanDevices = 0
            for (scan in ok) {
                val presets = scan.presets!!
                for (pid in PresetCleanup.deletablePids(presets)) {
                    val images = PresetCleanup.presetImages(presets.opt(pid.toString()))
                    val prev = byPid[pid]
                    byPid[pid] = if (prev == null) {
                        PresetPurgeItem(
                            pid = pid,
                            name = PresetCleanup.presetName(presets, pid),
                            group = PresetCleanup.groupOf(pid),
                            images = images,
                            deviceCount = 1
                        )
                    } else {
                        prev.copy(images = (prev.images + images).distinct(), deviceCount = prev.deviceCount + 1)
                    }
                }
                val files = scan.files ?: continue
                val orphans = PresetCleanup.orphanImages(presets, files.map { it.path })
                if (orphans.isNotEmpty()) {
                    orphanDevices++
                    orphanCount += orphans.size
                    val sizes = files.associate { it.path.lowercase() to it.sizeBytes }
                    orphanBytes += orphans.sumOf { sizes[it.lowercase()] ?: 0L }
                }
            }
            _presetPurgeState.value = PresetPurgeUiState(
                isVisible = true,
                deviceCount = ok.size,
                scopeLabel = scopeLabel,
                deviceIds = ok.map { it.device.id },
                items = byPid.values.toList(),
                orphanCount = orphanCount,
                orphanBytes = orphanBytes,
                orphanDevices = orphanDevices,
                scanErrors = scans.filter { it.error != null }.map {
                    PresetDeviceDeleteError(it.device.name, it.device.ipAddress, it.error!!)
                },
                scanWarnings = ok.filter { it.files == null }.map {
                    "${it.device.name}: không đọc được danh sách file — chỉ xoá preset, giữ nguyên ảnh."
                }
            )
        }
    }

    private suspend fun scanDeviceForPurge(d: WledDevice): PurgeScan {
        val presets = try {
            fetchPresetsJsonObject(d.ipAddress)
        } catch (e: Exception) {
            return PurgeScan(d, null, null, "không đọc được presets.json: ${e.message ?: "lỗi kết nối"}")
        }
        val files = try {
            fetchDeviceFileEntries(d.ipAddress)
        } catch (e: Exception) {
            null
        }
        return PurgeScan(d, presets, files, null)
    }

    /** Xoá các preset [pids] (kèm ảnh của chúng) trên mọi mạch đã quét; [includeOrphans] = xoá cả ảnh thừa. */
    fun confirmPresetPurge(pids: Set<Int>, includeOrphans: Boolean) {
        val state = _presetPurgeState.value
        if (state.isBusy || (pids.isEmpty() && !includeOrphans)) return
        val targets = devices.value.filter { it.id in state.deviceIds }
        if (targets.isEmpty()) {
            _presetPurgeState.value = state.copy(error = "Không còn thiết bị nào để xoá.")
            return
        }
        _presetPurgeState.value = state.copy(isDeleting = true, progressNote = "Đang chuẩn bị xoá trên ${targets.size} mạch...")
        val progress = java.util.concurrent.ConcurrentHashMap<String, String>()
        fun publishProgress() {
            _presetPurgeState.update { it.copy(progressNote = targets.mapNotNull { d -> progress[d.ipAddress] }.joinToString(" · ")) }
        }

        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                targets.map { d ->
                    async {
                        purgeOnDevice(d, pids, includeOrphans) { note ->
                            progress[d.ipAddress] = "${d.name}: $note"
                            publishProgress()
                        }
                    }
                }.awaitAll()
            }

            val presetsOk = outcomes.sumOf { it.presetsOk }
            val presetsFail = outcomes.sumOf { it.presetsFail }
            val filesOk = outcomes.sumOf { it.filesOk }
            val filesFail = outcomes.sumOf { it.filesFail }
            val errors = outcomes.mapNotNull { o ->
                when {
                    o.error != null -> PresetDeviceDeleteError(o.device.name, o.device.ipAddress, o.error)
                    o.presetsFail > 0 || o.filesFail > 0 -> PresetDeviceDeleteError(
                        o.device.name, o.device.ipAddress,
                        "${o.presetsFail} preset, ${o.filesFail} ảnh không xoá được"
                    )
                    else -> null
                }
            }
            val lines = outcomes.filter { it.error == null }.map { o ->
                "${o.device.name}: xoá ${o.presetsOk} preset + ${o.filesOk} ảnh" +
                    if (o.imagesKeptNoList) " (giữ ảnh — không đọc được danh sách file)" else ""
            }
            log(
                "Dọn preset (${state.scopeLabel}): xoá $presetsOk preset + $filesOk ảnh trên ${targets.size} mạch" +
                    if (presetsFail + filesFail > 0) " ($presetsFail preset, $filesFail ảnh lỗi)" else "",
                if (errors.isEmpty()) "WARN" else "ERROR"
            )

            // Dữ liệu đã đổi trên mạch → làm mới thống kê, danh sách preset tab Biên Tập, timeline 249.
            _editorPresets.value = _editorPresets.value - targets.map { it.id }.toSet()
            refreshOnlineDevicePresetStats()
            _selectedDevice.value?.let { selected ->
                if (targets.any { it.id == selected.id }) {
                    val details = repository.fetchDeviceDetails(selected)
                    if (_selectedDevice.value?.id == selected.id) _activeDeviceDetails.value = details
                    refreshPresetStatsForDevice(selected)
                }
            }
            if (outcomes.any { it.deletedPlaylist }) fetchTimelinesForAllDevices(playlistPresetSlot)

            _presetPurgeState.value = PresetPurgeUiState(
                isVisible = true,
                resultMessage = "Đã xoá $presetsOk preset và $filesOk ảnh trên ${outcomes.count { it.error == null }}/${targets.size} mạch.",
                resultLines = lines,
                resultErrors = errors
            )
        }
    }

    private suspend fun purgeOnDevice(
        d: WledDevice,
        selected: Set<Int>,
        includeOrphans: Boolean,
        onProgress: (String) -> Unit
    ): PurgeOutcome {
        val ip = d.ipAddress
        return try {
            onProgress("đọc lại preset")
            // Đọc lại ngay trước khi xoá (dialog có thể đã mở lâu).
            val text = fetchPresetsText(ip)
            val presets = parsePresetsText(text)
            val files = try { fetchDeviceFileEntries(ip) } catch (e: Exception) { null }
            val paths = files?.map { it.path }.orEmpty()
            val pids = PresetCleanup.deletablePids(presets).filter { it in selected }
            val extra = if (includeOrphans && files != null) PresetCleanup.orphanImages(presets, paths) else emptyList()
            val plan = PresetCleanup.plan(presets, paths, pids, extra)
            if (plan.isEmpty) {
                onProgress("không có gì để xoá")
                return PurgeOutcome(d, imagesKeptNoList = files == null && pids.isNotEmpty())
            }

            backupPresets(d.mac ?: ip, text, ip)
            runCatching { safeState(ip) }

            val requested = HashSet<Int>()
            plan.pids.forEachIndexed { i, pid ->
                onProgress("xoá preset ${i + 1}/${plan.pids.size}")
                try {
                    deletePresetSlot(ip, pid)
                    requested += pid
                } catch (e: Exception) {
                    android.util.Log.w("WledViewModel", "pdel $pid failed on $ip", e)
                }
                delay(150)
            }
            // Đọc lại xác nhận: firmware có thể bỏ qua lệnh mà không báo lỗi.
            val deleted: Set<Int> = if (MockDevice.isMock(ip) || plan.pids.isEmpty()) {
                requested
            } else {
                val after = try { fetchPresetsJsonObject(ip) } catch (e: Exception) { null }
                after?.let { a -> plan.pids.filter { !PresetSlots.isNonEmptyPreset(a.opt(it.toString())) }.toSet() } ?: requested
            }

            val toDelete = plan.filesAfter(deleted)
            var filesOk = 0
            var filesFail = 0
            toDelete.forEachIndexed { i, path ->
                onProgress("xoá ảnh ${i + 1}/${toDelete.size}")
                try {
                    deleteDeviceFile(ip, path)
                    filesOk++
                } catch (e: Exception) {
                    filesFail++
                }
                delay(150)
            }
            onProgress("xong")

            // Preset đã đổi → lần Phát sau phải đối chiếu lại; xoá 249 thì không tự ghi lại từ file timecode.
            verifiedIps.remove(ip)
            val deletedPlaylist = PresetSlots.PLAYLIST in deleted
            if (deletedPlaylist) forgetLastImportFor(ip, d.mac)

            PurgeOutcome(
                device = d,
                presetsOk = deleted.size,
                presetsFail = plan.pids.size - deleted.size,
                filesOk = filesOk,
                filesFail = filesFail,
                imagesKeptNoList = files == null,
                deletedPlaylist = deletedPlaylist
            )
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "purgeOnDevice failed for $ip", e)
            PurgeOutcome(d, error = e.message ?: "lỗi không xác định")
        }
    }

    fun dismissPresetPurge() {
        if (!_presetPurgeState.value.isBusy) _presetPurgeState.value = PresetPurgeUiState()
    }


    private suspend fun refreshPresetStatsForDevice(device: WledDevice) {
        if (_selectedDevice.value?.id != device.id) return
        if (!device.isOnline) {
            _activeDevicePresetStats.value = DevicePresetStorageStats(deviceId = device.id)
            return
        }

        _activeDevicePresetStats.value = DevicePresetStorageStats(deviceId = device.id, isLoading = true)
        try {
            val presets = fetchPresetsJsonObject(device.ipAddress)
            val stats = buildPresetStats(device.id, presets)
            if (_selectedDevice.value?.id == device.id) {
                _activeDevicePresetStats.value = stats
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Error fetching preset stats", e)
            if (_selectedDevice.value?.id == device.id) {
                _activeDevicePresetStats.value = DevicePresetStorageStats(
                    deviceId = device.id,
                    error = e.message ?: "Không đọc được presets.json"
                )
            }
        }
    }

    private fun buildPresetStats(deviceId: Int, presets: org.json.JSONObject): DevicePresetStorageStats {
        val ids = activePresetIds(presets)
        val knownPmax = devices.value.find { it.id == deviceId }?.pmax
            ?: _selectedDevice.value?.takeIf { it.id == deviceId }?.pmax
        val pmax = PresetSlots.pmaxHint(knownPmax, presets)
        return DevicePresetStorageStats(
            deviceId = deviceId,
            logoUsed = ids.count { it in logoPresetRange },
            logoCapacity = logoPresetRange.count(),
            timecodeUsed = ids.count { PresetSlots.isTimecodeSlot(it) },
            timecodeCapacity = PresetSlots.timecodeSlots(pmax).size,
            systemUsed = ids.count { it in systemPresetSlots },
            systemCapacity = systemPresetSlots.size,
            otherUsed = ids.count { it !in logoPresetRange && !PresetSlots.isTimecodeSlot(it) && it !in systemPresetSlots },
            totalPresets = ids.size,
            pmax = pmax
        )
    }

    private suspend fun deletePresetGroupOnDevice(
        device: WledDevice,
        action: PresetDeleteAction
    ): PresetDeletePreview = withContext(Dispatchers.IO) {
        val presets = fetchPresetsJsonObject(device.ipAddress)
        val plan = buildPresetDeletionPreview(device, action, presets)
        if (plan.presetIds.isEmpty() && plan.fileRefs.isEmpty()) {
            return@withContext plan
        }

        safeState(device.ipAddress)
        for (pid in plan.presetIds) {
            deletePresetSlot(device.ipAddress, pid)
            delay(200)
        }
        for (path in plan.fileRefs) {
            deleteDeviceFile(device.ipAddress, path)
            delay(200)
        }
        plan
    }

    private fun buildPresetDeletionPreview(
        device: WledDevice,
        action: PresetDeleteAction,
        presets: org.json.JSONObject
    ): PresetDeletePreview {
        val targetIds = activePresetIds(presets)
            .filter { pid -> isPresetTargetForAction(pid, action) }
            .sorted()
        val targetIdSet = targetIds.toSet()
        val targetRefs = mutableSetOf<String>()
        val remainingRefs = mutableSetOf<String>()

        val keys = presets.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val pid = key.toIntOrNull() ?: continue
            val preset = presets.optJSONObject(key) ?: continue
            if (preset.length() == 0) continue

            val refs = extractPresetFileRefs(preset)
            if (pid in targetIdSet) {
                targetRefs += refs
            } else {
                remainingRefs += refs
            }
        }

        return PresetDeletePreview(
            deviceId = device.id,
            deviceName = device.name,
            deviceIp = device.ipAddress,
            action = action,
            presetIds = targetIds,
            fileRefs = (targetRefs - remainingRefs).sorted(),
            protectedSlots = protectedPresetSlots.sorted()
        )
    }

    private fun activePresetIds(presets: org.json.JSONObject): List<Int> {
        val ids = mutableListOf<Int>()
        val keys = presets.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val pid = key.toIntOrNull() ?: continue
            if (isUsablePresetValue(presets.opt(key))) {
                ids += pid
            }
        }
        return ids
    }

    private fun isUsablePresetValue(value: Any?): Boolean {
        if (value == null || value == org.json.JSONObject.NULL) return false
        return value !is org.json.JSONObject || value.length() > 0
    }

    private fun isPresetTargetForAction(pid: Int, action: PresetDeleteAction): Boolean {
        return when (action) {
            PresetDeleteAction.LOGO_IMAGES -> pid in logoPresetRange
            PresetDeleteAction.TIMECODE_GROUP -> PresetSlots.isTimecodeSlot(pid) || pid == playlistPresetSlot
            PresetDeleteAction.ALL_EXCEPT_SYSTEM -> pid !in protectedPresetSlots && pid !in PresetSlots.RESERVED
        }
    }

    private fun extractPresetFileRefs(preset: org.json.JSONObject): Set<String> {
        val refs = mutableSetOf<String>()
        val segments = preset.optJSONArray("seg") ?: return refs
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONObject(i) ?: continue
            val raw = segment.optString("n", "").trim()
            if (raw.isBlank()) continue
            val path = if (raw.startsWith("/")) raw else "/$raw"
            val lower = path.lowercase()
            if (imageFileExtensions.any { lower.endsWith(it) }) {
                refs += path
            }
        }
        return refs
    }

    private suspend fun safeState(ip: String) {
        if (MockDevice.isMock(ip)) return
        val url = "http://$ip/json/state"
        api.updateState(url, WledStateRequest(ps = 0))
        delay(100)
        api.updateState(url, WledStateRequest(on = false))
        delay(100)
    }

    private suspend fun deletePresetSlot(ip: String, pid: Int) {
        if (MockDevice.isMock(ip)) return
        // Không bao giờ pdel 250 (preset nút bấm) hay 251–255 (firmware giữ riêng).
        if (pid == PresetSlots.BUTTON || pid in PresetSlots.RESERVED) return
        api.updateState("http://$ip/json/state", WledStateRequest(pdel = pid))
    }

    private fun deleteDeviceFile(ip: String, path: String) {
        if (MockDevice.isMock(ip)) return
        val normalized = if (path.startsWith("/")) path else "/$path"
        val encodedPath = java.net.URLEncoder.encode(normalized, "UTF-8")
        val request = okhttp3.Request.Builder()
            .url("http://$ip/edit?func=delete&path=$encodedPath")
            .build()
        presetFileClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw Exception("WLED đang khóa PIN phần /edit, cần mở khóa trước khi xóa file $normalized.")
            }
            if (!response.isSuccessful) {
                throw Exception("Không xóa được file $normalized (HTTP ${response.code})")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // TÌM & DỌN ẢNH .bmp/.gif TRÊN FILESYSTEM (chọn từng file để xóa)
    // ---------------------------------------------------------------------------

    /** Liệt kê file .bmp/.gif trên thiết bị (lớn trước). */
    private suspend fun listDeviceImageFiles(ip: String): List<DeviceImageFile> =
        fetchDeviceFileEntries(ip)
            .filter { val l = it.path.lowercase(); l.endsWith(".bmp") || l.endsWith(".gif") }
            .sortedByDescending { it.sizeBytes }

    /** Liệt kê toàn bộ file trên thiết bị qua GET /edit?list=/ (bỏ thư mục). */
    private suspend fun fetchDeviceFileEntries(ip: String): List<DeviceImageFile> = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext emptyList()
        val request = okhttp3.Request.Builder()
            .url("http://$ip/edit?list=/")
            .build()
        presetFileClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw Exception("WLED đang khóa PIN phần /edit, cần mở khóa trước khi liệt kê file.")
            }
            if (!response.isSuccessful) {
                throw Exception("Không liệt kê được file (HTTP ${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val arr = try {
                org.json.JSONArray(body)
            } catch (e: Exception) {
                throw Exception("Thiết bị không trả về danh sách file hợp lệ (có thể firmware không hỗ trợ /edit?list).")
            }
            val out = mutableListOf<DeviceImageFile>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("type", "file") == "dir") continue
                var name = o.optString("name", "").trim()
                if (name.isBlank()) continue
                if (!name.startsWith("/")) name = "/$name"
                out += DeviceImageFile(path = name, sizeBytes = o.optLong("size", 0L))
            }
            out
        }
    }

    /** Mở dialog dọn ảnh và nạp danh sách file .bmp/.gif của thiết bị. */
    fun openDeviceFileCleanup(device: WledDevice) {
        _fileCleanupState.value = FileCleanupUiState(
            isVisible = true,
            isLoading = true,
            deviceId = device.id,
            deviceName = device.name,
            deviceIp = device.ipAddress
        )
        viewModelScope.launch {
            try {
                val files = listDeviceImageFiles(device.ipAddress)
                _fileCleanupState.value = _fileCleanupState.value.copy(isLoading = false, files = files)
            } catch (e: Exception) {
                _fileCleanupState.value = _fileCleanupState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Lỗi không xác định khi liệt kê file."
                )
            }
        }
    }

    /** Tích/bỏ tích một file. */
    fun toggleFileCleanupSelection(path: String) {
        val state = _fileCleanupState.value
        val newSelected = if (path in state.selected) state.selected - path else state.selected + path
        _fileCleanupState.value = state.copy(selected = newSelected)
    }

    /** Chọn tất cả / bỏ chọn tất cả. */
    fun setAllFileCleanupSelection(select: Boolean) {
        val state = _fileCleanupState.value
        _fileCleanupState.value = state.copy(
            selected = if (select) state.files.map { it.path }.toSet() else emptySet()
        )
    }

    /** Xóa các file đã tích chọn, sau đó nạp lại danh sách + cập nhật thống kê bộ nhớ. */
    fun deleteSelectedDeviceFiles(device: WledDevice) {
        val state = _fileCleanupState.value
        val targets = state.files.filter { it.path in state.selected }
        if (targets.isEmpty() || state.isDeleting) return
        _fileCleanupState.value = state.copy(isDeleting = true, error = null, resultMessage = null)
        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            var firstError: String? = null
            withContext(Dispatchers.IO) {
                for (f in targets) {
                    try {
                        deleteDeviceFile(device.ipAddress, f.path)
                        deleted++
                        delay(150)
                    } catch (e: Exception) {
                        failed++
                        if (firstError == null) firstError = e.message
                    }
                }
            }
            log(
                "Dọn ảnh: xóa $deleted file .bmp/.gif trên ${device.name}" + if (failed > 0) " ($failed lỗi)" else "",
                if (failed > 0) "WARN" else "INFO"
            )
            val message = "Đã xóa $deleted file" + if (failed > 0) ", $failed file lỗi" else "."
            val refreshed = try {
                listDeviceImageFiles(device.ipAddress)
            } catch (e: Exception) {
                _fileCleanupState.value.files.filter { it.path !in targets.map { t -> t.path }.toSet() }
            }
            _fileCleanupState.value = _fileCleanupState.value.copy(
                isDeleting = false,
                files = refreshed,
                selected = emptySet(),
                resultMessage = message,
                error = firstError
            )
            if (device.isOnline) refreshActiveDeviceMemoryAndPresetStats(device)
        }
    }

    fun dismissFileCleanup() {
        _fileCleanupState.value = FileCleanupUiState()
    }

    fun clearFileCleanupResult() {
        _fileCleanupState.value = _fileCleanupState.value.copy(resultMessage = null, error = null)
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        proSubscriptionManager.destroy()
        stopAudio()
    }

    // --- STAGE MODE: SYNCHRONIZED MULTI-DEVICE CONTROL (PARALLEL) ---

    private data class FreezeCommandResult(
        val deviceName: String,
        val ipAddress: String,
        val success: Boolean,
        val error: String? = null
    )

    private data class FreezeCommandSummary(
        val attempted: Int,
        val succeeded: Int,
        val failures: List<FreezeCommandResult>
    )

    private suspend fun setFreezeAllOnlineDevices(frozen: Boolean): FreezeCommandSummary {
        val onlineDevices = devices.value.filter { it.isOnline }
        if (onlineDevices.isEmpty()) {
            return FreezeCommandSummary(attempted = 0, succeeded = 0, failures = emptyList())
        }

        val results = withContext(Dispatchers.IO) {
            onlineDevices.map { device ->
                async {
                    val url = "http://${device.ipAddress}/json/state"
                    if (MockDevice.isMock(device.ipAddress)) {
                        return@async FreezeCommandResult(device.name, device.ipAddress, success = true)
                    }
                    try {
                        val segmentIds = try {
                            api.getState(url)
                                .seg
                                ?.mapNotNull { it.id }
                                ?.distinct()
                                ?.ifEmpty { listOf(0) }
                                ?: listOf(0)
                        } catch (e: Exception) {
                            listOf(0)
                        }
                        val stateReq = WledStateRequest(
                            seg = segmentIds.map { segmentId ->
                                WledSegmentRequest(id = segmentId, frz = frozen)
                            }
                        )
                        api.updateState(url, stateReq)
                        FreezeCommandResult(
                            deviceName = device.name,
                            ipAddress = device.ipAddress,
                            success = true
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "WledViewModel",
                            "Lỗi ${if (frozen) "freeze" else "unfreeze"} thiết bị ${device.ipAddress}",
                            e
                        )
                        FreezeCommandResult(
                            deviceName = device.name,
                            ipAddress = device.ipAddress,
                            success = false,
                            error = e.message
                        )
                    }
                }
            }.awaitAll()
        }

        val failures = results.filterNot { it.success }
        return FreezeCommandSummary(
            attempted = results.size,
            succeeded = results.size - failures.size,
            failures = failures
        )
    }

    private fun logFreezeWarningIfNeeded(summary: FreezeCommandSummary, context: String) {
        if (summary.failures.isEmpty()) return
        val failedDevices = summary.failures
            .take(3)
            .joinToString(", ") { "${it.deviceName} (${it.ipAddress})" }
        val suffix = if (summary.failures.size > 3) ", ..." else ""
        log(
            "$context: chỉ gỡ freeze được ${summary.succeeded}/${summary.attempted} thiết bị. Lỗi: $failedDevices$suffix",
            "WARN"
        )
    }

    fun togglePowerAll(turnOn: Boolean) {
        log("Sân khấu đồng loạt: ${if (turnOn) "BẬT TOÀN BỘ (Màu đỏ)" else "TẮT TOÀN BỘ"}", "WARN")
        viewModelScope.launch {
            playlistFreezeJob?.join()
            val freezeSummary = setFreezeAllOnlineDevices(false)
            logFreezeWarningIfNeeded(freezeSummary, "Chuẩn bị ${if (turnOn) "bật" else "tắt"} toàn bộ LED")
            devices.value.forEach { device ->
                launch {
                    if (turnOn) {
                        repository.turnOnWithSolidRed(device)
                    } else {
                        repository.togglePower(device, false)
                    }
                }
            }
        }
    }

    fun updateColorAll(hexColor: String) {
        viewModelScope.launch {
            devices.value.forEach { device ->
                launch {
                    repository.updateColor(device, hexColor, effectId = 0)
                }
            }
        }
    }

    fun updateBrightnessAll(bri: Int) {
        viewModelScope.launch {
            devices.value.forEach { device ->
                launch {
                    repository.updateBrightness(device, bri)
                }
            }
        }
    }

    private suspend fun runPlaylistAllNow(playlistId: Int) {
        playlistFreezeJob?.join()
        val freezeSummary = setFreezeAllOnlineDevices(false)
        logFreezeWarningIfNeeded(freezeSummary, "Chuẩn bị chạy playlist $playlistId")

        // §5.4: dựng lệnh cho mọi mạch trước, rồi gửi CÙNG 1 THỜI ĐIỂM (trước đây gửi lần lượt
        // làm mạch sau chậm 50–300 ms).
        // Chỉ mạch đang online: mạch mất kết nối không phát được mà còn làm chậm phần báo kết quả.
        val targets = devices.value.filter { it.isOnline }
        if (targets.isEmpty()) return
        val body = "{\"on\":true,\"seg\":[{\"id\":0,\"frz\":false}],\"ps\":$playlistId}"
        val pending = postStateTogether(targets.map { it.ipAddress }, body)

        // Lệnh đã gửi → trả về ngay để nhạc bắt đầu cùng LED; báo cáo kết quả chạy nền.
        viewModelScope.launch(Dispatchers.IO) {
            val results = pending.mapValues { it.value.await() }
            val sentAt = results.values.mapNotNull { it.getOrNull() }
            if (sentAt.size >= 2) {
                val spreadMs = (sentAt.max() - sentAt.min()) / 1_000_000.0
                log(
                    "Phát playlist $playlistId đồng loạt ${sentAt.size} mạch — lệch thời điểm gửi " +
                        String.format(java.util.Locale.US, "%.2f", spreadMs) + " ms",
                    "INFO"
                )
            }
            for (device in targets) {
                val r = results[device.ipAddress] ?: continue
                if (r.isSuccess) {
                    repository.updateDeviceInDb(
                        device.copy(isOn = true, isOnline = true, lastSeenTimestamp = System.currentTimeMillis())
                    )
                } else {
                    android.util.Log.e("WledViewModel", "Error running playlist $playlistId for ${device.ipAddress}", r.exceptionOrNull())
                    if (device.isOnline) log("Không gửi được lệnh Phát tới ${device.name}: ${r.exceptionOrNull()?.message}", "WARN")
                }
            }
        }
    }

    fun runPlaylistAll(playlistId: Int) {
        viewModelScope.launch {
            runPlaylistAllNow(playlistId)
        }
    }

    // --- PLAYLIST TIMELINE & AUTO SHUTDOWN MANAGEMENT ---
    data class AudioHistoryItem(val uriString: String, val name: String, val duration: Int)

    private val _selectedAudioUri = MutableStateFlow<android.net.Uri?>(
        android.net.Uri.parse("android.resource://" + application.packageName + "/" + com.example.R.raw.default_audio)
    )
    val selectedAudioUri: StateFlow<android.net.Uri?> = _selectedAudioUri.asStateFlow()

    private val _selectedAudioName = MutableStateFlow<String>("Giai Điệu Vui Vẻ (Mặc định)")
    val selectedAudioName: StateFlow<String> = _selectedAudioName.asStateFlow()

    private val _audioHistory = MutableStateFlow<List<AudioHistoryItem>>(emptyList())
    val audioHistory: StateFlow<List<AudioHistoryItem>> = _audioHistory.asStateFlow()

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var synthJob: kotlinx.coroutines.Job? = null

    fun loadAudioPreferences() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("wled_audio_prefs", android.content.Context.MODE_PRIVATE)
            
            // 1. Load active audio
            val savedUriStr = prefs.getString("active_audio_uri", "") ?: ""
            val savedName = prefs.getString("active_audio_name", "") ?: ""
            val savedDur = prefs.getInt("active_audio_duration", -1)
            
            if (savedUriStr.isNotEmpty() && savedName.isNotEmpty() && savedDur > 0) {
                try {
                    val uri = android.net.Uri.parse(savedUriStr)
                    _selectedAudioUri.value = uri
                    _selectedAudioName.value = savedName
                    _playlistTotalSeconds.value = savedDur
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Error loading saved active audio", e)
                }
            }

            // 2. Load audio history
            val historyStr = prefs.getString("audio_history_list", "") ?: ""
            if (historyStr.isNotEmpty()) {
                val items = mutableListOf<AudioHistoryItem>()
                val outerDelim = if (historyStr.contains("\n")) "\n" else ";;"
                val itemStrings = historyStr.split(outerDelim)
                
                for (itemStr in itemStrings) {
                    if (itemStr.isEmpty()) continue
                    val innerDelim = if (itemStr.contains("\t")) "\t" else "||"
                    val parts = if (innerDelim == "||") {
                        itemStr.split(kotlin.text.Regex.escape("||"))
                    } else {
                        itemStr.split(innerDelim)
                    }
                    if (parts.size >= 3) {
                        try {
                            val uri = parts[0]
                            val name = parts[1]
                            val dur = parts[2].toIntOrNull() ?: 120
                            items.add(AudioHistoryItem(uri, name, dur))
                        } catch (e: Exception) {
                            android.util.Log.e("WledViewModel", "Error parsing single history item", e)
                        }
                    }
                }
                _audioHistory.value = items.take(3)
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Error loading audio preferences", e)
        }
    }

    private fun saveActiveAudio(uriString: String, name: String, duration: Int) {
        val prefs = getApplication<Application>().getSharedPreferences("wled_audio_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("active_audio_uri", uriString)
            putString("active_audio_name", name)
            putInt("active_audio_duration", duration)
            apply()
        }
    }

    private fun clearActiveAudioPref() {
        val prefs = getApplication<Application>().getSharedPreferences("wled_audio_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("active_audio_uri")
            remove("active_audio_name")
            remove("active_audio_duration")
            apply()
        }
    }

    private fun addAudioToHistory(uriString: String, name: String, duration: Int) {
        if (uriString.isEmpty()) return
        val current = _audioHistory.value.toMutableList()
        current.removeAll { it.uriString == uriString }
        current.add(0, AudioHistoryItem(uriString, name, duration))
        val updated = current.take(3)
        _audioHistory.value = updated

        val prefs = getApplication<Application>().getSharedPreferences("wled_audio_prefs", android.content.Context.MODE_PRIVATE)
        val historyStr = updated.joinToString("\n") { "${it.uriString}\t${it.name}\t${it.duration}" }
        prefs.edit().putString("audio_history_list", historyStr).apply()
    }

    fun setCustomAudio(uri: android.net.Uri?, name: String, durationSeconds: Int) {
        _selectedAudioUri.value = uri
        _selectedAudioName.value = name
        _playlistTotalSeconds.value = durationSeconds
        if (uri != null) {
            val uriStr = uri.toString()
            saveActiveAudio(uriStr, name, durationSeconds)
            addAudioToHistory(uriStr, name, durationSeconds)
        } else {
            clearActiveAudioPref()
        }
        if (_isPlaylistRunning.value) {
            stopAudio()
            playAudio()
        }
    }

    fun clearCustomAudio() {
        _selectedAudioUri.value = android.net.Uri.parse("android.resource://" + getApplication<Application>().packageName + "/" + com.example.R.raw.default_audio)
        _selectedAudioName.value = "Giai Điệu Vui Vẻ (Mặc định)"
        _playlistTotalSeconds.value = 60
        clearActiveAudioPref()
        if (_isPlaylistRunning.value) {
            stopAudio()
            playAudio()
        }
    }

    private fun playAudio() {
        stopAudio()
        val uri = _selectedAudioUri.value
        if (uri == null) {
            startSynthPulseLoop()
        } else {
            try {
                mediaPlayer = android.media.MediaPlayer().apply {
                    val uriString = uri.toString()
                    if (uriString.contains("default_audio") || uri.scheme == "android.resource") {
                        var afd: android.content.res.AssetFileDescriptor? = null
                        try {
                            afd = getApplication<Application>().resources.openRawResourceFd(com.example.R.raw.default_audio)
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            prepare()
                        } catch (inner: Exception) {
                            android.util.Log.e("WledViewModel", "Error playing default_audio via FD, trying direct URI resolver", inner)
                            try {
                                reset()
                                setDataSource(getApplication(), uri)
                                prepare()
                            } catch (fallbackEx: Exception) {
                                android.util.Log.e("WledViewModel", "Error in fallback URI solver", fallbackEx)
                                throw fallbackEx
                            }
                        } finally {
                            try {
                                afd?.close()
                            } catch (closeEx: Exception) {}
                        }
                    } else {
                        setDataSource(getApplication(), uri)
                        prepare()
                    }
                    val currentOffsetMs = (_playlistElapsedSeconds.value * 1000).toInt()
                    seekTo(currentOffsetMs)
                    start()
                }
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Lỗi khởi động nhạc gốc", e)
                startSynthPulseLoop()
            }
        }
    }

    private fun stopAudio() {
        synthJob?.cancel()
        synthJob = null
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
    }

    private fun playSynthTone(frequency: Double, durationMs: Int, volume: Double = 0.3) {
        try {
            val sampleRate = 8000
            val numSamples = (durationMs * sampleRate) / 1000
            val sample = DoubleArray(numSamples)
            val generatedSnd = ByteArray(2 * numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                sample[i] = Math.sin(2.0 * Math.PI * frequency * t) * volume
            }
            var idx = 0
            for (dVal in sample) {
                val valShort = (dVal * 32767).toInt().toShort()
                generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
                generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
            }
            val audioTrack = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                generatedSnd.size,
                android.media.AudioTrack.MODE_STATIC
            )
            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.play()
            viewModelScope.launch(Dispatchers.IO) {
                delay(durationMs.toLong() + 10)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            // Silence
        }
    }

    private fun startSynthPulseLoop() {
        synthJob?.cancel()
        synthJob = viewModelScope.launch(Dispatchers.IO) {
            val notes = doubleArrayOf(130.81, 146.83, 164.81, 196.00, 220.00) // C3, D3, E3, G3, A3
            var beatCount = 0
            while (_isPlaylistRunning.value && _selectedAudioUri.value == null) {
                val baseFreq = notes[beatCount % notes.size]
                if (beatCount % 4 == 0) {
                    playSynthTone(baseFreq, 180, 0.4)
                    playSynthTone(baseFreq * 2.0, 100, 0.2)
                } else if (beatCount % 2 == 1) {
                    playSynthTone(baseFreq * 1.5, 120, 0.3)
                } else {
                    playSynthTone(baseFreq, 120, 0.25)
                }
                beatCount++
                delay(500) // 120 BPM
            }
        }
    }

    private val _devicesTimelines = MutableStateFlow<Map<Int, DevicePlaylistTimeline>>(emptyMap())
    val devicesTimelines: StateFlow<Map<Int, DevicePlaylistTimeline>> = _devicesTimelines.asStateFlow()

    private val _activeStepsMap = MutableStateFlow<Map<Int, ActiveStepDetails>>(emptyMap())
    val activeStepsMap: StateFlow<Map<Int, ActiveStepDetails>> = _activeStepsMap.asStateFlow()

    private val lastSentPresetMap = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    private val _isLoadingTimelines = MutableStateFlow(false)
    val isLoadingTimelines: StateFlow<Boolean> = _isLoadingTimelines.asStateFlow()

    private val _isPlaylistRunning = MutableStateFlow(false)
    val isPlaylistRunning: StateFlow<Boolean> = _isPlaylistRunning.asStateFlow()

    private val _playlistPlaybackState = MutableStateFlow(PlaylistPlaybackState.Idle)
    val playlistPlaybackState: StateFlow<PlaylistPlaybackState> = _playlistPlaybackState.asStateFlow()

    private val _playlistElapsedSeconds = MutableStateFlow(0f)
    val playlistElapsedSeconds: StateFlow<Float> = _playlistElapsedSeconds.asStateFlow()

    private val _playlistTotalSeconds = MutableStateFlow(60) // Default 1 min for cheerful track
    val playlistTotalSeconds: StateFlow<Int> = _playlistTotalSeconds.asStateFlow()

    private val _playlistName = MutableStateFlow("")
    val playlistName: StateFlow<String> = _playlistName.asStateFlow()

    private val _playlistStepsCount = MutableStateFlow(0)
    val playlistStepsCount: StateFlow<Int> = _playlistStepsCount.asStateFlow()

    private val _isTimelineLocked = MutableStateFlow(true) // Locked by default to avoid accidental seeks; user can unlock via the switch.
    val isTimelineLocked: StateFlow<Boolean> = _isTimelineLocked.asStateFlow()

    private val _isChoreographyMode = MutableStateFlow(false) // True when user manually seeks
    val isChoreographyMode: StateFlow<Boolean> = _isChoreographyMode.asStateFlow()

    fun setTimelineLocked(locked: Boolean) {
        _isTimelineLocked.value = locked
    }

    fun setChoreographyMode(active: Boolean) {
        _isChoreographyMode.value = active
    }

    private var playlistTimelineJob: kotlinx.coroutines.Job? = null
    private var playlistFreezeJob: kotlinx.coroutines.Job? = null

    /**
     * Fetch timelines for all online WLED devices and calculate aggregated total duration.
     */
    fun fetchTimelinesForAllDevices(playlistId: Int) {
        viewModelScope.launch {
            _isLoadingTimelines.value = true
            val onlineDevices = devices.value.filter { it.isOnline }
            val newTimelines = mutableMapOf<Int, DevicePlaylistTimeline>()
            
            // Fetch in parallel
            val jobs = onlineDevices.map { device ->
                launch {
                    val timeline = fetchSingleDeviceTimeline(device, playlistId)
                    synchronized(newTimelines) {
                        newTimelines[device.id] = timeline
                    }
                }
            }
            
            // Wait for all jobs to complete or timeout
            jobs.forEach { it.join() }
            
            _devicesTimelines.value = newTimelines
            _isLoadingTimelines.value = false
            
            // Auto calculate maximum duration to set as default total playlist seconds
            val maxDur = newTimelines.values
                .filter { it.isLoaded && it.totalSeconds > 0 }
                .maxOfOrNull { it.totalSeconds }
            if (maxDur != null && maxDur > 0) {
                _playlistTotalSeconds.value = maxDur
            }
        }
    }

    private suspend fun fetchSingleDeviceTimeline(device: WledDevice, playlistId: Int): DevicePlaylistTimeline = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(device.ipAddress)) {
            return@withContext DevicePlaylistTimeline(
                deviceId = device.id,
                deviceName = device.name,
                playlistId = playlistId,
                playlistName = "Mock",
                steps = emptyList(),
                totalSeconds = 0,
                isLoaded = true
            )
        }
        val url = "http://${device.ipAddress}/presets.json"
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DevicePlaylistTimeline(
                        deviceId = device.id,
                        deviceName = device.name,
                        playlistId = playlistId,
                        playlistName = "N/A",
                        steps = emptyList(),
                        totalSeconds = 0,
                        isLoaded = false,
                        error = "HTTP Lỗi ${response.code}"
                    )
                }
                val bodyString = response.body?.string() ?: throw Exception("Empty body")
                
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val mapType = com.squareup.moshi.Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    Any::class.java
                )
                val adapter = moshi.adapter<Map<String, Any>>(mapType)
                val allPresets = adapter.fromJson(bodyString) ?: throw Exception("JSON Error")
                
                val presetData = allPresets[playlistId.toString()] as? Map<*, *>
                if (presetData == null) {
                    return@withContext DevicePlaylistTimeline(
                        deviceId = device.id,
                        deviceName = device.name,
                        playlistId = playlistId,
                        playlistName = "N/A",
                        steps = emptyList(),
                        totalSeconds = 0,
                        isLoaded = false,
                        error = "Không có Playlist ID $playlistId"
                    )
                }
                
                val name = presetData["n"] as? String ?: "Preset $playlistId"
                val playlistMap = presetData["playlist"] as? Map<*, *>
                if (playlistMap == null) {
                    // Falls back to showing as static preset step
                    val singleName = presetData["n"] as? String ?: "Preset Tĩnh $playlistId"
                    return@withContext DevicePlaylistTimeline(
                        deviceId = device.id,
                        deviceName = device.name,
                        playlistId = playlistId,
                        playlistName = singleName,
                        steps = listOf(
                            DevicePlaylistStep(
                                presetId = playlistId,
                                presetName = singleName,
                                durationSeconds = 120f,
                                transitionSeconds = 0f,
                                startSecond = 0f,
                                endSecond = 120f
                            )
                        ),
                        totalSeconds = 120,
                        isLoaded = true
                    )
                }
                
                val ps = playlistMap["ps"] as? List<*> ?: emptyList<Any>()
                val dur = playlistMap["dur"] as? List<*> ?: emptyList<Any>()
                val transition = playlistMap["transition"] as? List<*> ?: emptyList<Any>()
                val repeat = (playlistMap["repeat"] as? Number)?.toInt() ?: 1
                
                val parsedSteps = mutableListOf<DevicePlaylistStep>()
                var currentStart = 0f
                
                for (i in ps.indices) {
                    val pId = (ps[i] as? Number)?.toInt() ?: 0
                    
                    // Lookup custom name from presets map
                    val lookupPreset = allPresets[pId.toString()] as? Map<*, *>
                    val pName = (lookupPreset as? Map<String, Any>)?.get("n") as? String ?: "Hiệu ứng $pId"
                    
                    val dVal = if (i < dur.size) (dur[i] as? Number)?.toFloat() ?: 0f else 0f
                    val tVal = if (i < transition.size) (transition[i] as? Number)?.toFloat() ?: 0f else 0f
                    
                    val dSec = dVal / 10f
                    val tSec = tVal / 10f
                    
                    val stepDuration = if (dSec <= 0f) 5f else dSec // Fallback for invalid duration
                    
                    parsedSteps.add(
                        DevicePlaylistStep(
                            presetId = pId,
                            presetName = pName,
                            durationSeconds = stepDuration,
                            transitionSeconds = tSec,
                            startSecond = currentStart,
                            endSecond = currentStart + stepDuration + tSec
                        )
                    )
                    currentStart += stepDuration + tSec
                }
                
                // Scale if repeated
                val finalSteps = mutableListOf<DevicePlaylistStep>()
                var totalDuration = currentStart.toInt()
                
                if (repeat > 0) {
                    var accumulatedStart = 0f
                    for (r in 0 until repeat) {
                        for (step in parsedSteps) {
                            val duration = step.endSecond - step.startSecond
                            finalSteps.add(
                                step.copy(
                                    startSecond = accumulatedStart,
                                    endSecond = accumulatedStart + duration
                                )
                            )
                            accumulatedStart += duration
                        }
                    }
                    totalDuration = accumulatedStart.toInt()
                } else {
                    finalSteps.addAll(parsedSteps)
                    if (totalDuration <= 0) totalDuration = 120
                }
                
                DevicePlaylistTimeline(
                    deviceId = device.id,
                    deviceName = device.name,
                    playlistId = playlistId,
                    playlistName = name,
                    steps = finalSteps,
                    totalSeconds = totalDuration,
                    isLoaded = true
                )
            }
        } catch (e: Exception) {
            DevicePlaylistTimeline(
                deviceId = device.id,
                deviceName = device.name,
                playlistId = playlistId,
                playlistName = "N/A",
                steps = emptyList(),
                totalSeconds = 0,
                isLoaded = false,
                error = "Lỗi kết nối IP: ${e.message}"
            )
        }
    }

    /**
     * Manually update the estimated total running time
     */
    fun updatePlaylistTotalSeconds(seconds: Int) {
        _playlistTotalSeconds.value = seconds.coerceAtLeast(1)
    }

    private fun updateActiveSteps(playhead: Float) {
        val newMap = mutableMapOf<Int, ActiveStepDetails>()
        devices.value.forEach { device ->
            val timeline = _devicesTimelines.value[device.id]
            if (timeline != null && timeline.isLoaded) {
                val step = timeline.steps.find { playhead >= it.startSecond && playhead < it.endSecond }
                if (step != null) {
                    val totalDuration = step.endSecond - step.startSecond
                    val elapsedInStep = playhead - step.startSecond
                    val remaining = (totalDuration - elapsedInStep).coerceAtLeast(0f)
                    newMap[device.id] = ActiveStepDetails(
                        presetId = step.presetId,
                        presetName = step.presetName,
                        totalDuration = totalDuration,
                        elapsedInStep = elapsedInStep,
                        remainingDuration = remaining
                    )
                }
            }
        }
        _activeStepsMap.value = newMap
    }

    private fun dispatchStepTransitions(currentSec: Float) {
        val onlineDevices = devices.value.filter { it.isOnline }
        onlineDevices.forEach { device ->
            val timeline = _devicesTimelines.value[device.id]
            if (timeline != null && timeline.isLoaded) {
                val step = timeline.steps.find { currentSec >= it.startSecond && currentSec < it.endSecond }
                if (step != null) {
                    val previouslySent = lastSentPresetMap[device.id]
                    if (previouslySent != step.presetId) {
                        lastSentPresetMap[device.id] = step.presetId
                        viewModelScope.launch(Dispatchers.IO) {
                            val url = "http://${device.ipAddress}/json/state"
                            try {
                                val stateReq = WledStateRequest(on = true, ps = step.presetId)
                                api.updateState(url, stateReq)
                                val updated = device.copy(isOn = true, isOnline = true, lastSeenTimestamp = System.currentTimeMillis())
                                repository.updateDeviceInDb(updated)
                            } catch (e: Exception) {
                                android.util.Log.e("WledViewModel", "Lỗi gửi hiệu ứng ${step.presetId} đến ${device.ipAddress}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private var playlistStartTimeMs: Long = 0L
    private var playlistDurationSeconds: Int = 0

    fun seekPlaylistElapsedSeconds(seconds: Float) {
        val coerced = seconds.coerceIn(0f, _playlistTotalSeconds.value.toFloat())
        _playlistElapsedSeconds.value = coerced
        // Activate App-Orchestrated Choreography mode because they manually dragged / seeked the playhead
        _isChoreographyMode.value = true
        lastSentPresetMap.clear()
        updateActiveSteps(coerced)
        if (_isPlaylistRunning.value) {
            playlistStartTimeMs = System.currentTimeMillis() - (coerced * 1000L).toLong()
            try {
                mediaPlayer?.seekTo((coerced * 1000).toInt())
            } catch (e: Exception) {}
        }
    }

    /**
     * Starts the playlist timer with real-time progress.
     * When completed, it sends commands to turn off all connected LED strips.
     */
    fun startPlaylistAllWithTimeline(playlistId: Int, manualDuration: Int? = null, forceRestart: Boolean = false) {
        playlistTimelineJob?.cancel()
        playlistTimelineJob = null

        val currentPosition = if (forceRestart) 0f else _playlistElapsedSeconds.value
        val totalSecs = _playlistTotalSeconds.value.toFloat()
        val startFrom = if (forceRestart || currentPosition >= totalSecs - 0.2f) 0f else currentPosition

        // If starting from 0, reset to Native Playlist mode automatically
        if (startFrom == 0f) {
            _isChoreographyMode.value = false
        }

        _isPlaylistRunning.value = true
        _playlistPlaybackState.value = PlaylistPlaybackState.Running
        _playlistElapsedSeconds.value = startFrom
        _playlistName.value = "Playlist $playlistId"
        
        log("Bắt đầu chạy kịch bản đồng bộ từ giây: ${String.format(java.util.Locale.US, "%.1f", startFrom)}s", "INFO")
        
        lastSentPresetMap.clear()
        updateActiveSteps(startFrom)

        playlistTimelineJob = viewModelScope.launch {
            // §5.1: mạch vừa cắm lại có thể đã mất preset — kiểm (và ghi lại nếu được) trước khi phát.
            verifyDevicesBeforePlay(playlistId)

            var duration = manualDuration
            if (duration == null) {
                // Try to detect and parse from active device
                val fetchedDur = fetchPlaylistDurationFromDevice(playlistId)
                if (fetchedDur != null && fetchedDur > 0) {
                    _playlistTotalSeconds.value = fetchedDur
                    duration = fetchedDur
                } else {
                    duration = _playlistTotalSeconds.value
                }
            } else {
                _playlistTotalSeconds.value = duration
            }

            playlistDurationSeconds = duration!!

            // Reset again to force immediate first transition load if in choreography mode
            lastSentPresetMap.clear()

            if (_isChoreographyMode.value) {
                playlistFreezeJob?.join()
                val freezeSummary = setFreezeAllOnlineDevices(false)
                logFreezeWarningIfNeeded(freezeSummary, "Chuẩn bị chạy tiếp bằng Biên đạo chủ động")
            } else {
                // Native mode lets WLED run playlist 249 on-device after any pending freeze is cleared.
                runPlaylistAllNow(playlistId)
            }

            playlistStartTimeMs = System.currentTimeMillis() - (startFrom * 1000L).toLong()

            // Synchronized audio play
            playAudio()

            while (_isPlaylistRunning.value) {
                val elapsedMs = System.currentTimeMillis() - playlistStartTimeMs
                val currentSec = (elapsedMs / 1000f).coerceAtMost(playlistDurationSeconds.toFloat())
                _playlistElapsedSeconds.value = currentSec
                updateActiveSteps(currentSec)
                
                // ONLY dispatch active HTTP commands if we are in "Chế độ Biên Đạo Chủ Động" (Choreography Mode)!
                // Otherwise (Native Mode), the devices run the playlist natively itself.
                if (_isChoreographyMode.value) {
                    dispatchStepTransitions(currentSec)
                }

                if (elapsedMs >= playlistDurationSeconds * 1000L) {
                    _playlistElapsedSeconds.value = playlistDurationSeconds.toFloat()
                    updateActiveSteps(playlistDurationSeconds.toFloat())
                    break
                }
                delay(50) // update playhead smoothly with 1/20th of a second checks
            }

            // Playlist completed! Turn OFF all LEDs and stop audio
            if (_isPlaylistRunning.value) {
                log("Đã hoàn thành kịch bản đồng bộ.", "INFO")
                _playlistElapsedSeconds.value = _playlistTotalSeconds.value.toFloat()
                _isPlaylistRunning.value = false
                _playlistPlaybackState.value = PlaylistPlaybackState.Completed
                stopAudio()
                togglePowerAll(false)
            }
        }
    }

    fun stopPlaylistTimeline() {
        playlistTimelineJob?.cancel()
        playlistTimelineJob = null
        _isPlaylistRunning.value = false
        _playlistElapsedSeconds.value = 0f
        _playlistPlaybackState.value = PlaylistPlaybackState.Idle
        _isChoreographyMode.value = false // Reset back to default WLED native playlist mode
        lastSentPresetMap.clear()
        updateActiveSteps(0f)
        stopAudio()
        togglePowerAll(false)
        log("Đã dừng kịch bản liên hoàn và tắt toàn bộ LED.", "WARN")
    }

    fun pausePlaylistTimeline() {
        if (!_isPlaylistRunning.value) return

        playlistTimelineJob?.cancel()
        playlistTimelineJob = null
        _isPlaylistRunning.value = false
        _playlistPlaybackState.value = PlaylistPlaybackState.Paused
        stopAudio()
        updateActiveSteps(_playlistElapsedSeconds.value)
        
        playlistFreezeJob?.cancel()
        playlistFreezeJob = viewModelScope.launch {
            val summary = setFreezeAllOnlineDevices(true)
            when {
                summary.attempted == 0 -> {
                    log("Đã tạm dừng kịch bản, nhưng hiện không có thiết bị online để freeze.", "WARN")
                }
                summary.failures.isEmpty() -> {
                    log("Đã tạm dừng kịch bản liên hoàn và freeze ${summary.succeeded}/${summary.attempted} thiết bị.", "INFO")
                }
                else -> {
                    val failedDevices = summary.failures
                        .take(3)
                        .joinToString(", ") { "${it.deviceName} (${it.ipAddress})" }
                    val suffix = if (summary.failures.size > 3) ", ..." else ""
                    log(
                        "Đã tạm dừng timer, nhưng freeze chỉ thành công ${summary.succeeded}/${summary.attempted} thiết bị. Lỗi: $failedDevices$suffix",
                        "WARN"
                    )
                }
            }
        }
    }

    fun resumePlaylistTimeline() {
        if (_playlistPlaybackState.value != PlaylistPlaybackState.Paused) {
            startPlaylistAllWithTimeline(playlistPresetSlot)
            return
        }

        _isChoreographyMode.value = true
        startPlaylistAllWithTimeline(playlistPresetSlot)
    }

    /**
     * Fetch WLED devices' presets.json to parse step durations and transitions
     */
    private suspend fun fetchPlaylistDurationFromDevice(playlistId: Int): Int? = withContext(Dispatchers.IO) {
        val onlineDevice = devices.value.find { it.isOnline && !MockDevice.isMock(it.ipAddress) } ?: return@withContext null
        val url = "http://${onlineDevice.ipAddress}/presets.json"
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val mapType = com.squareup.moshi.Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    Any::class.java
                )
                val adapter = moshi.adapter<Map<String, Any>>(mapType)
                val allPresets = adapter.fromJson(bodyString) ?: return@withContext null
                
                val presetData = allPresets[playlistId.toString()] as? Map<*, *> ?: return@withContext null
                
                val name = presetData["n"] as? String ?: "Preset $playlistId"
                _playlistName.value = name

                val playlistMap = presetData["playlist"] as? Map<*, *> ?: return@withContext null
                
                val ps = playlistMap["ps"] as? List<*>
                val dur = playlistMap["dur"] as? List<*>
                val transition = playlistMap["transition"] as? List<*>
                val repeat = (playlistMap["repeat"] as? Number)?.toInt() ?: 1
                
                _playlistStepsCount.value = ps?.size ?: 0
                
                if (dur != null) {
                    var totalTenths = 0
                    for (i in dur.indices) {
                        val d = (dur[i] as? Number)?.toInt() ?: 0
                        val t = if (transition != null && i < transition.size) {
                            (transition[i] as? Number)?.toInt() ?: 0
                        } else {
                            0
                        }
                        totalTenths += (d + t)
                    }
                    
                    var totalSecs = (totalTenths + 5) / 10
                    if (totalSecs <= 0) totalSecs = 120
                    
                    val calculatedDuration = if (repeat > 0) totalSecs * repeat else totalSecs
                    return@withContext calculatedDuration
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Could not fetch or parse presets.json from ${onlineDevice.ipAddress}", e)
        }
        null
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(12000) // Poll every 12 seconds
                devices.value.forEach { device ->
                    launch {
                        repository.pingDevice(device)
                    }
                }
                // Refresh local details of active device
                _selectedDevice.value?.let { current ->
                    val updated = repository.getDeviceById(current.id)
                    if (updated != null) {
                        _selectedDevice.value = updated
                    }
                }
            }
        }
    }

    // ============================================================
    // TIMECODE IMPORT & UPLOAD — file desktop v3.17+: track mock + mạch thật, recipe, MAC.
    // Spec: docs/timecode-json-android-plugin.md + docs/android-timecode-recipe-task.md
    // ============================================================

    // 249 is a fixed playlist slot, but user-requested preset cleanup is allowed to delete it.
    private val systemPresetSlots = PresetSlots.SYSTEM
    private val protectedPresetSlots = setOf(PresetSlots.AUTOSAVE, PresetSlots.OFF, PresetSlots.BUTTON)
    private val playlistPresetSlot = PresetSlots.PLAYLIST
    private val logoPresetRange = PresetSlots.LOGO_RANGE
    private val imageFileExtensions = setOf(".gif", ".bmp", ".png", ".jpg", ".jpeg")
    private val presetFileClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val playClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    /** Gửi JSON dạng UTF-8 thật (§5.5) — firmware lưu nguyên `\uXXXX` nếu gửi escape. */
    private val jsonUtf8 = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val _timecodeImportState = MutableStateFlow(TimecodeImportUiState())
    val timecodeImportState: StateFlow<TimecodeImportUiState> = _timecodeImportState.asStateFlow()

    // Giữ file đã parse giữa "mở dialog" và "xác nhận gán".
    private var pendingTimecodeFile: TimecodeFile? = null
    private var pendingTimecodeRaw: String? = null

    /** IP đã đối chiếu presets.json kể từ lần online gần nhất (§5.1). */
    private val verifiedIps: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val lastMacByIp = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        // Mạch mất kết nối, hoặc IP giờ trả lời bằng MAC khác → đối chiếu lại trước lần Phát sau
        // (mạch có thể đã nạp lại firmware / xoá bộ nhớ / IP thuộc mạch khác).
        viewModelScope.launch {
            repository.allDevices.collect { list ->
                for (d in list) {
                    if (!d.isOnline) {
                        verifiedIps.remove(d.ipAddress)
                        continue
                    }
                    val mac = d.mac ?: continue
                    val prev = lastMacByIp.put(d.ipAddress, mac)
                    if (prev != null && prev != mac) verifiedIps.remove(d.ipAddress)
                }
            }
        }
    }

    /**
     * Bước 1: giải mã + parse file, tự nhận track mạch thật theo MAC/IP rồi mở dialog gán.
     * Chỉ đọc mạng để lấy MAC của mạch đã lưu chưa biết MAC.
     */
    fun prepareTimecodeImport(jsonString: String) {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.Default) { TimecodeCrypto.decryptTimecode(jsonString) }
                val version = config.optInt("version", 1)
                if (version > 1) {
                    _timecodeImportState.value = TimecodeImportUiState(
                        resultMessage = "File timecode phiên bản $version chưa được hỗ trợ (app hỗ trợ tối đa v1)."
                    )
                    return@launch
                }
                val file = TimecodeFile.parse(config)
                val tracks = file.tracks.filter { it.clips.isNotEmpty() }
                if (tracks.isEmpty()) {
                    pendingTimecodeFile = null
                    pendingTimecodeRaw = null
                    _timecodeImportState.value = TimecodeImportUiState(
                        resultMessage = "File timecode không có track nào chứa clip."
                    )
                    return@launch
                }
                val registered = refreshDeviceMacs()
                val matches = TrackMatcher.autoMatch(
                    tracks,
                    registered.map { TrackMatcher.Candidate(it.ipAddress, it.mac) }
                )
                pendingTimecodeFile = file
                pendingTimecodeRaw = jsonString
                _timecodeImportState.value = TimecodeImportUiState(
                    showDialog = true,
                    tracks = tracks.map { buildTrackItem(it, matches[it.index]) },
                    audioFileName = file.audioFileName
                )
            } catch (e: TimecodeCryptoException) {
                android.util.Log.e("WledViewModel", "Error decrypting timecode", e)
                pendingTimecodeFile = null
                pendingTimecodeRaw = null
                _timecodeImportState.value = TimecodeImportUiState(
                    resultMessage = "File timecode không đúng định dạng HSL đã mã hóa hoặc đã bị chỉnh sửa.\n${e.message}"
                )
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Error parsing timecode", e)
                pendingTimecodeFile = null
                pendingTimecodeRaw = null
                _timecodeImportState.value = TimecodeImportUiState(
                    resultMessage = "Lỗi đọc file timecode: ${e.message}"
                )
            }
        }
    }

    /** Đọc MAC cho mạch online đã lưu nhưng chưa biết MAC (bản cũ chưa lưu cột này), tối đa ~2.5 s. */
    private suspend fun refreshDeviceMacs(): List<WledDevice> = withContext(Dispatchers.IO) {
        fun valid(list: List<WledDevice>) = list.filter { it.product == null || WledDevice.isValidProduct(it.product) }
        val all = valid(repository.allDevices.first())
        val stale = all.filter { it.mac == null && it.isOnline && !MockDevice.isMock(it.ipAddress) }
        if (stale.isNotEmpty()) {
            kotlinx.coroutines.withTimeoutOrNull(2500) {
                stale.map { d -> async { runCatching { repository.pingDevice(d) } } }.awaitAll()
            }
        }
        valid(repository.allDevices.first())
    }

    private fun buildTrackItem(t: TimelineTrack, m: TrackMatcher.Match?): TimecodeTrackItem {
        val note = when {
            t.isMock -> null
            m == null -> null
            m.kind == TrackMatcher.Kind.MAC -> "Đúng mạch (khớp MAC)"
            m.kind == TrackMatcher.Kind.MAC_NEW_IP -> "Mạch đã đổi IP ${t.ip} → ${m.ip} (khớp MAC)"
            m.kind == TrackMatcher.Kind.IP ->
                if (t.mac.isEmpty()) "Nhận theo IP (file không có MAC)" else "Nhận theo IP (chưa đọc được MAC của mạch)"
            m.kind == TrackMatcher.Kind.IP_OTHER_DEVICE -> "IP ${t.ip} giờ là mạch khác — hãy chọn mạch"
            else -> "Chưa thấy mạch này trong danh sách — hãy chọn mạch"
        }
        return TimecodeTrackItem(
            key = t.index,
            name = t.name,
            sourceIp = t.ip,
            isMock = t.isMock,
            mac = t.mac,
            clipCount = t.clips.size,
            recipeCount = t.recipeCount,
            pendingBakeCount = t.pendingBakeCount,
            autoIp = m?.ip,
            matchNote = note
        )
    }

    /** Dismiss the mapping dialog without uploading. */
    fun cancelTimecodeImport() {
        pendingTimecodeFile = null
        pendingTimecodeRaw = null
        _timecodeImportState.value = TimecodeImportUiState()
    }

    /** Clear any leftover result banner / close the result dialog. */
    fun clearTimecodeResult() {
        _timecodeImportState.value = _timecodeImportState.value.copy(
            resultMessage = null,
            showResult = false,
            results = emptyList()
        )
    }

    /**
     * Bước 2: với mỗi cặp (track → IP mạch thật) đối chiếu / ghi preset, biên dịch playlist 249
     * và upload presets.json 1 lần (đọc lại để xác nhận). Các mạch chạy song song.
     * @param mapping chỉ số track trong file → IP mạch thật.
     */
    fun confirmTimecodeMapping(mapping: Map<Int, String>) {
        val file = pendingTimecodeFile
        if (file == null) {
            _timecodeImportState.value = TimecodeImportUiState(resultMessage = "Không có dữ liệu timecode để nạp.")
            return
        }
        val valid = mapping.filterValues { it.isNotBlank() }
        if (valid.isEmpty()) {
            _timecodeImportState.value = _timecodeImportState.value.copy(
                resultMessage = "Vui lòng gán ít nhất một track với thiết bị thật."
            )
            return
        }
        if (valid.values.toSet().size != valid.size) {
            _timecodeImportState.value = _timecodeImportState.value.copy(
                resultMessage = "Mỗi mạch chỉ nhận một track."
            )
            return
        }
        val raw = pendingTimecodeRaw
        _timecodeImportState.value = _timecodeImportState.value.copy(
            isProcessing = true,
            resultMessage = null,
            progressNote = "Đang đối chiếu preset trên ${valid.size} mạch..."
        )

        viewModelScope.launch(Dispatchers.IO) {
            val byIndex = file.tracks.associateBy { it.index }
            // Mọi mạch cùng độ dài timeline (OFF đệm cuối cho track ngắn hơn).
            val timelineSeconds = valid.keys.mapNotNull { byIndex[it]?.endSeconds }.maxOrNull() ?: 0.0
            val known = repository.allDevices.first()
            val done = java.util.concurrent.atomic.AtomicInteger(0)

            val results = valid.map { (idx, ip) ->
                async {
                    val track = byIndex.getValue(idx)
                    val name = known.find { it.ipAddress == ip }?.name ?: ip
                    val r = importTrackToDevice(file, track, ip, name, timelineSeconds)
                    val n = done.incrementAndGet()
                    _timecodeImportState.update { it.copy(progressNote = "Đã xử lý $n/${valid.size} mạch...") }
                    r
                }
            }.awaitAll()

            val ok = results.filter { it.success }
            if (raw != null && ok.isNotEmpty()) {
                saveLastImport(raw, ok, timelineSeconds)
            }
            val audioStatus = if (ok.isNotEmpty()) {
                withContext(Dispatchers.Main) { matchTimecodeAudio(file.audioFileName) }
            } else TimecodeAudioStatus.NONE

            pendingTimecodeFile = null
            pendingTimecodeRaw = null
            _timecodeImportState.value = TimecodeImportUiState(
                showDialog = false,
                isProcessing = false,
                results = results,
                showResult = true,
                totalSeconds = timelineSeconds,
                audioFileName = file.audioFileName,
                audioStatus = audioStatus
            )

            if (ok.isNotEmpty()) {
                delay(500)
                fetchTimelinesForAllDevices(playlistPresetSlot)
                refreshOnlineDevicePresetStats()
                log("Timecode: đã tự động reload timeline biên đạo playlist $playlistPresetSlot sau khi import.", "INFO")
            }
        }
    }

    /**
     * Đối chiếu + ghi 1 track lên 1 mạch (§4.4, §4.5, §5.2, §5.3). Không ghi gì nếu có clip không
     * phát đúng được (thiếu preset / thiếu ảnh) — báo rõ từng clip.
     */
    private suspend fun importTrackToDevice(
        file: TimecodeFile,
        track: TimelineTrack,
        ip: String,
        deviceName: String,
        timelineSeconds: Double
    ): TimecodeUploadResult = withContext(Dispatchers.IO) {
        var deviceMac = ""
        try {
            val identity = try {
                fetchDeviceIdentity(ip)
            } catch (e: java.io.IOException) {
                throw Exception("Không kết nối được mạch ($ip). Kiểm tra nguồn và WiFi.")
            }
            deviceMac = identity.mac
            val pmax = identity.pmax
            val sameDevice = !track.isMock && if (track.mac.isNotEmpty() && identity.mac.isNotEmpty()) {
                identity.mac == track.mac
            } else {
                ip == track.ip
            }

            val presetsText = fetchPresetsText(ip)
            val presets = parsePresetsText(presetsText)
            var plan = TimecodePlanner.plan(track.clips, file.recipeTable, file.recipeCache, presets, pmax, sameDevice, null)
            if (plan.needsDeviceFiles) {
                val files = listDeviceFilePaths(ip)
                plan = TimecodePlanner.plan(track.clips, file.recipeTable, file.recipeCache, presets, pmax, sameDevice, files)
            }
            if (plan.problems.isNotEmpty()) {
                throw Exception("Không nạp để tránh phát sai:\n• " + plan.problems.joinToString("\n• "))
            }
            if (plan.clips.all { it.action == TimecodePlanner.Action.OFF }) {
                throw Exception("Track không có clip hợp lệ")
            }

            // Ảnh trước, presets.json sau (preset tham chiếu ảnh qua seg[].n).
            for ((path, bytes) in plan.uploads) uploadImageWithRetry(ip, path, bytes)

            backupPresets(identity.mac.ifEmpty { ip }, presetsText, ip)
            val applied = TimecodePlanner.apply(plan, presets, pmax, timelineSeconds, file.loop)
            val missing = TimecodePlanner.missingPlaylistRefs(presets, applied.ps, pmax)
            if (missing.isNotEmpty()) {
                throw Exception("Playlist gọi preset không có trên mạch: ${missing.joinToString()}")
            }
            uploadPresetsVerified(ip, presets, applied.writtenSlots)
            verifiedIps += ip

            val baked = plan.clips.filter { it.action == TimecodePlanner.Action.BAKE }
            val details = buildList {
                val matched = plan.count(TimecodePlanner.Action.OK) + plan.count(TimecodePlanner.Action.EXISTING)
                if (matched > 0) add("$matched clip khớp preset sẵn có")
                val repointed = plan.count(TimecodePlanner.Action.REPOINTED)
                if (repointed > 0) add("$repointed clip dùng lại preset giống hệt ở slot khác")
                if (baked.isNotEmpty()) {
                    val slots = baked.mapNotNull { it.slot }.distinct()
                    val lost = baked.count { it.note != null }
                    add(
                        "${slots.size} preset ghi mới (slot ${compactRanges(slots)})" +
                            if (lost > 0) " — $lost clip bị mất preset đã được ghi lại" else ""
                    )
                }
                if (plan.uploads.isNotEmpty()) add("${plan.uploads.size} ảnh tải lên")
                if (pmax >= PresetSlots.EXT_START) add("Mạch hỗ trợ ID preset tới $pmax")
            }
            log(
                "Timecode: nạp ${applied.clipCount} clip \"${track.name}\" → $deviceName ($ip). " + details.joinToString("; "),
                "INFO"
            )
            plan.warnings.forEach { log("Timecode $deviceName: $it", "WARN") }
            TimecodeUploadResult(
                trackKey = track.index,
                trackName = track.name,
                deviceName = deviceName,
                deviceIp = ip,
                deviceMac = deviceMac,
                clipCount = applied.clipCount,
                success = true,
                details = details,
                warnings = plan.warnings
            )
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Timecode upload failed for $ip", e)
            log("Timecode: lỗi nạp \"${track.name}\" vào $deviceName ($ip) - ${e.message}", "ERROR")
            TimecodeUploadResult(
                trackKey = track.index,
                trackName = track.name,
                deviceName = deviceName,
                deviceIp = ip,
                deviceMac = deviceMac,
                clipCount = 0,
                success = false,
                error = e.message ?: "Lỗi không xác định"
            )
        }
    }

    /** "60–63, 70, 256" từ danh sách slot. */
    private fun compactRanges(slots: List<Int>): String {
        val sorted = slots.sorted()
        val parts = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            parts += if (j > i) "${sorted[i]}–${sorted[j]}" else "${sorted[i]}"
            i = j + 1
        }
        return parts.joinToString(", ")
    }

    /** Nhạc của timeline: tự chọn lại nếu file cùng tên có trong lịch sử nhạc (§4.7). */
    private fun matchTimecodeAudio(fileName: String?): TimecodeAudioStatus {
        if (fileName.isNullOrBlank()) return TimecodeAudioStatus.NONE
        if (_selectedAudioName.value.equals(fileName, ignoreCase = true)) return TimecodeAudioStatus.ALREADY_SELECTED
        val hit = _audioHistory.value.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
            ?: return TimecodeAudioStatus.MISSING
        return try {
            setCustomAudio(android.net.Uri.parse(hit.uriString), hit.name, hit.duration)
            log("Timecode: tự chọn nhạc \"${hit.name}\" theo file timeline.", "INFO")
            TimecodeAudioStatus.AUTO_SELECTED
        } catch (e: Exception) {
            TimecodeAudioStatus.MISSING
        }
    }

    // ---------------------------------------------------------------------------
    // Lần nạp timecode gần nhất — để ghi lại preset khi mạch bị xoá / nạp lại firmware (§5.1)
    // Lưu nguyên file đã MÃ HOÁ (không lưu bản giải mã) + track nào nạp vào mạch nào.
    // ---------------------------------------------------------------------------
    private val lastImportFile: java.io.File
        get() = java.io.File(getApplication<Application>().filesDir, "timecode_last_import.json")
    private val lastImportLock = Any()

    private fun saveLastImport(raw: String, ok: List<TimecodeUploadResult>, seconds: Double) {
        try {
            val assign = org.json.JSONArray()
            ok.forEach {
                assign.put(org.json.JSONObject().put("track", it.trackKey).put("ip", it.deviceIp).put("mac", it.deviceMac))
            }
            val o = org.json.JSONObject().put("raw", raw).put("seconds", seconds).put("assign", assign)
            synchronized(lastImportLock) { lastImportFile.writeText(o.toString(), Charsets.UTF_8) }
        } catch (e: Exception) {
            android.util.Log.w("WledViewModel", "saveLastImport failed", e)
        }
    }

    /** Mạch vừa được ghi 249 từ nguồn khác (tab Biên Tập) → không ghi lại từ file timecode nữa. */
    private fun forgetLastImportFor(ip: String, mac: String?) {
        try {
            synchronized(lastImportLock) {
                if (!lastImportFile.exists()) return
                val o = org.json.JSONObject(lastImportFile.readText(Charsets.UTF_8))
                val arr = o.optJSONArray("assign") ?: return
                val kept = org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    if (!lastImportEntryMatches(a, ip, mac)) kept.put(a)
                }
                o.put("assign", kept)
                lastImportFile.writeText(o.toString(), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            android.util.Log.w("WledViewModel", "forgetLastImportFor failed", e)
        }
    }

    private fun lastImportEntryMatches(a: org.json.JSONObject, ip: String, mac: String?): Boolean {
        val entryMac = normMac(a.optString("mac"))
        val devMac = normMac(mac)
        return if (entryMac.isNotEmpty() && devMac.isNotEmpty()) entryMac == devMac else a.optString("ip") == ip
    }

    /** Ghi lại track của lần nạp gần nhất lên mạch [ip]. Null = mạch không thuộc lần nạp đó. */
    private suspend fun repairFromLastImport(ip: String, mac: String?, deviceName: String): TimecodeUploadResult? =
        withContext(Dispatchers.IO) {
            val stored = try {
                synchronized(lastImportLock) {
                    if (!lastImportFile.exists()) null else org.json.JSONObject(lastImportFile.readText(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                null
            } ?: return@withContext null
            val arr = stored.optJSONArray("assign") ?: return@withContext null
            val entry = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { lastImportEntryMatches(it, ip, mac) } ?: return@withContext null
            val file = try {
                TimecodeFile.parse(TimecodeCrypto.decryptTimecode(stored.optString("raw")))
            } catch (e: Exception) {
                return@withContext null
            }
            val track = file.tracks.firstOrNull { it.index == entry.optInt("track", -1) } ?: return@withContext null
            importTrackToDevice(file, track, ip, deviceName, stored.optDouble("seconds", track.endSeconds))
        }

    // ---------------------------------------------------------------------------
    // Kiểm tra trước khi Phát: 1 lần mỗi lần mạch online (§5.1)
    // ---------------------------------------------------------------------------

    /**
     * Với mỗi mạch online chưa kiểm trong lần kết nối này: playlist [playlistId] phải gọi toàn
     * preset có thật. Thiếu → ghi lại từ lần nạp timecode gần nhất (nếu mạch thuộc lần nạp đó),
     * thiếu mỗi 248 → tự ghi lại OFF, còn lại → cảnh báo. Đọc lỗi vẫn tính là "đã thử".
     */
    private suspend fun verifyDevicesBeforePlay(playlistId: Int) {
        val targets = devices.value.filter {
            it.isOnline && !MockDevice.isMock(it.ipAddress) && it.ipAddress !in verifiedIps
        }
        if (targets.isEmpty()) return
        val notes = withContext(Dispatchers.IO) {
            targets.map { d -> async { verifyOneDeviceBeforePlay(d, playlistId) } }.awaitAll().filterNotNull()
        }
        if (notes.isNotEmpty()) {
            _timecodeImportState.update { it.copy(resultMessage = notes.joinToString("\n")) }
        }
    }

    private suspend fun verifyOneDeviceBeforePlay(d: WledDevice, playlistId: Int): String? {
        verifiedIps += d.ipAddress
        return try {
            val text = fetchPresetsText(d.ipAddress)
            val presets = parsePresetsText(text)
            val pmax = PresetSlots.pmaxHint(d.pmax, presets)
            val ids = TimecodePlanner.playlistIds(presets, playlistId)
            val missing = if (ids == null) listOf(playlistId) else TimecodePlanner.missingPlaylistRefs(presets, ids, pmax)
            if (missing.isEmpty()) return null

            log("Kiểm tra trước khi phát: ${d.name} thiếu preset ${missing.joinToString()} cho playlist $playlistId.", "WARN")
            val repaired = if (playlistId == playlistPresetSlot) repairFromLastImport(d.ipAddress, d.mac, d.name) else null
            if (repaired != null) {
                if (repaired.success) {
                    _devicesTimelines.value = _devicesTimelines.value + (d.id to fetchSingleDeviceTimeline(d, playlistId))
                    return "♻ ${d.name}: preset bị mất đã được ghi lại từ file timecode."
                }
                return "⚠ ${d.name}: thiếu preset ${missing.joinToString()}, ghi lại thất bại — ${repaired.error}"
            }
            if (missing == listOf(PresetSlots.OFF)) {
                backupPresets(d.mac ?: d.ipAddress, text, d.ipAddress)
                presets.put(PresetSlots.OFF.toString(), TimecodePlanner.timelineOffPreset())
                uploadPresetsVerified(d.ipAddress, presets, listOf(PresetSlots.OFF))
                log("Kiểm tra trước khi phát: đã ghi lại TIMELINE_OFF (248) cho ${d.name}.", "INFO")
                return null
            }
            if (ids == null) "⚠ ${d.name}: chưa có playlist $playlistId — hãy nạp timeline cho mạch này."
            else "⚠ ${d.name}: playlist $playlistId gọi preset không còn trên mạch (${missing.joinToString()}) — hãy nạp lại timeline."
        } catch (e: Exception) {
            log("Kiểm tra trước khi phát: không đọc được preset của ${d.name} - ${e.message}", "WARN")
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Phát đồng loạt: chuẩn bị xong hết rồi mới gửi cùng 1 lúc (§5.4)
    // ---------------------------------------------------------------------------

    /**
     * POST /json/state tới mọi IP cùng 1 thời điểm (chờ ở cổng tối đa 2 s). Trả về NGAY khi cổng
     * mở (lệnh đã được gửi đi) — kết quả từng mạch (thời điểm gửi, ns) nằm trong các Deferred, để
     * người gọi không phải chờ mạch chậm / mất kết nối mới bật nhạc.
     */
    private suspend fun postStateTogether(ips: List<String>, body: String): Map<String, kotlinx.coroutines.Deferred<Result<Long>>> {
        val targets = ips.distinct()
        val ready = java.util.concurrent.CountDownLatch(targets.size)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val jobs = targets.associateWith { ip ->
            viewModelScope.async(Dispatchers.IO) {
                // Dựng sẵn mọi thứ TRƯỚC cổng.
                val request = okhttp3.Request.Builder()
                    .url("http://$ip/json/state")
                    .post(body.toRequestBody(jsonUtf8))
                    .build()
                ready.countDown()
                gate.await()
                runCatching {
                    val sentAt = System.nanoTime()
                    if (!MockDevice.isMock(ip)) {
                        playClient.newCall(request).execute().use { r ->
                            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                        }
                    }
                    sentAt
                }
            }
        }
        withContext(Dispatchers.IO) { ready.await(2, java.util.concurrent.TimeUnit.SECONDS) }
        gate.complete(Unit)
        return jobs
    }

    // ---------------------------------------------------------------------------
    // Đọc / ghi presets.json an toàn
    // ---------------------------------------------------------------------------

    private class DeviceIdentity(val mac: String, val pmax: Int)

    /** GET /json/info → MAC chuẩn hoá + pmax (thiếu = 250, không suy từ vid). */
    private suspend fun fetchDeviceIdentity(ip: String): DeviceIdentity = withContext(Dispatchers.IO) {
        val text = if (MockDevice.isMock(ip)) {
            MockDevice.cannedInfoJson()
        } else {
            val request = okhttp3.Request.Builder().url("http://$ip/json/info").build()
            presetFileClient.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw Exception("Không đọc được /json/info (HTTP ${r.code})")
                r.body?.string().orEmpty()
            }
        }
        val info = org.json.JSONObject(text)
        DeviceIdentity(
            mac = normMac(info.optString("mac")),
            pmax = PresetSlots.pmaxOrLegacy(info.optJSONObject("fs")?.optInt("pmax", 0))
        )
    }

    /** GET /presets.json → text gốc ("{}" nếu mạch chưa có preset). */
    private suspend fun fetchPresetsText(ip: String): String = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext MockDevice.cannedPresetsJson()
        val request = okhttp3.Request.Builder().url("http://$ip/presets.json").build()
        presetFileClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Không đọc được presets.json (HTTP ${response.code})")
            val body = response.body?.string()?.trim()
            if (body.isNullOrEmpty() || body == "null") "{}" else body
        }
    }

    private fun parsePresetsText(text: String): org.json.JSONObject =
        if (text.isBlank() || text == "null") org.json.JSONObject() else org.json.JSONObject(text)

    /** GET /presets.json → JSONObject. Returns an empty object if the device has none. */
    private suspend fun fetchPresetsJsonObject(ip: String): org.json.JSONObject = parsePresetsText(fetchPresetsText(ip))

    /**
     * Sao lưu presets.json VỪA ĐỌC trước khi ghi đè cả file (giữ 10 bản gần nhất mỗi mạch):
     * `files/presets_backup/<mac hoặc ip>/presets_<thời điểm>.json`.
     */
    private fun backupPresets(deviceKey: String, text: String, ip: String) {
        if (MockDevice.isMock(ip)) return
        try {
            val safe = deviceKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dir = java.io.File(getApplication<Application>().filesDir, "presets_backup/$safe")
            dir.mkdirs()
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US).format(java.util.Date())
            java.io.File(dir, "presets_$stamp.json").writeText(text, Charsets.UTF_8)
            dir.listFiles { f -> f.name.startsWith("presets_") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.name }
                ?.drop(10)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("WledViewModel", "backupPresets failed for $deviceKey", e)
        }
    }

    /**
     * Upload presets.json dạng gọn (§5.2) rồi ĐỌC LẠI: mọi slot trong [checkSlots] phải là object
     * khác rỗng (thử 3 lần, cách 0.4 s). Firmware có thể nhận upload mà không lưu → báo lỗi.
     */
    private suspend fun uploadPresetsVerified(ip: String, presets: org.json.JSONObject, checkSlots: Collection<Int>) {
        uploadPresetsJson(ip, PresetJson.presetsJsonText(presets))
        if (MockDevice.isMock(ip) || checkSlots.isEmpty()) return
        var missing: List<Int> = checkSlots.toList()
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(400)
            val back = try { fetchPresetsJsonObject(ip) } catch (e: Exception) { null } ?: continue
            missing = checkSlots.filter { !PresetSlots.isNonEmptyPreset(back.opt(it.toString())) }
            if (missing.isEmpty()) return
        }
        throw Exception("Mạch chưa lưu preset ${missing.joinToString()} sau khi ghi (bộ nhớ đầy hoặc mạch bận) — thử lại.")
    }

    /** Đường dẫn mọi file trên mạch (chữ thường, có "/" đầu) — kiểm ảnh trước khi ghi preset. */
    private suspend fun listDeviceFilePaths(ip: String): Set<String> =
        fetchDeviceFileEntries(ip).map { it.path.lowercase() }.toSet()

    private fun mimeForPath(path: String): String {
        val p = path.lowercase()
        return when {
            p.endsWith(".gif") -> "image/gif"
            p.endsWith(".bmp") -> "image/bmp"
            p.endsWith(".png") -> "image/png"
            p.endsWith(".jpg") || p.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private suspend fun uploadImageWithRetry(ip: String, path: String, bytes: ByteArray) {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            try {
                uploadImageBytes(ip, path, bytes, mimeForPath(path))
                return
            } catch (e: Exception) {
                lastErr = e
                delay(500L * (attempt + 1))
            }
        }
        throw Exception("Tải ảnh $path lên mạch thất bại: ${lastErr?.message}")
    }

    /**
     * Upload raw bytes (BMP/GIF) via multipart POST /upload. Throws on failure so
     * callers (POI/Cờ LED) can retry. Timeout co giãn theo dung lượng cho GIF lớn.
     */
    private suspend fun uploadImageBytes(
        ip: String,
        filename: String,
        bytes: ByteArray,
        mime: String
    ) = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) { delay(120); return@withContext }
        val timeoutSec = (30L + bytes.size / 40000L).coerceIn(30L, 300L)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val path = if (filename.startsWith("/")) filename else "/$filename"
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("data", path, bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .build()
        val request = okhttp3.Request.Builder().url("http://$ip/upload").post(body).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw Exception("WLED đang khóa PIN phần /upload, cần mở khóa trước khi tải ảnh lên.")
            }
            if (!response.isSuccessful) {
                throw Exception("Upload ảnh thất bại (HTTP ${response.code})")
            }
        }
    }

    /** Replace the device's presets.json. Throws on failure so callers can report it. */
    private suspend fun uploadPresetsJson(ip: String, jsonContent: String) {
        if (MockDevice.isMock(ip)) { delay(120); return }
        withContext(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("data", "/presets.json", jsonContent.toRequestBody(jsonUtf8))
                .build()
            val request = okhttp3.Request.Builder().url("http://$ip/upload").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    throw Exception("WLED đang khóa PIN phần /upload, cần mở khóa trước khi ghi preset.")
                }
                if (!response.isSuccessful) {
                    throw Exception("Upload thất bại (HTTP ${response.code})")
                }
                android.util.Log.i("WledViewModel", "Upload successful for $ip")
            }
        }
    }

    // =========================================================================
    // UPLOAD ẢNH → PRESET HÌNH ẢNH (tab "Upload POI & Cờ LED")
    // POI  → BMP 24-bit, hiệu ứng "Poi HSL"   (docs/poi-preset-workflow.md §7)
    // Cờ LED → GIF, fx=53 + WAIT-PERSIST       (docs/poi-preset-workflow.md §11/§19)
    // =========================================================================

    private data class PreparedImage(
        val filename: String,
        val presetName: String,
        val bytes: ByteArray,
        /** Số LED pixel theo chiều ngang (POI = số pixel; Matrix = W). Dùng cho seg start/stop. */
        val segWidth: Int,
        /** Số LED pixel theo chiều dọc (Matrix = H). 0 = segment 1 chiều (POI), không gửi startY/stopY. */
        val segHeight: Int = 0
    )

    /** Bắt đầu upload danh sách ảnh đã chọn tới các mạch online được chọn. */
    fun startImageUpload(
        mode: ImageUploadMode,
        uris: List<android.net.Uri>,
        poiPixels: Int,
        flagWidth: Int,
        flagHeight: Int,
        writeMode: ImageWriteMode,
        targetDeviceIds: Set<Int>
    ) {
        if (_imageUploadState.value.isRunning) return
        val targets = devices.value.filter { it.isOnline && it.id in targetDeviceIds }
        if (uris.isEmpty()) {
            _imageUploadState.value = ImageUploadUiState(error = "Chưa chọn ảnh nào để upload.")
            return
        }
        if (targets.isEmpty()) {
            _imageUploadState.value = ImageUploadUiState(error = "Không có mạch online nào được chọn.")
            return
        }
        if (mode == ImageUploadMode.POI && poiPixels !in PoiImage.POI_MIN_W..PoiImage.POI_MAX_W) {
            _imageUploadState.value = ImageUploadUiState(error = "Số pixel POI phải trong khoảng ${PoiImage.POI_MIN_W}–${PoiImage.POI_MAX_W}.")
            return
        }
        if (mode == ImageUploadMode.FLAG && (flagWidth < 1 || flagHeight < 1)) {
            _imageUploadState.value = ImageUploadUiState(error = "Chiều rộng và chiều cao cờ LED phải ≥ 1.")
            return
        }

        viewModelScope.launch {
            _imageUploadState.value = ImageUploadUiState(isRunning = true, mode = mode, progressNote = "Đang xử lý ảnh…")
            try {
                val (prepared, warnings) = withContext(Dispatchers.Default) {
                    prepareImages(mode, uris, poiPixels, flagWidth, flagHeight)
                }
                if (prepared.isEmpty()) {
                    _imageUploadState.value = ImageUploadUiState(
                        finished = true,
                        mode = mode,
                        warnings = warnings,
                        error = "Không có ảnh hợp lệ để upload."
                    )
                    return@launch
                }
                _imageUploadState.update {
                    it.copy(total = prepared.size * targets.size, warnings = warnings, progressNote = "Đang upload…")
                }

                // Song song ≤ 4 mạch (tuần tự trong từng mạch do ràng buộc presetToSave).
                val results = mutableListOf<ImageUploadDeviceResult>()
                for (chunk in targets.chunked(4)) {
                    val chunkResults = chunk.map { device ->
                        async(Dispatchers.IO) { uploadToDevice(device, mode, prepared, writeMode) }
                    }.awaitAll()
                    results += chunkResults
                }

                _imageUploadState.update {
                    it.copy(isRunning = false, finished = true, results = results, progressNote = "Hoàn tất")
                }
                refreshOnlineDevicePresetStats()
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Image upload failed", e)
                _imageUploadState.update {
                    it.copy(isRunning = false, finished = true, error = e.message ?: "Upload thất bại")
                }
            }
        }
    }

    /** Đóng panel kết quả/ lỗi (không cho đóng khi đang chạy). */
    fun dismissImageUpload() {
        if (!_imageUploadState.value.isRunning) {
            _imageUploadState.value = ImageUploadUiState()
        }
    }

    private fun bumpUploadProgress(by: Int, note: String) {
        _imageUploadState.update {
            it.copy(completed = (it.completed + by).coerceAtMost(it.total), progressNote = note)
        }
    }

    /** Giải mã + xử lý ảnh thành bytes sẵn sàng upload (BMP cho POI, GIF cho Cờ LED). */
    private fun prepareImages(
        mode: ImageUploadMode,
        uris: List<android.net.Uri>,
        poiPixels: Int,
        flagWidth: Int,
        flagHeight: Int
    ): Pair<List<PreparedImage>, List<String>> {
        val ctx = getApplication<Application>()
        val out = mutableListOf<PreparedImage>()
        val warnings = mutableListOf<String>()
        val maxLen = if (mode == ImageUploadMode.POI) 20 else 24
        val (clampW, clampH) = if (mode == ImageUploadMode.FLAG) MatrixImage.clampSize(flagWidth, flagHeight) else (0 to 0)
        if (mode == ImageUploadMode.FLAG && (clampW != flagWidth || clampH != flagHeight)) {
            warnings += "Kích thước cờ vượt ngưỡng an toàn, đã co về ${clampW}×${clampH}."
        }
        for (uri in uris) {
            val display = queryDisplayName(ctx, uri)
            val base = display.substringBeforeLast('.', display)
            val safe = base.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(maxLen).ifEmpty { "Preset" }
            try {
                val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (raw == null) { warnings += "$display: không đọc được file."; continue }
                if (mode == ImageUploadMode.POI) {
                    val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    if (src == null) { warnings += "$display: không phải ảnh hợp lệ."; continue }
                    val processed = PoiImage.rotateResizeWidth(src, poiPixels)
                    val bmp = PoiImage.encodeBmp24(processed)
                    if (!PoiImage.bmpFitsLimit(bmp)) {
                        warnings += "$display: ảnh quá lớn (${bmp.size / 1024}KB ≥ 63KB), hãy giảm số pixel."
                        continue
                    }
                    // seg stop = chiều rộng BMP = số LED pixel POI (docs §6.3).
                    out += PreparedImage("$safe.bmp", safe, bmp, segWidth = processed.width)
                } else {
                    val gif = MatrixImage.buildGif(raw, clampW, clampH)
                    // seg stop = W, stopY = H của ma trận đã resize (docs §11.4).
                    out += PreparedImage("$safe.gif", safe, gif, segWidth = clampW, segHeight = clampH)
                }
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "prepareImages failed for $display", e)
                warnings += "$display: lỗi xử lý (${e.message ?: "không rõ"})."
            }
        }
        return out to warnings
    }

    private fun queryDisplayName(ctx: android.content.Context, uri: android.net.Uri): String {
        var name = "image"
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) {
                    name = c.getString(idx) ?: name
                }
            }
        } catch (e: Exception) { /* fallback */ }
        return name
    }

    private suspend fun uploadToDevice(
        device: WledDevice,
        mode: ImageUploadMode,
        prepared: List<PreparedImage>,
        writeMode: ImageWriteMode
    ): ImageUploadDeviceResult {
        val ip = device.ipAddress
        var ok = 0
        var failed = 0
        var skipped = 0
        try {
            val overwrite = writeMode == ImageWriteMode.OVERWRITE_FROM_1
            if (overwrite) {
                // Xóa preset logo (1–59) + file ảnh không còn dùng chung trước khi ghi lại từ ID 1.
                try {
                    deletePresetGroupOnDevice(device, PresetDeleteAction.LOGO_IMAGES)
                } catch (e: Exception) {
                    android.util.Log.w("WledViewModel", "Xóa logo cũ thất bại trên $ip", e)
                }
            }
            val presets = fetchPresetsJsonObject(ip)
            val slots = computeLogoSlots(presets, prepared.size, overwrite)
            if (slots.isEmpty()) {
                bumpUploadProgress(prepared.size, "${device.name}: hết slot")
                return ImageUploadDeviceResult(device.id, device.name, 0, 0, prepared.size, "Hết slot trống (1–59).")
            }
            val fx = if (mode == ImageUploadMode.POI) getPoiEffectId(ip) else MatrixImage.FX_GIF
            for ((index, img) in prepared.withIndex()) {
                if (index >= slots.size) {
                    skipped++
                    bumpUploadProgress(1, "${device.name}: hết slot (${index + 1}/${prepared.size})")
                    continue
                }
                val slot = slots[index]
                val success = try {
                    if (mode == ImageUploadMode.POI) uploadPoiOne(ip, img, slot, fx)
                    else uploadFlagOne(ip, img, slot)
                    true
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Upload ${img.filename} → $ip slot $slot lỗi", e)
                    false
                }
                if (success) ok++ else failed++
                bumpUploadProgress(1, "${device.name}: ${index + 1}/${prepared.size}")
                delay(150)
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "uploadToDevice $ip lỗi", e)
            val remaining = (prepared.size - ok - failed - skipped).coerceAtLeast(0)
            if (remaining > 0) bumpUploadProgress(remaining, "${device.name}: lỗi")
            return ImageUploadDeviceResult(device.id, device.name, ok, failed, skipped, e.message ?: "Lỗi không xác định")
        }
        return ImageUploadDeviceResult(device.id, device.name, ok, failed, skipped, null)
    }

    /** POI: upload BMP → chờ flash → psave (retry). */
    private suspend fun uploadPoiOne(ip: String, img: PreparedImage, slot: Int, fx: Int) {
        uploadWithRetry(ip, img, "image/bmp")
        delay(200)
        psaveWithRetry(ip, img, slot, fx)
    }

    /** Cờ LED: upload GIF → chờ file → psave → WAIT-PERSIST (poll /presets.json). */
    private suspend fun uploadFlagOne(ip: String, img: PreparedImage, slot: Int) {
        uploadWithRetry(ip, img, "image/gif")
        delay(200)
        // Chờ file xuất hiện (≤5s); nếu chưa thấy vẫn tiếp tục psave.
        val t0 = System.currentTimeMillis()
        while (!fileExistsOnDevice(ip, img.filename)) {
            if (System.currentTimeMillis() - t0 > 5000) break
            delay(150)
        }
        psaveWithRetry(ip, img, slot, MatrixImage.FX_GIF)
        if (waitPresetPersisted(ip, slot, 15000)) return
        // Cờ vẫn chưa commit → cho WLED idle rồi psave lại 1 lần.
        delay(1000)
        psaveWithRetry(ip, img, slot, MatrixImage.FX_GIF)
        if (!waitPresetPersisted(ip, slot, 15000)) {
            throw Exception("Preset $slot không persist sau 2 lần psave")
        }
    }

    private suspend fun uploadWithRetry(ip: String, img: PreparedImage, mime: String) {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            try {
                uploadImageBytes(ip, img.filename, img.bytes, mime)
                return
            } catch (e: Exception) {
                lastErr = e
                delay(500L * (attempt + 1))
            }
        }
        throw lastErr ?: Exception("Upload ${img.filename} thất bại")
    }

    private suspend fun psaveWithRetry(ip: String, img: PreparedImage, slot: Int, fx: Int) {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            try {
                saveImagePreset(ip, img.filename, slot, img.presetName, fx, segWidth = img.segWidth, segHeight = img.segHeight)
                return
            } catch (e: Exception) {
                lastErr = e
                delay(300L * (attempt + 1))
            }
        }
        throw lastErr ?: Exception("Lưu preset slot $slot thất bại")
    }

    /** Tra fx_id của "Poi HSL" qua GET /json; fallback 0 (Solid) nếu không thấy. */
    private suspend fun getPoiEffectId(ip: String): Int = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) {
            return@withContext MockDevice.cannedJsonResponse().effects?.indexOf(PoiImage.POI_EFFECT_NAME)?.takeIf { it >= 0 } ?: 0
        }
        try {
            val resp = api.getCompleteApi("http://$ip/json")
            val idx = resp.effects?.indexOf(PoiImage.POI_EFFECT_NAME) ?: -1
            if (idx >= 0) idx else 0
        } catch (e: Exception) {
            0
        }
    }

    /** psave preset hình ảnh (payload chuẩn — docs §6.3/§11.4). */
    private suspend fun saveImagePreset(
        ip: String,
        filename: String,
        slot: Int,
        presetName: String,
        fx: Int,
        segWidth: Int,
        segHeight: Int = 0,
        bri: Int = 128,
        segBri: Int = 255
    ) = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) { delay(60); return@withContext }
        val path = if (filename.startsWith("/")) filename else "/$filename"
        // start/stop tường minh để ảnh map đúng dải LED, không phụ thuộc bounds sống trên thiết bị.
        val seg = org.json.JSONObject()
            .put("id", 0).put("start", 0).put("stop", segWidth)
        // Matrix (GIF) là segment 2 chiều → thêm startY/stopY (docs §11.4).
        if (segHeight > 0) seg.put("startY", 0).put("stopY", segHeight)
        seg.put("on", true).put("bri", segBri)
            .put("n", path).put("fx", fx).put("ix", 0).put("ml2", 0)
        val payload = org.json.JSONObject()
            .put("on", true).put("bri", bri)
            .put("seg", org.json.JSONArray().put(seg))
            .put("psave", slot).put("n", presetName)
            .put("ib", true).put("sb", true)
        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = okhttp3.Request.Builder().url("http://$ip/json/state").post(body).build()
        presetFileClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw Exception("WLED đang khóa PIN, không lưu được preset.")
            }
            if (!response.isSuccessful) {
                throw Exception("Lưu preset thất bại (HTTP ${response.code})")
            }
        }
    }

    /** Kiểm tra file đã có trên thiết bị chưa (so khớp không phân biệt hoa thường). */
    private suspend fun fileExistsOnDevice(ip: String, filename: String): Boolean {
        if (MockDevice.isMock(ip)) return true
        val target = ("/" + filename.trimStart('/')).lowercase()
        return try {
            listDeviceImageFiles(ip).any { it.path.lowercase() == target }
        } catch (e: Exception) {
            false
        }
    }

    /** WAIT-PERSIST: poll /presets.json tới khi slot thật sự commit (docs §11.6). */
    private suspend fun waitPresetPersisted(ip: String, slot: Int, timeoutMs: Long): Boolean {
        if (MockDevice.isMock(ip)) return true
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val ok = try {
                val v = fetchPresetsJsonObject(ip).optJSONObject(slot.toString())
                v != null && v.length() > 0
            } catch (e: Exception) {
                false
            }
            if (ok) return true
            delay(500)
        }
        return false
    }

    /** Danh sách slot logo (1–59) để ghi [count] preset (docs §20). */
    private fun computeLogoSlots(
        presets: org.json.JSONObject,
        count: Int,
        overwrite: Boolean
    ): List<Int> {
        val candidates = logoPresetRange.filter { it !in systemPresetSlots }
        if (overwrite) return candidates.take(count)
        val used = candidates.filter { (presets.optJSONObject(it.toString())?.length() ?: 0) > 0 }.toSet()
        return candidates.filter { it !in used }.take(count)
    }

    // =========================================================================
    // TAB "BIÊN TẬP TIMELINE" — kéo thả preset vào track, biên dịch → playlist 249
    // =========================================================================

    /** Chọn thiết bị để hiện danh sách preset của nó ở khung trên. */
    fun selectEditorDevice(deviceId: Int) {
        _editorSelectedDeviceId.value = deviceId
        val device = devices.value.find { it.id == deviceId } ?: return
        if (_editorPresets.value[deviceId] == null) fetchEditorPresets(device)
    }

    /** Đọc presets.json → danh sách preset (bỏ slot hệ thống) cho khung kéo thả. */
    fun fetchEditorPresets(device: WledDevice) {
        if (!device.isOnline) return
        viewModelScope.launch {
            val list = try {
                val presets = fetchPresetsJsonObject(device.ipAddress)
                val out = mutableListOf<EditablePreset>()
                val keys = presets.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val pid = key.toIntOrNull() ?: continue
                    if (pid in systemPresetSlots || pid in PresetSlots.RESERVED || pid <= 0) continue
                    val obj = presets.optJSONObject(key) ?: continue
                    if (obj.length() == 0) continue
                    val name = obj.optString("n", "Preset $pid").ifBlank { "Preset $pid" }
                    out += EditablePreset(pid, name)
                }
                out.sortedBy { it.id }
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "fetchEditorPresets failed", e)
                emptyList()
            }
            _editorPresets.value = _editorPresets.value + (device.id to list)
        }
    }

    /** Touch nhanh vào preset → phát thử ngay trên thiết bị để xem trước. */
    fun previewPreset(deviceId: Int, presetId: Int) {
        val device = devices.value.find { it.id == deviceId } ?: return
        if (!device.isOnline) return
        if (MockDevice.isMock(device.ipAddress)) {
            android.util.Log.i("WledViewModel", "[MOCK] preview preset $presetId on ${device.name}")
            return
        }
        viewModelScope.launch {
            try {
                api.updateState(
                    "http://${device.ipAddress}/json/state",
                    WledStateRequest(on = true, ps = presetId)
                )
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "previewPreset failed", e)
            }
        }
    }

    fun addClip(deviceId: Int, presetId: Int, presetName: String, startSec: Float, durationSec: Float = 5f) {
        val existing = _editorClips.value[deviceId] ?: emptyList()
        val dur = durationSec.coerceAtLeast(EDITOR_MIN_CLIP_SEC)
        val clip = TimelineClip(
            id = nextEditorClipId++,
            deviceId = deviceId,
            presetId = presetId,
            presetName = presetName,
            startSec = startSec.coerceAtLeast(0f),
            durationSec = dur
        )
        // Clip mới "chiếm chỗ", đẩy các clip bị chồng sang phải theo dây chuyền.
        updateDeviceClips(deviceId, rippleResolve(existing + clip, clip.id))
    }

    fun moveClip(deviceId: Int, clipId: Long, newStartSec: Float) {
        val list = _editorClips.value[deviceId] ?: return
        val moved = list.map { if (it.id == clipId) it.copy(startSec = newStartSec.coerceAtLeast(0f)) else it }
        updateDeviceClips(deviceId, rippleResolve(moved, clipId))
    }

    fun resizeClip(deviceId: Int, clipId: Long, newDurationSec: Float) {
        val list = _editorClips.value[deviceId] ?: return
        val resized = list.map {
            if (it.id == clipId) it.copy(durationSec = newDurationSec.coerceAtLeast(EDITOR_MIN_CLIP_SEC)) else it
        }
        updateDeviceClips(deviceId, rippleResolve(resized, clipId))
    }

    /** Áp layout (id → startSec, durSec) do UI tính sẵn (đã ripple realtime) rồi persist. */
    fun setClipLayout(deviceId: Int, layout: List<Triple<Long, Float, Float>>) {
        val list = _editorClips.value[deviceId] ?: return
        val byId = layout.associateBy({ it.first }, { it.second to it.third })
        val updated = list.map { c ->
            byId[c.id]?.let { (s, d) ->
                c.copy(startSec = s.coerceAtLeast(0f), durationSec = d.coerceAtLeast(EDITOR_MIN_CLIP_SEC))
            } ?: c
        }
        // rippleResolve là lưới an toàn (layout đã không chồng nên thường idempotent).
        updateDeviceClips(deviceId, rippleResolve(updated, -1L))
    }

    /**
     * Giải chồng lấn kiểu "ripple" như TikTok: clip [anchorId] vừa được kéo/giãn được ƯU TIÊN
     * giữ vị trí; mọi clip bị đè sẽ bị ĐẨY SANG PHẢI nối tiếp (cascade). Kéo qua điểm bắt đầu
     * của clip khác → tự đổi thứ tự. Giữ nguyên khoảng trống ở những chỗ không đè.
     */
    private fun rippleResolve(clips: List<TimelineClip>, anchorId: Long): List<TimelineClip> {
        // Sắp theo startSec; khi trùng start, anchor đứng trước để "giành" chỗ.
        val sorted = clips.sortedWith(
            compareBy({ it.startSec }, { if (it.id == anchorId) 0 else 1 })
        )
        var prevEnd = Float.NEGATIVE_INFINITY
        val out = ArrayList<TimelineClip>(sorted.size)
        for (c in sorted) {
            val start = if (c.startSec < prevEnd) prevEnd else c.startSec
            out += if (start != c.startSec) c.copy(startSec = start) else c
            prevEnd = start + c.durationSec
        }
        return out
    }

    fun removeClip(deviceId: Int, clipId: Long) {
        val list = _editorClips.value[deviceId] ?: return
        updateDeviceClips(deviceId, list.filterNot { it.id == clipId })
    }

    fun clearDeviceClips(deviceId: Int) = updateDeviceClips(deviceId, emptyList())

    fun extendEditorTotalSeconds(by: Int = 30) {
        _editorTotalSeconds.value = (_editorTotalSeconds.value + by).coerceAtMost(3600)
    }

    private fun updateDeviceClips(deviceId: Int, clips: List<TimelineClip>) {
        val sorted = clips.sortedBy { it.startSec }
        _editorClips.value = _editorClips.value + (deviceId to sorted)
        recomputeEditorTotalSeconds()
        persistDeviceClips(deviceId, sorted)
    }

    private fun persistDeviceClips(deviceId: Int, clips: List<TimelineClip>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                timelineClipDao.replaceDeviceClips(deviceId, clips.map {
                    TimelineClipEntity(
                        deviceId = it.deviceId,
                        presetId = it.presetId,
                        presetName = it.presetName,
                        startDeciseconds = Math.round(it.startSec * 10),
                        durationDeciseconds = Math.round(it.durationSec * 10),
                        transitionDeciseconds = Math.round(it.transitionSec * 10)
                    )
                })
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "persistDeviceClips failed", e)
            }
        }
    }

    private fun recomputeEditorTotalSeconds() {
        val maxEnd = _editorClips.value.values.flatten().maxOfOrNull { it.startSec + it.durationSec } ?: 0f
        val rounded = (Math.ceil(maxEnd / 10.0).toInt() * 10).coerceAtLeast(60)
        if (rounded > _editorTotalSeconds.value) _editorTotalSeconds.value = rounded
    }

    /** Ghi timeline đã biên tập của mọi mạch online (có clip) vào slot 249 + upload. */
    fun compileAndUploadEditorTimelines(loop: Boolean, onDone: () -> Unit = {}) {
        if (_editorUploadState.value.isRunning) return
        val total = _editorTotalSeconds.value
        val targets = devices.value.filter { it.isOnline && !(_editorClips.value[it.id].isNullOrEmpty()) }
        if (targets.isEmpty()) {
            _editorUploadState.value = EditorUploadUiState(error = "Chưa có timeline nào để ghi (mạch online + có clip).")
            return
        }
        viewModelScope.launch {
            _editorUploadState.value = EditorUploadUiState(isRunning = true)
            val results = mutableListOf<String>()
            var hadError = false
            for (device in targets) {
                val clips = _editorClips.value[device.id] ?: continue
                try {
                    withContext(Dispatchers.IO) {
                        val text = fetchPresetsText(device.ipAddress)
                        val presets = parsePresetsText(text)
                        val pmax = PresetSlots.pmaxHint(device.pmax, presets)
                        val entries = clips.map {
                            TimecodePlanner.PlaylistEntry(
                                it.presetId, it.startSec.toDouble(), it.durationSec.toDouble(), it.transitionSec.toDouble()
                            )
                        }
                        if (entries.isEmpty()) throw Exception("Không có clip hợp lệ")
                        val compiled = TimecodePlanner.compilePlaylist(entries, total.toDouble(), loop)
                        // §5.3: không ghi playlist gọi preset không còn trên mạch (phần đó sẽ không phát gì).
                        val missing = TimecodePlanner.missingPlaylistRefs(
                            presets, compiled.ps.filter { it != PresetSlots.OFF }, pmax
                        )
                        if (missing.isNotEmpty()) {
                            val names = clips.filter { it.presetId in missing }.map { it.presetName }.distinct()
                            throw Exception(
                                "preset ${missing.joinToString()} không còn trên mạch (${names.joinToString()}) — kéo lại clip từ danh sách preset"
                            )
                        }
                        val written = mutableListOf(PresetSlots.PLAYLIST)
                        if (PresetSlots.OFF in compiled.ps) {
                            presets.put(PresetSlots.OFF.toString(), TimecodePlanner.timelineOffPreset())
                            written += PresetSlots.OFF
                        }
                        presets.put(
                            PresetSlots.PLAYLIST.toString(),
                            TimecodePlanner.playlistPreset(compiled.playlist, "Timeline Edit")
                        )
                        backupPresets(device.mac ?: device.ipAddress, text, device.ipAddress)
                        uploadPresetsVerified(device.ipAddress, presets, written)
                        verifiedIps += device.ipAddress
                        // 249 giờ do tab Biên Tập ghi → không ghi lại từ file timecode cũ nữa.
                        forgetLastImportFor(device.ipAddress, device.mac)
                    }
                    results += "${device.name}: OK (${clips.size} clip)"
                } catch (e: Exception) {
                    hadError = true
                    android.util.Log.e("WledViewModel", "compile editor timeline failed for ${device.ipAddress}", e)
                    results += "${device.name}: lỗi ${e.message ?: "không rõ"}"
                }
            }
            _editorUploadState.value = EditorUploadUiState(
                isRunning = false,
                finished = true,
                results = results,
                error = if (hadError) "Một số mạch ghi không thành công." else null
            )
            fetchTimelinesForAllDevices(249)
            onDone()
        }
    }

    fun dismissEditorUpload() {
        if (!_editorUploadState.value.isRunning) _editorUploadState.value = EditorUploadUiState()
    }
}

data class WledEffect(val id: Int, val name: String, val vietnameseName: String)
data class WledPalette(val id: Int, val name: String, val vietnameseName: String)

/** Chế độ upload ảnh: POI (thanh quay → BMP) hoặc Cờ LED (ma trận → GIF). */
enum class ImageUploadMode { POI, FLAG }

/** Cách ghi slot preset 1–59. */
enum class ImageWriteMode { OVERWRITE_FROM_1, APPEND_EMPTY }

/** Kết quả upload trên một thiết bị. */
data class ImageUploadDeviceResult(
    val deviceId: Int,
    val deviceName: String,
    val ok: Int,
    val failed: Int,
    val skipped: Int,
    val error: String? = null
)

/** Một preset có thể kéo vào timeline (tab Biên Tập). */
data class EditablePreset(val id: Int, val name: String)

/** Clip preset trên timeline biên tập (working state, đơn vị giây). */
data class TimelineClip(
    val id: Long,
    val deviceId: Int,
    val presetId: Int,
    val presetName: String,
    val startSec: Float,
    val durationSec: Float,
    val transitionSec: Float = 0f
)

/** Trạng thái ghi timeline biên tập vào thiết bị. */
data class EditorUploadUiState(
    val isRunning: Boolean = false,
    val finished: Boolean = false,
    val results: List<String> = emptyList(),
    val error: String? = null
)

/** Trạng thái tiến trình upload ảnh → preset cho UI. */
data class ImageUploadUiState(
    val isRunning: Boolean = false,
    val mode: ImageUploadMode = ImageUploadMode.POI,
    val total: Int = 0,
    val completed: Int = 0,
    val progressNote: String = "",
    val warnings: List<String> = emptyList(),
    val results: List<ImageUploadDeviceResult> = emptyList(),
    val finished: Boolean = false,
    val error: String? = null
)

/** Một track (mock hoặc mạch thật) của file timecode, hiện ở dialog gán thiết bị. */
data class TimecodeTrackItem(
    /** Chỉ số track trong file (khoá gán). */
    val key: Int,
    val name: String,
    /** IP mạch lúc lưu file, hoặc mock id ("mock:1"). */
    val sourceIp: String,
    val isMock: Boolean,
    /** MAC trong file ("" nếu không có). */
    val mac: String,
    val clipCount: Int,
    val recipeCount: Int,
    /** Clip chưa ghi xuống mạch lúc lưu file (bake_snapshot). */
    val pendingBakeCount: Int,
    /** IP mạch app tự nhận (theo MAC/IP), null = chưa nhận được. */
    val autoIp: String?,
    val matchNote: String?
)

/** Kết quả nạp 1 track lên 1 mạch, hiện ở dialog kết quả. */
data class TimecodeUploadResult(
    val trackKey: Int,
    val trackName: String,
    val deviceName: String,
    val deviceIp: String,
    val deviceMac: String = "",
    val clipCount: Int,
    val success: Boolean,
    val error: String? = null,
    val details: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/** Nhạc của timeline sau khi nạp (§4.7). */
enum class TimecodeAudioStatus { NONE, ALREADY_SELECTED, AUTO_SELECTED, MISSING }

/** UI state for the timecode import → track-to-device mapping flow. */
data class TimecodeImportUiState(
    val showDialog: Boolean = false,
    val tracks: List<TimecodeTrackItem> = emptyList(),
    val isProcessing: Boolean = false,
    val progressNote: String? = null,
    val resultMessage: String? = null,
    val showResult: Boolean = false,
    val results: List<TimecodeUploadResult> = emptyList(),
    val totalSeconds: Double = 0.0,
    /** Tên file nhạc trong timeline (từ audio_rel / audio_path). */
    val audioFileName: String? = null,
    val audioStatus: TimecodeAudioStatus = TimecodeAudioStatus.NONE
)

enum class PresetDeleteAction {
    LOGO_IMAGES,
    TIMECODE_GROUP,
    ALL_EXCEPT_SYSTEM
}

data class DevicePresetStorageStats(
    val deviceId: Int = 0,
    val logoUsed: Int = 0,
    val logoCapacity: Int = 59,
    val timecodeUsed: Int = 0,
    val timecodeCapacity: Int = 180,
    val systemUsed: Int = 0,
    val systemCapacity: Int = 4,
    val otherUsed: Int = 0,
    val totalPresets: Int = 0,
    /** ID preset lớn nhất mạch chấp nhận (250 = firmware cũ, tới 1024 trên firmware ≥ 2611090). */
    val pmax: Int = 250,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class PresetDeletePreview(
    val deviceId: Int,
    val deviceName: String,
    val deviceIp: String,
    val action: PresetDeleteAction,
    val presetIds: List<Int>,
    val fileRefs: List<String>,
    val protectedSlots: List<Int>
)

/** Một file ảnh (.bmp/.gif) trên filesystem của thiết bị, lấy từ GET /edit?list=/. */
data class DeviceImageFile(
    val path: String,        // đã chuẩn hoá có "/" đầu, ví dụ "/logo.bmp"
    val sizeBytes: Long      // dung lượng file (byte)
)

/** State cho dialog "Tìm & dọn ảnh BMP/GIF" của một thiết bị. */
data class FileCleanupUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val deviceId: Int? = null,
    val deviceName: String? = null,
    val deviceIp: String? = null,
    val files: List<DeviceImageFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val error: String? = null,
    val resultMessage: String? = null
)

data class PresetDeviceDeleteError(
    val deviceName: String,
    val deviceIp: String,
    val message: String
)

/** Một preset trong dialog "Xóa preset + ảnh" (gộp theo ID từ mọi mạch online). */
data class PresetPurgeItem(
    val pid: Int,
    val name: String,
    val group: PresetCleanup.Group,
    /** Ảnh preset này dùng (gộp các mạch). */
    val images: List<String>,
    /** Số mạch đang có preset ở ID này. */
    val deviceCount: Int
)

/** State dialog "Xóa preset + ảnh" của tab Đồng Loạt (ALL). */
data class PresetPurgeUiState(
    val isVisible: Boolean = false,
    val isScanning: Boolean = false,
    val isDeleting: Boolean = false,
    /** Số mạch đọc được preset (đích xoá). */
    val deviceCount: Int = 0,
    /** "3 mạch online" / "mạch ARGB-2X PRO" — hiện trong dialog. */
    val scopeLabel: String = "",
    val deviceIds: List<Int> = emptyList(),
    val items: List<PresetPurgeItem> = emptyList(),
    val orphanCount: Int = 0,
    val orphanBytes: Long = 0L,
    val orphanDevices: Int = 0,
    val scanErrors: List<PresetDeviceDeleteError> = emptyList(),
    val scanWarnings: List<String> = emptyList(),
    val progressNote: String? = null,
    val resultMessage: String? = null,
    val resultLines: List<String> = emptyList(),
    val resultErrors: List<PresetDeviceDeleteError> = emptyList(),
    val error: String? = null
) {
    val isBusy: Boolean get() = isScanning || isDeleting
}

data class DevicePlaylistStep(
    val presetId: Int,
    val presetName: String,
    val durationSeconds: Float,
    val transitionSeconds: Float,
    val startSecond: Float,
    val endSecond: Float
)

data class ActiveStepDetails(
    val presetId: Int,
    val presetName: String,
    val totalDuration: Float,
    val elapsedInStep: Float,
    val remainingDuration: Float
)

data class DevicePlaylistTimeline(
    val deviceId: Int,
    val deviceName: String,
    val playlistId: Int,
    val playlistName: String,
    val steps: List<DevicePlaylistStep>,
    val totalSeconds: Int,
    val isLoaded: Boolean = false,
    val error: String? = null
)
