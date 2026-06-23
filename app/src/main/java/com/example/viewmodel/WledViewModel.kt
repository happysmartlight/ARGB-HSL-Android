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

    private val _presetDeleteState = MutableStateFlow(PresetDeleteUiState())
    val presetDeleteState: StateFlow<PresetDeleteUiState> = _presetDeleteState.asStateFlow()

    private val _bulkPresetDeleteState = MutableStateFlow(PresetBulkDeleteUiState())
    val bulkPresetDeleteState: StateFlow<PresetBulkDeleteUiState> = _bulkPresetDeleteState.asStateFlow()

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
        _presetDeleteState.value = PresetDeleteUiState()
        _bulkPresetDeleteState.value = PresetBulkDeleteUiState()
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

    fun preparePresetDeletion(device: WledDevice, action: PresetDeleteAction) {
        if (!device.isOnline) {
            _presetDeleteState.value = PresetDeleteUiState(error = "Thiết bị ${device.name} đang ngoại tuyến.")
            return
        }
        viewModelScope.launch {
            _presetDeleteState.value = PresetDeleteUiState(isPreparing = true)
            try {
                val presets = fetchPresetsJsonObject(device.ipAddress)
                val preview = buildPresetDeletionPreview(device, action, presets)
                _presetDeleteState.value = PresetDeleteUiState(preview = preview)
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Error preparing preset deletion", e)
                _presetDeleteState.value = PresetDeleteUiState(
                    error = "Không đọc được presets.json từ ${device.name}: ${e.message ?: "lỗi không xác định"}"
                )
            }
        }
    }

    fun confirmPresetDeletion(device: WledDevice, action: PresetDeleteAction) {
        viewModelScope.launch {
            val currentPreview = _presetDeleteState.value.preview
            _presetDeleteState.value = PresetDeleteUiState(isDeleting = true, preview = currentPreview)
            try {
                val result = deletePresetGroupOnDevice(device, action)
                val details = repository.fetchDeviceDetails(device)
                if (_selectedDevice.value?.id == device.id) {
                    _activeDeviceDetails.value = details
                }
                refreshPresetStatsForDevice(device)
                _presetDeleteState.value = PresetDeleteUiState(
                    resultMessage = "Đã xóa ${result.presetIds.size} preset và ${result.fileRefs.size} file ảnh trên ${device.name}."
                )
                log(
                    "Preset: ${presetDeleteActionLogName(action)} trên ${device.name} - xóa ${result.presetIds.size} preset, ${result.fileRefs.size} file ảnh.",
                    "WARN"
                )
            } catch (e: Exception) {
                android.util.Log.e("WledViewModel", "Error deleting preset group", e)
                _presetDeleteState.value = PresetDeleteUiState(
                    error = "Xóa preset thất bại trên ${device.name}: ${e.message ?: "lỗi không xác định"}"
                )
                log("Preset: lỗi xóa trên ${device.name} - ${e.message}", "ERROR")
            }
        }
    }

    fun preparePresetDeletionForOnlineDevices(action: PresetDeleteAction) {
        val onlineDevices = devices.value.filter { it.isOnline }
        if (onlineDevices.isEmpty()) {
            _bulkPresetDeleteState.value = PresetBulkDeleteUiState(error = "Không có thiết bị online để dọn preset.")
            return
        }

        viewModelScope.launch {
            _bulkPresetDeleteState.value = PresetBulkDeleteUiState(isPreparing = true)
            val previews = mutableListOf<PresetDeletePreview>()
            val errors = mutableListOf<PresetDeviceDeleteError>()

            for (device in onlineDevices) {
                try {
                    val presets = fetchPresetsJsonObject(device.ipAddress)
                    previews += buildPresetDeletionPreview(device, action, presets)
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Error preparing bulk preset deletion", e)
                    errors += PresetDeviceDeleteError(
                        deviceName = device.name,
                        deviceIp = device.ipAddress,
                        message = e.message ?: "lỗi không xác định"
                    )
                }
            }

            _bulkPresetDeleteState.value = PresetBulkDeleteUiState(
                preview = PresetBulkDeletePreview(
                    action = action,
                    devicePreviews = previews,
                    errors = errors
                )
            )
        }
    }

    fun confirmPresetDeletionForOnlineDevices(action: PresetDeleteAction) {
        viewModelScope.launch {
            val currentPreview = _bulkPresetDeleteState.value.preview
            _bulkPresetDeleteState.value = PresetBulkDeleteUiState(isDeleting = true, preview = currentPreview)

            val targetPreviews = currentPreview?.devicePreviews.orEmpty()
            if (targetPreviews.isEmpty()) {
                _bulkPresetDeleteState.value = PresetBulkDeleteUiState(error = "Không có thiết bị nào đã sẵn sàng để xóa.")
                return@launch
            }

            var successCount = 0
            var totalPresetDeleted = 0
            var totalFileDeleted = 0
            val errors = currentPreview?.errors?.toMutableList() ?: mutableListOf()

            for (preview in targetPreviews) {
                val device = devices.value.find { it.id == preview.deviceId || it.ipAddress == preview.deviceIp }
                    ?: WledDevice(
                        id = preview.deviceId,
                        name = preview.deviceName,
                        ipAddress = preview.deviceIp,
                        isOnline = true
                    )
                try {
                    val result = deletePresetGroupOnDevice(device, action)
                    successCount++
                    totalPresetDeleted += result.presetIds.size
                    totalFileDeleted += result.fileRefs.size
                    log(
                        "Preset ALL: ${presetDeleteActionLogName(action)} trên ${device.name} - xóa ${result.presetIds.size} preset, ${result.fileRefs.size} file ảnh.",
                        "WARN"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Error deleting bulk preset group", e)
                    errors += PresetDeviceDeleteError(
                        deviceName = device.name,
                        deviceIp = device.ipAddress,
                        message = e.message ?: "lỗi không xác định"
                    )
                    log("Preset ALL: lỗi xóa trên ${device.name} - ${e.message}", "ERROR")
                }
            }

            _selectedDevice.value?.let { selected ->
                val details = repository.fetchDeviceDetails(selected)
                if (_selectedDevice.value?.id == selected.id) {
                    _activeDeviceDetails.value = details
                }
                refreshPresetStatsForDevice(selected)
            }
            refreshOnlineDevicePresetStats()

            val errorText = if (errors.isEmpty()) {
                ""
            } else {
                " Có ${errors.size} thiết bị lỗi."
            }
            _bulkPresetDeleteState.value = PresetBulkDeleteUiState(
                resultMessage = "Đã dọn $successCount/${targetPreviews.size} thiết bị online, xóa $totalPresetDeleted preset và $totalFileDeleted file ảnh.$errorText",
                resultErrors = errors
            )
        }
    }

    fun dismissPresetDeletionDialog() {
        val state = _presetDeleteState.value
        if (!state.isPreparing && !state.isDeleting) {
            _presetDeleteState.value = PresetDeleteUiState()
        }
    }

    fun dismissBulkPresetDeletionDialog() {
        val state = _bulkPresetDeleteState.value
        if (!state.isPreparing && !state.isDeleting) {
            _bulkPresetDeleteState.value = PresetBulkDeleteUiState()
        }
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
        return DevicePresetStorageStats(
            deviceId = deviceId,
            logoUsed = ids.count { it in logoPresetRange },
            logoCapacity = logoPresetRange.count(),
            timecodeUsed = ids.count { it in timecodePresetRange && it !in timecodeAllocationSkipSlots },
            timecodeCapacity = timecodeSlotCapacity,
            systemUsed = ids.count { it in systemPresetSlots },
            systemCapacity = systemPresetSlots.size,
            otherUsed = ids.count { it !in logoPresetRange && it !in timecodePresetRange && it !in systemPresetSlots },
            totalPresets = ids.size
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
            PresetDeleteAction.TIMECODE_GROUP -> (pid in timecodePresetRange && pid !in timecodeAllocationSkipSlots) || pid == playlistPresetSlot
            PresetDeleteAction.ALL_EXCEPT_SYSTEM -> pid !in protectedPresetSlots
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

    /** Liệt kê toàn bộ file trên thiết bị qua GET /edit?list=/ rồi lọc ra .bmp/.gif. */
    private suspend fun listDeviceImageFiles(ip: String): List<DeviceImageFile> = withContext(Dispatchers.IO) {
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
                val lower = name.lowercase()
                if (lower.endsWith(".bmp") || lower.endsWith(".gif")) {
                    out += DeviceImageFile(path = name, sizeBytes = o.optLong("size", 0L))
                }
            }
            out.sortedByDescending { it.sizeBytes }
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

    private fun presetDeleteActionLogName(action: PresetDeleteAction): String {
        return when (action) {
            PresetDeleteAction.LOGO_IMAGES -> "xóa nhóm logo/ảnh"
            PresetDeleteAction.TIMECODE_GROUP -> "xóa nhóm preset timecode"
            PresetDeleteAction.ALL_EXCEPT_SYSTEM -> "xóa tất cả preset trừ hệ thống"
        }
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

        withContext(Dispatchers.IO) {
            devices.value.map { device ->
                async {
                    val url = "http://${device.ipAddress}/json/state"
                    try {
                        val stateReq = WledStateRequest(on = true, ps = playlistId)
                        api.updateState(url, stateReq)
                        // Update local state isOnline and lastSeen
                        val updated = device.copy(isOn = true, isOnline = true, lastSeenTimestamp = System.currentTimeMillis())
                        repository.updateDeviceInDb(updated)
                    } catch (e: Exception) {
                        android.util.Log.e("WledViewModel", "Error running playlist $playlistId for ${device.ipAddress}", e)
                    }
                }
            }.awaitAll()
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
        val onlineDevice = devices.value.find { it.isOnline } ?: return@withContext null
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
    // TIMECODE IMPORT & UPLOAD (mock -> real device mapping flow)
    // ============================================================

    // 249 is a fixed playlist slot, but user-requested preset cleanup is allowed to delete it.
    private val systemPresetSlots = setOf(100, 248, 249, 250)
    private val protectedPresetSlots = setOf(100, 248, 250)
    private val playlistPresetSlot = 249
    private val logoPresetRange = 1..59
    private val timecodePresetRange = 60..240
    private val timecodeAllocationSkipSlots = systemPresetSlots
    private val timecodeSlotCapacity = timecodePresetRange.count { it !in timecodeAllocationSkipSlots }
    private val imageFileExtensions = setOf(".gif", ".bmp", ".png", ".jpg", ".jpeg")
    private val presetFileClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val timecodeSkipSlots = timecodeAllocationSkipSlots
    private val timecodeSlotRange = 60..240
    private val timecodeGapTolerance = 0.05

    private val _timecodeImportState = MutableStateFlow(TimecodeImportUiState())
    val timecodeImportState: StateFlow<TimecodeImportUiState> = _timecodeImportState.asStateFlow()

    // Holds the parsed config between "open dialog" and "confirm mapping".
    private var pendingTimecodeConfig: org.json.JSONObject? = null

    /**
     * Step 1: parse the picked timecode file, extract the mock devices and open the
     * mapping dialog. Does NOT touch the network.
     */
    fun prepareTimecodeImport(jsonString: String) {
        try {
            val config = TimecodeCrypto.decryptTimecode(jsonString)
            val version = config.optInt("version", 1)
            if (version > 1) {
                _timecodeImportState.value = TimecodeImportUiState(
                    resultMessage = "File timecode phiên bản $version chưa được hỗ trợ (app hỗ trợ tối đa v1)."
                )
                return
            }

            // Count clips per mock device id from the tracks array.
            val clipCounts = mutableMapOf<String, Int>()
            val tracks = config.optJSONArray("tracks")
            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(i)
                    val ip = track.optString("ip")
                    if (!ip.startsWith("mock:")) continue
                    val clips = track.optJSONArray("clips")
                    clipCounts[ip] = (clipCounts[ip] ?: 0) + (clips?.length() ?: 0)
                }
            }

            // Build the mock device list. Prefer the explicit mock_devices array,
            // but fall back to any mock track ip we found.
            val mockDevices = mutableListOf<TimecodeMockDevice>()
            val seen = mutableSetOf<String>()
            val mockArray = config.optJSONArray("mock_devices")
            if (mockArray != null) {
                for (i in 0 until mockArray.length()) {
                    val m = mockArray.getJSONObject(i)
                    val id = m.optString("id")
                    if (id.isBlank() || !seen.add(id)) continue
                    mockDevices.add(
                        TimecodeMockDevice(
                            id = id,
                            name = m.optString("name", id),
                            clipCount = clipCounts[id] ?: 0
                        )
                    )
                }
            }
            // Include mock tracks that weren't listed in mock_devices.
            for ((id, count) in clipCounts) {
                if (seen.add(id)) {
                    mockDevices.add(TimecodeMockDevice(id = id, name = id, clipCount = count))
                }
            }

            if (mockDevices.isEmpty()) {
                _timecodeImportState.value = TimecodeImportUiState(
                    resultMessage = "Không tìm thấy thiết bị Mock nào trong file timecode."
                )
                pendingTimecodeConfig = null
                return
            }

            pendingTimecodeConfig = config
            _timecodeImportState.value = TimecodeImportUiState(
                showDialog = true,
                mockDevices = mockDevices
            )
        } catch (e: TimecodeCryptoException) {
            android.util.Log.e("WledViewModel", "Error decrypting timecode", e)
            _timecodeImportState.value = TimecodeImportUiState(
                resultMessage = "File timecode không đúng định dạng HSL đã mã hóa hoặc đã bị chỉnh sửa.\n${e.message}"
            )
            pendingTimecodeConfig = null
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "Error parsing timecode", e)
            _timecodeImportState.value = TimecodeImportUiState(
                resultMessage = "Lỗi đọc file timecode: ${e.message}"
            )
            pendingTimecodeConfig = null
        }
    }

    /** Dismiss the mapping dialog without uploading. */
    fun cancelTimecodeImport() {
        pendingTimecodeConfig = null
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
     * Step 3-6 of the guide: for each (mockId -> real device IP) pair, bake the mock
     * clips onto the real device, compile the playlist into slot 249 and upload once.
     * @param mapping mock device id -> real device IP address.
     */
    fun confirmTimecodeMapping(mapping: Map<String, String>) {
        val config = pendingTimecodeConfig
        if (config == null) {
            _timecodeImportState.value = TimecodeImportUiState(resultMessage = "Không có dữ liệu timecode để nạp.")
            return
        }
        val validMapping = mapping.filterValues { it.isNotBlank() }
        if (validMapping.isEmpty()) {
            _timecodeImportState.value = _timecodeImportState.value.copy(
                resultMessage = "Vui lòng gán ít nhất một thiết bị Mock với thiết bị thật."
            )
            return
        }

        _timecodeImportState.value = _timecodeImportState.value.copy(isProcessing = true, resultMessage = null)

        viewModelScope.launch(Dispatchers.IO) {
            val tracks = config.optJSONArray("tracks") ?: org.json.JSONArray()
            val defaults = config.optJSONObject("defaults")
            val loop = defaults?.optBoolean("loop", false) ?: false

            // Timeline length = furthest clip end across every mapped mock track, so all
            // devices share the same total duration (trailing OFF pads the shorter ones).
            var timelineSeconds = 0.0
            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)
                if (!validMapping.containsKey(track.optString("ip"))) continue
                val clips = track.optJSONArray("clips") ?: continue
                for (j in 0 until clips.length()) {
                    val clip = clips.getJSONObject(j)
                    val end = clip.optDouble("start", 0.0) + clip.optDouble("duration", 0.0)
                    if (end > timelineSeconds) timelineSeconds = end
                }
            }

            val results = mutableListOf<TimecodeUploadResult>()
            for ((mockId, realIp) in validMapping) {
                val mockDevice = _timecodeImportState.value.mockDevices.find { it.id == mockId }
                val mockName = mockDevice?.name ?: mockId
                val deviceName = devices.value.find { it.ipAddress == realIp }?.name ?: realIp
                try {
                    val uploaded = bakeAndUploadForDevice(config, mockId, realIp, timelineSeconds, loop)
                    results.add(
                        TimecodeUploadResult(
                            mockName = mockName,
                            deviceName = deviceName,
                            deviceIp = realIp,
                            clipCount = uploaded,
                            success = true
                        )
                    )
                    log("Timecode: nạp $uploaded clip vào $deviceName ($realIp) từ \"$mockName\" thành công.", "INFO")
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Timecode upload failed for $realIp", e)
                    results.add(
                        TimecodeUploadResult(
                            mockName = mockName,
                            deviceName = deviceName,
                            deviceIp = realIp,
                            clipCount = 0,
                            success = false,
                            error = e.message ?: "Lỗi không xác định"
                        )
                    )
                    log("Timecode: lỗi nạp vào $deviceName ($realIp) - ${e.message}", "ERROR")
                }
            }

            pendingTimecodeConfig = null
            _timecodeImportState.value = TimecodeImportUiState(
                showDialog = false,
                isProcessing = false,
                results = results,
                showResult = true,
                totalSeconds = timelineSeconds
            )

            if (results.any { it.success }) {
                delay(500)
                fetchTimelinesForAllDevices(playlistPresetSlot)
                refreshOnlineDevicePresetStats()
                log("Timecode: đã tự động reload timeline biên đạo playlist $playlistPresetSlot sau khi import.", "INFO")
            }
        }
    }

    /**
     * Read presets.json from [realIp], bake every mock clip with a bake_snapshot into a
     * free slot, compile the playlist into slot 249 and upload the whole presets.json once.
     * @return number of clips written into the playlist.
     */
    private suspend fun bakeAndUploadForDevice(
        config: org.json.JSONObject,
        mockId: String,
        realIp: String,
        timelineSeconds: Double,
        loop: Boolean
    ): Int = withContext(Dispatchers.IO) {
        // Gather and time-sort this mock device's clips.
        val tracks = config.optJSONArray("tracks") ?: org.json.JSONArray()
        val clips = mutableListOf<org.json.JSONObject>()
        for (i in 0 until tracks.length()) {
            val track = tracks.getJSONObject(i)
            if (track.optString("ip") != mockId) continue
            val cs = track.optJSONArray("clips") ?: continue
            for (j in 0 until cs.length()) clips.add(cs.getJSONObject(j))
        }
        clips.sortBy { it.optDouble("start", 0.0) }

        // Read the device's existing presets so we don't clobber the user's slots.
        val presets = fetchPresetsJsonObject(realIp)

        // Upload binary file dependencies first (deduped by path).
        val uploadedFiles = mutableSetOf<String>()
        for (clip in clips) {
            val snapshot = clip.optJSONObject("bake_snapshot") ?: continue
            val files = snapshot.optJSONArray("files") ?: continue
            for (k in 0 until files.length()) {
                val file = files.getJSONObject(k)
                val path = file.optString("path")
                val b64 = file.optString("content_b64", "")
                if (path.isBlank() || b64.isBlank() || !uploadedFiles.add(path)) continue
                uploadBinaryFile(realIp, path, b64)
            }
        }

        // Bake clips + build playlist entries in timeline order.
        val psArray = org.json.JSONArray()
        val durArray = org.json.JSONArray()
        val transArray = org.json.JSONArray()
        var lastEnd = 0.0
        var needsOff = false
        var clipCount = 0

        for (clip in clips) {
            val start = clip.optDouble("start", 0.0)
            val duration = clip.optDouble("duration", 0.0)
            val transition = clip.optDouble("transition", 0.0)

            // Insert OFF placeholder for the gap before this clip.
            val gap = start - lastEnd
            if (gap > timecodeGapTolerance) {
                psArray.put(248)
                durArray.put(maxOf(1, Math.round(gap * 10).toInt()))
                transArray.put(0)
                needsOff = true
            }

            val snapshot = clip.optJSONObject("bake_snapshot")
            val slot: Int
            if (snapshot != null) {
                val payload = snapshot.optJSONObject("payload")
                    ?: throw Exception("bake_snapshot thiếu payload")
                val free = findFreeSlot(presets)
                    ?: throw Exception("Hết slot trống (60-240) trên thiết bị")
                presets.put(free.toString(), payload)
                slot = free
            } else {
                // Preset-type clip that references a preset already on the device.
                val pid = clip.optInt("preset_id", -1)
                if (pid <= 0) {
                    lastEnd = maxOf(lastEnd, start + duration)
                    continue
                }
                slot = pid
            }

            psArray.put(slot)
            durArray.put(maxOf(1, Math.round(duration * 10).toInt()))
            transArray.put(maxOf(0, Math.round(transition * 10).toInt()))
            lastEnd = maxOf(lastEnd, start + duration)
            clipCount++
        }

        // Trailing OFF so every device ends at the same timeline length.
        val trailing = timelineSeconds - lastEnd
        if (trailing > timecodeGapTolerance) {
            psArray.put(248)
            durArray.put(maxOf(1, Math.round(trailing * 10).toInt()))
            transArray.put(0)
            needsOff = true
        }

        if (clipCount == 0) throw Exception("Thiết bị Mock không có clip hợp lệ")

        // TIMELINE_OFF placeholder preset (slot 248) if any gap exists.
        if (needsOff) {
            presets.put("248", buildTimelineOffPreset())
        }

        // Compile playlist into slot 249.
        val playlist = org.json.JSONObject().apply {
            put("ps", psArray)
            put("dur", durArray)
            put("transition", transArray)
            put("repeat", if (loop) 0 else 1)
            put("end", 0)
            put("r", 0)
        }
        presets.put("249", org.json.JSONObject().apply {
            put("playlist", playlist)
            put("on", true)
            put("n", "Timecode 249")
        })

        // Single upload of the merged presets.json.
        uploadPresetsJson(realIp, presets.toString())
        clipCount
    }

    /** GET /presets.json → JSONObject. Returns an empty object if the device has none. */
    private suspend fun fetchPresetsJsonObject(ip: String): org.json.JSONObject = withContext(Dispatchers.IO) {
        if (MockDevice.isMock(ip)) return@withContext org.json.JSONObject(MockDevice.cannedPresetsJson())
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder().url("http://$ip/presets.json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Không đọc được presets.json (HTTP ${response.code})")
            val body = response.body?.string()?.trim()
            if (body.isNullOrEmpty() || body == "null") org.json.JSONObject() else org.json.JSONObject(body)
        }
    }

    /** First free slot in 60..240, skipping reserved slots and existing keys. */
    private fun findFreeSlot(presets: org.json.JSONObject): Int? {
        for (slot in timecodeSlotRange) {
            if (slot in timecodeSkipSlots) continue
            val key = slot.toString()
            if (!presets.has(key) || presets.isNull(key)) return slot
        }
        return null
    }

    private fun buildTimelineOffPreset(): org.json.JSONObject = org.json.JSONObject().apply {
        put("n", "TIMELINE_OFF")
        val seg = org.json.JSONObject().apply {
            put("id", 0)
            put("fx", 0)
            put("col", org.json.JSONArray().apply {
                put(org.json.JSONArray().apply { put(0); put(0); put(0) })
            })
        }
        put("seg", org.json.JSONArray().apply { put(seg) })
    }

    /** Upload one binary file (BMP/GIF) decoded from base64 to the device FS. */
    private suspend fun uploadBinaryFile(ip: String, path: String, contentB64: String) = withContext(Dispatchers.IO) {
        try {
            val bytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
            val filename = if (path.startsWith("/")) path else "/$path"
            val mime = if (filename.endsWith(".gif", true)) "image/gif" else "image/bmp"
            uploadImageBytes(ip, filename, bytes, mime)
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "File upload exception ($path) for $ip", e)
        }
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
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "http://$ip/upload"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("data", "/presets.json", jsonContent.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
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
                    if (pid in systemPresetSlots) continue
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
                        val presets = fetchPresetsJsonObject(device.ipAddress)
                        val ok = buildPlaylist249Json(presets, clips, total, loop)
                        if (!ok) throw Exception("Không có clip hợp lệ")
                        uploadPresetsJson(device.ipAddress, presets.toString())
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

    /** Build playlist 249 (+248 OFF cho gap) từ clip biên tập. Trả false nếu rỗng. */
    private fun buildPlaylist249Json(
        presets: org.json.JSONObject,
        clips: List<TimelineClip>,
        totalSeconds: Int,
        loop: Boolean
    ): Boolean {
        val psArray = org.json.JSONArray()
        val durArray = org.json.JSONArray()
        val transArray = org.json.JSONArray()
        var lastEnd = 0.0
        var needsOff = false
        var clipCount = 0
        for (clip in clips.sortedBy { it.startSec }) {
            val start = clip.startSec.toDouble()
            val duration = clip.durationSec.toDouble()
            val transition = clip.transitionSec.toDouble()
            val gap = start - lastEnd
            if (gap > timecodeGapTolerance) {
                psArray.put(248)
                durArray.put(maxOf(1, Math.round(gap * 10).toInt()))
                transArray.put(0)
                needsOff = true
            }
            psArray.put(clip.presetId)
            durArray.put(maxOf(1, Math.round(duration * 10).toInt()))
            transArray.put(maxOf(0, Math.round(transition * 10).toInt()))
            lastEnd = maxOf(lastEnd, start + duration)
            clipCount++
        }
        if (clipCount == 0) return false
        val trailing = totalSeconds - lastEnd
        if (trailing > timecodeGapTolerance) {
            psArray.put(248)
            durArray.put(maxOf(1, Math.round(trailing * 10).toInt()))
            transArray.put(0)
            needsOff = true
        }
        if (needsOff) presets.put("248", buildTimelineOffPreset())
        val playlist = org.json.JSONObject().apply {
            put("ps", psArray)
            put("dur", durArray)
            put("transition", transArray)
            put("repeat", if (loop) 0 else 1)
            put("end", 0)
            put("r", 0)
        }
        presets.put("249", org.json.JSONObject().apply {
            put("playlist", playlist)
            put("on", true)
            put("n", "Timeline Edit")
        })
        return true
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

/** A mock (virtual) device parsed from an imported timecode file. */
data class TimecodeMockDevice(
    val id: String,
    val name: String,
    val clipCount: Int
)

/** Per-device outcome of a timecode upload, shown in the result dialog. */
data class TimecodeUploadResult(
    val mockName: String,
    val deviceName: String,
    val deviceIp: String,
    val clipCount: Int,
    val success: Boolean,
    val error: String? = null
)

/** UI state for the timecode import → mock-to-real mapping flow. */
data class TimecodeImportUiState(
    val showDialog: Boolean = false,
    val mockDevices: List<TimecodeMockDevice> = emptyList(),
    val isProcessing: Boolean = false,
    val resultMessage: String? = null,
    val showResult: Boolean = false,
    val results: List<TimecodeUploadResult> = emptyList(),
    val totalSeconds: Double = 0.0
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

data class PresetDeleteUiState(
    val isPreparing: Boolean = false,
    val isDeleting: Boolean = false,
    val preview: PresetDeletePreview? = null,
    val resultMessage: String? = null,
    val error: String? = null
)

data class PresetDeviceDeleteError(
    val deviceName: String,
    val deviceIp: String,
    val message: String
)

data class PresetBulkDeletePreview(
    val action: PresetDeleteAction,
    val devicePreviews: List<PresetDeletePreview>,
    val errors: List<PresetDeviceDeleteError> = emptyList()
)

data class PresetBulkDeleteUiState(
    val isPreparing: Boolean = false,
    val isDeleting: Boolean = false,
    val preview: PresetBulkDeletePreview? = null,
    val resultMessage: String? = null,
    val resultErrors: List<PresetDeviceDeleteError> = emptyList(),
    val error: String? = null
)

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
