package com.CatMoeCircle.fuyutd.data.plugin

import android.os.Build

/**
 * 本 App 内置内核支持的插件 ABI。
 *
 * 插件与内核之间的硬协议：插件 ABI 高于宿主就不能加载（内核报告里会是一条失败的加载）。
 * 内核随 App 一起发版，升级内核时同步改这里；调用方也可以显式传别的值覆盖。
 *
 * 0.1.4 起内核宿主为 ABI 6（v5 → v6 迁移见上游文档 `plugin/abi-v6`）。
 */
const val CURRENT_PLUGIN_ABI = 6

/** 纯脚本插件的资产平台键。 */
const val ANY_PLUGIN_PLATFORM = "any"

/**
 * 设备的 ABIs（当前设备的可执行代码 ABI 列表，`Build.SUPPORTED_ABIS`）。
 *
 * Android 设备报告的是 `arm64-v8a` 这类 ABI 名，而插件索引用的是
 * `android-aarch64` / `android-x86_64` 这类平台键，两者不是一套命名，
 * 所以这里做一次映射。
 */
val deviceSupportedAbis: List<String>
    get() = Build.SUPPORTED_ABIS.toList()

/**
 * 当前设备支持的插件平台键（与索引 `assets` 的 key 对应）。
 *
 * 规则：只要设备的某个 ABI 能匹配到插件平台，就算支持。
 * 例如 arm64 设备 → `android-aarch64`；x86_64 设备 → `android-x86_64`。
 */
val devicePluginPlatforms: List<String>
    get() = deviceSupportedAbis.flatMap { abi -> abiToPluginPlatform(abi) }.distinct()

/**
 * 把 Android ABI 映射到插件索引的平台键；映射不到返回空列表。
 *
 * 只做精确映射，不臆测：索引目前只出现 `android-aarch64` 与 `android-x86_64` 两种安卓平台，
 * 其余（如 `windows-x86_64`）不是本设备的平台，直接过滤掉。
 */
private fun abiToPluginPlatform(abi: String): List<String> = when (abi) {
    "arm64-v8a" -> listOf("android-aarch64")
    "x86_64" -> listOf("android-x86_64")
    else -> emptyList()
}

/**
 * 该版本在本设备上的首选下载资产：先按设备平台找，再回落到纯脚本的 `any`。
 *
 * @return 平台键 与 对应资产；都不支持时返回 null。
 */
fun PluginVersion.assetForDevice(): Pair<String, PluginAsset>? {
    for (platform in devicePluginPlatforms) {
        assets[platform]?.let { return platform to it }
    }
    assets[ANY_PLUGIN_PLATFORM]?.let { return ANY_PLUGIN_PLATFORM to it }
    return null
}

/**
 * 版本能不能装在本机：未下架、ABI 不高于宿主、且有本平台（或 `any`）的包。
 *
 * 上游商店的选版规则就是这三条；`abi = 0` 表示清单没写 ABI，按兼容处理。
 */
fun PluginVersion.isUsable(hostAbi: Int = CURRENT_PLUGIN_ABI): Boolean =
    !yanked && (abi <= 0 || abi <= hostAbi) && assetForDevice() != null

/**
 * 判断某个插件是否支持当前设备。
 *
 * 轻索引没有版本表，先看 `platform_min_abi` 摘要：平台的最低 ABI 不高于宿主即可装。
 * 摘要缺失时退回版本表判断；两者都没有时宁多勿漏（返回 true），交给详情 / 安装时再判定。
 */
fun PluginInfo.supportsDevice(hostAbi: Int = CURRENT_PLUGIN_ABI): Boolean {
    val platforms = devicePluginPlatforms + ANY_PLUGIN_PLATFORM
    if (platformMinAbi.isNotEmpty()) {
        return platforms.any { platform ->
            platformMinAbi[platform]?.let { minAbi -> minAbi <= 0 || minAbi <= hostAbi } == true
        }
    }
    if (versions.isNotEmpty()) return versions.any { it.isUsable(hostAbi) }
    return true
}

/**
 * 按商店规则挑一个能装的版本：
 * 1. `latest` 未下架、ABI ≤ 宿主、有本平台包 → 用它；
 * 2. 否则从版本表末尾往前找第一个同样条件的版本；
 * 3. 都不满足返回 null。
 *
 * @return 选中的版本；版本表还没拉（轻索引）或没有可用版本时返回 null。
 */
fun PluginInfo.selectVersion(hostAbi: Int = CURRENT_PLUGIN_ABI): PluginVersion? {
    if (versions.isEmpty()) return null
    versions.firstOrNull { it.version == latest }
        ?.takeIf { it.isUsable(hostAbi) }
        ?.let { return it }
    return versions.lastOrNull { it.isUsable(hostAbi) }
}

/**
 * 设备平台在卡片上的展示名；无法识别时原样返回键名。
 */
fun platformDisplayName(platform: String): String = when (platform) {
    "android-aarch64" -> "Android ARM64"
    "android-x86_64" -> "Android x86_64"
    "windows-x86_64" -> "Windows x64"
    "windows-aarch64" -> "Windows ARM64"
    "linux-x86_64" -> "Linux x64"
    "linux-aarch64" -> "Linux ARM64"
    "macos-aarch64" -> "macOS ARM64"
    ANY_PLUGIN_PLATFORM -> "Any platform"
    else -> platform
}
