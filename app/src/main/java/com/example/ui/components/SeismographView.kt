package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SeismographSample
import com.example.ui.theme.KyoshinCardBorder
import kotlin.math.abs
import kotlin.math.max

@Composable
fun SeismographView(
    samples: List<SeismographSample>,
    stationName: String,
    modifier: Modifier = Modifier
) {
    val peakGal = samples.maxOfOrNull {
        max(abs(it.ew), max(abs(it.ns), abs(it.ud)))
    } ?: 0.1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF090E1A))
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
            .testTag("seismograph_view")
    ) {
        // Title Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "リアルタイム波形モニター",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[$stationName]",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = String.format("Peak: %.2f gal", peakGal),
                color = if (peakGal > 10f) Color(0xFFFF5252) else Color(0xFF4ADE80),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3-axis Oscilloscope Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF04070D))
        ) {
            val w = size.width
            val h = size.height
            val trackHeight = h / 3f

            // Zero center lines for EW, NS, UD
            val ewCenter = trackHeight * 0.5f
            val nsCenter = trackHeight * 1.5f
            val udCenter = trackHeight * 2.5f

            val gridColor = Color(0xFF162035)
            drawLine(gridColor, Offset(0f, trackHeight), Offset(w, trackHeight), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, trackHeight * 2), Offset(w, trackHeight * 2), strokeWidth = 1f)

            // Dotted center zero lines
            drawLine(Color(0xFF1E293B), Offset(0f, ewCenter), Offset(w, ewCenter), strokeWidth = 1f)
            drawLine(Color(0xFF1E293B), Offset(0f, nsCenter), Offset(w, nsCenter), strokeWidth = 1f)
            drawLine(Color(0xFF1E293B), Offset(0f, udCenter), Offset(w, udCenter), strokeWidth = 1f)

            if (samples.size < 2) return@Canvas

            // Dynamic scaling factor
            val maxAmp = max(peakGal, 1.0f)
            val scale = (trackHeight * 0.42f) / maxAmp
            val stepX = w / (samples.size - 1)

            val ewPath = Path()
            val nsPath = Path()
            val udPath = Path()

            samples.forEachIndexed { i, sample ->
                val x = i * stepX
                val ewY = ewCenter - (sample.ew * scale).coerceIn(-trackHeight * 0.48f, trackHeight * 0.48f)
                val nsY = nsCenter - (sample.ns * scale).coerceIn(-trackHeight * 0.48f, trackHeight * 0.48f)
                val udY = udCenter - (sample.ud * scale).coerceIn(-trackHeight * 0.48f, trackHeight * 0.48f)

                if (i == 0) {
                    ewPath.moveTo(x, ewY)
                    nsPath.moveTo(x, nsY)
                    udPath.moveTo(x, udY)
                } else {
                    ewPath.lineTo(x, ewY)
                    nsPath.lineTo(x, nsY)
                    udPath.lineTo(x, udY)
                }
            }

            // Draw waveforms: EW (Cyan), NS (Lime/Yellow), UD (Magenta)
            drawPath(ewPath, color = Color(0xFF00E5FF), style = Stroke(width = 1.6f))
            drawPath(nsPath, color = Color(0xFFFFD600), style = Stroke(width = 1.6f))
            drawPath(udPath, color = Color(0xFFFF4081), style = Stroke(width = 1.6f))
        }

        // Legend row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "● EW 東西", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "● NS 南北", color = Color(0xFFFFD600), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "● UD 上下", color = Color(0xFFFF4081), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "サンプリング: 100Hz", color = Color(0xFF64748B), fontSize = 9.sp)
        }
    }
}
