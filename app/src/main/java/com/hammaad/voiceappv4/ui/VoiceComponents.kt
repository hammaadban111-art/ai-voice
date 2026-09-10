package com.hammaad.voiceappv4.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.defaultMinSize

enum class BubbleVisualState {
    Idle,
    Recording,
    Processing,
    Error,
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    actionIcon: VoiceIcon? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            VoiceIconButton(
                icon = VoiceIcon.ArrowBack,
                label = actionLabel ?: "Go back",
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        if (onAction != null && actionIcon != null && actionLabel != null) {
            VoiceIconButton(icon = actionIcon, label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun VoiceIconButton(
    icon: VoiceIcon,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        VoiceGlyph(icon = icon, tint = tint, modifier = Modifier)
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.3.sp,
    )
}

@Composable
fun IntroBlock(
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(eyebrow)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VoiceSurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

@Composable
fun SetupProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .semantics {
                        contentDescription = "Setup step ${index + 1} of $total"
                    },
            )
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RequirementRow(
    title: String,
    detail: String,
    granted: Boolean,
    icon: VoiceIcon,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (granted) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            VoiceGlyph(
                icon = icon,
                tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 22.dp,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            VoiceGlyph(
                VoiceIcon.Check,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics { contentDescription = "Complete" },
            )
        } else {
            TextButton(onClick = onAction, modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ReadinessCard(
    snapshot: VoiceUiSnapshot,
    onReviewSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = buildList {
        if (!snapshot.permissions.microphoneGranted) add("microphone")
        if (!snapshot.permissions.overlayGranted) add("overlay access")
        if (!snapshot.permissions.accessibilityGranted) add("accessibility access")
        if (!snapshot.keyConfigured) add("Gemini key")
        if (!snapshot.settings.bubbleEnabled) add("contextual bubble")
    }
    val title = when {
        snapshot.isReady -> "Ready when you are"
        snapshot.isSnoozed -> "Bubble is snoozed"
        missing.isNotEmpty() -> "One more step"
        else -> "Bubble is paused"
    }
    val detail = when {
        snapshot.isReady -> "The bubble appears only in an eligible text field. It stays away from Home and empty screens."
        snapshot.isSnoozed -> "Resume it from Home or Settings when you want to dictate again."
        missing.isNotEmpty() -> "Complete ${missing.joinToString(", ")} before dictation can be ready."
        else -> "Turn on the contextual bubble to make dictation available while you type."
    }
    val statusLabel = when {
        snapshot.isReady -> "READY"
        snapshot.isSnoozed -> "SNOOZED"
        missing.isNotEmpty() -> "SETUP"
        else -> "PAUSED"
    }
    val accent = when {
        snapshot.isReady -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    VoiceSurfaceCard(
        modifier = modifier,
        color = accent,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (snapshot.isReady) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    VoiceGlyph(
                        icon = if (snapshot.isReady) VoiceIcon.Check else VoiceIcon.Spark,
                        tint = if (snapshot.isReady) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                        size = 21.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (snapshot.isReady) "Core checks look good" else "Needs your attention",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(statusLabel, positive = snapshot.isReady)
            }
            Spacer(Modifier.height(12.dp))
            Text(detail, style = MaterialTheme.typography.bodyLarge)
            if (!snapshot.isReady) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReviewSetup, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text("Review setup")
                }
            }
        }
    }
}

@Composable
fun DashboardMetricCard(
    label: String,
    value: String,
    icon: VoiceIcon,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    VoiceGlyph(icon, MaterialTheme.colorScheme.secondary, size = 18.dp)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun StatusBadge(label: String, positive: Boolean) {
    Surface(
        color = if (positive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        contentColor = if (positive) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SettingRow(
    title: String,
    detail: String,
    icon: VoiceIcon,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            VoiceGlyph(icon, MaterialTheme.colorScheme.primary, size = 21.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
fun BubbleDemo(
    state: BubbleVisualState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Control preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "This shows the bubble states. The core worker supplies the real microphone callbacks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        DictationControl(
            state = state,
            onTap = onTap,
            onLongPress = onLongPress,
            onCancel = onCancel,
            onDone = onDone,
        )
    }
}

@Composable
fun DictationControl(
    state: BubbleVisualState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    opacity: Float = 1f,
    audioLevel: Float = 0.42f,
) {
    val description = when (state) {
        BubbleVisualState.Idle -> "Dictation bubble. Double tap to start dictation. Long press for push to talk."
        BubbleVisualState.Recording -> "Recording. Release or tap Done to finish dictation."
        BubbleVisualState.Processing -> "Transcribing your recording."
        BubbleVisualState.Error -> "Dictation error. Open the app for recovery options."
    }
    val stateText = when (state) {
        BubbleVisualState.Idle -> "Ready"
        BubbleVisualState.Recording -> "Listening"
        BubbleVisualState.Processing -> "Transcribing"
        BubbleVisualState.Error -> "Needs attention"
    }
    val scale by animateFloatAsState(
        targetValue = if (state == BubbleVisualState.Recording) 1.04f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "dictation-control-scale",
    )

    AnimatedVisibility(
        visible = state != BubbleVisualState.Idle,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = .94f, animationSpec = tween(220)),
        exit = fadeOut(tween(130)),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(8.dp, RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surface)
                .semantics { contentDescription = description; stateDescription = stateText },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (state == BubbleVisualState.Error) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state == BubbleVisualState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            VoiceGlyph(
                                VoiceIcon.Wave,
                                tint = if (state == BubbleVisualState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                size = 24.dp,
                                modifier = Modifier
                                    .alpha((.45f + audioLevel.coerceIn(0f, 1f) * .55f) * scale)
                                    .semantics { contentDescription = "Live microphone activity" },
                            )
                        }
                        Text(stateText, style = MaterialTheme.typography.labelLarge)
                    }
                }
                VoiceIconButton(
                    icon = if (state == BubbleVisualState.Processing) VoiceIcon.Spark else VoiceIcon.Stop,
                    label = if (state == BubbleVisualState.Processing) "Transcribing" else "Done and insert transcript",
                    onClick = onDone,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = state == BubbleVisualState.Idle,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = .94f, animationSpec = tween(190)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .alpha(opacity.coerceIn(.55f, 1f))
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = .78f)),
                    ),
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() },
                    )
                }
                .semantics {
                    contentDescription = description
                    stateDescription = stateText
                    role = Role.Button
                    onClick("Start dictation") { onTap(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            VoiceGlyph(
                VoiceIcon.Mic,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = size * .42f,
            )
        }
    }
}

@Composable
fun AccessNotice(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (warning) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        VoiceGlyph(
            if (warning) VoiceIcon.Warning else VoiceIcon.Shield,
            tint = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.secondary,
            size = 20.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun SettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
        thickness = 1.dp,
    )
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        leadingIcon = if (selected) {
            { VoiceGlyph(VoiceIcon.Check, MaterialTheme.colorScheme.primary, size = 18.dp) }
        } else null,
    )
}

@Composable
fun ValuePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun EmptyStateRow(
    title: String,
    detail: String,
    icon: VoiceIcon,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceGlyph(icon, MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
