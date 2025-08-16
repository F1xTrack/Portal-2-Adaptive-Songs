package com.f1xtrack.portal2adaptivesongs.data.timeattack

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_attack_results")
data class TimeAttackResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: String,
    val difficulty: String,
    val type: String,
    val success: Boolean,
    val startedAtEpochSec: Long,
    val durationSec: Int,
    val distanceM: Int,
    val ssTimeSec: Int,
    val normalTimeSec: Int,
    val switches: Int
)
