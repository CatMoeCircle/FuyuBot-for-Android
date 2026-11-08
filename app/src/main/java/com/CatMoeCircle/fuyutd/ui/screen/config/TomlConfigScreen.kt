package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.viewmodel.TomlConfigViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun TomlConfigScreen(
    kernelRunning: Boolean,
    onRestartKernel: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<TomlConfigViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var searchMode by rememberSaveable { mutableStateOf<TomlEditorSearchMode?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var jumpLine by rememberSaveable { mutableStateOf("") }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var editor by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    val noMatchMessage = stringResource(R.string.toml_find_no_match)
    val emptyQueryMessage = stringResource(R.string.toml_find_empty)
    val invalidLineMessage = stringResource(R.string.toml_jump_invalid)

    val matches = remember(editor.text, query, searchMode) {
        if (searchMode == TomlEditorSearchMode.FIND ||
            searchMode == TomlEditorSearchMode.REPLACE
        ) {
            findTomlMatches(editor.text, query)
        } else {
            emptyList()
        }
    }
    val currentMatchIndex = matches.indexOfFirst { match ->
        match.first == editor.selection.min && match.last + 1 == editor.selection.max
    }

    fun updateEditor(value: TextFieldValue, recordHistory: Boolean = true) {
        if (value == editor) return
        if (recordHistory && value.text != editor.text) {
            undoStack.add(editor)
            if (undoStack.size > HISTORY_LIMIT) undoStack.removeAt(0)
            redoStack.clear()
        }
        editor = value
        viewModel.updateText(value.text)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(editor)
        updateEditor(undoStack.removeAt(undoStack.lastIndex), recordHistory = false)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(editor)
        updateEditor(redoStack.removeAt(redoStack.lastIndex), recordHistory = false)
    }

    fun selectMatch(match: IntRange) {
        editor = editor.copy(selection = TextRange(match.first, match.last + 1))
        searchMessage = null
    }

    fun nextMatch() {
        if (query.isEmpty()) {
            searchMessage = emptyQueryMessage
            return
        }
        if (matches.isEmpty()) {
            searchMessage = noMatchMessage
            return
        }
        val index = if (currentMatchIndex >= 0) {
            (currentMatchIndex + 1) % matches.size
        } else {
            0
        }
        selectMatch(matches[index])
    }

    fun previousMatch() {
        if (query.isEmpty()) {
            searchMessage = emptyQueryMessage
            return
        }
        if (matches.isEmpty()) {
            searchMessage = noMatchMessage
            return
        }
        val index = if (currentMatchIndex >= 0) {
            (currentMatchIndex - 1 + matches.size) % matches.size
        } else {
            matches.lastIndex
        }
        selectMatch(matches[index])
    }

    fun replaceCurrent() {
        if (query.isEmpty()) {
            searchMessage = emptyQueryMessage
            return
        }
        val match = matches.getOrNull(currentMatchIndex) ?: matches.firstOrNull()
        if (match == null) {
            searchMessage = noMatchMessage
            return
        }
        val updated = editor.text.replaceRange(match.first, match.last + 1, replacement)
        val nextMatches = findTomlMatches(updated, query)
        val next = nextMatches.firstOrNull { it.first >= match.first + replacement.length }
            ?: nextMatches.firstOrNull()
        updateEditor(
            editor.copy(
                text = updated,
                selection = if (next != null) {
                    TextRange(next.first, next.last + 1)
                } else {
                    TextRange((match.first + replacement.length).coerceAtMost(updated.length))
                },
            )
        )
        searchMessage = null
    }

    fun replaceAllMatches() {
        if (query.isEmpty()) {
            searchMessage = emptyQueryMessage
            return
        }
        if (matches.isEmpty()) {
            searchMessage = noMatchMessage
            return
        }
        val updated = editor.text.replace(query, replacement)
        updateEditor(
            editor.copy(
                text = updated,
                selection = TextRange(updated.length),
            )
        )
        searchMessage = null
    }

    fun closeSearch() {
        searchMode = null
        searchMessage = null
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun jumpToLine() {
        val requested = jumpLine.toIntOrNull()
        val lines = editor.text.split('\n')
        if (requested == null || requested !in 1..lines.size) {
            searchMessage = invalidLineMessage
            return
        }
        val offset = lines.take(requested - 1).sumOf { it.length + 1 }
        editor = editor.copy(selection = TextRange(offset))
        closeSearch()
    }

    LaunchedEffect(Unit) {
        viewModel.reload(context)
    }
    LaunchedEffect(uiState.text) {
        if (editor.text != uiState.text) {
            editor = TextFieldValue(
                text = uiState.text,
                selection = TextRange(0),
            )
            undoStack.clear()
            redoStack.clear()
        }
    }

    // 查询内容变化时自动跳到第一个匹配项，正文里滚动并高亮它。
    LaunchedEffect(searchMode, query) {
        if (searchMode == TomlEditorSearchMode.FIND ||
            searchMode == TomlEditorSearchMode.REPLACE
        ) {
            if (query.isNotEmpty()) {
                matches.firstOrNull()?.let { match ->
                    editor = editor.copy(selection = TextRange(match.first, match.last + 1))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFrom(context, uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.exportTo(context, uri)
    }

    val actions = TomlConfigActions(
        onBack = { navigator.pop() },
        onEditorChange = { value -> updateEditor(value) },
        onSave = viewModel::save,
        onFormat = viewModel::format,
        onValidate = viewModel::validateCurrent,
        onImport = {
            importLauncher.launch(
                arrayOf(
                    "text/plain",
                    "application/toml",
                    "application/x-toml",
                    "application/octet-stream",
                )
            )
        },
        onExport = {
            exportLauncher.launch("fuyubot.toml")
        },
        onRequestReset = { showResetConfirm = true },
        onRestartKernel = onRestartKernel,
    )
    val toolbarActions = TomlEditorToolbarActions(
        onUndo = ::undo,
        onRedo = ::redo,
        onFind = {
            searchMode = TomlEditorSearchMode.FIND
            searchMessage = null
        },
        onReplace = {
            searchMode = TomlEditorSearchMode.REPLACE
            searchMessage = null
        },
        onJump = {
            jumpLine = ""
            searchMode = TomlEditorSearchMode.JUMP
            searchMessage = null
        },
        onFormat = actions.onFormat,
        onValidate = actions.onValidate,
        onImport = actions.onImport,
        onExport = actions.onExport,
        onReset = actions.onRequestReset,
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
        enabled = !uiState.loading && !uiState.busy,
    )

    val matchPosition = if (searchMode == TomlEditorSearchMode.FIND ||
        searchMode == TomlEditorSearchMode.REPLACE
    ) {
        when {
            query.isEmpty() -> null
            matches.isEmpty() -> "0/0"
            currentMatchIndex >= 0 -> "${currentMatchIndex + 1}/${matches.size}"
            else -> "0/${matches.size}"
        }
    } else {
        null
    }
    val searchQuery = if (searchMode == TomlEditorSearchMode.FIND ||
        searchMode == TomlEditorSearchMode.REPLACE
    ) {
        query
    } else {
        ""
    }

    val bottomBar: @Composable () -> Unit = {
        val mode = searchMode
        if (mode == null) {
            TomlEditorToolbar(toolbarActions)
        } else {
            TomlEditorSearchBar(
                mode = mode,
                query = query,
                replacement = replacement,
                jumpLine = jumpLine,
                message = searchMessage,
                matchPosition = matchPosition,
                hasMatches = matches.isNotEmpty(),
                onQueryChange = { value ->
                    query = value
                    searchMessage = null
                },
                onReplacementChange = { value ->
                    replacement = value
                    searchMessage = null
                },
                onJumpLineChange = { value ->
                    if (value.isEmpty() || value.all { it.isDigit() }) {
                        jumpLine = value
                        searchMessage = null
                    }
                },
                onPrevious = ::previousMatch,
                onNext = ::nextMatch,
                onReplaceOne = ::replaceCurrent,
                onReplaceAll = ::replaceAllMatches,
                onJump = ::jumpToLine,
                onClose = ::closeSearch,
            )
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> TomlConfigScreenMiuix(
            uiState = uiState,
            editor = editor,
            kernelRunning = kernelRunning,
            searchQuery = searchQuery,
            actions = actions,
            bottomBar = bottomBar,
        )

        UiMode.Material -> TomlConfigScreenMaterial(
            uiState = uiState,
            editor = editor,
            kernelRunning = kernelRunning,
            searchQuery = searchQuery,
            actions = actions,
            bottomBar = bottomBar,
        )
    }

    TomlConfigResetDialog(
        show = showResetConfirm,
        onDismiss = { showResetConfirm = false },
        onConfirm = {
            showResetConfirm = false
            viewModel.resetToTemplate()
        },
    )
}

@Composable
private fun TomlConfigResetDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = show,
            title = stringResource(R.string.toml_config_reset_title),
            summary = stringResource(R.string.toml_config_reset_message),
            onDismissRequest = onDismiss,
            content = {
                Row {
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.toml_config_reset_confirm),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )

        UiMode.Material -> if (show) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.toml_config_reset_title)) },
                text = { Text(stringResource(R.string.toml_config_reset_message)) },
                confirmButton = {
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.toml_config_reset_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

private const val HISTORY_LIMIT = 100
