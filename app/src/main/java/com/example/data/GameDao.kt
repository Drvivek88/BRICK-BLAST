package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM game_stats WHERE id = 1 LIMIT 1")
    fun getStats(): Flow<GameRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStats(record: GameRecord)
}
