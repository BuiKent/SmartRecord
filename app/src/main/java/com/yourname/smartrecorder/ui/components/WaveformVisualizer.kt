package com.yourname.smartrecorder.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

@Composable
fun WaveformVisualizer(
    amplitude: Int,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAmplitude by animateIntAsState(
        targetValue = if (isRecording) amplitude else 0,
        animationSpec = tween(durationMillis = 50),
        label = "amplitude"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // Only add default height if modifier doesn't already specify height
                if (modifier == Modifier) Modifier.height(180.dp) else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2
            val maxAmplitude = 32767f // Max amplitude from MediaRecorder
            val normalizedAmplitude = if (maxAmplitude > 0) {
                (animatedAmplitude.toFloat() / maxAmplitude).coerceIn(0f, 1f)
            } else {
                0f
            }
            
            val barCount = 50
            val barWidth = size.width / barCount
            val maxBarHeight = size.height * 0.8f
            
            for (i in 0 until barCount) {
                // Create wave pattern with variation
                val timeMs = System.currentTimeMillis()
                val phase = (i.toDouble() / barCount) * 2.0 * PI
                val variation = (sin(phase + timeMs / 100.0) * 0.3 + 0.7).toFloat()
                val barHeight = normalizedAmplitude * maxBarHeight * variation
                
                val x = i * barWidth + barWidth / 2
                val startY = centerY - barHeight / 2
                val endY = centerY + barHeight / 2
                
                // Use gradient color based on amplitude
                val color = androidx.compose.ui.graphics.Color(
                    red = if (normalizedAmplitude > 0.5f) 0.3f else 0.5f,
                    green = if (normalizedAmplitude > 0.5f) 0.6f else 0.7f,
                    blue = if (normalizedAmplitude > 0.5f) 1f else 0.8f,
                    alpha = if (normalizedAmplitude > 0.5f) 1f else 0.7f
                )
                
                drawLine(
                    color = color,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = barWidth * 0.6f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

