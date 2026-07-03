package com.banglu.keyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Banglu strip iconography — one hand-drawn stroke grammar instead of emoji.
 *
 * Emoji glyphs render differently on every OEM font and read as prototype
 * work; these are Canvas strokes with a consistent 2dp rounded pen so the
 * toolbar looks owned, and they inherit whatever ink color the theme passes.
 * All icons share a square viewport and are drawn against 0..1 fractions.
 */

/** Banglu brand accent — the terracotta of the launcher/disclosure surfaces.
 *  Reserved for the live mic so "recording" is the single loudest color. */
val BangluVoiceAccent = Color(0xFFA34F3F)
val BangluVoiceAccentDeep = Color(0xFF7E3A2D)

private fun DrawScope.pen(width: Float = size.minDimension * 0.085f) =
    Stroke(width = width.coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Clipboard: board with a clip tab and two content lines. */
@Composable
fun IconClipboard(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.16f),
            size = Size(w * 0.56f, h * 0.70f),
            cornerRadius = CornerRadius(w * 0.10f),
            style = s
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.38f, h * 0.08f),
            size = Size(w * 0.24f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = s
        )
        drawLine(color, Offset(w * 0.34f, h * 0.45f), Offset(w * 0.66f, h * 0.45f), s.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.62f), Offset(w * 0.58f, h * 0.62f), s.width, StrokeCap.Round)
    }
}

/** Emoji: smile — circle, two dot eyes, arc mouth. */
@Composable
fun IconEmoji(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = s)
        val eyeR = s.width * 0.55f
        drawCircle(color, eyeR, Offset(w * 0.38f, h * 0.42f))
        drawCircle(color, eyeR, Offset(w * 0.62f, h * 0.42f))
        drawArc(
            color = color,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(w * 0.32f, h * 0.30f),
            size = Size(w * 0.36f, h * 0.36f),
            style = s
        )
    }
}

/** Sticker: rounded square with a peeled corner. */
@Composable
fun IconSticker(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.60f, h * 0.82f)
            lineTo(w * 0.30f, h * 0.82f)
            quadraticBezierTo(w * 0.18f, h * 0.82f, w * 0.18f, h * 0.70f)
            lineTo(w * 0.18f, h * 0.30f)
            quadraticBezierTo(w * 0.18f, h * 0.18f, w * 0.30f, h * 0.18f)
            lineTo(w * 0.70f, h * 0.18f)
            quadraticBezierTo(w * 0.82f, h * 0.18f, w * 0.82f, h * 0.30f)
            lineTo(w * 0.82f, h * 0.60f)
            // peeled corner
            lineTo(w * 0.60f, h * 0.82f)
            moveTo(w * 0.82f, h * 0.60f)
            quadraticBezierTo(w * 0.60f, h * 0.58f, w * 0.60f, h * 0.82f)
        }
        drawPath(p, color, style = s)
    }
}

/** Settings: gear — outer ring with tooth stubs crossing it, dot hub.
 *  The ring is what separates it from a sun; rays alone read as brightness. */
@Composable
fun IconGear(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        val c = Offset(w * 0.5f, h * 0.5f)
        val ring = w * 0.26f
        drawCircle(color, radius = ring, center = c, style = s)
        drawCircle(color, radius = w * 0.075f, center = c)
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = c) {
                drawLine(
                    color,
                    Offset(c.x, c.y - ring + s.width * 0.2f),
                    Offset(c.x, c.y - ring - w * 0.10f),
                    s.width,
                    StrokeCap.Round
                )
            }
        }
    }
}

/** More: three ink dots. */
@Composable
fun IconDots(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val r = w * 0.065f
        drawCircle(color, r, Offset(w * 0.26f, h * 0.5f))
        drawCircle(color, r, Offset(w * 0.50f, h * 0.5f))
        drawCircle(color, r, Offset(w * 0.74f, h * 0.5f))
    }
}

/** Collapse: chevron pointing down into the keyboard. */
@Composable
fun IconChevronDown(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.28f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.72f, h * 0.40f)
        }
        drawPath(p, color, style = s)
    }
}

/** Stop: rounded square. */
@Composable
fun IconStop(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.28f),
            size = Size(w * 0.44f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.10f)
        )
    }
}

/** Close/cancel: X. */
@Composable
fun IconClose(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        drawLine(color, Offset(w * 0.32f, h * 0.32f), Offset(w * 0.68f, h * 0.68f), s.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.32f), Offset(w * 0.32f, h * 0.68f), s.width, StrokeCap.Round)
    }
}

/** Retry: three-quarter arc with an arrowhead. */
@Composable
fun IconRetry(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = pen()
        val w = size.width; val h = size.height
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.56f),
            style = s
        )
        val tip = Offset(w * 0.64f, h * 0.20f)
        val p = Path().apply {
            moveTo(tip.x - w * 0.14f, tip.y + h * 0.02f)
            lineTo(tip.x, tip.y)
            lineTo(tip.x - w * 0.02f, tip.y + h * 0.15f)
        }
        drawPath(p, color, style = s)
    }
}

/**
 * The live-dictation badge: terracotta disc, white mic, and while [active] a
 * breathing halo plus an RMS-driven ring so the surface visibly *hears*.
 */
@Composable
fun MicBadge(
    active: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
    idleInk: Color,
    idleBg: Color
) {
    val breath by if (active) {
        rememberInfiniteTransition(label = "micBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "micBreathValue"
        )
    } else {
        animateFloatAsState(0f, label = "micBreathIdle")
    }
    val liveLevel by animateFloatAsState(level.coerceIn(0f, 1f), tween(90), label = "micLevel")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val disc = w * 0.34f
        if (active) {
            drawCircle(BangluVoiceAccent.copy(alpha = 0.10f + 0.10f * breath), disc + w * (0.10f + 0.06f * breath), c)
            drawCircle(BangluVoiceAccent.copy(alpha = 0.28f), disc + w * 0.055f + w * 0.06f * liveLevel, c)
        }
        drawCircle(if (active) BangluVoiceAccent else idleBg, disc, c)
        val ink = if (active) Color.White else idleInk
        val s = Stroke(width = (w * 0.055f).coerceAtLeast(2f), cap = StrokeCap.Round)
        // mic capsule
        drawRoundRect(
            color = ink,
            topLeft = Offset(w * 0.42f, h * 0.28f),
            size = Size(w * 0.16f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = s
        )
        // cradle
        drawArc(
            color = ink,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.36f, h * 0.36f),
            size = Size(w * 0.28f, h * 0.24f),
            style = s
        )
        // stem
        drawLine(ink, Offset(w * 0.50f, h * 0.60f), Offset(w * 0.50f, h * 0.68f), s.width, StrokeCap.Round)
    }
}

/**
 * Center-weighted live waveform: bars rise from the middle outward with the
 * RMS level, each column lagging slightly behind its inner neighbor so the
 * meter moves like sound, not like a progress bar.
 */
@Composable
fun CenterWaveform(
    level: Float,
    modifier: Modifier = Modifier,
    color: Color = BangluVoiceAccent,
    restColor: Color = color.copy(alpha = 0.25f),
    bars: Int = 13
) {
    val animated = (0 until bars).map { i ->
        val centerDistance = kotlin.math.abs(i - bars / 2) / (bars / 2f)
        val weight = 1f - 0.72f * centerDistance
        animateFloatAsState(
            targetValue = (level.coerceIn(0f, 1f) * weight),
            animationSpec = tween(90 + 34 * kotlin.math.abs(i - bars / 2)),
            label = "wave$i"
        )
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Single canvas keeps the bars crisp and cheap.
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val slot = w / bars
        val barW = slot * 0.42f
        for (i in 0 until bars) {
            val v = animated[i].value
            val barH = (h * 0.18f) + (h * 0.74f) * v
            val x = slot * i + (slot - barW) / 2f
            drawRoundRect(
                color = if (v > 0.04f) color else restColor,
                topLeft = Offset(x, (h - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
