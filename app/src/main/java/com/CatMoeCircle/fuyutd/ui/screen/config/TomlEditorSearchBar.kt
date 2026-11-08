package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

enum class TomlEditorSearchMode {
    FIND,
    REPLACE,
    JUMP,
}

private data class SearchBarColors(
    val background: Color,
    val inputBackground: Color,
    val divider: Color,
    val content: Color,
    val hint: Color,
    val accent: Color,
    val error: Color,
)

@Composable
private fun searchBarColors(): SearchBarColors = when (LocalUiMode.current) {
    UiMode.Material -> SearchBarColors(
        background = MaterialTheme.colorScheme.surfaceContainerHigh,
        inputBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        divider = MaterialTheme.colorScheme.outlineVariant,
        content = MaterialTheme.colorScheme.onSurface,
        hint = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.primary,
        error = MaterialTheme.colorScheme.error,
    )

    UiMode.Miuix -> SearchBarColors(
        background = colorScheme.surface,
        inputBackground = colorScheme.surfaceContainer,
        divider = colorScheme.onBackground.copy(alpha = 0.08f),
        content = colorScheme.onSurface,
        hint = colorScheme.onSurfaceVariantSummary,
        accent = colorScheme.primary,
        error = colorScheme.error,
    )
}

/**
 * 固定在页面底部的查找 / 替换 / 跳行栏。
 *
 * 编辑器保留在栏上方并随键盘缩小，命中的内容会在编辑器内高亮并滚动到可视区域，
 * 不再用弹窗遮挡正文。
 */
@Composable
fun TomlEditorSearchBar(
    mode: TomlEditorSearchMode,
    query: String,
    replacement: String,
    jumpLine: String,
    message: String?,
    matchPosition: String?,
    hasMatches: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onJumpLineChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onJump: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = searchBarColors()
    val queryFocus = remember { FocusRequester() }
    val jumpFocus = remember { FocusRequester() }

    LaunchedEffect(mode) {
        when (mode) {
            TomlEditorSearchMode.FIND,
            TomlEditorSearchMode.REPLACE -> queryFocus.requestFocus()

            TomlEditorSearchMode.JUMP -> jumpFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider),
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (mode) {
                    TomlEditorSearchMode.JUMP -> {
                        BarTextField(
                            value = jumpLine,
                            onValueChange = onJumpLineChange,
                            placeholder = stringResource(R.string.toml_jump_line),
                            colors = colors,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(jumpFocus),
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            onImeAction = onJump,
                        )
                        Spacer(Modifier.width(8.dp))
                        BarTextAction(
                            text = stringResource(R.string.toml_jump_confirm),
                            enabled = jumpLine.isNotEmpty(),
                            colors = colors,
                            onClick = onJump,
                        )
                    }

                    TomlEditorSearchMode.FIND,
                    TomlEditorSearchMode.REPLACE -> {
                        BarTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = stringResource(R.string.toml_find_query),
                            colors = colors,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(queryFocus),
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                            onImeAction = onNext,
                        )
                        if (matchPosition != null) {
                            BasicText(
                                text = matchPosition,
                                style = TextStyle(color = colors.hint, fontSize = 12.sp),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        BarIcon(
                            icon = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.toml_find_previous),
                            enabled = hasMatches,
                            colors = colors,
                            onClick = onPrevious,
                        )
                        BarIcon(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.toml_find_next),
                            enabled = hasMatches,
                            colors = colors,
                            onClick = onNext,
                        )
                    }
                }
                BarIcon(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.toml_search_close),
                    enabled = true,
                    colors = colors,
                    onClick = onClose,
                )
            }

            if (mode == TomlEditorSearchMode.REPLACE) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarTextField(
                        value = replacement,
                        onValueChange = onReplacementChange,
                        placeholder = stringResource(R.string.toml_replace_with),
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Done,
                        onImeAction = onReplaceOne,
                    )
                    Spacer(Modifier.width(8.dp))
                    BarTextAction(
                        text = stringResource(R.string.toml_replace_one),
                        enabled = query.isNotEmpty(),
                        colors = colors,
                        onClick = onReplaceOne,
                    )
                    BarTextAction(
                        text = stringResource(R.string.toml_replace_all),
                        enabled = query.isNotEmpty(),
                        colors = colors,
                        onClick = onReplaceAll,
                    )
                }
            }

            message?.let {
                BasicText(
                    text = it,
                    style = TextStyle(color = colors.error, fontSize = 12.sp),
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    colors: SearchBarColors,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: () -> Unit = {},
) {
    // 用 TextFieldValue 保存光标：重开搜索栏时光标落在已有内容末尾，而不是开头。
    var textState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    SideEffect {
        if (textState.text != value) {
            textState = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    BasicTextField(
        value = textState,
        onValueChange = { newValue ->
            textState = newValue
            onValueChange(newValue.text)
        },
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.inputBackground)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        textStyle = TextStyle(color = colors.content, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSearch = { onImeAction() },
            onDone = { onImeAction() },
            onNext = { onImeAction() },
        ),
        decorationBox = { innerTextField ->
            Box {
                if (textState.text.isEmpty()) {
                    BasicText(
                        text = placeholder,
                        style = TextStyle(color = colors.hint, fontSize = 14.sp),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    colors: SearchBarColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) colors.content else colors.hint.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun BarTextAction(
    text: String,
    enabled: Boolean,
    colors: SearchBarColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (enabled) colors.accent else colors.hint.copy(alpha = 0.5f),
                fontSize = 13.sp,
            ),
        )
    }
}
