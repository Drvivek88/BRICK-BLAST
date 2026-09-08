package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class GameState {
    MENU,
    AIMING,
    SHOOTING,
    PAUSED,
    AD_ACTIVE,
    GAME_OVER
}

enum class BrickShape {
    SQUARE,
    DIAMOND,
    TRIANGLE
}

enum class PowerUpType(val label: String, val color: Color) {
    BALL_1("+1 BALL", Color(0xFF4ADE80)),
    BALL_5("+5 BALLS", Color(0xFF22D3EE)),
    SPEED("SPEED UP!", Color(0xFFFDE047)),
    BOOST("COMBO +5", Color(0xFFFB923C))
}

data class Brick(
    val id: Long,
    var col: Int,
    var row: Int,
    var hp: Int,
    var maxHp: Int,
    val isPowerUp: Boolean,
    val powerUpType: PowerUpType?,
    val shape: BrickShape,
    val triangleDir: Int = 0, // 0: apex up, 1: right, 2: down, 3: left
    var cx: Float = 0f,
    var cy: Float = 0f,
    var boundR: Float = 0f,
    var poly: List<Offset> = emptyList(),
    var flash: Float = 0f
) {
    val tierColor: Color
        get() {
            if (isPowerUp && powerUpType != null) return powerUpType.color
            return when {
                hp >= 100 -> Color(0xFFA78BFA)
                hp >= 60 -> Color(0xFFF43F5E)
                hp >= 35 -> Color(0xFFFB923C)
                hp >= 20 -> Color(0xFFFACC15)
                hp >= 10 -> Color(0xFF4ADE80)
                else -> Color(0xFF4CC9F0)
            }
        }
}

data class Ball(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    var px: Float = x,
    var py: Float = y,
    var immuneBrickId: Long? = null,
    var immuneUntilSec: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var life: Float, // 1.0 down to 0
    val dur: Float,
    val color: Color
)

data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var life: Float = 1.0f,
    val sizeSp: Float = 15f
)

data class HitResult(
    val nx: Float,
    val ny: Float,
    val depth: Float
)

object GameGeometry {
    const val W = 420f
    const val COLS = 7
    const val CELL = 60f
    const val TOP = 64f
    const val BALL_R = 7f
    const val BALL_SPEED = 560f
    const val START_BALLS = 20
    const val MAX_BALLS = 100
    const val COMBO_WINDOW = 1.5f
    const val AD_LEN_SEC = 5f
    const val AD_USES_PER_RUN = 3

    fun shapeLocal(shape: BrickShape, dir: Int): List<Offset> {
        val h = CELL / 2f
        return when (shape) {
            BrickShape.SQUARE -> listOf(
                Offset(-h + 2f, -h + 2f),
                Offset(h - 2f, -h + 2f),
                Offset(h - 2f, h - 2f),
                Offset(-h + 2f, h - 2f)
            )
            BrickShape.DIAMOND -> {
                val d = h - 3f
                listOf(
                    Offset(0f, -d),
                    Offset(d, 0f),
                    Offset(0f, d),
                    Offset(-d, 0f)
                )
            }
            BrickShape.TRIANGLE -> {
                val t = h - 2f
                when (dir) {
                    0 -> listOf(Offset(-t, t), Offset(t, t), Offset(0f, -t))
                    1 -> listOf(Offset(-t, -t), Offset(-t, t), Offset(t, 0f))
                    2 -> listOf(Offset(-t, -t), Offset(t, -t), Offset(0f, t))
                    else -> listOf(Offset(t, -t), Offset(t, t), Offset(-t, 0f))
                }
            }
        }
    }

    fun layoutBrick(b: Brick) {
        b.cx = b.col * CELL + CELL / 2f
        b.cy = TOP + b.row * CELL + CELL / 2f
        val local = shapeLocal(b.shape, b.triangleDir)
        b.poly = local.map { Offset(it.x + b.cx, it.y + b.cy) }
        var maxD2 = 0f
        for (p in b.poly) {
            val d2 = (p.x - b.cx) * (p.x - b.cx) + (p.y - b.cy) * (p.y - b.cy)
            if (d2 > maxD2) maxD2 = d2
        }
        b.boundR = sqrt(maxD2) + 2f
    }

    // Circle vs convex polygon collision detection
    fun circlePoly(
        px: Float,
        py: Float,
        r: Float,
        poly: List<Offset>,
        cx: Float,
        cy: Float
    ): HitResult? {
        if (poly.size < 3) return null
        var bestD2 = Float.POSITIVE_INFINITY
        var bqx = 0f
        var bqy = 0f
        var bnx = 0f
        var bny = 0f
        var inside = true

        val n = poly.size
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val len2 = ex * ex + ey * ey
            val safeLen2 = if (len2 < 1e-6f) 1e-6f else len2
            var t = ((px - a.x) * ex + (py - a.y) * ey) / safeLen2
            t = if (t < 0f) 0f else if (t > 1f) 1f else t

            val qx = a.x + ex * t
            val qy = a.y + ey * t
            val dx = px - qx
            val dy = py - qy
            val d2 = dx * dx + dy * dy

            // Perpendicular of the edge pointing outward
            var nx = ey
            var ny = -ex
            val mx = (a.x + b.x) / 2f - cx
            val my = (a.y + b.y) / 2f - cy
            if (nx * mx + ny * my < 0) {
                nx = -nx
                ny = -ny
            }
            if ((px - a.x) * nx + (py - a.y) * ny >= 0) {
                inside = false
            }
            if (d2 < bestD2) {
                bestD2 = d2
                bqx = qx
                bqy = qy
                bnx = nx
                bny = ny
            }
        }

        val d = sqrt(bestD2)
        if (inside) {
            val normLen = sqrt(bnx * bnx + bny * bny).let { if (it < 1e-6f) 1f else it }
            return HitResult(bnx / normLen, bny / normLen, r + d + 0.05f)
        }
        if (d < 1e-6f) {
            return HitResult(0f, -1f, r)
        }
        if (d >= r) {
            return null
        }
        return HitResult((px - bqx) / d, (py - bqy) / d, r - d)
    }

    fun hpBase(turn: Int): Int {
        return (7 + turn * 3 + turn * turn * 0.15f).toInt()
    }
}
