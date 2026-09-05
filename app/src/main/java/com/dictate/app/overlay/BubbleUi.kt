package com.dictate.app.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dictate.app.ui.theme.DictateAccent

/** The small idle/connecting/result circle that users drag and tap. */
@Composable
fun CollapsedBubble(state: DictationState, sizeDp: Int, modifier: Modifier = Modifier) {
    val diameter = sizeDp.dp
    Surface(
        modifier = modifier.size(diameter),
        shape = CircleShape,
        color = bubbleColor(state),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                is DictationState.Connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(diameter * 0.5f),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                is DictationState.Success -> Icon(Icons.Filled.Check, null, tint = Color.White)
                is DictationState.Error -> Icon(Icons.Filled.ErrorOutline, null, tint = Color.White)
                else -> Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Start dictation",
                    tint = Color.White,
                    modifier = Modifier.size(diameter * 0.5f),
                )
            }
        }
    }
}

private fun bubbleColor(state: DictationState): Color = when (state) {
    is DictationState.Success -> Color(0xFF34C759)
    is DictationState.Error -> Color(0xFFE5484D)
    else -> DictateAccent
}

/** The expanded pill shown while actively recording/finalizing/inserting. */
@Composable
fun RecordingPill(
    state: DictationState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onPaste: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state is DictationState.Error) {
                Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFE5484D))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
                if (onPaste != null) {
                    IconAction(Icons.Filled.ContentPaste, "Paste", DictateAccent, onPaste)
                }
                IconAction(Icons.Filled.Close, "Dismiss", Color.Gray, onCancel)
                return@Row
            }

            IconAction(Icons.Filled.Close, "Cancel", Color.Gray, onCancel)
            WaveformIndicator(active = state is DictationState.Recording)
            when (state) {
                is DictationState.Finalizing, is DictationState.Inserting ->
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else -> IconAction(Icons.Filled.Check, "Done", DictateAccent, onDone)
            }
        }
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

@Composable
private fun WaveformIndicator(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val heightFraction by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = if (active) 1f else 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 280 + index * 60, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .height((18.dp * heightFraction))
                    .background(DictateAccent, RoundedCornerShape(2.dp)),
            )
        }
    }
}
