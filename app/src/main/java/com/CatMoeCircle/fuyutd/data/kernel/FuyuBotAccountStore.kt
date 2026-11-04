package com.CatMoeCircle.fuyutd.data.kernel

import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.data.AppPrefs
import java.io.File

/**
 * `getMe` 结果的本地缓存。
 *
 * 账户名与头像路径只在 `authorizationStateReady` 后拿得到，而内核启动 + 登录要几秒到几十秒；
 * 缓存一份，冷启动时首页就能立刻显示上次的昵称与头像，不必干等人形占位符。
 *
 * 只缓存「展示用的那几个字段」：真正的账户数据仍在 TDLib 的数据库里，
 * 这里纯粹是给界面垫底的快照，`getMe` 回来后会整体覆盖。
 *
 * 头像路径落地时校验文件还在——TDLib 清理缓存 / 用户换过头像后旧路径会失效，
 * 这时退回 null，由界面显示占位图标，而不是拿一个坏路径去解码。
 */
object FuyuBotAccountStore {

    /** 读缓存；没有缓存、或缓存里的头像文件已不存在时返回 null / 无头像的账户。 */
    fun load(): FuyuBotAccount? = runCatching { read() }.getOrNull()

    private fun read(): FuyuBotAccount? {
        val prefs = AppPrefs.prefs()
        val id = prefs.getLong(KEY_ID, 0L)
        val firstName = prefs.getString(KEY_FIRST_NAME, "").orEmpty()
        val lastName = prefs.getString(KEY_LAST_NAME, "").orEmpty()
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        if (id == 0L && firstName.isBlank() && lastName.isBlank() && username.isBlank()) {
            return null
        }
        val avatarPath = prefs.getString(KEY_AVATAR_PATH, "").orEmpty()
            .takeIf { it.isNotBlank() && File(it).isFile }
        return FuyuBotAccount(
            id = id,
            firstName = firstName,
            lastName = lastName,
            username = username,
            avatarPath = avatarPath,
        )
    }

    /** 覆盖缓存。头像路径为 null 时就清空，避免留着已经失效的旧路径。 */
    fun save(account: FuyuBotAccount) {
        runCatching {
            AppPrefs.prefs().edit {
                putLong(KEY_ID, account.id)
                putString(KEY_FIRST_NAME, account.firstName)
                putString(KEY_LAST_NAME, account.lastName)
                putString(KEY_USERNAME, account.username)
                putString(KEY_AVATAR_PATH, account.avatarPath.orEmpty())
            }
        }
    }

    private const val KEY_ID = "fuyubot_account_id"
    private const val KEY_FIRST_NAME = "fuyubot_account_first_name"
    private const val KEY_LAST_NAME = "fuyubot_account_last_name"
    private const val KEY_USERNAME = "fuyubot_account_username"
    private const val KEY_AVATAR_PATH = "fuyubot_account_avatar_path"
}
