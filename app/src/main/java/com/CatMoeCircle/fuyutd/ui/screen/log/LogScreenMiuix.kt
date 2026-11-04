package com.CatMoeCircle.fuyutd.ui.screen.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.LogEntry
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableBlur
import com.CatMoeCircle.fuyutd.ui.util.BlurredBar
import com.CatMoeCircle.fuyutd.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LogPageMiuix(
    uiState: LogUiState,
    actions: LogScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    var showMore by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.log_title),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { showClear = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = stringResource(R.string.log_clear),
                                    tint = colorScheme.onBackground,
                                )
                            }
                            LogClearPopupMiuix(
                                show = showClear,
                                onConfirm = {
                                    actions.onClear()
                                    showClear = false
                                },
                                onDismiss = { showClear = false },
                            )
                        }
                        Box {
                            IconButton(onClick = { showMore = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.log_more),
                                    tint = colorScheme.onBackground,
                                )
                            }
                            // 弹窗挂在按钮所在容器上，因此贴着按钮弹出而不是底部抽屉。
                            LogMorePopupMiuix(
                                show = showMore,
                                uiState = uiState,
                                actions = actions,
                                onDismiss = { showMore = false },
                            )
                        }
                    },
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
                            label = stringResource(R.string.log_search_placeholder),
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    content = { },
                )

                if (uiState.visibleEntries.isEmpty()) {
                    LogEmptyState(
                        fileMissing = uiState.fileMissing,
                        filtered = uiState.entries.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val listState = rememberLazyListState()
                    // 有新日志时自动贴底；用户往上翻则不再打扰。
                    LaunchedEffect(uiState.visibleEntries.size) {
                        val lastIndex = uiState.visibleEntries.lastIndex
                        if (lastIndex >= 0) {
                            val visibleLast = listState.layoutInfo.visibleItemsInfo
                                .lastOrNull()?.index ?: -1
                            if (visibleLast >= lastIndex - 1) {
                                listState.animateScrollToItem(lastIndex)
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(
                            bottom = bottomInnerPadding + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        overscrollEffect = null,
                    ) {
                        items(uiState.visibleEntries) { entry ->
                            LogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val context = LocalContext.current
    val levelColor = entry.level.miuixColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("log", entry.copyText))
                Toast.makeText(context, context.getString(R.string.log_copied), Toast.LENGTH_SHORT)
                    .show()
            },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(levelColor)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = entry.level.label(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = entry.displayTime,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                if (entry.module.isNotEmpty()) {
                    Text(
                        text = entry.module,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.message,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun LogEmptyState(
    fileMissing: Boolean,
    filtered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Article,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (filtered) R.string.log_empty_filtered else R.string.log_empty
            ),
            fontSize = 15.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        if (!filtered && fileMissing) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.log_empty_hint),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun LogMorePopupMiuix(
    show: Boolean,
    uiState: LogUiState,
    actions: LogScreenActions,
    onDismiss: () -> Unit,
) {
    // 三级只在这一个小窗口里切换，不跳页面：「更多」→「日志级别」/「过滤」。
    var page by remember { mutableStateOf(LogMorePage.Root) }
    LaunchedEffect(show) {
        if (!show) page = LogMorePage.Root
    }

    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.End,
        maxHeight = 360.dp,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            when (page) {
                LogMorePage.Root -> {
                    MoreRow(
                        label = stringResource(R.string.log_level_title),
                        hasSubmenu = true,
                        index = 0,
                        lastIndex = 1,
                        onClick = { page = LogMorePage.Level },
                    )
                    MoreRow(
                        label = stringResource(R.string.log_filter_title),
                        hasSubmenu = true,
                        index = 1,
                        lastIndex = 1,
                        onClick = { page = LogMorePage.Module },
                    )
                }

                LogMorePage.Level -> {
                    LogLevels.forEachIndexed { index, level ->
                        DropdownImpl(
                            item = DropdownItem(
                                text = level.label(),
                                selected = level == uiState.minLevel,
                            ),
                            optionSize = LogLevels.size,
                            isSelected = level == uiState.minLevel,
                            index = index,
                            isFirst = index == 0,
                            isLast = index == LogLevels.lastIndex,
                            onSelectedIndexChange = {
                                actions.onSetMinLevel(level)
                                onDismiss()
                            },
                        )
                    }
                }

                LogMorePage.Module -> {
                    if (uiState.availableModules.isEmpty()) {
                        DropdownImpl(
                            item = DropdownItem(text = stringResource(R.string.log_filter_empty)),
                            optionSize = 1,
                            isSelected = false,
                            index = 0,
                            isFirst = true,
                            isLast = true,
                            onSelectedIndexChange = { },
                        )
                    } else {
                        uiState.availableModules.forEachIndexed { index, module ->
                            DropdownImpl(
                                item = DropdownItem(
                                    text = module,
                                    selected = module in uiState.moduleFilter,
                                ),
                                optionSize = uiState.availableModules.size,
                                isSelected = module in uiState.moduleFilter,
                                index = index,
                                isFirst = index == 0,
                                isLast = index == uiState.availableModules.lastIndex,
                                onSelectedIndexChange = { actions.onToggleModule(module) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(
    label: String,
    hasSubmenu: Boolean,
    index: Int,
    lastIndex: Int,
    onClick: () -> Unit,
) {
    DropdownImpl(
        item = DropdownItem(text = label),
        optionSize = lastIndex + 1,
        isSelected = false,
        index = index,
        hasSubmenu = hasSubmenu,
        isFirst = index == 0,
        isLast = index == lastIndex,
        onSelectedIndexChange = { onClick() },
    )
}

@Composable
private fun LogClearPopupMiuix(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            DropdownImpl(
                item = DropdownItem(text = stringResource(R.string.log_clear)),
                optionSize = 1,
                isSelected = false,
                index = 0,
                isFirst = true,
                isLast = true,
                onSelectedIndexChange = { onConfirm() },
            )
        }
    }
}

/** 日志级别的展示与配色，两个风格共用。 */
