package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WledDao {
    @Query("SELECT * FROM wled_devices ORDER BY name ASC")
    fun getAllDevices(): Flow<List<WledDevice>>

    @Query("SELECT * FROM wled_devices WHERE id = :id")
    suspend fun getDeviceById(id: Int): WledDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: WledDevice): Long

    @Update
    suspend fun updateDevice(device: WledDevice)

    @Delete
    suspend fun deleteDevice(device: WledDevice)

    @Query("UPDATE wled_devices SET isOnline = :isOnline WHERE id = :id")
    suspend fun updateOnlineStatus(id: Int, isOnline: Boolean)
}
