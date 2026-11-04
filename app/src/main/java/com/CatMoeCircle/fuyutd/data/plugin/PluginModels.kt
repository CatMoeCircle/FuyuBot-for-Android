package com.CatMoeCircle.fuyutd.data.plugin

import org.json.JSONObject

/**
 * 插件商城索引里，一个插件的某个发布版本的单个平台资产。
 */
data class PluginAsset(
    val url: String,
    val sha256: String,
    val size: Long,
)

/**
 * 插件商城索引里，某个插件的某个发布版本。
 *
 * [platforms] 取自该版本 `assets` 的 key（如 `android-aarch64` / `windows-x86_64`），
 * 平台名与真实键名保持一致，方便与「当前设备平台」做精确匹配。
 */
data class PluginVersion(
    val version: String,
    val abi: Int,
    val platforms: List<String>,
    val assets: Map<String, PluginAsset>,
)

/**
 * 插件商城索引里的一项。`latest` 字段指向「最新版本」，多版本时按它取默认下载资产。
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val repo: String,
    val latest: String,
    val license: String,
    val homepage: String,
    val tags: List<String>,
    val versions: List<PluginVersion>,
) {
    /** 按 `latest` 找最新版本；找不到时回落到第一个版本。 */
    val latestVersion: PluginVersion?
        get() = versions.firstOrNull { it.version == latest } ?: versions.firstOrNull()

    /** 该插件支持的全部平台（合并所有版本，去重、保持出现顺序）。 */
    val supportedPlatforms: List<String>
        get() = versions.flatMap { it.platforms }.distinct()
}

/**
 * 解析插件商城索引的根 JSON。解析刻意宽容：字段缺失当空值，避免上游改格式后整页崩。
 *
 * @return 插件列表；顶层结构异常时返回空列表。
 */
fun parsePluginIndex(json: String): List<PluginInfo> = runCatching {
    val root = JSONObject(json)
    val plugins = root.optJSONArray("plugins") ?: return@runCatching emptyList()
    buildList {
        for (index in 0 until plugins.length()) {
            val item = plugins.optJSONObject(index) ?: continue
            val versions = buildList {
                val versionArray = item.optJSONArray("versions") ?: return@buildList
                for (versionIndex in 0 until versionArray.length()) {
                    val version = versionArray.optJSONObject(versionIndex) ?: continue
                    val assetsJson = version.optJSONObject("assets")
                    val assets = buildMap {
                        if (assetsJson != null) {
                            val keys = assetsJson.keys()
                            while (keys.hasNext()) {
                                val platform = keys.next()
                                val asset = assetsJson.optJSONObject(platform) ?: continue
                                val url = asset.optString("url")
                                if (url.isEmpty()) continue
                                put(
                                    platform,
                                    PluginAsset(
                                        url = url,
                                        sha256 = asset.optString("sha256"),
                                        size = asset.optLong("size"),
                                    ),
                                )
                            }
                        }
                    }
                    if (assets.isNotEmpty()) {
                        add(
                            PluginVersion(
                                version = version.optString("version"),
                                abi = version.optInt("abi"),
                                platforms = assets.keys.toList(),
                                assets = assets,
                            )
                        )
                    }
                }
            }
            if (versions.isEmpty()) continue
            val tags = buildList {
                val tagArray = item.optJSONArray("tags")
                if (tagArray != null) {
                    for (tagIndex in 0 until tagArray.length()) {
                        tagArray.optString(tagIndex).takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
            }
            add(
                PluginInfo(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    summary = item.optString("summary"),
                    description = item.optString("description"),
                    repo = item.optString("repo"),
                    latest = item.optString("latest"),
                    license = item.optString("license"),
                    homepage = item.optString("homepage"),
                    tags = tags,
                    versions = versions,
                )
            )
        }
    }
}.getOrDefault(emptyList())
