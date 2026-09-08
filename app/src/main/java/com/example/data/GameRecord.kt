package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_stats")
data class GameRecord(
    @PrimaryKey val id: Int = 1,
    val bestScore: Int = 0,
    val totalGamesPlayed: Int = 0,
    val totalBricksBroken: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
