package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.plugin.CURRENT_PLUGIN_ABI
import com.CatMoeCircle.fuyutd.data.plugin.PluginVersion
import com.CatMoeCircle.fuyutd.data.plugin.assetForDevice
import com.CatMoeCircle.fuyutd.data.plugin.isUsable
import com.CatMoeCircle.fuyutd.data.plugin.platformDisplayName
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun PluginVersionsMiuix(
    uiState: PluginUiState,
    actions: PluginVersionsActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val info = uiState.versionsInfo

    Scaffold(
        topBar = {
            TopAppBar(
                color = colorScheme.surface,
                title = info?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.plugin_versions_title),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val versions = info?.versions.orEmpty()
            when {
                versions.isEmpty() && uiState.versionsLoading -> {
                    PluginCenterHint(stringResource(R.string.plugin_loading))
                }

                versions.isEmpty() && uiState.versionsError != null -> {
                    PluginCenterHint(
                        stringResource(
                            R.string.plugin_versions_load_failed,
                            uiState.versionsError.orEmpty(),
                        )
                    )
                }

                versions.isEmpty() -> {
                    PluginCenterHint(stringResource(R.string.plugin_versions_empty))
                }

                else -> VersionListMiuix(uiState, actions, versions)
            }
        }
    }
}

@Composable
private fun VersionListMiuix(
    uiState: PluginUiState,
    actions: PluginVersionsActions,
    versions: List<PluginVersion>,
) {
    val installedVersion = uiState.versionsInstalled?.version
    val latest = uiState.versionsInfo?.latest.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.plugin_versions_hint, CURRENT_PLUGIN_ABI),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (uiState.versionsError != null) {
            item(key = "error") {
                Text(
                    text = uiState.versionsError.orEmpty(),
                    fontSize = 12.sp,
                    color = colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        // 版本表按版本号升序，界面上新的排前面。
        items(versions.asReversed(), key = { it.version }) { version ->
            VersionCardMiuix(
                version = version,
                latest = version.version == latest,
                installed = version.version == installedVersion,
                installing = version.version == uiState.versionsInstallingVersion,
                progress = uiState.versionsProgress,
                onInstall = { actions.onInstall(version) },
            )
        }
    }
}

@Composable
private fun VersionCardMiuix(
    version: PluginVersion,
    latest: Boolean,
    installed: Boolean,
    installing: Boolean,
    progress: Int?,
    onInstall: () -> Unit,
) {
    val asset = version.assetForDevice()
    val usable = version.isUsable()
    // 插件 ABI 与宿主 ABI 是硬切关系：低版本 ABI 的插件在新内核里同样加载不了。
    // 它是「兼容性提示」，和 isUsable（商店的 abi ≤ 宿主规则）分开判断：ABI 不同的版本
    // 即使还能点安装，也要像「最新」一样挂标签提醒。
    val abiIncompatible = version.abi > 0 && version.abi != CURRENT_PLUGIN_ABI
    val statusReason = when {
        version.yanked -> R.string.plugin_versions_yanked
        abiIncompatible -> R.string.plugin_versions_abi_incompatible
        asset == null -> R.string.plugin_versions_no_platform
        !usable -> R.string.plugin_versions_incompatible
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        showIndication = false,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${version.version}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (latest) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMiuix(
                        text = stringResource(R.string.plugin_versions_latest),
                        background = colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        tint = colorScheme.onSecondaryContainer,
                    )
                }
                if (installed) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMiuix(
                        text = stringResource(R.string.plugin_installed),
                        background = colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        tint = colorScheme.onSecondaryContainer,
                    )
                }
                // 兼容性提示放在「最新」前面，窄屏上也不会被后面的标签挤掉。
                if (statusReason != null) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMiuix(
                        text = stringResource(statusReason),
                        background = colorScheme.errorContainer.copy(alpha = 0.8f),
                        tint = colorScheme.onErrorContainer,
                    )
                }
            }

            Text(
                text = if (asset == null) {
                    stringResource(R.string.plugin_versions_abi, version.abi)
                } else {
                    stringResource(
                        R.string.plugin_versions_meta,
                        version.abi,
                        platformDisplayName(asset.first),
                        formatPluginSize(asset.second.size),
                    )
                },
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (version.depends.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.plugin_versions_depends,
                        version.depends.joinToString(", "),
                    ),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (installing) {
                // 安装中：按钮位置换成填充式进度条，安装期间不可点击。
                InstallProgressButtonMiuix(progress = progress ?: 0)
            } else if (usable && !installed) {
                MiuixTextButton(
                    text = stringResource(R.string.plugin_versions_install),
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * 安装进度按钮：外层保留按钮圆角，内部按 progress/100 的宽度用 primary 填充，
 * 文字居中显示「正在安装 N%」。安装期间不可点击，高度与普通按钮一致。
 */
@Composable
private fun InstallProgressButtonMiuix(progress: Int) {
    val percent = progress.coerceIn(0, 100)
    val fraction by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "miuix-install-progress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .align(Alignment.CenterStart)
                .background(colorScheme.primary),
        )
        Text(
            text = stringResource(R.string.plugin_installing, percent),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            // 填充过半后文字落在 primary 上，用 onPrimary 保证对比度；否则落在浅色轨道上。
            color = if (fraction > 0.5f) colorScheme.onPrimary else colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun VersionTagMiuix(text: String, background: Color, tint: Color) {
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
