package com.example.data

import com.example.BuildConfig

/**
 * Thiết bị ẢO chỉ dùng cho môi trường DEV (debug) để thử nghiệm UI khi không có mạch
 * WLED thật (ví dụ chạy trên máy ảo).
 *
 * AN TOÀN RELEASE: mọi nhánh mock đều đi qua [isMock], mà [isMock] luôn trả về `false`
 * khi không phải build debug. Vì vậy ở bản release: KHÔNG seed thiết bị ảo, KHÔNG có IP
 * nào khớp, toàn bộ canned-data bên dưới là code chết không bao giờ chạy.
 */
object MockDevice {
    /** IP giả nhận diện thiết bị ảo (không phải IP thật nên không bao giờ gọi mạng tới). */
    const val MOCK_IP = "mock.local"
    const val MOCK_NAME = "🧪 MOCK Tablet Sim"
    // Giả lập mạch 2X Pro firmware mới (pmax 1024) để thử dải ID 256+.
    const val MOCK_PRODUCT = "ARGB HSL Controller 2X Pro"
    const val MOCK_MAC = "02000000a001"
    const val MOCK_PMAX = 1024

    fun isMock(ip: String): Boolean = BuildConfig.DEBUG && ip == MOCK_IP

    /** `/json/info` giả lập (mac, fs.pmax) cho luồng import timecode. */
    fun cannedInfoJson(): String =
        """{"ver":"mock-0.14","product":"$MOCK_PRODUCT","mac":"$MOCK_MAC","fs":{"u":120,"t":983,"pmax":$MOCK_PMAX}}"""

    /** Bản ghi WledDevice ảo để seed vào DB (debug). */
    fun seedDevice(): WledDevice = WledDevice(
        name = MOCK_NAME,
        ipAddress = MOCK_IP,
        isOn = true,
        brightness = 160,
        hexColor = "#00C8FF",
        effectId = 0,
        isOnline = true,
        lastSeenTimestamp = System.currentTimeMillis(),
        product = MOCK_PRODUCT,
        fwVersion = "mock-0.14",
        vid = 240101,
        mac = MOCK_MAC,
        pmax = MOCK_PMAX
    )

    /** Phản hồi /json giả lập (state + info + effects). */
    fun cannedJsonResponse(): WledResponse = WledResponse(
        state = WledState(
            on = true,
            bri = 160,
            seg = listOf(
                WledSegment(id = 0, on = true, bri = 255, col = listOf(listOf(0, 200, 255)), fx = 0)
            )
        ),
        info = WledInfo(
            ver = "mock-0.14",
            name = MOCK_NAME,
            product = MOCK_PRODUCT,
            leds = WledLedsInfo(count = 120, fps = 60),
            fs = WledFilesystemInfo(usedBytes = 120, totalBytes = 983, pmax = MOCK_PMAX),
            mac = MOCK_MAC
        ),
        effects = listOf("Solid", "Blink", "Breathe", "Wipe", "Poi HSL", "Rainbow", "Theater", "Chase"),
        palettes = listOf("Default", "Rainbow", "Party")
    )

    /** Nội dung presets.json giả lập — vài preset có tên để thử kéo thả timeline. */
    fun cannedPresetsJson(): String = """
        {
          "1": {"n":"Logo Đỏ"},
          "2": {"n":"Nhịp Xanh Dương"},
          "3": {"n":"Cầu Vồng Chạy"},
          "4": {"n":"Sóng Tím"},
          "5": {"n":"Chớp Trắng"},
          "6": {"n":"Lửa Trại"}
        }
    """.trimIndent()
}
