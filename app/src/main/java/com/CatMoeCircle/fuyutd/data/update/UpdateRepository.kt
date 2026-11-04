package com.CatMoeCircle.fuyutd.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 直接读取 GitHub Release 的 `releases/latest` 接口。
 *
 * 解析刻意做得宽容：字段缺失只当作空值，避免仓库改格式后整页报错。
 */
class UpdateRepository {

    suspend fun fetchLatest(channel: UpdateChannel): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) { runCatching { request(channel.apiUrl) } }

    private fun request(apiUrl: String): ReleaseInfo {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "fuyuTD")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ReleaseInfo {
        val json = JSONObject(body)
        val tagName = json.optString("tag_name")
        val assetsJson = json.optJSONArray("assets")
        val assets = buildList {
            for (index in 0 until (assetsJson?.length() ?: 0)) {
                val asset = assetsJson!!.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    add(ReleaseAsset(name = name, downloadUrl = url))
                }
            }
        }
        return ReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            publishedAt = json.optString("published_at"),
            changelog = json.optString("body"),
            htmlUrl = json.optString("html_url"),
            assets = assets,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
