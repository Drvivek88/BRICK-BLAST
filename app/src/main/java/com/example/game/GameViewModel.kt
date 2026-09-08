package com.example.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundManager
import com.example.data.GameDatabase
import com.example.data.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GameRepository
    val soundManager = SoundManager()

    init {
        val db = GameDatabase.getInstance(application)
        repository = GameRepository(db.gameDao())
    }

    private val _gameState = MutableStateFlow(GameState.MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    private val _isNewBest = MutableStateFlow(false)
    val isNewBest: StateFlow<Boolean> = _isNewBest.asStateFlow()

    private val _ballsOwned = MutableStateFlow(GameGeometry.START_BALLS)
    val ballsOwned: StateFlow<Int> = _ballsOwned.asStateFlow()

    private val _ballsInFlight = MutableStateFlow(0)
    val ballsInFlight: StateFlow<Int> = _ballsInFlight.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    private val _adUsesLeft = MutableStateFlow(GameGeometry.AD_USES_PER_RUN)
    val adUsesLeft: StateFlow<Int> = _adUsesLeft.asStateFlow()

    private val _adTimerSec = MutableStateFlow(GameGeometry.AD_LEN_SEC)
    val adTimerSec: StateFlow<Float> = _adTimerSec.asStateFlow()

    private val _adClaimReady = MutableStateFlow(false)
    val adClaimReady: StateFlow<Boolean> = _adClaimReady.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Game field metrics in logical units
    var fieldHeight = 700f
        private set
    var launcherY = 700f - 84f
        private set
    var dangerY = 700f - 120f
        private set
    val launchX = GameGeometry.W / 2f
    val launchR = 24f

    // Physics entities
    val bricks = CopyOnWriteArrayList<Brick>()
    val balls = CopyOnWriteArrayList<Ball>()
    val particles = CopyOnWriteArrayList<Particle>()
    val floats = CopyOnWriteArrayList<FloatingText>()

    // Aiming state
    var aimAngle = -PI.toFloat() / 2f
    var isAiming = false
    var aimPreviewPoints = emptyList<Offset>()

    // Snapshot state to drive Compose Canvas redraw on every frame
    var frameTick by mutableLongStateOf(0L)
        private set

    // Animation & FX states
    var shake = 0f
    var shiftT = 1f
    var comboPop = 0f
    var comboFade = 0f

    private var turn = 0
    private var speedMult = 1f
    private var lastHitAt = -9f
    private var nowSec = 0f
    private var launchQueueCount = 0
    private var launchTimer = 0f
    private var shotStartTime = 0f
    private val nextBrickId = AtomicLong(1)
    private var totalBricksDestroyedInRun = 0
    private var totalGamesPlayed = 0

    private var loopJob: Job? = null

    init {
        // Collect saved stats
        viewModelScope.launch {
            repository.stats.collect { stats ->
                if (stats.bestScore > _bestScore.value) {
                    _bestScore.value = stats.bestScore
                }
                totalGamesPlayed = stats.totalGamesPlayed
            }
        }
        // Spawn initial background rows for menu aesthetic
        spawnInitialMenuBoard()
        startLoop()
    }

    fun updateScreenDimensions(canvasWidth: Float, canvasHeight: Float) {
        if (canvasWidth <= 0 || canvasHeight <= 0) return
        val scl = min(canvasWidth / GameGeometry.W, canvasHeight / 560f)
        val h = min(960f, max(560f, canvasHeight / scl))
        fieldHeight = h
        launcherY = h - 84f
        dangerY = h - 120f
    }

    private fun spawnInitialMenuBoard() {
        bricks.clear()
        spawnRow(2, 0)
        spawnRow(1, 1)
        spawnRow(0, 2)
        turn = 3
    }

    fun startGame() {
        bricks.clear()
        balls.clear()
        particles.clear()
        floats.clear()
        _score.value = 0
        _ballsOwned.value = GameGeometry.START_BALLS
        _ballsInFlight.value = 0
        _combo.value = 0
        _isNewBest.value = false
        _adUsesLeft.value = GameGeometry.AD_USES_PER_RUN
        _adClaimReady.value = false
        speedMult = 1f
        turn = 0
        aimAngle = -PI.toFloat() / 2f
        isAiming = false
        aimPreviewPoints = emptyList()
        totalBricksDestroyedInRun = 0

        spawnRow(2, 0)
        spawnRow(1, 1)
        spawnRow(0, 2)
        turn = 3
        shiftT = 1f

        _gameState.value = GameState.AIMING
        soundManager.playTick()
    }

    fun pauseGame() {
        if (_gameState.value == GameState.AIMING || _gameState.value == GameState.SHOOTING) {
            _gameState.value = GameState.PAUSED
        }
    }

    fun resumeGame() {
        if (_gameState.value == GameState.PAUSED) {
            _gameState.value = if (launchQueueCount > 0 || balls.isNotEmpty()) GameState.SHOOTING else GameState.AIMING
        }
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        soundManager.isMuted = newMute
        if (!newMute) soundManager.playTick()
    }

    fun openRewardedAd() {
        if (_adUsesLeft.value <= 0 || _gameState.value != GameState.AIMING || _ballsOwned.value >= GameGeometry.MAX_BALLS) return
        _adTimerSec.value = GameGeometry.AD_LEN_SEC
        _adClaimReady.value = false
        _gameState.value = GameState.AD_ACTIVE
        soundManager.playTick()
    }

    fun claimRewardedAd() {
        if (_gameState.value != GameState.AD_ACTIVE || !_adClaimReady.value) return
        val currentBalls = _ballsOwned.value
        val newCount = min(GameGeometry.MAX_BALLS, currentBalls * 2)
        val gained = newCount - currentBalls
        _ballsOwned.value = newCount
        _adUsesLeft.value = max(0, _adUsesLeft.value - 1)
        _gameState.value = GameState.AIMING

        floats.add(FloatingText(launchX, launcherY - 46f, "+$gained BALLS!", Color(0xFF22D3EE), 1f, 18f))
        spawnSparks(launchX, launcherY - 20f, Color(0xFF22D3EE), 12)
        soundManager.playPower()
        soundManager.playCombo()
    }

    fun cancelRewardedAd() {
        if (_gameState.value == GameState.AD_ACTIVE) {
            _gameState.value = GameState.AIMING
            soundManager.playTick()
        }
    }

    // Touch pointer aiming handling
    fun onAimStart(logicalX: Float, logicalY: Float) {
        if (_gameState.value != GameState.AIMING) return
        isAiming = true
        updateAimAngle(logicalX, logicalY)
        updateAimPreview()
        frameTick++
    }

    fun onAimMove(logicalX: Float, logicalY: Float) {
        if (!isAiming || _gameState.value != GameState.AIMING) return
        updateAimAngle(logicalX, logicalY)
        updateAimPreview()
        frameTick++
    }

    fun onAimRelease(logicalX: Float, logicalY: Float) {
        if (!isAiming || _gameState.value != GameState.AIMING) return
        updateAimAngle(logicalX, logicalY)
        isAiming = false
        aimPreviewPoints = emptyList()
        frameTick++
        fireVolley()
    }

    fun onAimCancel() {
        isAiming = false
        aimPreviewPoints = emptyList()
        frameTick++
    }

    private fun updateAimAngle(targetX: Float, targetY: Float) {
        val dx = targetX - launchX
        val dy = targetY - launcherY
        if (dx * dx + dy * dy < 100f) return

        var a = atan2(dy, dx)
        val minAngle = -168f * (PI.toFloat() / 180f)
        val maxAngle = -12f * (PI.toFloat() / 180f)
        if (a > maxAngle) {
            a = if (a < PI.toFloat() / 2f) maxAngle else minAngle
        } else if (a < minAngle) {
            a = minAngle
        }
        aimAngle = a
    }

    private fun updateAimPreview() {
        val points = mutableListOf<Offset>()
        var px = launchX
        var py = launcherY - 4f
        var dx = cos(aimAngle)
        var dy = sin(aimAngle)
        var immuneId: Long? = null
        var immuneSec = 0f
        points.add(Offset(px, py))

        val sdt = 4f / GameGeometry.BALL_SPEED
        var t = 0f
        var acc = 0f

        while (t < 2.2f) {
            t += sdt
            px += dx * GameGeometry.BALL_SPEED * sdt
            py += dy * GameGeometry.BALL_SPEED * sdt

            // Wall collisions
            if (px < GameGeometry.BALL_R) {
                px = GameGeometry.BALL_R
                if (dx < 0) dx = -dx
            } else if (px > GameGeometry.W - GameGeometry.BALL_R) {
                px = GameGeometry.W - GameGeometry.BALL_R
                if (dx > 0) dx = -dx
            }
            if (py < 56f + GameGeometry.BALL_R) {
                py = 56f + GameGeometry.BALL_R
                if (dy < 0) dy = -dy
            }

            // Brick collision
            var hit = false
            for (br in bricks) {
                val ddx = px - br.cx
                val ddy = py - br.cy
                val rr = br.boundR + GameGeometry.BALL_R
                if (ddx * ddx + ddy * ddy > rr * rr) continue
                if (immuneId == br.id && t < immuneSec) continue

                val h = GameGeometry.circlePoly(px, py, GameGeometry.BALL_R, br.poly, br.cx, br.cy)
                if (h != null) {
                    px += h.nx * (h.depth + 0.06f)
                    py += h.ny * (h.depth + 0.06f)
                    val vd = dx * h.nx + dy * h.ny
                    if (vd < 0) {
                        dx -= 2 * vd * h.nx
                        dy -= 2 * vd * h.ny
                    }
                    immuneId = br.id
                    immuneSec = t + 0.04f
                    points.add(Offset(px, py))
                    hit = true
                    break
                }
            }

            if (hit) break

            acc += 4f
            if (acc >= 16f) {
                acc = 0f
                points.add(Offset(px, py))
            }
            if (py > launcherY) break
        }
        aimPreviewPoints = points
    }

    private fun fireVolley() {
        if (_ballsOwned.value <= 0) return
        _gameState.value = GameState.SHOOTING
        launchQueueCount = _ballsOwned.value
        launchTimer = 0f
        shotStartTime = nowSec
        _combo.value = 0
        lastHitAt = -9f
        soundManager.playLaunch()
    }

    private fun launchInterval(): Float {
        return max(0.028f, 0.075f - _ballsOwned.value * 0.0009f)
    }

    private fun spawnRow(row: Int, diff: Int) {
        val base = GameGeometry.hpBase(diff)
        val cols = (0 until GameGeometry.COLS).toMutableList()
        cols.shuffle()
        val gaps = (Math.random() * 2.4).toInt()
        val cells = cols.take(GameGeometry.COLS - gaps)

        var powerUp: PowerUpType? = null
        if (diff < 3) {
            powerUp = PowerUpType.BALL_1
        } else if (Math.random() < 0.32) {
            val r = Math.random()
            powerUp = when {
                r < 0.45 -> PowerUpType.BALL_1
                r < 0.70 -> PowerUpType.SPEED
                r < 0.90 -> PowerUpType.BOOST
                else -> PowerUpType.BALL_5
            }
        }

        cells.forEachIndexed { index, col ->
            val bId = nextBrickId.getAndIncrement()
            val brick = if (index == 0 && powerUp != null) {
                Brick(
                    id = bId,
                    col = col,
                    row = row,
                    hp = 1,
                    maxHp = 1,
                    isPowerUp = true,
                    powerUpType = powerUp,
                    shape = BrickShape.SQUARE
                )
            } else {
                val hp = max(1, (base * (0.7 + Math.random() * 0.7)).roundToInt())
                val r = Math.random()
                val diaChance = min(0.25, 0.04 + diff * 0.015)
                val triChance = if (diff < 2) 0.0 else min(0.30, 0.08 + diff * 0.02)
                val shape = when {
                    r < diaChance -> BrickShape.DIAMOND
                    r < diaChance + triChance -> BrickShape.TRIANGLE
                    else -> BrickShape.SQUARE
                }
                val dir = if (shape == BrickShape.TRIANGLE) (Math.random() * 4).toInt() else 0
                Brick(
                    id = bId,
                    col = col,
                    row = row,
                    hp = hp,
                    maxHp = hp,
                    isPowerUp = false,
                    powerUpType = null,
                    shape = shape,
                    triangleDir = dir
                )
            }
            GameGeometry.layoutBrick(brick)
            bricks.add(brick)
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            var lastTimeNanos = System.nanoTime()
            while (isActive) {
                val nowNanos = System.nanoTime()
                val dt = min(0.033f, (nowNanos - lastTimeNanos) / 1_000_000_000f)
                lastTimeNanos = nowNanos
                nowSec += dt

                updateGame(dt)
                delay(16) // ~60fps tick rate
            }
        }
    }

    private fun updateGame(dt: Float) {
        // Rewarded ad timer countdown
        if (_gameState.value == GameState.AD_ACTIVE) {
            if (_adTimerSec.value > 0f) {
                _adTimerSec.value = max(0f, _adTimerSec.value - dt)
                if (_adTimerSec.value <= 0f && !_adClaimReady.value) {
                    _adClaimReady.value = true
                    soundManager.playCombo()
                }
            }
        }

        shake = max(0f, shake - dt * 26f)
        comboPop = max(0f, comboPop - dt * 5f)
        if (comboFade > 0f) comboFade = max(0f, comboFade - dt * 2f)
        if (shiftT < 1f) shiftT = min(1f, shiftT + dt * 6f)

        for (b in bricks) {
            if (b.flash > 0f) b.flash = max(0f, b.flash - dt * 7f)
        }

        if (_gameState.value == GameState.SHOOTING) {
            // Spawn queued balls
            launchTimer -= dt
            while (launchQueueCount > 0 && launchTimer <= 0f) {
                launchQueueCount--
                val a = aimAngle + (Math.random().toFloat() - 0.5f) * 0.07f
                balls.add(
                    Ball(
                        x = launchX,
                        y = launcherY - 4f,
                        dx = cos(a),
                        dy = sin(a),
                        px = launchX,
                        py = launcherY - 4f
                    )
                )
                soundManager.playTick()
                launchTimer += launchInterval()
            }

            _ballsInFlight.value = balls.size + launchQueueCount

            // Substepped physics simulation
            val sp = GameGeometry.BALL_SPEED * speedMult
            val steps = max(1, ceil(sp * dt / 4f).toInt())
            val sdt = dt / steps

            val toRemove = mutableListOf<Ball>()
            for (ball in balls) {
                ball.px = ball.x
                ball.py = ball.y
                var dead = false
                for (s in 0 until steps) {
                    val code = stepProjectile(ball, sp, sdt, nowSec)
                    if (code == 2) {
                        dead = true
                        break
                    }
                }
                if (dead) toRemove.add(ball)
            }
            balls.removeAll(toRemove)

            // Failsafe: balls stuck over 20s
            if (nowSec - shotStartTime > 20f) {
                balls.clear()
                launchQueueCount = 0
            }

            // Shot ends when all balls are finished
            if (launchQueueCount == 0 && balls.isEmpty()) {
                endShot()
            }
        } else {
            _ballsInFlight.value = 0
        }

        // Update particles
        val deadParticles = mutableListOf<Particle>()
        for (p in particles) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 340f * dt
            p.life -= dt / p.dur
            if (p.life <= 0f) deadParticles.add(p)
        }
        particles.removeAll(deadParticles)

        // Update floating texts
        val deadFloats = mutableListOf<FloatingText>()
        for (f in floats) {
            f.y -= 42f * dt
            f.life -= dt / 0.9f
            if (f.life <= 0f) deadFloats.add(f)
        }
        floats.removeAll(deadFloats)

        frameTick++
    }

    private fun stepProjectile(p: Ball, sp: Float, sdt: Float, now: Float): Int {
        p.x += p.dx * sp * sdt
        p.y += p.dy * sp * sdt

        // Wall reflections
        if (p.x < GameGeometry.BALL_R) {
            p.x = GameGeometry.BALL_R
            if (p.dx < 0) p.dx = -p.dx
        } else if (p.x > GameGeometry.W - GameGeometry.BALL_R) {
            p.x = GameGeometry.W - GameGeometry.BALL_R
            if (p.dx > 0) p.dx = -p.dx
        }
        if (p.y < 56f + GameGeometry.BALL_R) {
            p.y = 56f + GameGeometry.BALL_R
            if (p.dy < 0) p.dy = -p.dy
        }

        // Brick collisions
        for (br in bricks) {
            val ddx = p.x - br.cx
            val ddy = p.y - br.cy
            val rr = br.boundR + GameGeometry.BALL_R
            if (ddx * ddx + ddy * ddy > rr * rr) continue
            if (p.immuneBrickId == br.id && now < p.immuneUntilSec) continue

            val h = GameGeometry.circlePoly(p.x, p.y, GameGeometry.BALL_R, br.poly, br.cx, br.cy)
            if (h != null) {
                p.x += h.nx * (h.depth + 0.06f)
                p.y += h.ny * (h.depth + 0.06f)
                val vd = p.dx * h.nx + p.dy * h.ny
                if (vd < 0) {
                    p.dx -= 2 * vd * h.nx
                    p.dy -= 2 * vd * h.ny
                }
                p.immuneBrickId = br.id
                p.immuneUntilSec = now + 0.04f

                hitBrick(br, p.x, p.y, now)

                if (Math.abs(p.dy) < 0.12f) {
                    p.dy = if (p.dy < 0) -0.12f else 0.12f
                    renorm(p)
                }
                return 1
            }
        }

        // Anti-stall downward bias
        if (Math.abs(p.dy) < 0.1f) {
            p.dy = 0.1f
            renorm(p)
        }

        if (p.y > fieldHeight + GameGeometry.BALL_R + 6f) return 2 // Fallen off bottom
        return 0
    }

    private fun renorm(p: Ball) {
        val l = hypot(p.dx, p.dy).let { if (it == 0f) 1f else it }
        p.dx /= l
        p.dy /= l
    }

    private fun hitBrick(br: Brick, x: Float, y: Float, now: Float) {
        if (now - lastHitAt > GameGeometry.COMBO_WINDOW) {
            _combo.value = 0
        }
        lastHitAt = now
        val newCombo = _combo.value + 1
        _combo.value = newCombo
        comboPop = 1f
        if (newCombo % 10 == 0) soundManager.playCombo()

        br.flash = 1f
        spawnSparks(x, y, br.tierColor, 4)

        if (!br.isPowerUp) {
            br.hp--
            if (br.hp <= 0) {
                destroyBrick(br, now)
            } else {
                soundManager.playHit(newCombo)
            }
        } else {
            destroyBrick(br, now)
        }
    }

    private fun destroyBrick(br: Brick, now: Float) {
        bricks.remove(br)
        totalBricksDestroyedInRun++
        if (!br.isPowerUp) {
            spawnBurst(br)
            val mult = max(1, _combo.value)
            val gained = br.maxHp * mult
            _score.value += gained
            floats.add(FloatingText(br.cx, br.cy, "+$gained", Color.White, 1f, 15f))
            soundManager.playBreak()
            shake = min(9f, shake + 2.5f)

            if (_score.value > _bestScore.value) {
                _bestScore.value = _score.value
                _isNewBest.value = true
                viewModelScope.launch {
                    repository.updateBestScore(_score.value, totalBricksDestroyedInRun)
                }
            }
        } else {
            applyPower(br, now)
        }
    }

    private fun applyPower(br: Brick, now: Float) {
        val type = br.powerUpType ?: return
        when (type) {
            PowerUpType.BALL_1 -> addBalls(1)
            PowerUpType.BALL_5 -> addBalls(5)
            PowerUpType.SPEED -> speedMult = 1.65f
            PowerUpType.BOOST -> {
                _combo.value += 5
                lastHitAt = now
            }
        }
        floats.add(FloatingText(br.cx, br.cy, type.label, type.color, 1f, 14f))
        soundManager.playPower()
    }

    private fun addBalls(n: Int) {
        val updated = min(GameGeometry.MAX_BALLS, _ballsOwned.value + n)
        _ballsOwned.value = updated
        if (_gameState.value == GameState.SHOOTING && launchQueueCount > 0) {
            launchQueueCount += n
        }
    }

    private fun spawnSparks(x: Float, y: Float, color: Color, count: Int) {
        for (i in 0 until count) {
            if (particles.size > 240) break
            val a = (Math.random() * 2.0 * PI).toFloat()
            val s = 70f + (Math.random() * 140f).toFloat()
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(a) * s,
                    vy = sin(a) * s - 60f,
                    size = 2.2f + (Math.random() * 1.6f).toFloat(),
                    life = 1f,
                    dur = 0.3f + (Math.random() * 0.2f).toFloat(),
                    color = color
                )
            )
        }
    }

    private fun spawnBurst(br: Brick) {
        val cols = listOf(br.tierColor, Color.White, br.tierColor)
        for (i in 0 until 13) {
            if (particles.size > 240) break
            val a = (Math.random() * 2.0 * PI).toFloat()
            val s = 60f + (Math.random() * 230f).toFloat()
            particles.add(
                Particle(
                    x = br.cx,
                    y = br.cy,
                    vx = cos(a) * s,
                    vy = sin(a) * s - 80f,
                    size = 2.5f + (Math.random() * 2.5f).toFloat(),
                    life = 1f,
                    dur = 0.45f + (Math.random() * 0.35f).toFloat(),
                    color = cols[i % 3]
                )
            )
        }
    }

    private fun endShot() {
        speedMult = 1f
        comboFade = 1f

        // Board drops one row
        for (b in bricks) {
            b.row++
            GameGeometry.layoutBrick(b)
        }
        turn++
        spawnRow(0, turn)
        shiftT = 0f

        // Check lose condition: if any brick touches or crosses dangerY
        for (b in bricks) {
            if (GameGeometry.TOP + (b.row + 1) * GameGeometry.CELL >= dangerY - 4f) {
                gameOver()
                return
            }
        }

        _gameState.value = GameState.AIMING
    }

    private fun gameOver() {
        _gameState.value = GameState.GAME_OVER
        isAiming = false
        soundManager.playGameOver()
        shake = 12f

        viewModelScope.launch {
            repository.recordGameFinished(
                finalScore = _score.value,
                bricksBroken = totalBricksDestroyedInRun,
                currentBest = _bestScore.value,
                totalGames = totalGamesPlayed
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
    }
}
