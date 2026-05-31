package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "system_logs")
data class SystemLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO" // INFO, WARN, ERROR
)

@Dao
interface SystemLogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT 500")
    fun getAllLogs(): Flow<List<SystemLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SystemLog)

    @Query("DELETE FROM system_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteLogsOlderThan(cutoffTime: Long)

    @Query("DELETE FROM system_logs")
    suspend fun clearAllLogs()
}
