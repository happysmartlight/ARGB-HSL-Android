package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WledRepository(
    private val wledDao: WledDao,
    private val wledApi: WledApi
) {
    val allDevices: Flow<List<WledDevice>> = wledDao.getAllDevices()

    suspend fun getDeviceById(id: Int): WledDevice? = withContext(Dispatchers.IO) {
        wledDao.getDeviceById(id)
    }

    suspend fun addDevice(name: String, ipAddress: String): Long = withContext(Dispatchers.IO) {
        // Clean IP Address (remove http/https/trailing slashes if user typed it)
        val cleanedIp = cleanIpAddress(ipAddress)
        val device = WledDevice(
            name = name,
            ipAddress = cleanedIp,
            isOnline = false
        )
        wledDao.insertDevice(device)
    }

    suspend fun deleteDevice(device: WledDevice) = withContext(Dispatchers.IO) {
        wledDao.deleteDevice(device)
    }

    suspend fun updateDeviceInDb(device: WledDevice) = withContext(Dispatchers.IO) {
        wledDao.updateDevice(device)
    }

    /**
     * Checks online status of device, updates name / capabilities if possible, and saves state to DB
     */
    suspend fun pingDevice(device: WledDevice): WledDevice = withContext(Dispatchers.IO) {
        val baseUrl = "http://${device.ipAddress}/json"
        try {
            val response = wledApi.getCompleteApi(baseUrl)
            val state = response.state
            val info = response.info

            val primaryColorList = state?.seg?.firstOrNull()?.col?.firstOrNull() ?: listOf(255, 255, 255)
            val r = primaryColorList.getOrElse(0) { 255 }
            val g = primaryColorList.getOrElse(1) { 255 }
            val b = primaryColorList.getOrElse(2) { 255 }
            val hexColor = rgbToHex(r, g, b)

            val updatedDevice = device.copy(
                isOn = state?.on ?: device.isOn,
                brightness = state?.bri ?: device.brightness,
                hexColor = hexColor,
                effectId = state?.seg?.firstOrNull()?.fx ?: device.effectId,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis(),
                name = info?.name ?: device.name,
                wifiSignal = info?.wifi?.signal,
                product = info?.product,
                fwVersion = info?.ver,
                vid = info?.vid
            )
            val prod = info?.product
            if (prod != null && !WledDevice.isValidProduct(prod)) {
                wledDao.deleteDevice(device)
                return@withContext device.copy(isOnline = false, product = prod)
            }
            wledDao.updateDevice(updatedDevice)
            updatedDevice
        } catch (e: Exception) {
            Log.e("WledRepository", "Error pinging device ${device.ipAddress}", e)
            val now = System.currentTimeMillis()
            val shouldMarkOffline = device.lastSeenTimestamp == 0L || (now - device.lastSeenTimestamp > 45000L)
            val updatedDevice = device.copy(
                isOnline = if (shouldMarkOffline) false else device.isOnline
            )
            wledDao.updateDevice(updatedDevice)
            updatedDevice
        }
    }

    /**
     * Toggles a device power state
     */
    suspend fun togglePower(device: WledDevice, turnOn: Boolean): Boolean = withContext(Dispatchers.IO) {
        val stateRequest = WledStateRequest(on = turnOn)
        val url = "http://${device.ipAddress}/json/state"
        try {
            val returnedState = wledApi.updateState(url, stateRequest)
            val updatedDevice = device.copy(
                isOn = returnedState.on ?: turnOn,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            wledDao.updateDevice(updatedDevice)
            true
        } catch (e: Exception) {
            Log.e("WledRepository", "Error toggling power for ${device.ipAddress}", e)
            false
        }
    }

    /**
     * Turns on the device and forces solid red color (fx = 0, color = Red)
     */
    suspend fun turnOnWithSolidRed(device: WledDevice): Boolean = withContext(Dispatchers.IO) {
        val rgb = listOf(255, 0, 0)
        val segmentRequest = WledSegmentRequest(
            id = 0,
            col = listOf(rgb, listOf(0,0,0), listOf(0,0,0)), // Set primary color, secondary, tertiary
            fx = 0
        )
        val stateRequest = WledStateRequest(
            on = true,
            seg = listOf(segmentRequest)
        )
        val url = "http://${device.ipAddress}/json/state"
        try {
            wledApi.updateState(url, stateRequest)
            val updatedDevice = device.copy(
                isOn = true,
                hexColor = "#FF0000",
                effectId = 0,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            wledDao.updateDevice(updatedDevice)
            true
        } catch (e: Exception) {
            Log.e("WledRepository", "Error turning on with solid red for ${device.ipAddress}", e)
            false
        }
    }

    /**
     * Updates brightness (0..255)
     */
    suspend fun updateBrightness(device: WledDevice, bri: Int): Boolean = withContext(Dispatchers.IO) {
        val stateRequest = WledStateRequest(bri = bri)
        val url = "http://${device.ipAddress}/json/state"
        try {
            val returnedState = wledApi.updateState(url, stateRequest)
            val updatedDevice = device.copy(
                brightness = returnedState.bri ?: bri,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            wledDao.updateDevice(updatedDevice)
            true
        } catch (e: Exception) {
            Log.e("WledRepository", "Error updating brightness for ${device.ipAddress}", e)
            false
        }
    }

    /**
     * Updates primary color, optionally forcing an effect ID (e.g. solid color = 0)
     */
    suspend fun updateColor(device: WledDevice, hexColor: String, effectId: Int? = null): Boolean = withContext(Dispatchers.IO) {
        val rgb = hexToRgb(hexColor)
        val segmentRequest = WledSegmentRequest(
            id = 0,
            col = listOf(rgb, listOf(0,0,0), listOf(0,0,0)), // Set primary color, secondary, tertiary
            fx = effectId
        )
        val stateRequest = WledStateRequest(
            seg = listOf(segmentRequest)
        )
        val url = "http://${device.ipAddress}/json/state"
        try {
            wledApi.updateState(url, stateRequest)
            val updatedDevice = device.copy(
                hexColor = hexColor,
                effectId = effectId ?: device.effectId,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            wledDao.updateDevice(updatedDevice)
            true
        } catch (e: Exception) {
            Log.e("WledRepository", "Error updating color for ${device.ipAddress}", e)
            false
        }
    }

    /**
     * Updates effect, speed and intensity
     */
    suspend fun updateEffect(device: WledDevice, effectId: Int, speed: Int? = null, intensity: Int? = null): Boolean = withContext(Dispatchers.IO) {
        val segmentRequest = WledSegmentRequest(
            id = 0,
            fx = effectId,
            sx = speed,
            ix = intensity
        )
        val stateRequest = WledStateRequest(
            seg = listOf(segmentRequest)
        )
        val url = "http://${device.ipAddress}/json/state"
        try {
            val returnedState = wledApi.updateState(url, stateRequest)
            val returnedFx = returnedState.seg?.firstOrNull()?.fx ?: effectId
            val updatedDevice = device.copy(
                effectId = returnedFx,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            wledDao.updateDevice(updatedDevice)
            true
        } catch (e: Exception) {
            Log.e("WledRepository", "Error updating effect for ${device.ipAddress}", e)
            false
        }
    }

    /**
     * Fetch complete responses (e.g. dynamic effects, palettes names from device)
     */
    suspend fun fetchDeviceDetails(device: WledDevice): WledResponse? = withContext(Dispatchers.IO) {
        val url = "http://${device.ipAddress}/json"
        try {
            wledApi.getCompleteApi(url)
        } catch (e: Exception) {
            Log.e("WledRepository", "Error fetching details for ${device.ipAddress}", e)
            null
        }
    }

    private suspend fun markOffline(device: WledDevice) {
        val updatedDevice = device.copy(isOnline = false)
        wledDao.updateDevice(updatedDevice)
    }

    companion object {
        fun hexToRgb(hexColor: String): List<Int> {
            val cleanHex = if (hexColor.startsWith("#")) hexColor.substring(1) else hexColor
            return try {
                if (cleanHex.length == 6) {
                    val r = cleanHex.substring(0, 2).toInt(16)
                    val g = cleanHex.substring(2, 4).toInt(16)
                    val b = cleanHex.substring(4, 6).toInt(16)
                    listOf(r, g, b)
                } else {
                    listOf(255, 255, 255)
                }
            } catch (e: Exception) {
                listOf(255, 255, 255)
            }
        }

        fun rgbToHex(r: Int, g: Int, b: Int): String {
            return String.format("#%02X%02X%02X", r, g, b)
        }

        fun cleanIpAddress(ip: String): String {
            return ip.trim()
                .replace("http://", "")
                .replace("https://", "")
                .trimEnd('/')
        }
    }
}
