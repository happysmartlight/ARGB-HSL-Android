package com.example.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DB v4 (bản đang phát hành) → v5 (thêm cột mac + pmax): thiết bị đã lưu phải còn nguyên,
 * Room không được rơi vào fallbackToDestructiveMigration (xoá sạch thiết bị).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    /** Test khác (mở MainActivity) có thể đã tạo singleton trong cùng sandbox → đóng và bỏ nó. */
    private fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as? AppDatabase)?.close()
        field.set(null, null)
    }

    @Test
    fun migrates4To5KeepingDevices() {
        resetDatabaseSingleton()
        val ctx = RuntimeEnvironment.getApplication()
        ctx.deleteDatabase("wled_manager_db")
        val path = ctx.getDatabasePath("wled_manager_db")
        path.parentFile?.mkdirs()

        // Schema v4 đúng như Room sinh ra (bảng wled_devices chưa có mac/pmax).
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wled_devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `ipAddress` TEXT NOT NULL, `isOn` INTEGER NOT NULL, `brightness` INTEGER NOT NULL, " +
                    "`hexColor` TEXT NOT NULL, `effectId` INTEGER NOT NULL, `isOnline` INTEGER NOT NULL, " +
                    "`lastSeenTimestamp` INTEGER NOT NULL, `wifiSignal` INTEGER, `product` TEXT, `fwVersion` TEXT, `vid` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `system_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`message` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `level` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `timeline_clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`deviceId` INTEGER NOT NULL, `presetId` INTEGER NOT NULL, `presetName` TEXT NOT NULL, " +
                    "`startDeciseconds` INTEGER NOT NULL, `durationDeciseconds` INTEGER NOT NULL, `transitionDeciseconds` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO wled_devices (name, ipAddress, isOn, brightness, hexColor, effectId, isOnline, lastSeenTimestamp, product, vid) " +
                    "VALUES ('Gậy 1', '192.168.0.21', 0, 127, '#FFFFFF', 0, 0, 0, 'ARGB HSL Controller 2X Pro', 2611103)"
            )
            db.execSQL(
                "INSERT INTO timeline_clips (deviceId, presetId, presetName, startDeciseconds, durationDeciseconds, transitionDeciseconds) " +
                    "VALUES (1, 5, 'Logo', 0, 50, 0)"
            )
            db.version = 4
        }

        val room = AppDatabase.getDatabase(ctx)
        val dao = room.wledDao()
        val device = runBlocking { dao.getDeviceById(1) }
        assertNotNull("thiết bị cũ phải còn sau migration", device)
        assertEquals("Gậy 1", device!!.name)
        assertEquals("192.168.0.21", device.ipAddress)
        assertEquals(2611103, device.vid)
        assertNull(device.mac)
        assertNull(device.pmax)

        // Cột mới ghi/đọc được.
        runBlocking { dao.updateDevice(device.copy(mac = "a4cb8fe1ae9c", pmax = 1024)) }
        val updated = runBlocking { dao.getDeviceById(1) }!!
        assertEquals("a4cb8fe1ae9c", updated.mac)
        assertEquals(1024, updated.pmax)

        // Clip timeline (bảng từ v4) cũng còn.
        val c = room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM timeline_clips")
        c.moveToFirst()
        assertEquals(1, c.getInt(0))
        c.close()
        resetDatabaseSingleton()
    }
}
