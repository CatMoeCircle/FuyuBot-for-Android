package com.CatMoeCircle.fuyutd.data.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 读取插件商城索引 `index.json`。
 *
 * 解析宽容：字段缺失只当空值，避免上游改格式后整页报错。
 */
class PluginRepository {

    suspend fun fetchIndex(url: String): Result<List<PluginInfo>> =
        withContext(Dispatchers.IO) { runCatching { parsePluginIndex(request(url)) } }

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
