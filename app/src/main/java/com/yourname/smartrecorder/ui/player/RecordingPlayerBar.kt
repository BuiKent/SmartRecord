package com.yourname.smartrecorder.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * 
 * @param isCompact Nếu true: kích thước nhỏ hơn (dùng cho History card, notification, lockscreen)
 *                  Nếu false: kích thước chuẩn (dùng cho TranscriptScreen)
 */
@Composable
fun RecordingPlayerBar(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Giá trị progress (0f–1f)
    val progressValue = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // Kích thước dựa trên isCompact
    val buttonSize = if (isCompact) 20.dp else 40.dp // Button nhỏ hơn trong compact mode để phù hợp với icon nhỏ
    val iconSize = if (isCompact) 16.dp else 24.dp // Icon size tỷ lệ với button: 20dp button → 16dp icon, 40dp button → 24dp icon
    val spacing = if (isCompact) 8.dp else 16.dp
    val trackHeight = if (isCompact) 3.dp else 4.dp
    val thumbSize = if (isCompact) 10.dp else 12.dp
    val horizontalPadding = if (isCompact) 8.dp else 16.dp
    val verticalPadding = if (isCompact) 5.dp else 10.dp

    // Box với gradient nhẹ - pill shape card
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
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dùng Box thay vì IconButton để kiểm soát chính xác kích thước
            Box(
                modifier = Modifier
                    .size(buttonSize) // Kích thước chính xác, không có padding mặc định
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize) // Set icon size tỷ lệ với button size
                )
            }

            Spacer(Modifier.width(spacing))

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
                                .height(trackHeight)
                                .clip(RoundedCornerShape(trackHeight / 2))
                        )
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(thumbSize)
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
                        .padding(top = if (isCompact) 18.dp else 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TimeFormatter.formatTime(positionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isCompact) 
                                MaterialTheme.typography.labelSmall.fontSize * 0.75f
                            else 
                                MaterialTheme.typography.labelSmall.fontSize * 0.85f
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = TimeFormatter.formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isCompact) 
                                MaterialTheme.typography.labelSmall.fontSize * 0.75f
                            else 
                                MaterialTheme.typography.labelSmall.fontSize * 0.85f
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}


