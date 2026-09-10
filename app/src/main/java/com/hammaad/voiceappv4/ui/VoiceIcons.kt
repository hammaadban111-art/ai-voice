package com.hammaad.voiceappv4.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class VoiceIcon {
    ArrowBack,
    Bubble,
    Check,
    ChevronDown,
    Close,
    Gear,
    Globe,
    Hand,
    History,
    Key,
    Lock,
    Mic,
    VoxtaMark,
    Shield,
    Sliders,
    Spark,
    Stop,
    Warning,
    Wave,
}

@Composable
fun VoiceGlyph(
    icon: VoiceIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(
            width = size.toPx() * 0.09f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val w = this.size.width
        val h = this.size.height
        when (icon) {
            VoiceIcon.ArrowBack -> {
                drawLine(tint, Offset(w * .78f, h * .5f), Offset(w * .22f, h * .5f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .5f), Offset(w * .44f, h * .28f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .5f), Offset(w * .44f, h * .72f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Bubble -> {
                drawRoundRect(tint, Offset(w * .14f, h * .16f), Size(w * .72f, h * .58f), CornerRadius(w * .2f), style = stroke)
                val tail = Path().apply {
                    moveTo(w * .33f, h * .74f)
                    lineTo(w * .28f, h * .88f)
                    lineTo(w * .48f, h * .74f)
                }
                drawPath(tail, tint, style = stroke)
            }
            VoiceIcon.Check -> {
                drawLine(tint, Offset(w * .2f, h * .53f), Offset(w * .43f, h * .74f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .43f, h * .74f), Offset(w * .82f, h * .28f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.ChevronDown -> {
                drawLine(tint, Offset(w * .27f, h * .4f), Offset(w * .5f, h * .64f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .64f), Offset(w * .73f, h * .4f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Close -> {
                drawLine(tint, Offset(w * .27f, h * .27f), Offset(w * .73f, h * .73f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .73f, h * .27f), Offset(w * .27f, h * .73f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Gear -> {
                drawCircle(tint, radius = w * .2f, center = center, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4.0
                    val from = Offset(
                        center.x + cos(angle).toFloat() * w * .3f,
                        center.y + sin(angle).toFloat() * h * .3f,
                    )
                    val to = Offset(
                        center.x + cos(angle).toFloat() * w * .43f,
                        center.y + sin(angle).toFloat() * h * .43f,
                    )
                    drawLine(tint, from, to, stroke.width, StrokeCap.Round)
                }
            }
            VoiceIcon.Globe -> {
                drawCircle(tint, radius = w * .36f, center = center, style = stroke)
                drawOval(tint, Offset(w * .36f, h * .14f), Size(w * .28f, h * .72f), style = stroke)
                drawLine(tint, Offset(w * .16f, h * .5f), Offset(w * .84f, h * .5f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Hand -> {
                val hand = Path().apply {
                    moveTo(w * .28f, h * .78f)
                    lineTo(w * .22f, h * .48f)
                    lineTo(w * .3f, h * .44f)
                    lineTo(w * .4f, h * .62f)
                    lineTo(w * .38f, h * .25f)
                    lineTo(w * .46f, h * .24f)
                    lineTo(w * .52f, h * .56f)
                    lineTo(w * .54f, h * .2f)
                    lineTo(w * .62f, h * .21f)
                    lineTo(w * .65f, h * .55f)
                    lineTo(w * .7f, h * .3f)
                    lineTo(w * .78f, h * .33f)
                    lineTo(w * .75f, h * .64f)
                    lineTo(w * .66f, h * .8f)
                    close()
                }
                drawPath(hand, tint, style = stroke)
            }
            VoiceIcon.History -> {
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * .18f, h * .22f), Offset(w * .18f, h * .45f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .18f, h * .22f), Offset(w * .4f, h * .22f), stroke.width, StrokeCap.Round)
                drawLine(tint, center, Offset(w * .5f, h * .3f), stroke.width, StrokeCap.Round)
                drawLine(tint, center, Offset(w * .68f, h * .58f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Key -> {
                drawCircle(tint, radius = w * .2f, center = Offset(w * .3f, h * .38f), style = stroke)
                drawLine(tint, Offset(w * .45f, h * .5f), Offset(w * .8f, h * .78f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .64f, h * .65f), Offset(w * .76f, h * .53f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .72f, h * .72f), Offset(w * .84f, h * .6f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Lock -> {
                drawRoundRect(tint, Offset(w * .2f, h * .42f), Size(w * .6f, h * .42f), CornerRadius(w * .08f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * .31f, h * .16f),
                    size = Size(w * .38f, h * .48f),
                    style = stroke,
                )
            }
            VoiceIcon.Mic -> {
                drawRoundRect(tint, Offset(w * .34f, h * .12f), Size(w * .32f, h * .5f), CornerRadius(w * .16f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * .2f, h * .32f),
                    size = Size(w * .6f, h * .48f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * .5f, h * .8f), Offset(w * .5f, h * .92f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .34f, h * .92f), Offset(w * .66f, h * .92f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.VoxtaMark -> {
                val signal = Path().apply {
                    moveTo(w * .16f, h * .43f)
                    lineTo(w * .31f, h * .65f)
                    lineTo(w * .47f, h * .29f)
                    lineTo(w * .63f, h * .65f)
                    lineTo(w * .72f, h * .48f)
                }
                drawPath(signal, tint, style = Stroke(width = w * .12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(
                    tint,
                    Offset(w * .82f, h * .2f),
                    Offset(w * .82f, h * .8f),
                    w * .12f,
                    StrokeCap.Round,
                )
            }
            VoiceIcon.Shield -> {
                val shield = Path().apply {
                    moveTo(w * .5f, h * .12f)
                    lineTo(w * .8f, h * .24f)
                    lineTo(w * .76f, h * .58f)
                    quadraticTo(w * .68f, h * .78f, w * .5f, h * .88f)
                    quadraticTo(w * .32f, h * .78f, w * .24f, h * .58f)
                    lineTo(w * .2f, h * .24f)
                    close()
                }
                drawPath(shield, tint, style = stroke)
            }
            VoiceIcon.Sliders -> {
                drawLine(tint, Offset(w * .22f, h * .26f), Offset(w * .78f, h * .26f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .5f), Offset(w * .78f, h * .5f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .22f, h * .74f), Offset(w * .78f, h * .74f), stroke.width, StrokeCap.Round)
                drawCircle(tint, w * .1f, Offset(w * .4f, h * .26f))
                drawCircle(tint, w * .1f, Offset(w * .62f, h * .5f))
                drawCircle(tint, w * .1f, Offset(w * .34f, h * .74f))
            }
            VoiceIcon.Spark -> {
                drawLine(tint, Offset(w * .5f, h * .12f), Offset(w * .5f, h * .88f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .12f, h * .5f), Offset(w * .88f, h * .5f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .24f, h * .24f), Offset(w * .76f, h * .76f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .76f, h * .24f), Offset(w * .24f, h * .76f), stroke.width, StrokeCap.Round)
            }
            VoiceIcon.Stop -> {
                drawRoundRect(tint, Offset(w * .28f, h * .28f), Size(w * .44f, h * .44f), CornerRadius(w * .04f), style = stroke)
            }
            VoiceIcon.Warning -> {
                val warning = Path().apply {
                    moveTo(w * .5f, h * .12f)
                    lineTo(w * .88f, h * .82f)
                    lineTo(w * .12f, h * .82f)
                    close()
                }
                drawPath(warning, tint, style = stroke)
                drawLine(tint, Offset(w * .5f, h * .36f), Offset(w * .5f, h * .6f), stroke.width, StrokeCap.Round)
                drawCircle(tint, w * .035f, Offset(w * .5f, h * .72f))
            }
            VoiceIcon.Wave -> {
                val bars = listOf(.28f, .5f, .76f, .44f, .64f)
                bars.forEachIndexed { index, level ->
                    val x = w * (.18f + index * .16f)
                    drawLine(tint, Offset(x, h * (.5f - level * .35f)), Offset(x, h * (.5f + level * .35f)), stroke.width * 1.35f, StrokeCap.Round)
                }
            }
        }
    }
}
