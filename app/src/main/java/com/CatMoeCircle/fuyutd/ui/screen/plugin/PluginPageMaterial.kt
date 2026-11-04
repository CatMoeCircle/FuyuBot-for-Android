package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveSwitch
import com.CatMoeCircle.fuyutd.ui.component.material.TonalCard
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun PluginPageMaterial(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.plugin_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onOpenRepo) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = stringResource(R.string.plugin_tab_repo),
                        )
                    }
                },
                actions = {
                    if (uiState.rebootNeeded) {
                        IconButton(onClick = actions.onReboot) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = stringResource(R.string.plugin_reboot),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.loading,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                InstalledListMaterial(
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
private fun InstalledListMaterial(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    bottomInnerPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.installed.isEmpty()) {
            PluginCenterHintMaterial(stringResource(R.string.plugin_empty))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomInnerPadding + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState.rebootNeeded) {
                    item(key = "reboot_banner") {
                        RebootBannerMaterial(onReboot = actions.onReboot)
                    }
                }
                items(uiState.installed, key = { it.installed.id }) { item ->
                    InstalledCardMaterial(
                        item = item,
                        onToggleEnabled = actions.onToggleEnabled,
                        onUninstall = actions.onUninstall,
                        onUndoUninstall = actions.onUndoUninstall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RebootBannerMaterial(onReboot: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.plugin_reboot_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onReboot) {
                Text(stringResource(R.string.plugin_reboot))
            }
        }
    }
}

@Composable
private fun InstalledCardMaterial(
    item: InstalledPluginItem,
    onToggleEnabled: (InstalledPlugin, Boolean) -> Unit,
    onUninstall: (InstalledPlugin) -> Unit,
    onUndoUninstall: (InstalledPlugin) -> Unit,
) {
    val installed = item.installed
    val name = item.info?.name ?: installed.id
    val version = installed.version.ifBlank { item.info?.latest.orEmpty() }
    val summary = item.info?.summary.orEmpty()
    val textDecoration = if (installed.pendingUninstall) TextDecoration.LineThrough else null
    var expanded by remember(installed.id) { mutableStateOf(false) }

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (summary.isNotBlank()) expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stringResource(R.string.plugin_version)}: $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = textDecoration,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ExpressiveSwitch(
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (installed.pendingUninstall) R.string.plugin_pending_uninstall
                            else R.string.plugin_restart_needed
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installed.pendingUninstall) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            HorizontalDivider(thickness = Dp.Hairline, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = {
                        if (installed.pendingUninstall) onUndoUninstall(installed)
                        else onUninstall(installed)
                    },
                ) {
                    Icon(
                        imageVector = if (installed.pendingUninstall) {
                            Icons.Rounded.Sync
                        } else {
                            Icons.Rounded.DeleteOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (installed.pendingUninstall) R.string.plugin_undo else R.string.plugin_uninstall
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun PluginCenterHintMaterial(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
