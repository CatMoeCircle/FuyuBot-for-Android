package com.CatMoeCircle.fuyutd.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 主操作按钮所处的形态，用于在环形进度与播放 / 停止图标间做衔接动画。 */
private enum class HomeButtonPhase { PLAY, RING, STOP }

/**
 * 首页主操作按钮（Miuix 与 Material 共用）。
 *
 * 形态随状态切换：
 * - [preparingProgress] 为 0..99 → 环形进度条（内核自动安装 / 升级中），不可点击；
 * - 进度转满（100）→ 小动画衔接回「播放」样式；
 * - 运行中 → 「停止」样式。
 *
 * 图标渲染交由调用方通过 [icon] 传入，以兼容两套主题各自的 [androidx.compose.material3.Icon] /
 * miuix [top.yukonga.miuix.kmp.basic.Icon]。
 */
@Composable
fun HomeActionButton(
    isRunning: Boolean,
    preparingProgress: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundIdle: Color,
    backgroundRunning: Color,
    ringTrack: Color,
    ringColor: Color,
    iconIdle: Color,
    iconRunning: Color,
    playIcon: ImageVector,
    stopIcon: ImageVector,
    contentDescriptionStart: String,
    contentDescriptionStop: String,
    icon: @Composable (ImageVector, String?, Color) -> Unit,
) {
    val preparing = preparingProgress != null && preparingProgress < 100
    val phase = when {
        isRunning -> HomeButtonPhase.STOP
        preparing -> HomeButtonPhase.RING
        else -> HomeButtonPhase.PLAY
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "homeActionScale",
    )

    Box(
        modifier = modifier
            .size(84.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (isRunning) backgroundRunning else backgroundIdle)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !preparing,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "homeActionPhase",
        ) { current ->
            when (current) {
                HomeButtonPhase.RING -> {
                    val progress = (preparingProgress ?: 0).coerceIn(0, 100)
                    HomePreparingRing(
                        progress = progress,
                        color = ringColor,
                        trackColor = ringTrack,
                        modifier = Modifier.size(48.dp),
                    )
                }

                HomeButtonPhase.PLAY -> icon(
                    playIcon,
                    contentDescriptionStart,
                    iconIdle,
                )

                HomeButtonPhase.STOP -> icon(
                    stopIcon,
                    contentDescriptionStop,
                    iconRunning,
                )
            }
        }
    }
}

/**
 * 环形进度环，用 [Canvas] 绘制，避免引入额外动画依赖。
 *
 * 从 12 点方向顺时针填充，[progress] 取值 0..100。
 */
@Composable
private fun HomePreparingRing(
    progress: Int,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * (progress.coerceIn(0, 100) / 100f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
