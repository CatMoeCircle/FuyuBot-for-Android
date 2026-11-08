package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableBlur
import com.CatMoeCircle.fuyutd.ui.util.BlurredBar
import com.CatMoeCircle.fuyutd.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun TomlConfigScreenMiuix(
    uiState: TomlConfigUiState,
    editor: TextFieldValue,
    kernelRunning: Boolean,
    searchQuery: String,
    actions: TomlConfigActions,
    bottomBar: @Composable () -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.toml_config_file_name),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
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
                        IconButton(
                            onClick = actions.onSave,
                            enabled = !uiState.loading &&
                                !uiState.busy &&
                                (uiState.dirty || uiState.error != null),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.toml_config_save),
                                tint = if (uiState.dirty || uiState.error != null) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onBackground.copy(alpha = 0.4f)
                                },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = bottomBar,
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
                TomlConfigStatusMiuix(uiState)

                TomlCodeEditor(
                    value = editor,
                    onValueChange = actions.onEditorChange,
                    colors = TomlEditorColors(
                        background = colorScheme.background,
                        gutterBackground = colorScheme.background,
                        divider = colorScheme.onBackground.copy(alpha = 0.12f),
                        text = colorScheme.onBackground,
                        lineNumber = colorScheme.onSurfaceVariantSummary,
                        cursor = colorScheme.primary,
                        comment = colorScheme.onSurfaceVariantSummary,
                        section = colorScheme.primary,
                        key = colorScheme.primary,
                        string = colorScheme.error,
                        value = colorScheme.primary.copy(alpha = 0.85f),
                        matchBackground = colorScheme.primary.copy(alpha = 0.16f),
                        activeMatchBackground = colorScheme.primary.copy(alpha = 0.32f),
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
                            modifier = Modifier.padding(top = 8.dp),
                            color = colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    TomlConfigNoticeMiuix(
                        uiState = uiState,
                        kernelRunning = kernelRunning,
                        onRestartKernel = actions.onRestartKernel,
                    )
                }
            }
        }
    }
}

@Composable
private fun TomlConfigStatusMiuix(uiState: TomlConfigUiState) {
    val statusText = when {
        uiState.loading || uiState.busy -> stringResource(R.string.toml_config_working)
        uiState.error != null -> stringResource(R.string.toml_config_status_invalid)
        uiState.dirty -> stringResource(R.string.toml_config_status_modified)
        else -> stringResource(R.string.toml_config_status_valid)
    }
    val statusColor = when {
        uiState.error != null -> colorScheme.error
        uiState.dirty && !uiState.loading && !uiState.busy -> colorScheme.primary
        else -> colorScheme.onSurfaceVariantSummary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Text(text = statusText, color = statusColor, fontSize = 12.sp)
        if (uiState.path.isNotEmpty()) {
            Text(
                text = uiState.path,
                color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TomlConfigNoticeMiuix(
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
            color = colorScheme.primary,
            fontSize = 12.sp,
        )
    }
    if (uiState.restartRequired) {
        Card(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .fillMaxWidth(),
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
                    TextButton(
                        text = stringResource(R.string.toml_config_restart_kernel),
                        onClick = onRestartKernel,
                        modifier = Modifier.padding(top = 6.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
