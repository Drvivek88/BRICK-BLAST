package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.Brick
import com.example.game.BrickShape
import com.example.game.GameGeometry
import com.example.game.GameState
import com.example.game.GameViewModel
import com.example.game.PowerUpType
import com.example.ui.theme.ArcadeBg
import com.example.ui.theme.ArcadeBorder
import com.example.ui.theme.ArcadeCard
import com.example.ui.theme.ArcadeSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.TextMuted
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BallBlastScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val isNewBest by viewModel.isNewBest.collectAsStateWithLifecycle()
    val ballsOwned by viewModel.ballsOwned.collectAsStateWithLifecycle()
    val ballsInFlight by viewModel.ballsInFlight.collectAsStateWithLifecycle()
    val combo by viewModel.combo.collectAsStateWithLifecycle()
    val adUsesLeft by viewModel.adUsesLeft.collectAsStateWithLifecycle()
    val adTimerSec by viewModel.adTimerSec.collectAsStateWithLifecycle()
    val adClaimReady by viewModel.adClaimReady.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArcadeBg)
    ) {
        // Main Game Area with Layout constraints
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()

            // Update logical height according to aspect ratio
            LaunchedEffect(canvasWidth, canvasHeight) {
                viewModel.updateScreenDimensions(canvasWidth, canvasHeight)
            }

            val scale = minOf(canvasWidth / GameGeometry.W, canvasHeight / 560f)
            val logicalW = GameGeometry.W
            val logicalH = viewModel.fieldHeight
            val renderW = logicalW * scale
            val renderH = logicalH * scale
            val offsetX = (canvasWidth - renderW) / 2f
            val offsetY = (canvasHeight - renderH) / 2f

            // Game Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_canvas")
                    .pointerInput(gameState) {
                        if (gameState != GameState.AIMING) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val lx = (down.position.x - offsetX) / scale
                            val ly = (down.position.y - offsetY) / scale
                            viewModel.onAimStart(lx, ly)

                            var lastX = lx
                            var lastY = ly

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    lastX = (change.position.x - offsetX) / scale
                                    lastY = (change.position.y - offsetY) / scale
                                    viewModel.onAimMove(lastX, lastY)
                                    change.consume()
                                } else {
                                    viewModel.onAimRelease(lastX, lastY)
                                    change.consume()
                                    break
                                }
                            }
                        }
                    }
            ) {
                // Ensure Canvas redraws on every tick of physics / animation
                val currentTick = viewModel.frameTick

                // Background & Grid
                drawRect(color = ArcadeSurface)

                // Render in scaled logical coordinates
                clipRect(left = offsetX, top = offsetY, right = offsetX + renderW, bottom = offsetY + renderH) {
                    val shakeX = if (viewModel.shake > 0.2f) (Math.random().toFloat() - 0.5f) * viewModel.shake * scale else 0f
                    val shakeY = if (viewModel.shake > 0.2f) (Math.random().toFloat() - 0.5f) * viewModel.shake * scale else 0f

                    // Subtle background grid
                    val gridCol = Color(0x0CFFFFFF)
                    for (c in 0..GameGeometry.COLS) {
                        val gx = offsetX + c * GameGeometry.CELL * scale
                        drawLine(
                            color = gridCol,
                            start = Offset(gx, offsetY),
                            end = Offset(gx, offsetY + renderH),
                            strokeWidth = 1f
                        )
                    }

                    // Danger zone strip
                    val dangerLogicalY = viewModel.dangerY
                    val dangerScreenY = offsetY + dangerLogicalY * scale
                    val isDangerNear = gameState != GameState.MENU && viewModel.bricks.any {
                        GameGeometry.TOP + (it.row + 1) * GameGeometry.CELL > dangerLogicalY - GameGeometry.CELL * 2.2f
                    }

                    val dangerBg = if (isDangerNear) Color(0x1EF43F5E) else Color(0x0AFFFFFF)
                    drawRect(
                        color = dangerBg,
                        topLeft = Offset(offsetX, dangerScreenY),
                        size = Size(renderW, offsetY + renderH - dangerScreenY)
                    )

                    val dashColor = if (isDangerNear) Color(0xFFF43F5E).copy(alpha = pulseAlpha) else Color(0x4D94A3B8)
                    drawLine(
                        color = dashColor,
                        start = Offset(offsetX, dangerScreenY),
                        end = Offset(offsetX + renderW, dangerScreenY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f), 0f)
                    )

                    // Draw Bricks with row slide animation
                    val shiftPx = -(1f - (1f - (1f - viewModel.shiftT) * (1f - viewModel.shiftT) * (1f - viewModel.shiftT))) * GameGeometry.CELL * scale

                    for (brick in viewModel.bricks) {
                        drawBrickOnCanvas(
                            brick = brick,
                            scale = scale,
                            offsetX = offsetX + shakeX,
                            offsetY = offsetY + shakeY + shiftPx
                        )
                    }

                    // Draw Particles
                    for (p in viewModel.particles) {
                        val px = offsetX + p.x * scale + shakeX
                        val py = offsetY + p.y * scale + shakeY
                        drawCircle(
                            color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                            radius = p.size * scale * p.life.coerceIn(0.2f, 1f),
                            center = Offset(px, py)
                        )
                    }

                    // Draw Aim Trajectory Preview
                    if (viewModel.isAiming && gameState == GameState.AIMING && viewModel.aimPreviewPoints.size > 1) {
                        val pts = viewModel.aimPreviewPoints
                        for (i in pts.indices) {
                            val pt = pts[i]
                            val px = offsetX + pt.x * scale
                            val py = offsetY + pt.y * scale
                            val progress = i.toFloat() / pts.size
                            val alpha = (0.85f - progress * 0.7f).coerceIn(0.12f, 0.9f)
                            drawCircle(
                                color = Color.White.copy(alpha = alpha),
                                radius = 2.4f * scale,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // Draw Flying Balls
                    for (ball in viewModel.balls) {
                        val bx = offsetX + ball.x * scale + shakeX
                        val by = offsetY + ball.y * scale + shakeY
                        val pbx = offsetX + ball.px * scale + shakeX
                        val pby = offsetY + ball.py * scale + shakeY

                        // Motion trail
                        drawLine(
                            color = Color(0x40FFFFFF),
                            start = Offset(pbx, pby),
                            end = Offset(bx, by),
                            strokeWidth = 5f * scale,
                            cap = StrokeCap.Round
                        )
                        // Ball Body
                        drawCircle(
                            color = Color.White,
                            radius = GameGeometry.BALL_R * scale,
                            center = Offset(bx, by)
                        )
                    }

                    // Draw Launcher Base
                    val launchPx = offsetX + viewModel.launchX * scale
                    val launchPy = offsetY + viewModel.launcherY * scale

                    // Aiming Direction Arrow
                    if (viewModel.isAiming && gameState == GameState.AIMING) {
                        val dirX = cos(viewModel.aimAngle)
                        val dirY = sin(viewModel.aimAngle)
                        drawLine(
                            color = Color(0xFFE7ECFF),
                            start = Offset(launchPx + dirX * 10f * scale, launchPy + dirY * 10f * scale),
                            end = Offset(launchPx + dirX * 36f * scale, launchPy + dirY * 36f * scale),
                            strokeWidth = 6f * scale,
                            cap = StrokeCap.Round
                        )
                    }

                    // Launcher Circle
                    drawCircle(
                        color = Color(0xFF141931),
                        radius = viewModel.launchR * scale,
                        center = Offset(launchPx, launchPy)
                    )
                    drawCircle(
                        color = Color(0xFFE7ECFF),
                        radius = viewModel.launchR * scale,
                        center = Offset(launchPx, launchPy),
                        style = Stroke(width = 3f * scale)
                    )

                    // Balls Count inside Launcher
                    val displayCount = if (gameState == GameState.SHOOTING) ballsInFlight else ballsOwned
                    drawTextNative(
                        text = displayCount.toString(),
                        x = launchPx,
                        y = launchPy + 5f * scale,
                        textSizeSp = 18f * scale,
                        textColor = Color.White,
                        isBold = true
                    )
                    drawTextNative(
                        text = "BALLS",
                        x = launchPx,
                        y = launchPy + (viewModel.launchR + 14f) * scale,
                        textSizeSp = 9f * scale,
                        textColor = TextMuted,
                        isBold = true
                    )

                    // Floating Score / Pickup Texts
                    for (f in viewModel.floats) {
                        val fx = offsetX + f.x * scale
                        val fy = offsetY + f.y * scale
                        drawTextNative(
                            text = f.text,
                            x = fx,
                            y = fy,
                            textSizeSp = f.sizeSp * scale,
                            textColor = f.color.copy(alpha = f.life.coerceIn(0f, 1f)),
                            isBold = true
                        )
                    }

                    // Combo HUD
                    if (combo >= 2 && (gameState == GameState.SHOOTING || viewModel.comboFade > 0f)) {
                        val comboAlpha = if (gameState == GameState.SHOOTING) 1f else viewModel.comboFade
                        val comboCol = when {
                            combo >= 25 -> NeonRose
                            combo >= 12 -> NeonYellow
                            else -> Color(0xFFE7ECFF)
                        }
                        val comboY = launchPy - 58f * scale
                        drawTextNative(
                            text = "COMBO",
                            x = launchPx,
                            y = comboY - 18f * scale,
                            textSizeSp = 11f * scale,
                            textColor = TextMuted.copy(alpha = comboAlpha),
                            isBold = true
                        )
                        drawTextNative(
                            text = "×$combo",
                            x = launchPx,
                            y = comboY,
                            textSizeSp = (28f + viewModel.comboPop * 8f) * scale,
                            textColor = comboCol.copy(alpha = comboAlpha),
                            isBold = true
                        )
                    }
                }
            }
        }

        // Top HUD Bar
        TopHudBar(
            score = score,
            bestScore = bestScore,
            isNewBest = isNewBest,
            adUsesLeft = adUsesLeft,
            isAdDisabled = adUsesLeft <= 0 || gameState != GameState.AIMING || ballsOwned >= GameGeometry.MAX_BALLS,
            isMuted = isMuted,
            gameState = gameState,
            onAdClick = { viewModel.openRewardedAd() },
            onMuteClick = { viewModel.toggleMute() },
            onPauseClick = { viewModel.pauseGame() }
        )

        // Overlay Menus
        when (gameState) {
            GameState.MENU -> {
                MenuOverlay(
                    bestScore = bestScore,
                    onPlayClick = { viewModel.startGame() }
                )
            }
            GameState.PAUSED -> {
                PauseOverlay(
                    onResumeClick = { viewModel.resumeGame() },
                    onRestartClick = { viewModel.startGame() }
                )
            }
            GameState.AD_ACTIVE -> {
                RewardedAdModal(
                    timerSec = adTimerSec,
                    isClaimReady = adClaimReady,
                    onClaimClick = { viewModel.claimRewardedAd() },
                    onSkipClick = { viewModel.cancelRewardedAd() }
                )
            }
            GameState.GAME_OVER -> {
                GameOverOverlay(
                    finalScore = score,
                    bestScore = bestScore,
                    isNewBest = isNewBest,
                    onRestartClick = { viewModel.startGame() }
                )
            }
            else -> {}
        }
    }
}

// Canvas Drawing Helpers
private fun DrawScope.drawBrickOnCanvas(
    brick: Brick,
    scale: Float,
    offsetX: Float,
    offsetY: Float
) {
    val h = (GameGeometry.CELL / 2f) * scale
    val bcx = offsetX + brick.cx * scale
    val bcy = offsetY + brick.cy * scale

    if (!brick.isPowerUp) {
        val polyPath = Path()
        if (brick.shape == BrickShape.SQUARE) {
            val left = bcx - h + 2f * scale
            val top = bcy - h + 2f * scale
            val size = (GameGeometry.CELL - 4f) * scale
            drawRoundRect(
                color = brick.tierColor,
                topLeft = Offset(left, top),
                size = Size(size, size),
                cornerRadius = CornerRadius(7f * scale, 7f * scale)
            )
            drawRoundRect(
                color = Color(0x38FFFFFF),
                topLeft = Offset(left, top),
                size = Size(size, size),
                cornerRadius = CornerRadius(7f * scale, 7f * scale),
                style = Stroke(width = 2f * scale)
            )
            if (brick.flash > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = (brick.flash * 0.6f).coerceIn(0f, 1f)),
                    topLeft = Offset(left, top),
                    size = Size(size, size),
                    cornerRadius = CornerRadius(7f * scale, 7f * scale)
                )
            }
        } else {
            // Draw Diamond or Triangle Polygon
            if (brick.poly.isNotEmpty()) {
                val p0 = brick.poly[0]
                polyPath.moveTo(offsetX + p0.x * scale, offsetY + p0.y * scale)
                for (i in 1 until brick.poly.size) {
                    val p = brick.poly[i]
                    polyPath.lineTo(offsetX + p.x * scale, offsetY + p.y * scale)
                }
                polyPath.close()

                drawPath(path = polyPath, color = brick.tierColor)
                drawPath(path = polyPath, color = Color(0x38FFFFFF), style = Stroke(width = 2f * scale))
                if (brick.flash > 0f) {
                    drawPath(path = polyPath, color = Color.White.copy(alpha = (brick.flash * 0.6f).coerceIn(0f, 1f)))
                }
            }
        }

        // Live HP Text
        val hpStr = brick.hp.toString()
        val textSize = when {
            hpStr.length <= 2 -> 20f
            hpStr.length == 3 -> 16f
            else -> 13f
        } * scale

        drawTextNative(
            text = hpStr,
            x = bcx,
            y = bcy + 5f * scale,
            textSizeSp = textSize,
            textColor = Color.White,
            isBold = true
        )
    } else {
        // Power-Up Block
        val pSize = 52f * scale
        val left = bcx - pSize / 2f
        val top = bcy - pSize / 2f

        drawRoundRect(
            color = Color(0xFF232A4D),
            topLeft = Offset(left, top),
            size = Size(pSize, pSize),
            cornerRadius = CornerRadius(11f * scale, 11f * scale)
        )
        val puColor = brick.powerUpType?.color ?: NeonGreen
        drawRoundRect(
            color = puColor,
            topLeft = Offset(left, top),
            size = Size(pSize, pSize),
            cornerRadius = CornerRadius(11f * scale, 11f * scale),
            style = Stroke(width = 2.5f * scale)
        )

        // Draw Powerup Badge Text / Icon
        val badge = when (brick.powerUpType) {
            PowerUpType.BALL_1 -> "+1"
            PowerUpType.BALL_5 -> "+5"
            PowerUpType.SPEED -> "⚡"
            PowerUpType.BOOST -> "★"
            null -> "+"
        }
        drawTextNative(
            text = badge,
            x = bcx,
            y = bcy + 6f * scale,
            textSizeSp = 18f * scale,
            textColor = puColor,
            isBold = true
        )
    }
}

private fun DrawScope.drawTextNative(
    text: String,
    x: Float,
    y: Float,
    textSizeSp: Float,
    textColor: Color,
    isBold: Boolean = false
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = textColor.toArgb()
            textSize = textSizeSp
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(120, 0, 0, 0))
        }
        drawText(text, x, y, paint)
    }
}

// Top HUD Component
@Composable
private fun TopHudBar(
    score: Int,
    bestScore: Int,
    isNewBest: Boolean,
    adUsesLeft: Int,
    isAdDisabled: Boolean,
    isMuted: Boolean,
    gameState: GameState,
    onAdClick: () -> Unit,
    onMuteClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SCORE Pill
        Surface(
            color = Color(0x1AFFFFFF),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadeBorder),
            modifier = Modifier.testTag("score_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCORE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = score.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        // BEST Pill
        Surface(
            color = if (isNewBest) Color(0x33FACC15) else Color(0x1AFFFFFF),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isNewBest) NeonYellow.copy(alpha = 0.5f) else ArcadeBorder
            ),
            modifier = Modifier.testTag("best_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BEST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = if (isNewBest) NeonYellow else TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = bestScore.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isNewBest) NeonYellow else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Rewarded Ad Boost Button (Doubles Balls)
        Surface(
            color = if (isAdDisabled) Color(0x0D22D3EE) else Color(0x2422D3EE),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isAdDisabled) NeonCyan.copy(alpha = 0.25f) else NeonCyan.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .testTag("ad_double_balls_button")
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isAdDisabled, onClick = onAdClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "×2",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isAdDisabled) NeonCyan.copy(alpha = 0.4f) else NeonCyan
                )
                Surface(
                    color = if (isAdDisabled) Color(0x1A22D3EE) else Color(0x4022D3EE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = adUsesLeft.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAdDisabled) NeonCyan.copy(alpha = 0.4f) else NeonCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Sound Toggle
        IconButton(
            onClick = onMuteClick,
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .border(1.dp, ArcadeBorder, RoundedCornerShape(12.dp))
                .testTag("sound_toggle_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) TextMuted else Color(0xFFAAB3D0),
                modifier = Modifier.size(18.dp)
            )
        }

        // Pause Button
        if (gameState == GameState.AIMING || gameState == GameState.SHOOTING) {
            IconButton(
                onClick = onPauseClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, ArcadeBorder, RoundedCornerShape(12.dp))
                    .testTag("pause_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = Color(0xFFAAB3D0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Overlay: Start Menu
@Composable
private fun MenuOverlay(
    bestScore: Int,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB070912))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .background(ArcadeCard.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                .border(1.dp, ArcadeBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            // Stylized Title: BALL BLAST
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleLetters = listOf(
                    'B' to Color(0xFF4CC9F0),
                    'A' to Color(0xFF4ADE80),
                    'L' to Color(0xFFFACC15),
                    'L' to Color(0xFFFB923C),
                    ' ' to Color.Transparent,
                    'B' to Color(0xFFF43F5E),
                    'L' to Color(0xFFA78BFA),
                    'A' to Color(0xFF4CC9F0),
                    'S' to Color(0xFF4ADE80),
                    'T' to Color(0xFFFACC15)
                )
                for ((char, col) in titleLetters) {
                    if (char == ' ') {
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Text(
                            text = char.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = col
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game description & Instructions
            Surface(
                color = Color(0x14FFFFFF),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "• Drag anywhere to aim, release to fire\n• Every run starts with 20 balls — grab pickups to grow\n• Tap the ×2 AD button to double your volley\n• Destroy numbered bricks before they reach the red line",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFB0B9D6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "BEST SCORE:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = bestScore.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // High-contrast Play button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), spotColor = NeonGreen)
                    .testTag("play_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PLAY",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// Overlay: Pause
@Composable
private fun PauseOverlay(
    onResumeClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB070912))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(ArcadeCard, RoundedCornerShape(20.dp))
                .border(1.dp, ArcadeBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "PAUSED",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onResumeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resume_button")
            ) {
                Text(text = "RESUME", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRestartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x26FFFFFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("restart_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "RESTART", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Overlay: Rewarded Ad Modal (Simulated Ad Creative)
@Composable
private fun RewardedAdModal(
    timerSec: Float,
    isClaimReady: Boolean,
    onClaimClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC070912))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(Color(0xFF12172E), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "ADVERTISEMENT · SIMULATED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF5D678C)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "BALL BLAST: DELUXE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Ad Creative Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(0xFF0D1023), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "★ 4.9 · 10M+ plays",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7F89AD),
                    modifier = Modifier.align(Alignment.TopStart)
                )

                Surface(
                    color = NeonGreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "INSTALL FREE!",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF052E16),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Decorative bouncing balls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val ballCols = listOf(
                        Color(0xFF4CC9F0),
                        Color(0xFF4ADE80),
                        Color(0xFFFACC15),
                        Color(0xFFFB923C),
                        Color(0xFFF43F5E),
                        Color(0xFFA78BFA)
                    )
                    for (c in ballCols) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(c, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Countdown Progress Bar
            val progress = (1f - (timerSec / GameGeometry.AD_LEN_SEC)).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = NeonCyan,
                    trackColor = Color(0x1AFFFFFF),
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = kotlin.math.ceil(timerSec).toInt().toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Claim Reward Button
            Button(
                onClick = onClaimClick,
                enabled = isClaimReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color(0xFF052E16),
                    disabledContainerColor = Color(0x334ADE80),
                    disabledContentColor = Color(0x66052E16)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("claim_ad_button")
            ) {
                Text(
                    text = if (isClaimReady) "CLAIM ×2 BALLS!" else "REWARD IN ${kotlin.math.ceil(timerSec).toInt()}s…",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Skip button
            Text(
                text = "Skip — no reward",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier
                    .testTag("skip_ad_button")
                    .clickable(onClick = onSkipClick)
                    .padding(vertical = 6.dp)
            )
        }
    }
}

// Overlay: Game Over
@Composable
private fun GameOverOverlay(
    finalScore: Int,
    bestScore: Int,
    isNewBest: Boolean,
    onRestartClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC070912))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(ArcadeCard, RoundedCornerShape(20.dp))
                .border(1.dp, ArcadeBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "GAME OVER",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = NeonRose
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SCORE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextMuted
            )
            Text(
                text = finalScore.toString(),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isNewBest) {
                Surface(
                    color = Color(0x2BFACC15),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonYellow.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "NEW BEST!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = NeonYellow,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BEST:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bestScore.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRestartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = NeonGreen)
                    .testTag("play_again_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PLAY AGAIN",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
