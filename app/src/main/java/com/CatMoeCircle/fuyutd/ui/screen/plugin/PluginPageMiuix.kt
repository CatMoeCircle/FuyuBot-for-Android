package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.KernelPluginInfo
import com.CatMoeCircle.fuyutd.data.kernel.KernelPluginState
import com.CatMoeCircle.fuyutd.data.kernel.KernelSkippedPlugin
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableBlur
import com.CatMoeCircle.fuyutd.ui.util.BlurredBar
import com.CatMoeCircle.fuyutd.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PluginPageMiuix(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.plugin_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = actions.onOpenRepo) {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = stringResource(R.string.plugin_tab_repo),
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        if (uiState.rebootNeeded) {
                            IconButton(onClick = actions.onReboot) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = stringResource(R.string.plugin_reboot),
                                    tint = colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            PullToRefresh(
                isRefreshing = uiState.loading,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
                topAppBarScrollBehavior = scrollBehavior,
                refreshTexts = listOf(
                    stringResource(R.string.plugin_pull_to_refresh),
                    stringResource(R.string.plugin_release_to_refresh),
                    stringResource(R.string.plugin_refreshing),
                    stringResource(R.string.plugin_refreshed),
                ),
            ) {
                InstalledListMiuix(
                    uiState = uiState,
                    actions = actions,
                    bottomInnerPadding = bottomInnerPadding,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                )
            }
        }
    }
}

@Composable
private fun InstalledListMiuix(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    bottomInnerPadding: Dp,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.installed.isEmpty() &&
            uiState.kernelSkipped.isEmpty() &&
            uiState.kernelOrphans.isEmpty()
        ) {
            PluginCenterHint(stringResource(R.string.plugin_empty))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomInnerPadding + 12.dp),
                overscrollEffect = null,
            ) {
                items(uiState.installed, key = { it.installed.id }) { item ->
                    InstalledCardMiuix(
                        item = item,
                        onToggleEnabled = actions.onToggleEnabled,
                        onUninstall = actions.onUninstall,
                        onUndoUninstall = actions.onUndoUninstall,
                    )
                }
                // 内核报告只在真的有内容时才出区块：内核没跑 / 没拿到报告时列表保持原样。
                if (uiState.kernelSkipped.isNotEmpty()) {
                    item(key = "kernel_skipped") {
                        KernelSkippedCardMiuix(items = uiState.kernelSkipped)
                    }
                }
                if (uiState.kernelOrphans.isNotEmpty()) {
                    item(key = "kernel_orphans") {
                        KernelOrphanCardMiuix(items = uiState.kernelOrphans)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledCardMiuix(
    item: InstalledPluginItem,
    onToggleEnabled: (InstalledPlugin, Boolean) -> Unit,
    onUninstall: (InstalledPlugin) -> Unit,
    onUndoUninstall: (InstalledPlugin) -> Unit,
) {
    val installed = item.installed
    val name = item.info?.name ?: installed.id
    val version = installed.version.ifBlank { item.info?.latest.orEmpty() }
    val summary = item.info?.summary.orEmpty()
    val kernel = item.kernel
    val textDecoration = if (installed.pendingUninstall) TextDecoration.LineThrough else null
    var expanded by remember(installed.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                ) {
                    Text(
                        text = name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${stringResource(R.string.plugin_version)}: $version",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariantSummary,
                            textDecoration = textDecoration,
                        )
                        // 内核真实加载结果；内核没跑 / 没匹配上时不显示。
                        if (kernel != null) {
                            Spacer(Modifier.width(6.dp))
                            KernelStateBadgeMiuix(state = kernel.state)
                        }
                    }
                }
                Switch(
                    enabled = !installed.pendingUninstall,
                    checked = installed.enabled && !installed.pendingUninstall,
                    onCheckedChange = { checked ->
                        if (checked != installed.enabled) onToggleEnabled(installed, checked)
                    },
                )
            }

            if (summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    textDecoration = textDecoration,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                )
            }

            if (installed.pendingRestart || installed.pendingUninstall) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (installed.pendingUninstall) R.string.plugin_pending_uninstall
                            else R.string.plugin_restart_needed
                        ),
                        fontSize = 12.sp,
                        color = if (installed.pendingUninstall) colorScheme.error else colorScheme.primary,
                    )
                }
            }

            if (kernel != null && kernel.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.plugin_kernel_reason, kernel.reason),
                    fontSize = 12.sp,
                    color = colorScheme.error,
                )
            }

            if (kernel != null && kernel.state == KernelPluginState.PENDING && kernel.missing.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.plugin_kernel_missing,
                        kernel.missing.joinToString("、"),
                    ),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )

            Row {
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (installed.pendingUninstall) onUndoUninstall(installed)
                        else onUninstall(installed)
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (installed.pendingUninstall) MiuixIcons.Undo else MiuixIcons.Delete,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (installed.pendingUninstall) R.string.plugin_undo else R.string.plugin_uninstall
                            ),
                            color = colorScheme.onSurfaceVariantActions,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 内核状态徽标：把内核报的 `state` 直接标在插件上。
 */
@Composable
private fun KernelStateBadgeMiuix(state: KernelPluginState) {
    val background = when (state) {
        KernelPluginState.ACTIVE -> colorScheme.secondaryContainer.copy(alpha = 0.8f)
        KernelPluginState.FAILED -> colorScheme.error.copy(alpha = 0.12f)
        KernelPluginState.PENDING, KernelPluginState.ACTIVATING, KernelPluginState.UNKNOWN ->
            colorScheme.surfaceContainerHigh
    }
    val tint = when (state) {
        KernelPluginState.ACTIVE -> colorScheme.onSecondaryContainer
        KernelPluginState.FAILED -> colorScheme.error
        KernelPluginState.PENDING, KernelPluginState.ACTIVATING, KernelPluginState.UNKNOWN ->
            colorScheme.onSurfaceVariantSummary
    }
    Text(
        text = stringResource(
            when (state) {
                KernelPluginState.ACTIVE -> R.string.plugin_kernel_state_active
                KernelPluginState.PENDING -> R.string.plugin_kernel_state_pending
                KernelPluginState.ACTIVATING -> R.string.plugin_kernel_state_activating
                KernelPluginState.FAILED -> R.string.plugin_kernel_state_failed
                KernelPluginState.UNKNOWN -> R.string.plugin_kernel_state_unknown
            }
        ),
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

/** 「内核跳过的插件」：内核在插件目录里扫到、但连内核都没进去的候选。 */
@Composable
private fun KernelSkippedCardMiuix(items: List<KernelSkippedPlugin>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.plugin_kernel_skipped_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.plugin_kernel_skipped_desc),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
            items.forEach { skipped ->
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = skipped.path.substringAfterLast('/'),
                        fontSize = 14.sp,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (skipped.reason.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.plugin_kernel_reason, skipped.reason),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 「内核已加载但不在本机注册表」：匹配不上注册表的条目单独列出，不丢。 */
@Composable
private fun KernelOrphanCardMiuix(items: List<KernelPluginInfo>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.plugin_kernel_orphans_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.plugin_kernel_orphans_desc),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
            items.forEach { plugin ->
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name.ifBlank { plugin.file },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        KernelStateBadgeMiuix(state = plugin.state)
                    }
                    Text(
                        text = stringResource(R.string.plugin_kernel_file, plugin.file),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (plugin.reason.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.plugin_kernel_reason, plugin.reason),
                            fontSize = 12.sp,
                            color = colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (plugin.state == KernelPluginState.PENDING && plugin.missing.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.plugin_kernel_missing,
                                plugin.missing.joinToString("、"),
                            ),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PluginCenterHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}
