package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * High-precision Speedometer/Gal Gauge Indicator for real-time seismic acceleration.
 * Displays log-scaled Gal level from 0 to 1000+ Gal with colored intensity zones,
 * animated needle, peak hold pip, and digital readout.
 */
@Composable
fun GalSpeedometerGauge(
    currentGal: Float,
    peakGal: Float,
    estimatedShindo: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Map Gal to angle (135° to 405°, total 270° sweep)
    // Logarithmic distribution: 0.1, 1, 5, 20, 50, 100, 250, 500, 1000 Gal
    val targetFraction = if (isEnabled && currentGal > 0.05f) {
        val minLog = -1.0 // 0.1 Gal
        val maxLog = 3.0  // 1000 Gal
        val currentLog = log10(currentGal.coerceIn(0.1f, 1500f).toDouble())
        ((currentLog - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "gal_needle_anim"
    )

    val peakFraction = if (isEnabled && peakGal > 0.05f) {
        val minLog = -1.0
        val maxLog = 3.0
        val peakLog = log10(peakGal.coerceIn(0.1f, 1500f).toDouble())
        ((peakLog - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gal_speedometer_gauge"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)
                val radius = (min(w, h) / 2f) - 16.dp.toPx()
                val strokeWidth = 14.dp.toPx()

                val startAngle = 135f
                val sweepAngle = 270f

                // 1. Background Arc Track
                drawArc(
                    color = Color(0xFF1E293B),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (isEnabled) {
                    // 2. Colored Arc Gradient Segments (Green -> Yellow -> Orange -> Red -> Purple)
                    val activeSweep = sweepAngle * animatedFraction
                    if (activeSweep > 1f) {
                        val colors = listOf(
                            Color(0xFF10B981), // <10 Gal (Safe/Micro)
                            Color(0xFF38BDF8), // 10-25 Gal (Shindo 1-2)
                            Color(0xFFFACC15), // 25-80 Gal (Shindo 3-4)
                            Color(0xFFF97316), // 80-250 Gal (Shindo 5)
                            Color(0xFFEF4444), // 250-500 Gal (Shindo 6)
                            Color(0xFF9333EA)  // >500 Gal (Shindo 7 Extreme)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = colors,
                                center = center
                            ),
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 3. Tick Marks & Log Labels
                    // Selected points: 0.1, 1, 10, 50, 100, 500, 1000
                    val tickGals = listOf(0.1f, 1f, 10f, 50f, 100f, 500f, 1000f)
                    val minLog = -1.0
                    val maxLog = 3.0

                    tickGals.forEach { g ->
                        val frac = ((log10(g.toDouble()) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
                        val angleDeg = startAngle + frac * sweepAngle
                        val angleRad = Math.toRadians(angleDeg.toDouble())

                        val tickInnerRadius = radius - strokeWidth / 2f - 4.dp.toPx()
                        val tickOuterRadius = radius + strokeWidth / 2f + 4.dp.toPx()

                        val p1 = Offset(
                            (center.x + tickInnerRadius * cos(angleRad)).toFloat(),
                            (center.y + tickInnerRadius * sin(angleRad)).toFloat()
                        )
                        val p2 = Offset(
                            (center.x + tickOuterRadius * cos(angleRad)).toFloat(),
                            (center.y + tickOuterRadius * sin(angleRad)).toFloat()
                        )

                        drawLine(
                            color = Color(0xFF64748B),
                            start = p1,
                            end = p2,
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    // 4. Peak Hold Pip Indicator (Golden Tick)
                    if (peakFraction > 0.01f) {
                        val peakAngleDeg = startAngle + peakFraction * sweepAngle
                        val peakAngleRad = Math.toRadians(peakAngleDeg.toDouble())

                        val pipRadiusInner = radius - strokeWidth / 2f - 2.dp.toPx()
                        val pipRadiusOuter = radius + strokeWidth / 2f + 5.dp.toPx()

                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(
                                (center.x + pipRadiusInner * cos(peakAngleRad)).toFloat(),
                                (center.y + pipRadiusInner * sin(peakAngleRad)).toFloat()
                            ),
                            end = Offset(
                                (center.x + pipRadiusOuter * cos(peakAngleRad)).toFloat(),
                                (center.y + pipRadiusOuter * sin(peakAngleRad)).toFloat()
                            ),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 5. Needle Pointer
                    val needleAngleDeg = startAngle + animatedFraction * sweepAngle
                    val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                    val needleLength = radius - 16.dp.toPx()
                    val needleTip = Offset(
                        (center.x + needleLength * cos(needleAngleRad)).toFloat(),
                        (center.y + needleLength * sin(needleAngleRad)).toFloat()
                    )

                    drawLine(
                        color = if (currentGal > 100f) Color(0xFFEF4444) else Color(0xFF38BDF8),
                        start = center,
                        end = needleTip,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Needle center hub
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 12.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 6.dp.toPx(),
                        center = center
                    )
                } else {
                    // Disabled center indicator
                    drawCircle(
                        color = Color(0xFF334155),
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }
            }

            // Central Digital Speedometer Readout Display
            Column(
                modifier = Modifier.padding(top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isEnabled) {
                    Text(
                        text = String.format("%.2f", currentGal),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "GAL (cm/s²)",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (estimatedShindo) {
                            "0" -> Color(0xFF334155)
                            "1", "2" -> Color(0xFF0284C7)
                            "3", "4" -> Color(0xFFF59E0B)
                            "5弱", "5強" -> Color(0xFFEA580C)
                            else -> Color(0xFFDC2626)
                        }
                    ) {
                        Text(
                            text = "推定震度 $estimatedShindo",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "--.--",
                        color = Color(0xFF64748B),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "センサー停止中",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Sub-gauge indicators row (Min ~ Max scale indicators)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "0.1 gal", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(text = "10 gal (微震)", color = Color(0xFF64748B), fontSize = 10.sp)
            Text(text = "100 gal (強震)", color = Color(0xFF64748B), fontSize = 10.sp)
            Text(text = "1000+ gal (激震)", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
