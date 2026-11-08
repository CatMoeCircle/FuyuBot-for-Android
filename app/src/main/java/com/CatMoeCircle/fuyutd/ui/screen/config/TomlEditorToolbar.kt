package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import androidx.compose.material3.Text as MaterialText
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

data class TomlEditorToolbarActions(
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onFind: () -> Unit,
    val onReplace: () -> Unit,
    val onJump: () -> Unit,
    val onFormat: () -> Unit,
    val onValidate: () -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
    val onReset: () -> Unit,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val enabled: Boolean,
)

data class TomlToolbarItem(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun TomlEditorToolbar(actions: TomlEditorToolbarActions) {
    val items = listOf(
        TomlToolbarItem(stringResource(R.string.toml_tool_undo), actions.canUndo, actions.onUndo),
        TomlToolbarItem(stringResource(R.string.toml_tool_redo), actions.canRedo, actions.onRedo),
        TomlToolbarItem(stringResource(R.string.toml_tool_find), actions.enabled, actions.onFind),
        TomlToolbarItem(stringResource(R.string.toml_tool_replace), actions.enabled, actions.onReplace),
        TomlToolbarItem(stringResource(R.string.toml_tool_jump), actions.enabled, actions.onJump),
        TomlToolbarItem(stringResource(R.string.toml_tool_format), actions.enabled, actions.onFormat),
        TomlToolbarItem(stringResource(R.string.toml_tool_validate), actions.enabled, actions.onValidate),
        TomlToolbarItem(stringResource(R.string.toml_tool_import), actions.enabled, actions.onImport),
        TomlToolbarItem(stringResource(R.string.toml_tool_export), actions.enabled, actions.onExport),
        TomlToolbarItem(stringResource(R.string.toml_tool_reset), actions.enabled, actions.onReset),
    )

    when (LocalUiMode.current) {
        UiMode.Material -> EditorToolbar(
            items = items,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            divider = MaterialTheme.colorScheme.outlineVariant,
            content = MaterialTheme.colorScheme.onSurface,
        )

        UiMode.Miuix -> EditorToolbar(
            items = items,
            background = colorScheme.surface,
            divider = colorScheme.onBackground.copy(alpha = 0.08f),
            content = colorScheme.onBackground,
        )
    }
}

@Composable
private fun EditorToolbar(
    items: List<TomlToolbarItem>,
    background: Color,
    divider: Color,
    content: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 3.dp),
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = item.enabled, onClick = item.onClick)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ToolbarLabel(
                        text = item.label,
                        color = if (item.enabled) content else content.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarLabel(text: String, color: Color) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(text = text, color = color, fontSize = 13.sp, maxLines = 1)
        UiMode.Material -> MaterialText(text = text, color = color, fontSize = 13.sp, maxLines = 1)
    }
}
