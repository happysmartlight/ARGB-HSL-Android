package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Một clip preset trên timeline biên tập của một thiết bị (tab "Biên Tập Timeline").
 * Thời lượng/transition lưu theo decisecond (giây×10) để khớp đơn vị playlist WLED.
 */
@Entity(tableName = "timeline_clips")
data class TimelineClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Int,
    val presetId: Int,
    val presetName: String,
    val startDeciseconds: Int,
    val durationDeciseconds: Int,
    val transitionDeciseconds: Int
)

@Dao
interface TimelineClipDao {
    @Query("SELECT * FROM timeline_clips ORDER BY deviceId ASC, startDeciseconds ASC")
    fun getAllClips(): Flow<List<TimelineClipEntity>>

    @Query("DELETE FROM timeline_clips WHERE deviceId = :deviceId")
    suspend fun deleteByDevice(deviceId: Int)

    @Insert
    suspend fun insertAll(clips: List<TimelineClipEntity>)

    /** Thay toàn bộ clip của một thiết bị (write-through từ working state). */
    @Transaction
    suspend fun replaceDeviceClips(deviceId: Int, clips: List<TimelineClipEntity>) {
        deleteByDevice(deviceId)
        if (clips.isNotEmpty()) insertAll(clips)
    }
}
