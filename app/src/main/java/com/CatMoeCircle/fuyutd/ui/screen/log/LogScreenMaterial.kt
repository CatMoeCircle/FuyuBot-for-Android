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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.LogEntry
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun LogPageMaterial(
    uiState: LogUiState,
    actions: LogScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showMore by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    // 「更多」弹窗内部的层级，不跳页面。
    var morePage by remember { mutableStateOf(LogMorePage.Root) }
    LaunchedEffect(showMore) {
        if (!showMore) morePage = LogMorePage.Root
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showClear = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.log_clear),
                            )
                        }
                        DropdownMenu(
                            expanded = showClear,
                            onDismissRequest = { showClear = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log_clear)) },
                                onClick = {
                                    actions.onClear()
                                    showClear = false
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.log_more),
                            )
                        }
                        DropdownMenu(
                            expanded = showMore,
                            onDismissRequest = { showMore = false },
                        ) {
                            when (morePage) {
                                LogMorePage.Root -> {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.log_level_title)) },
                                        onClick = { morePage = LogMorePage.Level },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.log_runtime_level_title))
                                        },
                                        onClick = { morePage = LogMorePage.RuntimeLevel },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.log_filter_title)) },
                                        onClick = { morePage = LogMorePage.Module },
                                    )
                                }

                                LogMorePage.Level -> {
                                    LogLevels.forEach { level ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = level.label(),
                                                    color = if (level == uiState.minLevel) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                )
                                            },
                                            trailingIcon = {
                                                if (level == uiState.minLevel) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                actions.onSetMinLevel(level)
                                                showMore = false
                                            },
                                        )
                                    }
                                }

                                LogMorePage.RuntimeLevel -> {
                                    RuntimeLogLevels.forEach { level ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = level.runtimeLabel(),
                                                    color = if (level == uiState.pluginLogLevel) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                )
                                            },
                                            trailingIcon = {
                                                if (level == uiState.pluginLogLevel) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                actions.onSetRuntimeLevel(level)
                                                showMore = false
                                            },
                                        )
                                    }
                                }

                                LogMorePage.Module -> {
                                    if (uiState.availableModules.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.log_filter_empty)) },
                                            onClick = { },
                                        )
                                    } else {
                                        uiState.availableModules.forEach { module ->
                                            val checked = module in uiState.moduleFilter
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = module,
                                                        color = if (checked) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (checked) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                },
                                                onClick = { actions.onToggleModule(module) },
                                            )
                                        }
                                    }
                                }
                            }
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
                .padding(horizontal = 16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.log_search_placeholder)) },
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
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = bottomInnerPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(uiState.visibleEntries) { entry ->
                        LogEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val context = LocalContext.current
    val levelColor = entry.level.materialColor()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("log", entry.copyText))
                Toast.makeText(context, context.getString(R.string.log_copied), Toast.LENGTH_SHORT)
                    .show()
            },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (entry.module.isNotEmpty()) {
                    Text(
                        text = entry.module,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (filtered) R.string.log_empty_filtered else R.string.log_empty
            ),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!filtered && fileMissing) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.log_empty_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
