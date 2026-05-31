package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
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

class WledViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val api = WledApi.create()
    private val repository = WledRepository(db.wledDao(), api)
    private val discoveryManager = WledDiscoveryManager(application)

    private val _addDeviceState = MutableStateFlow<AddDeviceState>(AddDeviceState.Idle)
    val addDeviceState: StateFlow<AddDeviceState> = _addDeviceState.asStateFlow()

    fun resetAddDeviceState() {
        _addDeviceState.value = AddDeviceState.Idle
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
        if (device != null) {
            viewModelScope.launch {
                // Ping to update latest state
                val updated = repository.pingDevice(device)
                if (updated.id == _selectedDevice.value?.id) {
                    _selectedDevice.value = updated
                }
                // Fetch complete details if possible
                val details = repository.fetchDeviceDetails(device)
                if (details != null && _selectedDevice.value?.id == device.id) {
                    _activeDeviceDetails.value = details
                }
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

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        stopAudio()
    }

    // --- STAGE MODE: SYNCHRONIZED MULTI-DEVICE CONTROL (PARALLEL) ---
    
    fun togglePowerAll(turnOn: Boolean) {
        log("Sân khấu đồng loạt: ${if (turnOn) "BẬT TOÀN BỘ (Màu đỏ)" else "TẮT TOÀN BỘ"}", "WARN")
        viewModelScope.launch {
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

    fun runPlaylistAll(playlistId: Int) {
        viewModelScope.launch {
            devices.value.forEach { device ->
                launch {
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
            }
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
        _playlistElapsedSeconds.value = startFrom
        _playlistName.value = "Playlist $playlistId"
        
        log("Bắt đầu chạy kịch bản đồng bộ từ giây: ${String.format("%.1f", startFrom)}s", "INFO")
        
        lastSentPresetMap.clear()
        updateActiveSteps(startFrom)

        // IF we are in Native Play mode (no manual seek / choreography yet), we fire
        // the parallel native Playlist 249 command to run directly on WLED device hardware!
        if (!_isChoreographyMode.value) {
            runPlaylistAll(playlistId)
        }

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
            playlistStartTimeMs = System.currentTimeMillis() - (startFrom * 1000L).toLong()

            // Reset again to force immediate first transition load if in choreography mode
            lastSentPresetMap.clear()

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
        _isChoreographyMode.value = false // Reset back to default WLED native playlist mode
        lastSentPresetMap.clear()
        updateActiveSteps(0f)
        stopAudio()
        togglePowerAll(false)
        log("Đã dừng kịch bản liên hoàn và tắt toàn bộ LED.", "WARN")
    }

    fun pausePlaylistTimeline() {
        playlistTimelineJob?.cancel()
        playlistTimelineJob = null
        _isPlaylistRunning.value = false
        stopAudio()
        
        // Freeze all online devices via segment 0
        val onlineDevices = devices.value.filter { it.isOnline }
        onlineDevices.forEach { device ->
            viewModelScope.launch(Dispatchers.IO) {
                val url = "http://${device.ipAddress}/json/state"
                try {
                    val segReq = WledSegmentRequest(id = 0, frz = true)
                    val stateReq = WledStateRequest(seg = listOf(segReq))
                    api.updateState(url, stateReq)
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Lỗi tạm dừng (freeze) thiết bị ${device.ipAddress}", e)
                }
            }
        }
        
        log("Đã tạm dừng kịch bản liên hoàn. Bấm Tiếp tục để chạy tiếp bằng Biên đạo chủ động.", "INFO")
    }

    fun resumePlaylistTimeline() {
        _isChoreographyMode.value = true
        
        // Unfreeze all online devices via segment 0
        val onlineDevices = devices.value.filter { it.isOnline }
        onlineDevices.forEach { device ->
            viewModelScope.launch(Dispatchers.IO) {
                val url = "http://${device.ipAddress}/json/state"
                try {
                    val segReq = WledSegmentRequest(id = 0, frz = false)
                    val stateReq = WledStateRequest(seg = listOf(segReq))
                    api.updateState(url, stateReq)
                } catch (e: Exception) {
                    android.util.Log.e("WledViewModel", "Lỗi tiếp tục (unfreeze) thiết bị ${device.ipAddress}", e)
                }
            }
        }
        
        startPlaylistAllWithTimeline(249)
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

    // Reserved slots per the integration guide — never allocate these for baking.
    private val timecodeSkipSlots = setOf(100, 248, 249, 250)
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
            val config = org.json.JSONObject(jsonString)
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
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val filename = if (path.startsWith("/")) path else "/$path"
            val mime = if (filename.endsWith(".gif", true)) "image/gif" else "image/bmp"
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("data", filename, bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .build()
            val request = okhttp3.Request.Builder().url("http://$ip/upload").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("WledViewModel", "File upload failed ($path) for $ip: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WledViewModel", "File upload exception ($path) for $ip", e)
        }
    }

    /** Replace the device's presets.json. Throws on failure so callers can report it. */
    private suspend fun uploadPresetsJson(ip: String, jsonContent: String) {
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
}

data class WledEffect(val id: Int, val name: String, val vietnameseName: String)
data class WledPalette(val id: Int, val name: String, val vietnameseName: String)

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
