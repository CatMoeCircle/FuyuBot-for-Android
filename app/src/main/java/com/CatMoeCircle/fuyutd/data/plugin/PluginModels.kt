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
 *
 * [abi] 是插件编进去的 ABI；`0` 表示清单里没写。`yanked = true` 表示已被撤回，
 * 商店不会再选中这个版本。
 */
data class PluginVersion(
    val version: String,
    val abi: Int,
    val api: String = "",
    val depends: List<String> = emptyList(),
    val yanked: Boolean = false,
    val platforms: List<String>,
    val assets: Map<String, PluginAsset>,
)

/**
 * 插件商城索引里的一项。
 *
 * schema 2 的索引是**轻索引**：列表项只有 id / 名称 / 简介 / latest / 平台摘要，
 * 版本表与描述在 [detail] 指向的分片（`plugins/<id>.json`）里，展开时再拉。
 * schema 1 的全量索引仍可解析——那时 [detail] 为空、[versions] 直接内联。
 *
 * `latest` 字段指向「最新版本」；真正能装哪个版本由 [selectVersion] 结合宿主 ABI 决定。
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
    /** 分片地址，相对索引地址解析；schema 1 全量索引里为空。 */
    val detail: String = "",
    /** 摘要：非下架版本出现过的 ABI（仅展示 / 筛选，客户端可忽略）。 */
    val abis: List<Int> = emptyList(),
    /** 摘要：平台 → 该平台可用版本里最低的 ABI；`0` 表示版本没声明 ABI。 */
    val platformMinAbi: Map<String, Int> = emptyMap(),
    val versions: List<PluginVersion> = emptyList(),
) {
    /** 该插件支持的全部平台（优先取索引摘要，其次合并各版本，去重、保持出现顺序）。 */
    val supportedPlatforms: List<String>
        get() = (platformMinAbi.keys + versions.flatMap { it.platforms }).distinct()
}

/**
 * 解析插件商城索引的根 JSON（schema 2 轻索引，或 schema 1 全量索引）。
 *
 * 解析刻意宽容：字段缺失当空值，避免上游改格式后整页崩。
 * schema 2 的条目没有内联版本表，只要带 `detail` 就保留，版本在展开时再拉。
 *
 * @return 插件列表；顶层结构异常时返回空列表。
 */
fun parsePluginIndex(json: String): List<PluginInfo> = runCatching {
    val root = JSONObject(json)
    val plugins = root.optJSONArray("plugins") ?: return@runCatching emptyList()
    buildList {
        for (index in 0 until plugins.length()) {
            val item = plugins.optJSONObject(index) ?: continue
            val info = parsePluginInfo(item) ?: continue
            // 轻索引靠 detail 分片补版本；两者都没有说明这条既没版本表也没分片，跳过。
            if (info.versions.isEmpty() && info.detail.isEmpty()) continue
            add(info)
        }
    }
}.getOrDefault(emptyList())

/**
 * 解析单个插件的明细分片（schema 2：`{"schema":2,"plugin":{…}}`）。
 *
 * @return 插件信息；结构不对时返回 null。
 */
fun parsePluginDetail(json: String): PluginInfo? = runCatching {
    val root = JSONObject(json)
    parsePluginInfo(root.optJSONObject("plugin") ?: root)
}.getOrNull()

/** 解析索引项 / 分片里的 `plugin` 对象（两者字段同形，只是轻索引缺版本与描述）。 */
private fun parsePluginInfo(item: JSONObject): PluginInfo? {
    val id = item.optString("id")
    if (id.isEmpty()) return null
    return PluginInfo(
        id = id,
        name = item.optString("name"),
        summary = item.optString("summary"),
        description = item.optString("description"),
        repo = item.optString("repo"),
        latest = item.optString("latest"),
        license = item.optString("license"),
        homepage = item.optString("homepage"),
        tags = stringArray(item.optJSONArray("tags")),
        detail = item.optString("detail"),
        abis = intArray(item.optJSONArray("abis")),
        platformMinAbi = platformAbiMap(item.optJSONObject("platform_min_abi")),
        versions = pluginVersions(item.optJSONArray("versions")),
    )
}

/** 解析版本表；按上游约定顺序即「按版本号升序」，选版时从后往前找。 */
private fun pluginVersions(array: org.json.JSONArray?): List<PluginVersion> {
    if (array == null) return emptyList()
    return buildList {
        for (versionIndex in 0 until array.length()) {
            val version = array.optJSONObject(versionIndex) ?: continue
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
            if (assets.isEmpty()) continue
            add(
                PluginVersion(
                    version = version.optString("version"),
                    abi = version.optInt("abi"),
                    api = version.optString("api"),
                    depends = stringArray(version.optJSONArray("depends")),
                    yanked = version.optBoolean("yanked"),
                    platforms = assets.keys.toList(),
                    assets = assets,
                )
            )
        }
    }
}

private fun stringArray(array: org.json.JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
}

private fun intArray(array: org.json.JSONArray?): List<Int> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.optInt(index))
        }
    }
}

private fun platformAbiMap(json: JSONObject?): Map<String, Int> {
    if (json == null) return emptyMap()
    return buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val platform = keys.next()
            put(platform, json.optInt(platform))
        }
    }
}
