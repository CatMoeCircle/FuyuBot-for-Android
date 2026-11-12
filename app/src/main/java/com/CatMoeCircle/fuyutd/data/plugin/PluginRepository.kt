package com.CatMoeCircle.fuyutd.data.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 读取插件商城索引 `index.json` 与每个插件的明细分片。
 *
 * schema 2 是「轻索引 + 明细分片」：主文件只有列表字段，选中插件时再拉
 * `detail` 指向的分片拿版本表与描述。解析宽容：字段缺失只当空值，避免上游改格式后整页报错。
 */
class PluginRepository {

    suspend fun fetchIndex(url: String): Result<List<PluginInfo>> =
        withContext(Dispatchers.IO) { runCatching { parsePluginIndex(request(url)) } }

    /**
     * 拉取单个插件的明细分片。
     *
     * [detail] 来自索引项，按 [indexUrl] 解析相对路径；只接受 HTTPS 且不许带 `..`
     * （与上游商店的索引规则一致）。
     */
    suspend fun fetchDetail(indexUrl: String, detail: String): Result<PluginInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                parsePluginDetail(request(resolveDetailUrl(indexUrl, detail)))
                    ?: error("插件详情格式不正确")
            }
        }

    /**
     * 拉取插件仓库首页（README）的 Markdown 原文。
     *
     * 按 `raw.githubusercontent.com/<repo>/HEAD/README.md` 尝试；失败（404 等）返回 null，
     * 由调用方回落到索引里的 `description` 字段。
     */
    suspend fun fetchReadme(repo: String): String? = withContext(Dispatchers.IO) {
        val path = repo.trim().trimEnd('/')
        if (path.isEmpty()) return@withContext null
        runCatching { request("https://raw.githubusercontent.com/$path/HEAD/README.md") }
            .getOrNull()
    }

    /** 分片地址相对索引地址解析；绝对地址必须是 HTTPS。 */
    private fun resolveDetailUrl(indexUrl: String, detail: String): String {
        require(detail.split('/').none { it == ".." }) { "插件详情地址非法：$detail" }
        val target = if (detail.startsWith("https://")) {
            detail
        } else {
            URI(indexUrl).resolve(detail).toString()
        }
        require(target.startsWith("https://")) { "插件详情地址不是 HTTPS：$target" }
        return target
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "fuyuTD")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
