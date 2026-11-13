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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.TonalCard
import com.CatMoeCircle.fuyutd.ui.component.material.TopBarBackButton
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun PluginVersionsMaterial(
    uiState: PluginUiState,
    actions: PluginVersionsActions,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val info = uiState.versionsInfo

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                title = {
                    Text(
                        text = info?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.plugin_versions_title),
                    )
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            val versions = info?.versions.orEmpty()
            when {
                versions.isEmpty() && uiState.versionsLoading -> {
                    PluginCenterHintMaterial(stringResource(R.string.plugin_loading))
                }

                versions.isEmpty() && uiState.versionsError != null -> {
                    PluginCenterHintMaterial(
                        stringResource(
                            R.string.plugin_versions_load_failed,
                            uiState.versionsError.orEmpty(),
                        )
                    )
                }

                versions.isEmpty() -> {
                    PluginCenterHintMaterial(stringResource(R.string.plugin_versions_empty))
                }

                else -> VersionListMaterial(uiState, actions, versions)
            }
        }
    }
}

@Composable
private fun VersionListMaterial(
    uiState: PluginUiState,
    actions: PluginVersionsActions,
    versions: List<PluginVersion>,
) {
    val installedVersion = uiState.versionsInstalled?.version
    val latest = uiState.versionsInfo?.latest.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.plugin_versions_hint, CURRENT_PLUGIN_ABI),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.versionsError != null) {
            item(key = "error") {
                Text(
                    text = uiState.versionsError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        // 版本表按版本号升序，界面上新的排前面。
        items(versions.asReversed(), key = { it.version }) { version ->
            VersionCardMaterial(
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
private fun VersionCardMaterial(
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

    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${version.version}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (latest) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMaterial(
                        text = stringResource(R.string.plugin_versions_latest),
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (installed) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMaterial(
                        text = stringResource(R.string.plugin_installed),
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                // 兼容性提示放在「最新」前面，窄屏上也不会被后面的标签挤掉。
                if (statusReason != null) {
                    Spacer(Modifier.width(8.dp))
                    VersionTagMaterial(
                        text = stringResource(statusReason),
                        background = MaterialTheme.colorScheme.errorContainer,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (version.depends.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.plugin_versions_depends,
                        version.depends.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (installing) {
                // 安装中：按钮位置换成填充式进度条，安装期间不可点击。
                InstallProgressButtonMaterial(progress = progress ?: 0)
            } else if (usable && !installed) {
                TextButton(
                    onClick = onInstall,
                    modifier = Modifier.padding(top = 4.dp),
                    // 去掉按钮自带的左右内边距，文字与上方 ABI / 大小说明左对齐。
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.plugin_versions_install))
                }
            }
        }
    }
}

/**
 * 安装进度按钮：外层保留按钮圆角，内部按 progress/100 的宽度用 primary 填充，
 * 文字居中显示「正在安装 N%」。安装期间不可点击，高度与普通按钮一致。
 */
@Composable
private fun InstallProgressButtonMaterial(progress: Int) {
    val percent = progress.coerceIn(0, 100)
    val fraction by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "material-install-progress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringResource(R.string.plugin_installing, percent),
            style = MaterialTheme.typography.labelLarge,
            // 填充过半后文字落在 primary 上，用 onPrimary 保证对比度；否则落在浅色轨道上。
            color = if (fraction > 0.5f) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun VersionTagMaterial(text: String, background: Color, tint: Color) {
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
