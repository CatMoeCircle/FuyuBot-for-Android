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
        // 仓库同时发布 App、TDLib 等不同类型；必须从列表中筛出带 APK 的正式 App Release。
        apiUrl = "https://api.github.com/repos/CatMoeCircle/Fuyu-Bot/releases?per_page=30",
    ),
}

/**
 * 当前 APK 内置内核的版本标记。
 *
 * App 和内核按同一个 Release tag 绑定发布。正式构建时由上游发布流程把这里与
 * App 的 `versionName` 一起改写成同一个版本；App 内不提供独立的内核更新入口。
 */
const val CURRENT_KERNEL_VERSION = "0.1.2"
