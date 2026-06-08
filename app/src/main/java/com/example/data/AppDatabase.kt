package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WledDevice::class, SystemLog::class, TimelineClipEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wledDao(): WledDao
    abstract fun systemLogDao(): SystemLogDao
    abstract fun timelineClipDao(): TimelineClipDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 3→4: chỉ thêm bảng timeline_clips, KHÔNG xoá dữ liệu thiết bị hiện có.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timeline_clips` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`deviceId` INTEGER NOT NULL, " +
                        "`presetId` INTEGER NOT NULL, " +
                        "`presetName` TEXT NOT NULL, " +
                        "`startDeciseconds` INTEGER NOT NULL, " +
                        "`durationDeciseconds` INTEGER NOT NULL, " +
                        "`transitionDeciseconds` INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wled_manager_db"
                ).addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
