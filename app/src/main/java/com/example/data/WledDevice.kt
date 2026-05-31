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
    val vid: Int? = null
) {
    companion object {
        fun isValidProduct(product: String?): Boolean {
            if (product == null) return false
            val validList = listOf(
                "ARGB HSL Controller",
                "ARGB HSL Controller 2X",
                "ARGB HSL Controller 4X",
                "ARGB HSL Controller 8X"
            )
            return validList.contains(product.trim())
        }
    }
}
