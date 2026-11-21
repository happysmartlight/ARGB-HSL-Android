package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wled_devices")
data class WledDevice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val isOn: Boolean = false,
    val brightness: Int = 127,
    val hexColor: String = "#FFFFFF",
    val effectId: Int = 0,
    val isOnline: Boolean = false,
    val lastSeenTimestamp: Long = 0L,
    val wifiSignal: Int? = null,
    val product: String? = null,
    val fwVersion: String? = null,
    val vid: Int? = null,
    /** MAC chuẩn hoá (12 hex thường, không dấu) từ `/json/info` → `mac`. Null = chưa đọc được. */
    val mac: String? = null,
    /** `fs.pmax` của mạch (ID preset lớn nhất). Null = firmware cũ / chưa đọc → coi như 250. */
    val pmax: Int? = null
) {
    companion object {
        /** Dòng mạch được app hỗ trợ (`/json/info` → `product`). Đồng bộ với desktop `scan_worker._VALID_PRODUCTS`. */
        val VALID_PRODUCTS = listOf(
            "ARGB HSL Controller",
            "ARGB HSL Controller 2X",
            "ARGB HSL Controller 2X Pro",
            "ARGB HSL Controller 4X",
            "ARGB HSL Controller 8X",
            "ARGB HSL Controller 16X"
        )

        fun isValidProduct(product: String?): Boolean {
            if (product == null) return false
            val p = product.trim()
            return VALID_PRODUCTS.any { it.equals(p, ignoreCase = true) }
        }
    }
}
