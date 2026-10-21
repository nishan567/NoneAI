package com.nonea.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { Idle, Listening, Thinking, Speaking }

/**
 * The AI orb: a breathing glass core, a rotating energy ring, ripples while listening (driven by the mic
 * level), orbiting arcs while thinking and a radial waveform while listening/speaking.
 */
@Composable
fun AiOrb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val inf = rememberInfiniteTransition(label = "orb")
    val spinA by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val spinFastA by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spinFast")
    val breatheA by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val phaseA by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase")
    val rippleA by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ripple")
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(100), label = "level")

    val c1 by animateColorAsState(
        when (state) { OrbState.Listening -> p.cyan; OrbState.Speaking -> p.pink; else -> p.violet }, tween(500), label = "c1")
    val c2 by animateColorAsState(
        when (state) { OrbState.Thinking -> p.cyan; OrbState.Listening -> p.violet; OrbState.Speaking -> p.violet; else -> p.cyan }, tween(500), label = "c2")

    // With "remove animations" on, hold every motion value still.
    val spin = if (reduce) 0f else spinA
    val spinFast = if (reduce) 0f else spinFastA
    val breathe = if (reduce) 0.5f else breatheA
    val phase = if (reduce) 0f else phaseA
    val ripple = if (reduce) 0.4f else rippleA

    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val amp = when (state) {
            OrbState.Listening -> 0.04f * breathe + 0.22f * lv
            OrbState.Speaking -> if (reduce) 0.06f else 0.05f + 0.07f * ((sin(phase) + 1f) / 2f)
            else -> 0.05f * breathe
        }
        val coreR = r * 0.5f * (1f + amp)

        // soft outer glow
        drawCircle(
            Brush.radialGradient(listOf(c1.copy(alpha = 0.42f * p.glow), c2.copy(alpha = 0.14f * p.glow), Color.Transparent), center = c, radius = r),
            radius = r, center = c
        )
        // listening ripples
        if (state == OrbState.Listening) {
            val n = if (reduce) 1 else 3
            for (i in 0 until n) {
                val t = (ripple + i / 3f) % 1f
                drawCircle(c1.copy(alpha = (1f - t) * 0.55f), radius = coreR + (r * 0.95f - coreR) * t, center = c,
                    style = Stroke(width = 2.dp.toPx()))
            }
        }
        // rotating energy ring
        rotate(if (state == OrbState.Thinking) spinFast else spin, pivot = c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, c1, c2, Color.Transparent), center = c),
                radius = r * 0.7f, center = c, style = Stroke(width = r * 0.035f, cap = StrokeCap.Round)
            )
        }
        // thinking: three orbiting arcs
        if (state == OrbState.Thinking) {
            rotate(-spinFast * 1.3f, pivot = c) {
                for (k in 0 until 3) {
                    drawArc(c2.copy(alpha = 0.95f), startAngle = k * 120f, sweepAngle = 48f, useCenter = false,
                        topLeft = Offset(c.x - r * 0.84f, c.y - r * 0.84f), size = Size(r * 1.68f, r * 1.68f),
                        style = Stroke(width = r * 0.03f, cap = StrokeCap.Round))
                }
            }
        }
        // radial waveform (listening = mic level, speaking = synthetic voice pulse)
        if (state == OrbState.Listening || state == OrbState.Speaking) {
            val n = 56
            val r0 = r * 0.8f
            for (i in 0 until n) {
                val a = i.toFloat() / n * 2f * PI.toFloat()
                val wob = 0.5f + 0.5f * sin(phase + i * 0.9f) * sin(phase * 0.5f + i * 0.31f)
                val mag = if (state == OrbState.Listening) 0.12f + lv * (0.3f + 0.7f * wob) else 0.15f + 0.85f * wob
                val r1 = r0 + r * 0.16f * mag.coerceIn(0.08f, 1f)
                drawLine(c1.copy(alpha = 0.9f), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                    strokeWidth = r * 0.022f, cap = StrokeCap.Round)
            }
        }
        // glass core with a highlight
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), c1, c2), center = Offset(c.x - coreR * 0.35f, c.y - coreR * 0.4f), radius = coreR * 1.6f),
            radius = coreR, center = c
        )
        drawCircle(Color.White.copy(alpha = 0.3f), radius = coreR, center = c, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Small animated equaliser used inside the composer while listening. */
@Composable
fun MiniWave(level: Float, color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val inf = rememberInfiniteTransition(label = "wave")
    val ph by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(100), label = "lv")
    Canvas(modifier) {
        val n = 11
        val slot = size.width / n
        val w = slot * 0.45f
        for (i in 0 until n) {
            val wob = if (reduce) 0.6f else 0.5f + 0.5f * sin(ph + i * 0.8f)
            val h = size.height * (0.18f + 0.82f * (0.25f + 0.75f * lv) * wob)
            drawRoundRect(color, Offset(slot * i + (slot - w) / 2f, (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2f))
        }
    }
}
