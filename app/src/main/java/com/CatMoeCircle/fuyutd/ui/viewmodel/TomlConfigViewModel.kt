package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.FuyuBotConfigRepository
import com.CatMoeCircle.fuyutd.ui.screen.config.TomlConfigNotice
import com.CatMoeCircle.fuyutd.ui.screen.config.TomlConfigUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * 官方 `fuyubot.toml` 的编辑状态。
 *
 * 导入 / 导出走系统文件选择器；保存时先校验完整 TOML，再原子替换配置文件。
 */
class TomlConfigViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TomlConfigUiState())
    val uiState: StateFlow<TomlConfigUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var operationJob: Job? = null

    /** 每次进入页面重新读取磁盘文件，便于识别外部编辑。 */
    fun reload(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
        if (operationJob?.isActive == true) return

        _uiState.update { it.copy(loading = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val repository = FuyuBotConfigRepository(appContext)
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val text = repository.readText()
                    Triple(repository.configPath(), text, repository.validateText(text))
                }
            }
            loaded.fold(
                onSuccess = { (path, text, validationError) ->
                    _uiState.value = TomlConfigUiState(
                        path = path,
                        text = text,
                        savedText = text,
                        loading = false,
                        error = validationError,
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun updateText(text: String) {
        _uiState.update {
            it.copy(
                text = text,
                error = null,
                notice = null,
            )
        }
    }

    fun save() {
        val context = appContext ?: return
        if (operationJob?.isActive == true) return
        val snapshot = _uiState.value.text
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FuyuBotConfigRepository(context).saveText(snapshot)
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            savedText = snapshot,
                            notice = TomlConfigNotice.SAVED,
                            restartRequired = true,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    /** 只校验当前编辑器内容，不写入文件。 */
    fun validateCurrent() {
        val context = appContext ?: return
        if (operationJob?.isActive == true) return
        val snapshot = _uiState.value.text
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                FuyuBotConfigRepository(context).validateText(snapshot)
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    error = error,
                    notice = if (error == null) TomlConfigNotice.VALID else null,
                )
            }
        }
    }

    /** 校验并重新排版编辑器内容，仍需手动保存。 */
    fun format() {
        val context = appContext ?: return
        if (operationJob?.isActive == true) return
        val snapshot = _uiState.value.text
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val formatted = withContext(Dispatchers.IO) {
                FuyuBotConfigRepository(context).formatText(snapshot)
            }
            formatted.fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(
                            text = text,
                            busy = false,
                            notice = TomlConfigNotice.FORMATTED,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun importFrom(context: Context, uri: Uri) {
        if (operationJob?.isActive == true) return
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw FileNotFoundException(uri.toString())
                    require(text.length <= MAX_CONFIG_CHARS) {
                        "配置文件过大（上限 $MAX_CONFIG_CHARS 字符）"
                    }
                    text
                }
            }
            imported.fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(
                            text = text,
                            busy = false,
                            notice = TomlConfigNotice.IMPORTED,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun exportTo(context: Context, uri: Uri) {
        if (operationJob?.isActive == true) return
        val snapshot = _uiState.value.text
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val exported = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(snapshot) }
                        ?: throw FileNotFoundException(uri.toString())
                }
            }
            exported.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(busy = false, notice = TomlConfigNotice.EXPORTED)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun resetToTemplate() {
        val context = appContext ?: return
        if (operationJob?.isActive == true) return
        _uiState.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            val repository = FuyuBotConfigRepository(context)
            val reset = withContext(Dispatchers.IO) {
                runCatching {
                    repository.resetToTemplate().getOrThrow()
                    repository.configPath() to repository.readText()
                }
            }
            reset.fold(
                onSuccess = { (path, text) ->
                    _uiState.value = TomlConfigUiState(
                        path = path,
                        text = text,
                        savedText = text,
                        loading = false,
                        notice = TomlConfigNotice.RESET,
                        restartRequired = true,
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    /** 内核已按新配置重启成功，清除「待重启」提示。 */
    fun markRestartApplied() {
        _uiState.update { it.copy(restartRequired = false) }
    }

    private companion object {
        const val MAX_CONFIG_CHARS = 1_000_000
    }
}
