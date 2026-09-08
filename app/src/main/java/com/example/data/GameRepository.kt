package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(private val gameDao: GameDao) {
    val stats: Flow<GameRecord> = gameDao.getStats().map { record ->
        record ?: GameRecord(id = 1, bestScore = 0, totalGamesPlayed = 0, totalBricksBroken = 0)
    }

    suspend fun updateBestScore(score: Int, additionalBricks: Int) {
        val current = try {
            // Read or default
            GameRecord(id = 1, bestScore = score, totalBricksBroken = additionalBricks)
        } catch (e: Exception) {
            GameRecord(id = 1, bestScore = score)
        }
        gameDao.saveStats(current)
    }

    suspend fun recordGameFinished(finalScore: Int, bricksBroken: Int, currentBest: Int, totalGames: Int) {
        val newBest = maxOf(finalScore, currentBest)
        val updated = GameRecord(
            id = 1,
            bestScore = newBest,
            totalGamesPlayed = totalGames + 1,
            totalBricksBroken = bricksBroken,
            lastUpdated = System.currentTimeMillis()
        )
        gameDao.saveStats(updated)
    }
}
