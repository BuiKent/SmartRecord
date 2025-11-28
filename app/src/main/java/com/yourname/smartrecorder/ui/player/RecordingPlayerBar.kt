package com.yourname.smartrecorder.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourname.smartrecorder.core.utils.TimeFormatter
import kotlin.math.roundToLong

/**
 * RecordingPlayerBar - Card player mỏng, nhẹ, màu cam nhạt
 * Design: 2 lớp nhẹ, không quá dày, đúng concept media player
 */
@Composable
fun RecordingPlayerBar(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Giá trị progress (0f–1f)
    val progressValue = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Box với gradient nhẹ - pill shape card (đơn giản như code cũ)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(16.dp))

            // Phần progress: slider ở giữa, time label nhỏ bên trong
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Slider nằm giữa
                Slider(
                    value = progressValue,
                    onValueChange = { value ->
                        val newPos = (value * durationMs).roundToLong()
                        onSeekTo(newPos)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                )
                
                // Time labels nhỏ, nằm dưới slider, hai bên
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(top = 24.dp), // Đẩy xuống dưới slider
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TimeFormatter.formatTime(positionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = TimeFormatter.formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}


