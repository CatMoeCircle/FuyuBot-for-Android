package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.TonalCard
import com.CatMoeCircle.fuyutd.ui.component.material.TopBarBackButton
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun PluginRepoScreenMaterial(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    onBack: () -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showFilter by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.plugin_repo_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = stringResource(R.string.plugin_repo_filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilter,
                            onDismissRequest = { showFilter = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.plugin_repo_only_supported),
                                        color = if (uiState.repoOnlySupported) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.repoOnlySupported) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    actions.onToggleRepoOnlySupported()
                                    showFilter = false
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.plugin_repo_sort_by_name),
                                        color = if (uiState.repoSortByName) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.repoSortByName) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    actions.onToggleRepoSortByName()
                                    showFilter = false
                                },
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
            OutlinedTextField(
                value = uiState.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.plugin_repo_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            PullToRefreshBox(
                isRefreshing = uiState.loading,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                RepoListMaterial(
                    uiState = uiState,
                    actions = actions,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                )
            }
        }
    }
}

@Composable
private fun RepoListMaterial(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    nestedScrollConnection: NestedScrollConnection,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loadStatus == PluginLoadStatus.Loading && uiState.repoItems.isEmpty() -> {
                PluginCenterHintMaterial(stringResource(R.string.plugin_loading))
            }

            uiState.loadStatus == PluginLoadStatus.Failed -> {
                PluginCenterHintMaterial(stringResource(R.string.plugin_load_failed, uiState.loadError.orEmpty()))
            }

            uiState.repoItems.isEmpty() -> {
                PluginCenterHintMaterial(stringResource(R.string.plugin_empty_filtered))
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.repoItems, key = { it.info.id }) { item ->
                        RepoCardMaterial(
                            item = item,
                            onClick = { actions.onOpenDetail(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCardMaterial(
    item: RepoPluginItem,
    onClick: () -> Unit,
) {
    val installed = item.installed

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.info.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (installed != null) {
                    Spacer(Modifier.width(8.dp))
                    TagMaterial(
                        text = stringResource(R.string.plugin_installed),
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Text(
                text = stringResource(R.string.plugin_repo_id, item.info.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stringResource(R.string.plugin_version)}: ${item.info.latest}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.info.summary.isNotBlank()) {
                Text(
                    text = item.info.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (installed == null && item.installProgress != null) {
                LinearProgressIndicator(
                    progress = { item.installProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.plugin_downloading, item.installProgress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.error != null) {
                Text(
                    text = item.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TagMaterial(text: String, background: Color, tint: Color) {
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
