package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue

enum class TomlConfigNotice {
    SAVED,
    IMPORTED,
    EXPORTED,
    RESET,
    VALID,
    FORMATTED,
}

@Immutable
data class TomlConfigUiState(
    val path: String = "",
    val text: String = "",
    val savedText: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: TomlConfigNotice? = null,
    val restartRequired: Boolean = false,
) {
    val dirty: Boolean
        get() = text != savedText
}

@Immutable
data class TomlConfigActions(
    val onBack: () -> Unit,
    val onEditorChange: (TextFieldValue) -> Unit,
    val onSave: () -> Unit,
    val onFormat: () -> Unit,
    val onValidate: () -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
    val onRequestReset: () -> Unit,
    val onRestartKernel: () -> Unit,
)
