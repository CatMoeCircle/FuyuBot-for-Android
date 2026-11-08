package com.CatMoeCircle.fuyutd.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 读取 GitHub Release 列表，并从中筛选正式 App 版本。
 *
 * 仓库同时包含 App、TDLib 等 Release，不能直接使用 `releases/latest`。这里只接受
 * 非草稿、非预发布、带 APK 资产，且 tag 不是 TDLib 版本的最新语义版本。
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
        val releases = JSONArray(body)
        val candidates = buildList {
            for (index in 0 until releases.length()) {
                val json = releases.optJSONObject(index) ?: continue
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) continue

                val release = parseRelease(json)
                if (release.tagName.startsWith("TDLib-", ignoreCase = true)) continue
                if (release.assets.none { it.name.endsWith(".apk", ignoreCase = true) }) continue
                add(release)
            }
        }
        return candidates.maxWithOrNull { left, right ->
            compareVersion(left.versionName, right.versionName)
        } ?: error("Release 列表中没有包含 APK 的正式 App 版本")
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
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
