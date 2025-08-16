package com.f1xtrack.portal2adaptivesongs.data.timeattack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TimeAttackResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: TimeAttackResultEntity): Long

    @Query("SELECT COUNT(*) FROM time_attack_results WHERE success = 1")
    suspend fun getSuccessCount(): Int
}
