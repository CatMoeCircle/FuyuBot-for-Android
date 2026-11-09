package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableBlur
import com.CatMoeCircle.fuyutd.ui.util.BlurredBar
import com.CatMoeCircle.fuyutd.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PluginRepoScreenMiuix(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.plugin_repo_title),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showFilter = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Filter,
                                    contentDescription = stringResource(R.string.plugin_repo_filter),
                                    tint = colorScheme.onBackground,
                                )
                            }
                            RepoFilterPopupMiuix(
                                show = showFilter,
                                uiState = uiState,
                                onToggleOnlySupported = actions.onToggleRepoOnlySupported,
                                onToggleSortByName = actions.onToggleRepoSortByName,
                                onDismiss = { showFilter = false },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                SearchBar(
                    expanded = false,
                    onExpandedChange = { },
                    inputField = {
                        InputField(
                            query = uiState.query,
                            onQueryChange = actions.onQueryChange,
                            onSearch = actions.onQueryChange,
                            expanded = false,
                            onExpandedChange = { },
                            label = stringResource(R.string.plugin_repo_search_placeholder),
                        )
                    },
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp),
                    content = { },
                )

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
                    RepoListMiuix(
                        uiState = uiState,
                        actions = actions,
                        nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoFilterPopupMiuix(
    show: Boolean,
    uiState: PluginUiState,
    onToggleOnlySupported: () -> Unit,
    onToggleSortByName: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            DropdownImpl(
                item = DropdownItem(
                    text = stringResource(R.string.plugin_repo_only_supported),
                    selected = uiState.repoOnlySupported,
                ),
                optionSize = 2,
                isSelected = uiState.repoOnlySupported,
                index = 0,
                isFirst = true,
                onSelectedIndexChange = {
                    onToggleOnlySupported()
                    onDismiss()
                },
            )
            DropdownImpl(
                item = DropdownItem(
                    text = stringResource(R.string.plugin_repo_sort_by_name),
                    selected = uiState.repoSortByName,
                ),
                optionSize = 2,
                isSelected = uiState.repoSortByName,
                index = 1,
                isLast = true,
                onSelectedIndexChange = {
                    onToggleSortByName()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun RepoListMiuix(
    uiState: PluginUiState,
    actions: PluginScreenActions,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.storeDisabled -> {
                PluginCenterHint(stringResource(R.string.plugin_store_disabled))
            }

            uiState.loadStatus == PluginLoadStatus.Loading && uiState.repoItems.isEmpty() -> {
                PluginCenterHint(stringResource(R.string.plugin_loading))
            }

            uiState.loadStatus == PluginLoadStatus.Failed -> {
                PluginCenterHint(stringResource(R.string.plugin_load_failed, uiState.loadError.orEmpty()))
            }

            uiState.repoItems.isEmpty() -> {
                PluginCenterHint(stringResource(R.string.plugin_empty_filtered))
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    overscrollEffect = null,
                ) {
                    items(uiState.repoItems, key = { it.info.id }) { item ->
                        RepoCardMiuix(
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
private fun RepoCardMiuix(
    item: RepoPluginItem,
    onClick: () -> Unit,
) {
    val installed = item.installed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
        showIndication = true,
        onClick = onClick,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.info.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (installed != null) {
                    Spacer(Modifier.width(8.dp))
                    TagMiuix(
                        text = stringResource(R.string.plugin_installed),
                        background = colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        tint = colorScheme.onSecondaryContainer,
                    )
                }
            }

            Text(
                text = stringResource(R.string.plugin_repo_id, item.info.id),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "${stringResource(R.string.plugin_version)}: ${item.info.latest}",
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 1.dp),
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
            )

            if (item.info.summary.isNotBlank()) {
                Text(
                    text = item.info.summary,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }

            if (installed == null && item.installProgress != null) {
                LinearProgressIndicator(
                    progress = item.installProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.plugin_downloading, item.installProgress),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.error != null) {
                Text(
                    text = item.error.orEmpty(),
                    fontSize = 12.sp,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TagMiuix(text: String, background: Color, tint: Color) {
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
