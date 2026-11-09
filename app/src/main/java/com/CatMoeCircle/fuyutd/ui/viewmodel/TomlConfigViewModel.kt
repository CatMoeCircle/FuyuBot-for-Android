package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.FuyuBotConfigRepository
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotRuntime
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
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
import org.json.JSONObject
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
            val reset = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = FuyuBotConfigRepository(context)
                    resetWithKernelOrTemplate(context, repository)
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

    /**
     * 优先让内核按自己的模板重写配置，避免 App 自带模板和上游 schema 漂移。
     *
     * 旧内核没有 `nativeInitConfig`、或内核还没安装时，回退到 App 的 raw 模板。
     */
    private fun resetWithKernelOrTemplate(
        context: Context,
        repository: FuyuBotConfigRepository,
    ) {
        val runtime = FuyuBotRuntime.instance()
        val viaKernel = runtime.ensureLoaded(context).mapCatching {
            runtime.initConfig(configInitJson(context)).getOrThrow()
        }
        if (viaKernel.isFailure) {
            repository.resetToTemplate().getOrThrow()
        }
    }

    /** `nativeInitConfig` 的入参：整份重写，并保留 App 侧固定的目录布局。 */
    private fun configInitJson(context: Context): String = JSONObject()
        .put("root", KernelManager.storageDir(context).absolutePath)
        .put("api_id", 0)
        .put("api_hash", "")
        .put("account_type", "bot")
        .put("data_dir", KernelManager.DATA_DIR_NAME)
        .put("plugin_dir", KernelManager.PLUGIN_DIR_NAME)
        .put("log_level", "info")
        .put("tdlib_log_verbosity", 1)
        .put("tdlib_path", "")
        .put("overwrite", true)
        .toString()

    private companion object {
        const val MAX_CONFIG_CHARS = 1_000_000
    }
}
