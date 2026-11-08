package com.CatMoeCircle.fuyutd.data

import android.content.Context
import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import org.json.JSONObject
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlWriteOptions
import java.io.File

/**
 * Fuyu-Bot 的官方 TOML 配置。
 *
 * 文件固定为 `Android/data/<包名>/files/fuyubot.toml`，字段结构与桌面版一致。
 * `[plugin.*]` 由内核直接读取；账号、插件日志和 TDLib 选项由这里解析后组装成 JNI options。
 */
class FuyuBotConfigRepository(context: Context) {

    private val context = context.applicationContext

    data class RuntimeConfig(
        val optionsJson: String,
        /** 用户插件目录；启动前会同步到内核可 dlopen 的私有只读副本。 */
        val pluginDir: File,
    )

    /** 首次授权（api_id / api_hash）是否已完成，且 TOML 本身可解析。 */
    val isAuthorized: Boolean
        get() = runCatching {
            val document = load()
            apiId(document) > 0 &&
                !document.getString(TELEGRAM_API_HASH).isNullOrBlank() &&
                accountType(document) != null
        }.getOrDefault(false)

    /** 返回配置错误；null 表示文件可解析。 */
    fun validationError(): String? = runCatching {
        validate(parse(configFileText()))
        null
    }.getOrElse { error ->
        error.message ?: error.javaClass.simpleName
    }

    /** 校验尚未保存的 TOML 文本。 */
    fun validateText(text: String): String? = runCatching {
        validate(parse(text))
        null
    }.getOrElse { error ->
        error.message ?: error.javaClass.simpleName
    }

    /** 读取当前配置文件原文。 */
    fun readText(): String = ensureConfigFile().readText()

    /** 用户实际编辑的 TOML 路径。 */
    fun configPath(): String = configFile().absolutePath

    /** 校验后原子写回完整 TOML。 */
    fun saveText(text: String): Result<Unit> = runCatching {
        validate(parse(text))
        writeConfig(text)
    }

    /** 校验并按标准布局重新排版，保留注释与每个值的 TOML 写法。 */
    fun formatText(text: String): Result<String> = runCatching {
        val document = parse(text)
        validate(document)
        document.toToml(
            TomlWriteOptions.defaults()
                .keep(TomlWriteOptions.Keep.NOTATION)
        )
    }

    /** 恢复随 APK 分发的默认模板。 */
    fun resetToTemplate(): Result<Unit> = runCatching {
        writeConfig(defaultTemplateText())
    }

    private fun validate(document: TomlParseResult) {
        require(apiId(document) >= 0) { "telegram.api_id 不能是负数" }
        require(apiId(document) <= Int.MAX_VALUE) { "telegram.api_id 超出 Int32 范围" }
        require(accountType(document) != null) {
            "telegram.account_type 必须是 bot 或 user"
        }
        pluginLogLevel(document)
        tdlibLogVerbosity(document)
    }

    /**
     * 解析 TOML 并组装 `nativeStart` 的 options JSON。
     *
     * [transientBotToken] 来自本次配置弹窗，不会写回 TOML；官方格式明确不持久化 Bot Token。
     * 如果用户手动在 TOML 里写了 `telegram.bot_token`，这里也会兼容读取。
     */
    fun buildRuntimeConfig(
        paths: KernelManager.KernelPaths,
        transientBotToken: String? = null,
    ): RuntimeConfig? {
        val document = load()
        val id = apiId(document)
        require(id <= Int.MAX_VALUE) { "telegram.api_id 超出 Int32 范围" }
        val hash = document.getString(TELEGRAM_API_HASH).orEmpty().trim()
        if (id <= 0 || hash.isEmpty()) return null

        val type = accountType(document) ?: error("telegram.account_type 必须是 bot 或 user")
        val configuredToken = document.getString(TELEGRAM_BOT_TOKEN).orEmpty().trim()
        val botToken = if (type == ACCOUNT_BOT) {
            transientBotToken.orEmpty().trim().ifEmpty { configuredToken }
        } else {
            ""
        }

        val dataDir = resolvePath(
            configured = document.getString(TELEGRAM_DATA_DIR).orEmpty(),
            default = File(paths.dataDir),
        )
        val libDir = resolveTdlibDir(
            configured = document.getString(TDLIB_PATH).orEmpty(),
            default = File(paths.libDir),
        )
        val options = JSONObject()
            .put("root", paths.root)
            .put("plugin_dir", relativeTo(paths.root, paths.pluginDir))
            .put("data_dir", relativeTo(paths.root, dataDir.absolutePath))
            .put("lib_dir", libDir.absolutePath)
            .put("api_id", id)
            .put("api_hash", hash)
            .put("log_level", pluginLogLevel(document))
            .put("tdlib_log_verbosity", tdlibLogVerbosity(document))
        if (botToken.isNotEmpty()) {
            options.put("bot_token", botToken)
        }

        return RuntimeConfig(
            optionsJson = options.toString(),
            pluginDir = pluginSourceDir(document),
        )
    }

    /** 用户插件目录，`[plugins].dir` 相对配置文件所在目录解析。 */
    fun pluginSourceDir(): File =
        runCatching { pluginSourceDir(load()) }
            .getOrElse { File(KernelManager.storageDir(context), DEFAULT_PLUGIN_DIR) }

    /**
     * 把配置弹窗里的账号信息写回官方 TOML，同时保留注释和 `[plugin.*]` 段。
     *
     * Bot Token 不落盘；它只用于本次启动。`account_type` 根据是否填写 Token 决定：
     * 填了为 bot，留空为 user。
     */
    fun writeAccount(apiId: Int, apiHash: String, botToken: String): Result<Unit> = runCatching {
        val document = load()
        document.set(TELEGRAM_API_ID, apiId.toLong())
        document.set(TELEGRAM_API_HASH, apiHash)
        document.set(
            TELEGRAM_ACCOUNT_TYPE,
            if (botToken.isBlank()) ACCOUNT_USER else ACCOUNT_BOT,
        )
        writeConfig(document.toToml())
    }

    /** 确保官方 TOML 存在；旧版 SharedPreferences 凭据会一次性迁入，之后删除旧键。 */
    fun ensureConfigFile(): File = synchronized(FILE_LOCK) {
        val file = configFile()
        if (file.isFile) return file

        file.parentFile?.mkdirs()
        val document = parse(defaultTemplateText())

        val prefs = AppPrefs.prefs()
        val legacyId = prefs.getInt(LEGACY_KEY_API_ID, 0)
        val legacyHash = prefs.getString(LEGACY_KEY_API_HASH, "").orEmpty()
        val legacyToken = prefs.getString(LEGACY_KEY_BOT_TOKEN, "").orEmpty()
        if (legacyId > 0 || legacyHash.isNotBlank()) {
            document.set(TELEGRAM_API_ID, legacyId.toLong())
            document.set(TELEGRAM_API_HASH, legacyHash)
            document.set(
                TELEGRAM_ACCOUNT_TYPE,
                if (legacyToken.isBlank()) ACCOUNT_USER else ACCOUNT_BOT,
            )
        }

        writeConfig(document.toToml())
        prefs.edit {
            remove(LEGACY_KEY_API_ID)
            remove(LEGACY_KEY_API_HASH)
            remove(LEGACY_KEY_BOT_TOKEN)
        }
        return file
    }

    private fun load(): TomlParseResult {
        val file = ensureConfigFile()
        return parse(file.readText())
    }

    private fun configFileText(): String = ensureConfigFile().readText()

    private fun defaultTemplateText(): String =
        context.resources.openRawResource(R.raw.fuyubot_toml)
            .bufferedReader()
            .use { it.readText() }

    private fun parse(text: String): TomlParseResult {
        val result = Toml.parse(text)
        require(!result.hasErrors()) {
            result.errors().joinToString("；") { it.toString() }
        }
        return result
    }

    private fun apiId(document: TomlParseResult): Long =
        document.getLong(TELEGRAM_API_ID) ?: 0

    private fun accountType(document: TomlParseResult): String? =
        document.getString(TELEGRAM_ACCOUNT_TYPE)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it == ACCOUNT_BOT || it == ACCOUNT_USER }

    private fun pluginLogLevel(document: TomlParseResult): String {
        val level = document.getString(PLUGIN_LOG_LEVEL)
            ?.trim()
            ?.lowercase()
            ?: PLUGIN_LOG_DEFAULT
        require(level in LOG_LEVELS) {
            "plugins.log_level 必须是 ${LOG_LEVELS.joinToString(" / ")}"
        }
        return level
    }

    private fun tdlibLogVerbosity(document: TomlParseResult): Long {
        val verbosity = document.getLong(TDLIB_LOG_VERBOSITY) ?: 1L
        require(verbosity >= 0) { "tdlib.log_verbosity 不能是负数" }
        return verbosity
    }

    private fun pluginSourceDir(document: TomlParseResult): File =
        resolvePath(
            configured = document.getString(PLUGIN_DIR).orEmpty(),
            default = File(KernelManager.storageDir(context), DEFAULT_PLUGIN_DIR),
        )

    private fun resolveTdlibDir(configured: String, default: File): File {
        if (configured.isBlank()) return default
        val path = resolvePath(configured, default)
        return if (path.isFile) path.parentFile ?: default else path
    }

    private fun resolvePath(configured: String, default: File): File {
        if (configured.isBlank()) return default
        val raw = File(configured)
        return if (raw.isAbsolute) raw else File(KernelManager.storageDir(context), configured)
    }

    private fun relativeTo(root: String, path: String): String {
        val prefix = File(root).absoluteFile.path + File.separator
        val target = File(path).absoluteFile.path
        return if (target.startsWith(prefix)) target.removePrefix(prefix) else target
    }

    private fun configFile(): File =
        File(KernelManager.storageDir(context), CONFIG_FILE_NAME)

    private fun writeConfig(text: String) {
        val target = configFile()
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        val FILE_LOCK = Any()

        const val CONFIG_FILE_NAME = "fuyubot.toml"
        const val DEFAULT_PLUGIN_DIR = "fuyubot-plugins"

        const val TELEGRAM_ACCOUNT_TYPE = "telegram.account_type"
        const val TELEGRAM_API_ID = "telegram.api_id"
        const val TELEGRAM_API_HASH = "telegram.api_hash"
        const val TELEGRAM_BOT_TOKEN = "telegram.bot_token"
        const val TELEGRAM_DATA_DIR = "telegram.data_dir"
        const val PLUGIN_DIR = "plugins.dir"
        const val PLUGIN_LOG_LEVEL = "plugins.log_level"
        const val PLUGIN_LOG_DEFAULT = "info"
        const val TDLIB_PATH = "tdlib.path"
        const val TDLIB_LOG_VERBOSITY = "tdlib.log_verbosity"
        const val ACCOUNT_BOT = "bot"
        const val ACCOUNT_USER = "user"

        val LOG_LEVELS = setOf("error", "warn", "info", "debug", "trace")

        const val LEGACY_KEY_API_ID = "fuyubot_api_id"
        const val LEGACY_KEY_API_HASH = "fuyubot_api_hash"
        const val LEGACY_KEY_BOT_TOKEN = "fuyubot_bot_token"
    }
}
