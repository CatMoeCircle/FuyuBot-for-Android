package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.data.plugin.platformDisplayName
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.component.markdown.MarkdownView
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 插件仓库详情弹窗：Markdown 正文 + 底部取消 / 安装按钮。
 *
 * 正文优先展示仓库首页 README，取不到时回落到索引里的 `description`。
 * 正文按内容自然高度渲染：短内容保持紧凑，长内容撑到「当前窗口可用高度的比例」后
 * 由 [MarkdownView] 内部滚动，避免把弹窗挤出屏幕。只有当前设备支持该插件时才显示
 * 「安装」按钮；已安装则显示「卸载」。
 */
@Composable
fun PluginRepoDetailDialog(
    item: RepoPluginItem?,
    markdown: String?,
    installed: InstalledPlugin?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val current = item ?: return
    val deviceSupported = current.deviceSupported
    val supportedPlatforms = current.info.supportedPlatforms

    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixRepoDetailDialog(
            title = current.info.name,
            markdown = markdown,
            deviceSupported = deviceSupported,
            supportedPlatforms = supportedPlatforms,
            installed = installed,
            onDismiss = onDismiss,
            onInstall = onInstall,
            onUninstall = onUninstall,
        )

        UiMode.Material -> MaterialRepoDetailDialog(
            title = current.info.name,
            markdown = markdown,
            deviceSupported = deviceSupported,
            supportedPlatforms = supportedPlatforms,
            installed = installed,
            onDismiss = onDismiss,
            onInstall = onInstall,
            onUninstall = onUninstall,
        )
    }
}

/**
 * 正文可用的高度上限：跟随当前窗口高度按比例伸缩，而不是写死 480dp。
 *
 * 小屏手机上 0.6 倍屏高能留出标题与按钮的空间；平板 / 横屏等大窗口下同步变高，
 * 让 README 尽量一屏读完，超出部分才交给 [MarkdownView] 内部滚动。
 */
@Composable
private fun markdownMaxHeight(): Dp {
    val windowInfo = LocalWindowInfo.current
    val windowHeight = windowInfo.containerDpSize.height
    return (windowHeight * 0.6f).coerceIn(160.dp, 640.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiuixRepoDetailDialog(
    title: String,
    markdown: String?,
    deviceSupported: Boolean,
    supportedPlatforms: List<String>,
    installed: InstalledPlugin?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column {
                if (markdown.isNullOrBlank()) {
                    MiuixText(text = stringResource(R.string.plugin_summary_empty))
                } else {
                    // MarkdownView 自适应高度并内部滚动，这里不再套外层 verticalScroll，避免挤压按钮。
                    // weight(fill = false)：正文最多吃掉按钮之上的剩余空间，不会把按钮挤出弹窗。
                    MarkdownView(
                        markdown = markdown,
                        modifier = Modifier.weight(1f, fill = false),
                        maxHeight = markdownMaxHeight(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row {
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    if (deviceSupported && installed == null) {
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = stringResource(R.string.plugin_install),
                            onClick = {
                                onInstall()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    } else if (deviceSupported) {
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = stringResource(R.string.plugin_uninstall),
                            onClick = {
                                onUninstall()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (!deviceSupported) {
                    MiuixHorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = colorScheme.outline.copy(alpha = 0.5f),
                    )
                    MiuixText(
                        text = stringResource(R.string.plugin_supported_platforms),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        supportedPlatforms.forEach { platform ->
                            MiuixTag(
                                text = platformDisplayName(platform),
                                background = colorScheme.surfaceContainerHigh,
                                tint = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialRepoDetailDialog(
    title: String,
    markdown: String?,
    deviceSupported: Boolean,
    supportedPlatforms: List<String>,
    installed: InstalledPlugin?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (markdown.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.plugin_summary_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // MarkdownView 自适应高度并内部滚动，这里不再套外层 verticalScroll，避免挤压按钮。
                    // weight(fill = false)：正文最多吃掉按钮之上的剩余空间，不会把按钮挤出弹窗。
                    MarkdownView(
                        markdown = markdown,
                        modifier = Modifier.weight(1f, fill = false),
                        maxHeight = markdownMaxHeight(),
                    )
                }

                if (!deviceSupported) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = stringResource(R.string.plugin_supported_platforms),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        supportedPlatforms.forEach { platform ->
                            MaterialTag(
                                text = platformDisplayName(platform),
                                background = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (deviceSupported && installed == null) {
                TextButton(
                    onClick = {
                        onInstall()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.plugin_install))
                }
            } else if (deviceSupported) {
                TextButton(
                    onClick = {
                        onUninstall()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.plugin_uninstall))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MiuixTag(text: String, background: Color, tint: Color) {
    MiuixText(
        text = text,
        fontSize = 11.sp,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
private fun MaterialTag(text: String, background: Color, tint: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
