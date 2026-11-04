package com.CatMoeCircle.fuyutd.data.update

/**
 * 需要检查更新的通道。
 *
 * 内核已改为随 App 内置、不再单独发版，因此只保留软件（App）这一条通道。
 */
enum class UpdateChannel(
    val apiUrl: String,
) {
    App(
        apiUrl = "https://api.github.com/repos/fengyegf/fuyuTD/releases/latest",
    ),
}

/**
 * 当前内置的 Fuyu-Bot 内核版本。升级内核时手动改这里，并同步更新
 * `assets/fuyubot-kernel/` 下的文件即可，App 不需要重新发包校验逻辑。
 */
const val CURRENT_KERNEL_VERSION = "0.1.2"
