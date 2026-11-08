package com.CatMoeCircle.fuyutd.data.update

/**
 * GitHub Release 的发布资产（一个 tag 下的一个文件）。
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

/**
 * 一次「获取最新 Release」的结果。[versionName] 已去掉 `v` 前缀，便于直接比较与展示。
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val publishedAt: String,
    val changelog: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
) {

    /**
     * 按设备支持的 ABI 顺序挑一个 Android APK。
     *
     * `Build.SUPPORTED_ABIS` 已按优先级排序，因此这里也按同样顺序找第一个匹配项。
     * 找不到对应 APK 时返回 null，界面仍会打开 Release 页面让用户自行选择。
     */
    fun apkAssetForAbis(supportedAbis: List<String>): ReleaseAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in supportedAbis) {
            apkAssets.firstOrNull { asset ->
                asset.name.contains(abi, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }
}
