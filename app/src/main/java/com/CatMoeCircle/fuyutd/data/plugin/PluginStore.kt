package com.CatMoeCircle.fuyutd.data.plugin

import android.content.Context
import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.data.AppPrefs
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * 单个已安装插件的注册信息，持久化在 SharedPreferences（`plugin_*` 前缀）。
 *
 * [enabled] 不落盘，由 [PluginStore.installed] 读磁盘实时推导：
 * 插件文件为 `<id>.so` 时启用，为 `<id>.so.disable` 时停用。
 *
 * [pendingRestart] 表示「插件文件已变更但内核尚未重启加载」；
 * [pendingUninstall] 表示「内核运行中发起了卸载，待内核停止后才真正删除」。
 */
data class InstalledPlugin(
    val id: String,
    val version: String,
    val fileName: String,
    val pendingRestart: Boolean = false,
    val pendingUninstall: Boolean = false,
    /** 当前是否启用（由磁盘上的文件名推导，不落盘）。 */
    val enabled: Boolean = true,
)

/**
 * 已安装插件的本地注册表。
 *
 * 内核实际加载的是 `filesDir/fuyubot-kernel/plugins/` 下的 `.so` 文件（见 [KernelManager]）。
 * 内核只报插件名（点分服务名），并不报「这是商城里的哪个插件」，所以这里用一份注册表
 * 把「商城插件 id / 版本 / 落盘文件名」对应起来，供插件页展示安装状态与卸载。
 *
 * 启停（enable/disable）用「改名」实现：`<id>.so` ↔ `<id>.so.disable`，
 * 停用后内核加载时按 `.so` 后缀扫描自然读不到它，与内核 `pluginCount` 的口径一致。
 */
object PluginStore {

    private const val KEY_INSTALLED = "installed_plugins"

    /** 停用文件的后缀，加在 `.so` 之后。 */
    const val DISABLE_SUFFIX = ".disable"

    /** 所有已注册的插件；[enabled] 由磁盘文件名实时推导。 */
    fun installed(context: Context): List<InstalledPlugin> {
        val dir = KernelManager.pluginDir(context)
        return runCatching {
            val raw = AppPrefs.prefs().getString(KEY_INSTALLED, null)
                ?: return@runCatching emptyList()
            val root = JSONObject(raw)
            val array = root.optJSONArray("plugins") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val fileName = item.optString("file")
                    if (id.isEmpty() || fileName.isEmpty()) continue
                    add(
                        InstalledPlugin(
                            id = id,
                            version = item.optString("version"),
                            fileName = fileName,
                            pendingRestart = item.optBoolean("pendingRestart"),
                            pendingUninstall = item.optBoolean("pendingUninstall"),
                            enabled = File(dir, fileName).isFile,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 按商城插件 id 查注册信息；未安装返回 null。 */
    fun find(context: Context, id: String): InstalledPlugin? =
        installed(context).firstOrNull { it.id == id }

    /**
     * 本地插件列表的完整口径：注册表里的商城插件 + 磁盘上的系统 / 内置插件。
     *
     * 系统插件随内核资产一起安装到 `plugins/` 下，没有商城 id / 版本，也不会进注册表
     * （见 [reconcile] 的「保留原状」分支）。这里用文件名（去掉 `.so` 后缀）作为 id、
     * 版本留空，界面在索引里匹配不到时回退显示 id。
     *
     * [enabled] 由文件名实时推导：`<name>.so` 启用，`<name>.so.disable` 停用。
     */
    fun installedIncludingSystem(context: Context): List<InstalledPlugin> {
        val registered = installed(context)
        val registeredFiles = registered.flatMap {
            listOf(it.fileName, it.fileName + DISABLE_SUFFIX)
        }.toSet()
        val dir = KernelManager.pluginDir(context)
        val system = dir.listFiles()
            ?.filter { it.isFile &&
                    (it.name.endsWith(".so", ignoreCase = true) ||
                            it.name.endsWith(".so" + DISABLE_SUFFIX, ignoreCase = true)) }
            ?.mapNotNull { file ->
                val disabled = file.name.endsWith(DISABLE_SUFFIX, ignoreCase = true)
                val soName = if (disabled) {
                    file.name.substring(0, file.name.length - DISABLE_SUFFIX.length)
                } else {
                    file.name
                }
                // 已在注册表里的文件（商城装的）不重复列出。
                if (soName in registeredFiles) return@mapNotNull null
                InstalledPlugin(
                    id = soName.removeSuffix(".so"),
                    version = "",
                    fileName = soName,
                    pendingRestart = false,
                    pendingUninstall = false,
                    enabled = !disabled,
                )
            }
            ?.sortedBy { it.fileName }
            ?: emptyList()
        return registered + system
    }

    /** 写入 / 覆盖一条注册信息。 */
    fun put(context: Context, plugin: InstalledPlugin) {
        val list = installed(context).filterNot { it.id == plugin.id } + plugin
        persist(context, list)
    }

    /** 删除一条注册信息（不删文件，文件由调用方先删）。 */
    fun remove(context: Context, id: String) {
        persist(context, installed(context).filterNot { it.id == id })
    }

    /** 把某个插件标记为「待重启生效」。 */
    fun markPendingRestart(context: Context, id: String) {
        mutate(context, id) { it.copy(pendingRestart = true) }
    }

    /** 把某个插件标记为「待卸载」（内核停止后删除文件并移除注册）。 */
    fun markPendingUninstall(context: Context, id: String) {
        mutate(context, id) { it.copy(pendingUninstall = true, pendingRestart = true) }
    }

    /** 撤销「待卸载」标记。 */
    fun clearPendingUninstall(context: Context, id: String) {
        mutate(context, id) { it.copy(pendingUninstall = false, pendingRestart = false) }
    }

    /** 内核启动后调用：把所有「待重启」标记清掉（文件已按当前状态加载完毕）。 */
    fun clearAllPendingRestart(context: Context) {
        persist(context, installed(context).map { it.copy(pendingRestart = false) })
    }

    /**
     * 内核停止后调用：真正删除所有「待卸载」插件的落盘文件与注册条目。
     */
    fun finalizePendingUninstall(context: Context) {
        val remaining = installed(context).filter { plugin ->
            if (!plugin.pendingUninstall) return@filter true
            deletePluginFiles(context, plugin)
            false
        }
        persist(context, remaining)
    }

    private fun mutate(context: Context, id: String, transform: (InstalledPlugin) -> InstalledPlugin) {
        persist(context, installed(context).map { if (it.id == id) transform(it) else it })
    }

    private fun persist(context: Context, list: List<InstalledPlugin>) {
        runCatching {
            val root = JSONObject()
            val array = org.json.JSONArray()
            for (plugin in list) {
                val item = JSONObject()
                item.put("id", plugin.id)
                item.put("version", plugin.version)
                item.put("file", plugin.fileName)
                item.put("pendingRestart", plugin.pendingRestart)
                item.put("pendingUninstall", plugin.pendingUninstall)
                array.put(item)
            }
            root.put("plugins", array)
            AppPrefs.prefs().edit { putString(KEY_INSTALLED, root.toString()) }
        }
    }

    /**
     * 把本地 `plugins/` 目录下的实际文件与注册表对齐：
     * - 注册表里 fileName 对应的 `.so` / `.so.disable` 都已不在磁盘 → 移除该条注册；
     * - 磁盘上有插件但注册表里没有 → 保留原状（可能是内置插件，不是商城装的）。
     */
    fun reconcile(context: Context): List<InstalledPlugin> {
        val pluginDir = KernelManager.pluginDir(context)
        // 收集 `.so`（启用）与 `.so.disable`（停用）两种落盘文件。
        val onDisk = pluginDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".so", ignoreCase = true) ||
                    it.name.endsWith(".so" + DISABLE_SUFFIX, ignoreCase = true)) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        val list = installed(context).filter { plugin ->
            plugin.fileName in onDisk || (plugin.fileName + DISABLE_SUFFIX) in onDisk
        }
        if (list.size != installed(context).size) {
            persist(context, list)
        }
        return list
    }

    /** 删除某个插件落盘的 `.so` 与 `.so.disable` 两个可能存在的文件。 */
    fun deletePluginFiles(context: Context, plugin: InstalledPlugin) {
        if (plugin.fileName.isBlank()) return
        val dir = KernelManager.pluginDir(context)
        File(dir, plugin.fileName).delete()
        File(dir, plugin.fileName + DISABLE_SUFFIX).delete()
    }
}

/**
 * 从已下载解压的插件包里读出 `plugin.json` 里的元数据（id / version / entry）。
 *
 * zip 结构（以 mongo 为例）：`bin/libmongo_plugin.so` + `plugin.json`，
 * `plugin.json` 里 `entry` 指向 `.so` 在包内的相对路径。
 */
data class PluginManifest(
    val id: String,
    val version: String,
    val entry: String,
)

/**
 * 下载、校验、安装、卸载、启停插件的执行器。
 *
 * 安装流程：下载 zip → 校验 sha256 → 解压 `plugin.json` 与 `entry` 指向的 `.so` →
 * 写入 `plugins/` 根目录（文件名用 manifest id + `.so`）→ 写入注册表。
 * 内核在运行时做任何变更都标记「待重启」；未运行时变更即时生效。
 */
object PluginInstaller {

    private const val STAGING_DIR = "plugin-staging"
    private const val ZIP_CACHE = "plugin-download.zip"

    /** 目标 `.so` 文件名：`<manifest id>.so`，避免不同插件互相覆盖。 */
    fun targetFileName(manifest: PluginManifest): String = manifest.id + ".so"

    /**
     * 下载并安装一个插件资产。
     */
    suspend fun install(
        context: Context,
        id: String,
        asset: PluginAsset,
        kernelRunning: Boolean,
        onProgress: (Int) -> Unit,
    ): Result<InstalledPlugin> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, STAGING_DIR)
            val zipCache = File(context.cacheDir, ZIP_CACHE)
            try {
                onProgress(0)
                download(asset.url, zipCache, onProgress)

                asset.sha256.takeIf { it.isNotBlank() }?.let { expected ->
                    val actual = sha256(zipCache)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        error("插件校验失败：sha256 不匹配")
                    }
                }
                onProgress(85)

                staging.deleteRecursively()
                staging.mkdirs()

                val manifest = readManifest(zipCache) ?: error("插件包缺少 plugin.json")
                val entry = manifest.entry.trimStart('/', '\\').replace('\\', '/')
                if (!entry.endsWith(".so", ignoreCase = true)) {
                    error("插件 entry 不是 .so：$entry")
                }

                val safeEntry = requireSafePath(entry)
                ZipFile(zipCache).use { zip ->
                    val zipEntry = zip.getEntry(safeEntry) ?: error("插件包缺少 $safeEntry")
                    if (zipEntry.isDirectory) error("插件 entry 是目录：$safeEntry")
                    zip.getInputStream(zipEntry).use { input ->
                        val target = File(KernelManager.pluginDir(context), targetFileName(manifest))
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                onProgress(100)

                val installed = InstalledPlugin(
                    id = id,
                    version = manifest.version,
                    fileName = targetFileName(manifest),
                    pendingRestart = kernelRunning,
                    pendingUninstall = false,
                    enabled = true,
                )
                PluginStore.put(context, installed)
                installed
            } finally {
                zipCache.delete()
                staging.deleteRecursively()
            }
        }
    }

    /**
     * 卸载插件：
     * - 内核未运行 → 直接删除 `.so` / `.so.disable` 并移除注册条目；
     * - 内核运行中 → 只标记「待卸载」，文件删除与注册移除推迟到内核停止后。
     */
    fun uninstall(context: Context, installed: InstalledPlugin, kernelRunning: Boolean): Result<Unit> =
        runCatching {
            if (kernelRunning) {
                PluginStore.markPendingUninstall(context, installed.id)
            } else {
                PluginStore.deletePluginFiles(context, installed)
                PluginStore.remove(context, installed.id)
            }
        }

    /** 撤销「待卸载」标记（内核运行中误删后反悔）。 */
    fun undoUninstall(context: Context, installed: InstalledPlugin): Result<Unit> =
        runCatching { PluginStore.clearPendingUninstall(context, installed.id) }

    /**
     * 启用 / 停用插件：把落盘文件在 `<id>.so` 与 `<id>.so.disable` 之间改名。
     * 内核运行中变更会标记「待重启」，因为改名不影响已经加载进内存的实例。
     */
    fun setEnabled(
        context: Context,
        installed: InstalledPlugin,
        enabled: Boolean,
        kernelRunning: Boolean,
    ): Result<Unit> = runCatching {
        val dir = KernelManager.pluginDir(context)
        val so = File(dir, installed.fileName)
        val disabled = File(dir, installed.fileName + PluginStore.DISABLE_SUFFIX)
        when {
            enabled && !so.isFile && disabled.isFile -> disabled.renameTo(so)
            !enabled && so.isFile -> so.renameTo(disabled)
            else -> Unit
        }
        if (kernelRunning) {
            PluginStore.markPendingRestart(context, installed.id)
        }
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "fuyuTD")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        if (total > 0) {
                            onProgress((written * 80 / total).toInt().coerceIn(0, 80))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 从 zip 里读 plugin.json 并解析出 manifest。 */
    private fun readManifest(zipFile: File): PluginManifest? = runCatching {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("plugin.json") ?: return@use null
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val id = json.optString("id")
            val entryPath = json.optString("entry")
            if (id.isEmpty() || entryPath.isEmpty()) null
            else PluginManifest(
                id = id,
                version = json.optString("version"),
                entry = entryPath,
            )
        }
    }.getOrNull()

    /** 归一化包内路径并拒绝目录穿越。 */
    private fun requireSafePath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.isNotEmpty() && normalized.split('/').none { it == ".." }) {
            "插件包中存在非法路径：$path"
        }
        return normalized
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
