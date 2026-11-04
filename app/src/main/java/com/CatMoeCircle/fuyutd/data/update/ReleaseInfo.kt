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

    /** 按名称关键字挑第一个匹配的资产，找不到时回落到 Release 页面地址。 */
    fun downloadUrlFor(vararg keywords: String): String {
        val matched = assets.firstOrNull { asset ->
            keywords.any { asset.name.contains(it, ignoreCase = true) }
        }
        return matched?.downloadUrl ?: htmlUrl
    }
}
