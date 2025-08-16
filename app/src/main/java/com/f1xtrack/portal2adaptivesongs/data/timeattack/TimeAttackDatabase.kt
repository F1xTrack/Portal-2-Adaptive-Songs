package com.f1xtrack.portal2adaptivesongs.data.timeattack

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TimeAttackResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TimeAttackDatabase : RoomDatabase() {
    abstract fun timeAttackResultDao(): TimeAttackResultDao

    companion object {
        @Volatile private var INSTANCE: TimeAttackDatabase? = null

        fun get(context: Context): TimeAttackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeAttackDatabase::class.java,
                    "time_attack.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
