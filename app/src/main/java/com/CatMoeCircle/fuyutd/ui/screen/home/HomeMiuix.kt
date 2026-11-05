package com.CatMoeCircle.fuyutd.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeMiuix(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    bottomInnerPadding: Dp,
) {
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 18.dp),
            )

            // 运行状态卡片
            Card(cornerRadius = HomeCardCornerRadius, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.drawBehind {
                        // 右上角柔光，衬托主按钮。用绘制而非子节点，避免撑高卡片。
                        val radius = 120.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colorScheme.primary.copy(alpha = 0.20f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width - 48.dp.toPx(), -24.dp.toPx()),
                                radius = radius,
                            ),
                            radius = radius,
                            center = Offset(size.width - 48.dp.toPx(), -24.dp.toPx()),
                        )
                    }
                ) {

                    Column(modifier = Modifier.padding(HomeCardPadding)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.home_status),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.size(10.dp))
                            StatusPill(isRunning = state.isRunning)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 头像：TDLib 下到本地的图片，没有就显示人形图标。
                                    HomeAvatar(
                                        avatarPath = state.avatarPath,
                                        background = colorScheme.surfaceContainerHigh,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = stringResource(R.string.home_avatar),
                                            tint = colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Spacer(Modifier.size(12.dp))
                                    Text(
                                        text = state.name
                                            ?: stringResource(R.string.home_unknown),
                                        fontSize = 24.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(Modifier.size(12.dp))

                            // 主操作：比头像更大，圆形强调按钮。
                            // 内核自动准备中显示环形进度条，转满后衔接成播放样式；运行态点击停止。
                            HomeActionButton(
                                isRunning = state.isRunning,
                                preparingProgress = state.preparingProgress,
                                onClick = { if (state.isRunning) onStop() else onStart() },
                                backgroundIdle = colorScheme.primary,
                                backgroundRunning = colorScheme.surfaceContainerHigh,
                                ringTrack = colorScheme.primary.copy(alpha = 0.25f),
                                ringColor = colorScheme.onPrimary,
                                iconIdle = colorScheme.onPrimary,
                                iconRunning = colorScheme.onSurfaceVariantActions,
                                playIcon = Icons.Rounded.PlayArrow,
                                stopIcon = Icons.Rounded.Stop,
                                contentDescriptionStart = stringResource(R.string.home_start),
                                contentDescriptionStop = stringResource(R.string.home_stop),
                                icon = { vector, desc, tint ->
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = desc,
                                        tint = tint,
                                        modifier = Modifier.size(42.dp),
                                    )
                                },
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            BigStat(
                                label = stringResource(R.string.home_uptime),
                                value = state.uptime,
                                modifier = Modifier.weight(1f),
                            )
                            BigStat(
                                label = stringResource(R.string.home_plugin_count),
                                value = state.pluginCount?.toString()
                                    ?: stringResource(R.string.home_value_unknown),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(HomeCardSpacing))

            // TDLib 状态卡片
            Card(cornerRadius = HomeCardCornerRadius, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(HomeCardPadding)) {
                    SectionHeader(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.home_tdlib),
                    )
                    Spacer(Modifier.height(14.dp))
                    TdlibStatusRow(
                        status = state.authState,
                        detail = state.tdlibDetail,
                        connection = state.connectionState,
                        needsReLogin = state.needsReLogin,
                    )
                }
            }

            Spacer(Modifier.height(HomeCardSpacing))

            // 插件卡片
            Card(cornerRadius = HomeCardCornerRadius, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(HomeCardPadding)) {
                    SectionHeader(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.home_plugins),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricBox(
                            label = stringResource(R.string.home_plugins_loaded),
                            value = state.loadedPluginCount?.toString()
                                ?: stringResource(R.string.home_value_unknown),
                            unit = stringResource(R.string.home_unit_count),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBox(
                            label = stringResource(R.string.home_plugins_failed),
                            value = state.failedPluginCount?.toString()
                                ?: stringResource(R.string.home_value_unknown),
                            unit = stringResource(R.string.home_unit_count),
                            emphasized = (state.failedPluginCount ?: 0) > 0,
                            modifier = Modifier.weight(1f),
                        )
                        MetricBox(
                            label = stringResource(R.string.home_plugins_skipped),
                            value = state.skippedPluginCount?.toString()
                                ?: stringResource(R.string.home_value_unknown),
                            unit = stringResource(R.string.home_unit_count),
                            emphasized = (state.skippedPluginCount ?: 0) > 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(bottomInnerPadding + 14.dp))
        }
    }
}

@Composable
private fun TdlibStatusRow(
    status: TdlibAuthState,
    detail: String,
    connection: TdlibConnectionState,
    needsReLogin: Boolean,
) {
    val colorScheme = MiuixTheme.colorScheme
    val tint = when {
        status == TdlibAuthState.READY -> colorScheme.primary
        status.isUnsupported -> colorScheme.error
        else -> colorScheme.onSurfaceVariantSummary
    }
    // 卡片只保留三条信息：主显示内核推的状态描述，下面是授权状态，最后是链路状态。
    // 任一项缺失都统一退回「尚未启动」，不留空行。
    val headline = detail.ifBlank { stringResource(R.string.home_tdlib_not_reported) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = headline,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = status.label(),
            fontSize = 13.sp,
            color = if (status.isUnsupported) {
                colorScheme.error
            } else {
                colorScheme.onSurfaceVariantSummary
            },
        )
        // 链路状态单独一行：断网时授权可能还停在 Ready，但请求发不出去。
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_connection) + "：" + connection.label(),
            fontSize = 13.sp,
            color = if (connection == TdlibConnectionState.READY) {
                colorScheme.primary
            } else {
                colorScheme.onSurfaceVariantSummary
            },
        )
        if (needsReLogin) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_tdlib_needs_relogin),
                fontSize = 13.sp,
                color = colorScheme.error,
            )
        }
        status.hint()?.let { hint ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                fontSize = 13.sp,
                color = if (status.isUnsupported) {
                    colorScheme.error
                } else {
                    colorScheme.onSurfaceVariantSummary
                },
            )
        }
    }
}

/**
 * 状态对应的下一步提示；不需要提示的状态返回 null。
 *
 * 只有扫码登录（[TdlibAuthState.isUnsupported]）用错误色提醒——本 App 不做二维码，
 * 这条路走不通；其余状态都是正常流程中的一步。
 */
@Composable
private fun TdlibAuthState.hint(): String? = when (this) {
    TdlibAuthState.WAIT_TDLIB_PARAMETERS -> stringResource(R.string.home_tdlib_hint_wait_tdlib_parameters)
    TdlibAuthState.WAIT_PHONE_NUMBER -> stringResource(R.string.home_tdlib_hint_wait_phone_number)
    TdlibAuthState.WAIT_CODE -> stringResource(R.string.home_tdlib_hint_wait_code)
    TdlibAuthState.WAIT_PASSWORD -> stringResource(R.string.home_tdlib_hint_wait_password)
    TdlibAuthState.WAIT_OTHER_DEVICE_CONFIRMATION ->
        stringResource(R.string.home_tdlib_hint_wait_other_device_confirmation)

    else -> null
}

@Composable
private fun TdlibAuthState.label(): String = stringResource(
    when (this) {
        TdlibAuthState.NOT_REPORTED -> R.string.home_tdlib_not_reported
        TdlibAuthState.WAIT_TDLIB_PARAMETERS -> R.string.home_tdlib_wait_tdlib_parameters
        TdlibAuthState.WAIT_PHONE_NUMBER -> R.string.home_tdlib_wait_phone_number
        TdlibAuthState.WAIT_OTHER_DEVICE_CONFIRMATION ->
            R.string.home_tdlib_wait_other_device_confirmation
        TdlibAuthState.WAIT_CODE -> R.string.home_tdlib_wait_code
        TdlibAuthState.WAIT_PASSWORD -> R.string.home_tdlib_wait_password
        TdlibAuthState.WAIT_REGISTRATION -> R.string.home_tdlib_wait_registration
        TdlibAuthState.WAIT_EMAIL_ADDRESS -> R.string.home_tdlib_wait_email_address
        TdlibAuthState.WAIT_EMAIL_CODE -> R.string.home_tdlib_wait_email_code
        TdlibAuthState.WAIT_PREMIUM_PURCHASE -> R.string.home_tdlib_wait_premium_purchase
        TdlibAuthState.READY -> R.string.home_tdlib_ready
        TdlibAuthState.LOGGING_OUT -> R.string.home_tdlib_logging_out
        TdlibAuthState.CLOSING -> R.string.home_tdlib_closing
        TdlibAuthState.CLOSED -> R.string.home_tdlib_closed
        TdlibAuthState.UNKNOWN -> R.string.home_tdlib_unknown
    }
)

@Composable
private fun TdlibConnectionState.label(): String = stringResource(
    when (this) {
        TdlibConnectionState.NOT_REPORTED -> R.string.home_connection_not_reported
        TdlibConnectionState.WAITING_FOR_NETWORK -> R.string.home_connection_waiting_network
        TdlibConnectionState.CONNECTING_TO_PROXY -> R.string.home_connection_connecting_proxy
        TdlibConnectionState.CONNECTING -> R.string.home_connection_connecting
        TdlibConnectionState.UPDATING -> R.string.home_connection_updating
        TdlibConnectionState.READY -> R.string.home_connection_ready
        TdlibConnectionState.UNKNOWN -> R.string.home_connection_unknown
    }
)

@Composable
private fun StatusPill(isRunning: Boolean) {
    val colorScheme = MiuixTheme.colorScheme
    val tint = if (isRunning) colorScheme.primary else colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isRunning) colorScheme.primary.copy(alpha = 0.14f)
                else colorScheme.surfaceContainerHigh
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(
                if (isRunning) R.string.home_status_running else R.string.home_status_stopped,
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BigStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) colorScheme.error else colorScheme.onSurface,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = unit,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
    }
}
