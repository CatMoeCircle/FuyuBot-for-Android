package com.CatMoeCircle.fuyutd.data

import androidx.core.content.edit

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

    /** 组装 options JSON 交给 `nativeStart`；缺少必要字段返回 null。 */
    fun buildOptionsJson(root: String): String? {
        if (!isAuthorized) return null
        return buildString {
            append("{")
            append("\"root\":").append(quote(root)).append(",")
            append("\"api_id\":").append(apiId).append(",")
            append("\"api_hash\":").append(quote(apiHash))
            if (botToken.isNotBlank()) {
                append(",")
                append("\"bot_token\":").append(quote(botToken))
            }
            append("}")
        }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val KEY_API_ID = "fuyubot_api_id"
        const val KEY_API_HASH = "fuyubot_api_hash"
        const val KEY_BOT_TOKEN = "fuyubot_bot_token"
    }
}
