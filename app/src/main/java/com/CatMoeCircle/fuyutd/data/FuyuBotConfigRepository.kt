package com.CatMoeCircle.fuyutd.data

import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import java.io.File

/**
 * Fuyu-Bot 的账户与运行配置，全部落在 [AppPrefs] 的 SharedPreferences 里。
 *
 * 首次启动强制要求 `api_id` / `api_hash`（对应「授权状态」），
 * `bot_token` 可选——有值就走 bot 登录，无值走真人登录（手机号/验证码/两步）。
 */
class FuyuBotConfigRepository {

    private val prefs get() = AppPrefs.prefs()

    var apiId: Int
        get() = prefs.getInt(KEY_API_ID, 0)
        set(value) = prefs.edit { putInt(KEY_API_ID, value) }

    var apiHash: String
        get() = prefs.getString(KEY_API_HASH, "") ?: ""
        set(value) = prefs.edit { putString(KEY_API_HASH, value) }

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BOT_TOKEN, value) }

    /** 首次授权（api_id / api_hash）是否已完成。 */
    val isAuthorized: Boolean
        get() = apiId > 0 && apiHash.isNotBlank()

    /**
     * 组装 options JSON 交给 `nativeStart`；缺少必要字段返回 null。
     *
     * [paths] 用 [KernelManager.paths] 的返回值：`root` / `data_dir` 位于应用专属
     * 外置存储，`plugin_dir` 指向启动前同步的私有只读插件副本，`lib_dir` 指向
     * 私有内核安装目录。这样用户可以直接调整数据与插件，内核升级也不会清掉它们。
     */
    fun buildOptionsJson(paths: KernelManager.KernelPaths): String? {
        if (!isAuthorized) return null
        return buildString {
            append("{")
            append("\"root\":").append(quote(paths.root)).append(",")
            append("\"plugin_dir\":").append(quote(relativeTo(paths.root, paths.pluginDir))).append(",")
            append("\"data_dir\":").append(quote(relativeTo(paths.root, paths.dataDir))).append(",")
            append("\"lib_dir\":").append(quote(paths.libDir)).append(",")
            append("\"api_id\":").append(apiId).append(",")
            append("\"api_hash\":").append(quote(apiHash))
            if (botToken.isNotBlank()) {
                append(",")
                append("\"bot_token\":").append(quote(botToken))
            }
            append("}")
        }
    }

    /** 在 `root` 之下的路径给相对值（内核按 root 解析），否则退回绝对路径。 */
    private fun relativeTo(root: String, path: String): String {
        val prefix = File(root).absoluteFile.path + File.separator
        val target = File(path).absoluteFile.path
        return if (target.startsWith(prefix)) target.removePrefix(prefix) else target
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val KEY_API_ID = "fuyubot_api_id"
        const val KEY_API_HASH = "fuyubot_api_hash"
        const val KEY_BOT_TOKEN = "fuyubot_bot_token"
    }
}
