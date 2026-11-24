package com.ganeshaher.calai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CalendarSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1650, easing = LinearEasing))
        onFinished()
    }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(colors.background, colors.primaryContainer))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer { alpha = ((1f - progress.value) * 8f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Canvas(Modifier.size(180.dp)) {
                val unit = size.width / 100f
                val ink = colors.primary
                val phase = progress.value
                val orbit = ((phase - .02f) / .72f).coerceIn(0f, 1f)
                val accent = Color(0xFF9A49D3)
                drawCircle(
                    brush = Brush.radialGradient(listOf(colors.surface, colors.primaryContainer.copy(alpha = 0f))),
                    radius = 50 * unit
                )
                drawArc(
                    color = accent.copy(alpha = .32f),
                    startAngle = -110f, sweepAngle = 360f * orbit, useCenter = false,
                    topLeft = Offset(6 * unit, 6 * unit), size = Size(88 * unit, 88 * unit),
                    style = Stroke(1.3f * unit, cap = StrokeCap.Round)
                )
                val angle = (-110 + 360 * orbit) * PI / 180
                val sparkCenter = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (44 * unit)
                val sparkAlpha = (orbit * 8f).coerceAtMost(1f) * ((.9f - phase) * 8f).coerceIn(0f, 1f)
                drawCircle(accent, radius = 5 * unit, center = sparkCenter, alpha = sparkAlpha * .19f)
                drawCircle(accent, radius = 2.5f * unit, center = sparkCenter, alpha = sparkAlpha)

                val outline = Path().apply {
                    addRoundRect(RoundRect(Rect(25 * unit, 28 * unit, 75 * unit, 78 * unit), CornerRadius(8 * unit)))
                }
                val measure = PathMeasure().apply { setPath(outline, false) }
                val traced = Path()
                val outlineFraction = ((phase - .04f) * 3f).coerceIn(0f, 1f)
                measure.getSegment(0f, measure.length * outlineFraction, traced, true)
                val lineStyle = Stroke(2.5f * unit, cap = StrokeCap.Round)
                drawPath(traced, ink, style = lineStyle)
                val detail = ((phase - .26f) * 8f).coerceIn(0f, 1f)
                for (x in listOf(38f, 62f)) {
                    drawLine(ink, Offset(x * unit, 23 * unit), Offset(x * unit, 34 * unit),
                        strokeWidth = 3 * unit, cap = StrokeCap.Round, alpha = detail)
                }
                drawLine(ink, Offset(25 * unit, 41 * unit), Offset(75 * unit, 41 * unit),
                    strokeWidth = 2 * unit, alpha = detail)
                repeat(6) { index ->
                    val threshold = .32f + index * .038f
                    val opacity = ((phase - threshold) * 9f).coerceIn(0f, 1f)
                    drawCircle(
                        color = ink,
                        radius = 2.7f * unit,
                        center = Offset((37 + index % 3 * 13) * unit, (53 + index / 3 * 13) * unit),
                        alpha = opacity
                    )
                }
                val sparkle = ((phase - .46f) * 6f).coerceIn(0f, 1f)
                val point = Offset(78 * unit, 31 * unit)
                val radius = (4 + 11 * sparkle) * unit
                val path = Path().apply {
                    moveTo(point.x, point.y - radius)
                    lineTo(point.x + radius * .28f, point.y - radius * .28f)
                    lineTo(point.x + radius, point.y)
                    lineTo(point.x + radius * .28f, point.y + radius * .28f)
                    lineTo(point.x, point.y + radius)
                    lineTo(point.x - radius * .28f, point.y + radius * .28f)
                    lineTo(point.x - radius, point.y)
                    lineTo(point.x - radius * .28f, point.y - radius * .28f)
                    close()
                }
                drawPath(path, accent, alpha = sparkle)

                val confirmation = ((phase - .64f) * 7f).coerceIn(0f, 1f)
                val badge = Offset(74 * unit, 74 * unit)
                drawCircle(ink, radius = 11 * unit * confirmation, center = badge)
                val check = Path().apply {
                    moveTo(69 * unit, 74 * unit)
                    lineTo(73 * unit, 78 * unit)
                    lineTo(80 * unit, 70 * unit)
                }
                measure.setPath(check, false)
                val tracedCheck = Path()
                measure.getSegment(0f, measure.length * confirmation, tracedCheck, true)
                drawPath(tracedCheck, colors.onPrimary, style = lineStyle)
            }
            Text("CalAi", color = colors.onBackground, style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("AI calendar assistant", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
