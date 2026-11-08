package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.component.material.ExpressiveScaffold
import com.CatMoeCircle.fuyutd.ui.component.material.TopBarBackButton
import com.CatMoeCircle.fuyutd.ui.component.material.expressiveTopAppBarColors

@Composable
fun TomlConfigScreenMaterial(
    uiState: TomlConfigUiState,
    editor: TextFieldValue,
    kernelRunning: Boolean,
    searchQuery: String,
    actions: TomlConfigActions,
    bottomBar: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    ExpressiveScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                title = { Text(stringResource(R.string.toml_config_file_name)) },
                actions = {
                    IconButton(
                        onClick = actions.onSave,
                        enabled = !uiState.loading &&
                            !uiState.busy &&
                            (uiState.dirty || uiState.error != null),
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.toml_config_save),
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
            )
        },
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            TomlConfigStatusMaterial(uiState)

            TomlCodeEditor(
                value = editor,
                onValueChange = actions.onEditorChange,
                colors = TomlEditorColors(
                    background = colors.surface,
                    gutterBackground = colors.surface,
                    divider = colors.outlineVariant,
                    text = colors.onSurface,
                    lineNumber = colors.onSurfaceVariant,
                    cursor = colors.primary,
                    comment = colors.onSurfaceVariant,
                    section = colors.primary,
                    key = colors.tertiary,
                    string = colors.error,
                    value = colors.secondary,
                    matchBackground = colors.primary.copy(alpha = 0.16f),
                    activeMatchBackground = colors.primary.copy(alpha = 0.32f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                searchQuery = searchQuery,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                uiState.error?.let { error ->
                    Text(
                        text = stringResource(R.string.toml_config_error, error),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TomlConfigNoticeMaterial(
                    uiState = uiState,
                    kernelRunning = kernelRunning,
                    onRestartKernel = actions.onRestartKernel,
                )
            }
        }
    }
}

@Composable
private fun TomlConfigStatusMaterial(uiState: TomlConfigUiState) {
    val statusText = when {
        uiState.loading || uiState.busy -> stringResource(R.string.toml_config_working)
        uiState.error != null -> stringResource(R.string.toml_config_status_invalid)
        uiState.dirty -> stringResource(R.string.toml_config_status_modified)
        else -> stringResource(R.string.toml_config_status_valid)
    }
    val statusColor = when {
        uiState.error != null -> MaterialTheme.colorScheme.error
        uiState.dirty && !uiState.loading && !uiState.busy -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.labelMedium,
        )
        if (uiState.path.isNotEmpty()) {
            Text(
                text = uiState.path,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TomlConfigNoticeMaterial(
    uiState: TomlConfigUiState,
    kernelRunning: Boolean,
    onRestartKernel: () -> Unit,
) {
    val noticeText = when (uiState.notice) {
        TomlConfigNotice.SAVED -> stringResource(R.string.toml_config_saved)
        TomlConfigNotice.IMPORTED -> stringResource(R.string.toml_config_imported)
        TomlConfigNotice.EXPORTED -> stringResource(R.string.toml_config_exported)
        TomlConfigNotice.RESET -> stringResource(R.string.toml_config_reset_done)
        TomlConfigNotice.VALID -> stringResource(R.string.toml_config_valid)
        TomlConfigNotice.FORMATTED -> stringResource(R.string.toml_config_formatted)
        null -> null
    }
    noticeText?.let {
        Text(
            text = it,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (uiState.restartRequired) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(
                        if (kernelRunning) {
                            R.string.toml_config_restart_required
                        } else {
                            R.string.toml_config_apply_next_start
                        }
                    ),
                )
                if (kernelRunning) {
                    androidx.compose.material3.TextButton(
                        onClick = onRestartKernel,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(stringResource(R.string.toml_config_restart_kernel))
                    }
                }
            }
        }
    }
}
