package com.CatMoeCircle.fuyutd.data.plugin

import android.os.Build

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
 * 判断某个插件是否支持当前设备：最新版本（优先）或任一版本的平台列表里，
 * 只要包含 [devicePluginPlatforms] 的任意一项即可。
 */
fun PluginInfo.supportsDevice(): Boolean =
    supportedPlatforms.any { it in devicePluginPlatforms }

/**
 * 该插件在当前设备上的首选下载资产：优先取「最新版本」里的设备平台资产；
 * 最新版本没有时，从各版本里挑第一个匹配设备平台的资产。
 *
 * @return 平台键 与 对应资产；都不支持时返回 null。
 */
fun PluginInfo.deviceAsset(): Pair<String, PluginAsset>? {
    val latest = latestVersion
    val candidates = listOfNotNull(latest) + versions.filter { it !== latest }
    for (version in candidates) {
        val platform = version.platforms.firstOrNull { it in devicePluginPlatforms } ?: continue
        val asset = version.assets[platform] ?: continue
        return platform to asset
    }
    // 极端情况：设备平台为空但插件只有安卓平台时无法命中，保持 null。
    return null
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
    else -> platform
}
