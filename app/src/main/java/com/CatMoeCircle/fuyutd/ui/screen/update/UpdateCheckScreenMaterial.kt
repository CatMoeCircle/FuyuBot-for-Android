package com.CatMoeCircle.fuyutd.ui.screen.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.SegmentedColumn
import com.CatMoeCircle.fuyutd.ui.component.material.SegmentedListItem
import com.CatMoeCircle.fuyutd.ui.component.material.SegmentedSwitchItem
import com.CatMoeCircle.fuyutd.ui.component.material.TopBarBackButton
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun UpdateCheckScreenMaterial(
    uiState: UpdateUiState,
    actions: UpdateScreenActions,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                title = { Text(stringResource(R.string.update_title)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Rounded.Sync,
                        title = stringResource(R.string.update_auto_check),
                        summary = stringResource(R.string.update_auto_check_summary),
                        checked = uiState.autoCheck,
                        onCheckedChange = actions.onSetAutoCheck,
                    )
                },
            )

            UpdateChannelColumnMaterial(
                icon = Icons.Rounded.SystemUpdate,
                state = uiState.app,
                onAction = actions.onCheckApp,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UpdateChannelColumnMaterial(
    icon: ImageVector,
    state: UpdateChannelState,
    onAction: () -> Unit,
) {
    val title = channelTitle(state.channel)

    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf {
            SegmentedListItem(
                onClick = onAction,
                leadingContent = { Icon(icon, contentDescription = title) },
                headlineContent = { Text(title) },
                supportingContent = { Text(channelStatusText(state)) },
                trailingContent = {
                    Text(
                        text = channelActionText(state),
                        color = if (state.hasUpdate) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        },
    )
}
