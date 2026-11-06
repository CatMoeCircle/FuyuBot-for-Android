package com.CatMoeCircle.fuyutd.data.plugin

import android.content.Context
import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.data.AppPrefs
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** 插件在磁盘上的两种形态。 */
enum class PluginLayout {
    /** 旧形态：插件目录下平铺的一个动态库（`<id>.so`）。 */
    LEGACY,

    /** 目录包：`<id>/plugin.json` + `bin/`，内核推荐的形态。 */
    BUNDLE,
}

/**
 * 单个已安装插件的注册信息，持久化在 SharedPreferences（`plugin_*` 前缀）。
 *
 * [enabled] 不落盘，由 [PluginStore.installed] 读磁盘实时推导：
 * - 旧形态：`<id>.so` 启用、`<id>.so.disable` 停用；
 * - 目录包：`plugins/<id>/` 启用、`plugins/.disabled/<id>/` 停用。
 *
 * [pendingRestart] 表示「插件文件已变更但内核尚未重启加载」；
 * [pendingUninstall] 表示「内核运行中发起了卸载：插件已挪到停用位置、重启后不再加载，
 * 待内核停止后才真正删除」。
 */
data class InstalledPlugin(
    val id: String,
    val version: String,
    /** 旧形态是 `<id>.so` 文件名；目录包是包目录名（= `plugin.json` 里的 id）。 */
    val fileName: String,
    val layout: PluginLayout = PluginLayout.LEGACY,
    val pendingRestart: Boolean = false,
    val pendingUninstall: Boolean = false,
    /**
     * 发起「待卸载」前是否处于启用状态。
     *
     * 只有 [pendingUninstall] 为 true 时有意义：待卸载会把插件先挪到停用位置
     * （让内核下次重启不再加载它），撤销时按这个值决定是否挪回启用位置。
     */
    val enabledBeforeUninstall: Boolean = true,
    /** 当前是否启用（由磁盘上的位置推导，不落盘）。 */
    val enabled: Boolean = true,
    /** 目录包 `plugin.json` 里的展示名；旧形态 / 读不到时为空串（不落盘）。 */
    val name: String = "",
    /** 目录包 `plugin.json` 里的简介；旧形态 / 读不到时为空串（不落盘）。 */
    val summary: String = "",
)

/**
 * 已安装插件的本地注册表。
 *
 * 内核实际加载的是 `filesDir/fuyubot-plugins/` 下的 `.so` 文件（见 [KernelManager]）。
 * 内核只报插件名（点分服务名），并不报「这是商城里的哪个插件」，所以这里用一份注册表
 * 把「商城插件 id / 版本 / 落盘文件名」对应起来，供插件页展示安装状态与卸载。
 *
 * 启停（enable/disable）用「改名」实现：`<id>.so` ↔ `<id>.so.disable`，
 * 停用后内核加载时按 `.so` 后缀扫描自然读不到它，与内核 `pluginCount` 的口径一致。
 */
object PluginStore {

    private const val KEY_INSTALLED = "installed_plugins"

    private val _revision = MutableStateFlow(0L)

    /**
     * 注册表的写入次数。
     *
     * 插件页据此在「别处改了注册表」时重新读取列表：例如内核重启 / 停止后
     * [clearAllPendingRestart] 清掉了「待重启」标记，界面不能还留着旧提示。
     */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** 旧形态停用后缀，加在 `.so` 之后：`<id>.so` → `<id>.so.disable`。 */
    const val DISABLE_SUFFIX = ".disable"

    /** 目录包停用时整包挪进的子目录；内核只扫一层，不会把它当插件。 */
    const val DISABLED_DIR = ".disabled"

    /** 目录包的清单文件名。 */
    const val MANIFEST_FILE = "plugin.json"

    /** 脚本插件的根目录（`plugins/scripts/<目录>/`），不是原生插件包。 */
    private const val SCRIPTS_DIR = "scripts"

    /** 注册表里记目录包形态用的标记。 */
    private const val LAYOUT_BUNDLE = "bundle"

    /** 这个目录是不是一个目录包（根上有 `plugin.json`）。 */
    private fun isBundleDir(dir: File): Boolean = File(dir, MANIFEST_FILE).isFile

    /** 这个插件当前是否在「启用」位置上。 */
    private fun isEnabledOnDisk(root: File, layout: PluginLayout, fileName: String): Boolean =
        when (layout) {
            PluginLayout.LEGACY -> File(root, fileName).isFile
            PluginLayout.BUNDLE -> isBundleDir(File(root, fileName))
        }

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
                    val layout = if (item.optString("layout") == LAYOUT_BUNDLE) {
                        PluginLayout.BUNDLE
                    } else {
                        PluginLayout.LEGACY
                    }
                    add(
                        InstalledPlugin(
                            id = id,
                            version = item.optString("version"),
                            fileName = fileName,
                            layout = layout,
                            pendingRestart = item.optBoolean("pendingRestart"),
                            pendingUninstall = item.optBoolean("pendingUninstall"),
                            enabledBeforeUninstall = item.optBoolean("enabledBeforeUninstall", true),
                            enabled = isEnabledOnDisk(dir, layout, fileName),
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
     * 系统插件随内核资产一起安装到 `plugins/` 下，没有商城 id，也不会进注册表
     * （见 [reconcile] 的「保留原状」分支）。两种形态都认：
     * - 旧形态：`plugins/<id>.so` 与 `<id>.so.disable`，id 取文件名；
     * - 目录包：`plugins/<id>/` 与 `plugins/.disabled/<id>/`，id / 版本 / 名称 / 简介
     *   取自 `plugin.json`（与 `bin/` 同级），因此断网时也能显示完整信息。
     *
     * 界面在索引里匹配不到时回退到这里的 `name` / `id`。
     */
    fun installedIncludingSystem(context: Context): List<InstalledPlugin> {
        val pluginDir = KernelManager.pluginDir(context)
        // 注册表里的商城插件也回填 plugin.json 的展示信息，否则断网时只能显示 id。
        val registered = installed(context).map { plugin ->
            if (plugin.layout != PluginLayout.BUNDLE) return@map plugin
            val info = readBundleInfo(findBundleDir(pluginDir, plugin.fileName))
            plugin.copy(
                name = info.name,
                summary = info.summary,
                version = plugin.version.ifBlank { info.version },
            )
        }
        val registeredNames = registered.map { it.fileName }.toSet()

        val onDisk = buildList {
            pluginDir.listFiles()?.forEach { child ->
                if (child.isFile) {
                    val disabled = child.name.endsWith(DISABLE_SUFFIX, ignoreCase = true)
                    val soName = if (disabled) {
                        child.name.dropLast(DISABLE_SUFFIX.length)
                    } else {
                        child.name
                    }
                    if (!soName.endsWith(".so", ignoreCase = true)) return@forEach
                    // 已在注册表里的文件（商城装的）不重复列出。
                    if (soName in registeredNames) return@forEach
                    add(
                        InstalledPlugin(
                            id = soName.removeSuffix(".so"),
                            version = "",
                            fileName = soName,
                            enabled = !disabled,
                        )
                    )
                } else if (child.isDirectory &&
                    child.name != DISABLED_DIR && child.name != SCRIPTS_DIR &&
                    isBundleDir(child)
                ) {
                    if (child.name in registeredNames) return@forEach
                    val info = readBundleInfo(child)
                    add(
                        InstalledPlugin(
                            id = child.name,
                            version = info.version,
                            fileName = child.name,
                            layout = PluginLayout.BUNDLE,
                            name = info.name,
                            summary = info.summary,
                        )
                    )
                }
            }
            // 停用的目录包：整包在 plugins/.disabled/ 下。
            File(pluginDir, DISABLED_DIR).listFiles()?.forEach { child ->
                if (!child.isDirectory || !isBundleDir(child)) return@forEach
                if (child.name in registeredNames) return@forEach
                val info = readBundleInfo(child)
                add(
                    InstalledPlugin(
                        id = child.name,
                        version = info.version,
                        fileName = child.name,
                        layout = PluginLayout.BUNDLE,
                        enabled = false,
                        name = info.name,
                        summary = info.summary,
                    )
                )
            }
        }
        return registered + onDisk.sortedBy { it.fileName }
    }

    /** 目录包的实际位置：启用位置优先，其次停用位置（两者都没有时返回启用位置）。 */
    private fun findBundleDir(root: File, fileName: String): File {
        val enabled = File(root, fileName)
        if (enabled.isDirectory) return enabled
        val disabled = File(File(root, DISABLED_DIR), fileName)
        return if (disabled.isDirectory) disabled else enabled
    }

    /** 目录包 `plugin.json` 里界面展示需要的元数据；解析宽容，读不到就是空值。 */
    private data class BundleInfo(
        val version: String = "",
        val name: String = "",
        val summary: String = "",
    )

    private fun readBundleInfo(dir: File): BundleInfo = runCatching {
        val json = JSONObject(File(dir, MANIFEST_FILE).readText())
        BundleInfo(
            version = json.optString("version"),
            name = json.optString("name"),
            // 插件清单用 `description` 放介绍；索引里的同义字段叫 summary。
            summary = json.optString("description"),
        )
    }.getOrDefault(BundleInfo())

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

    /**
     * 把某个插件标记为「待卸载」（内核停止后删除文件并移除注册）。
     *
     * [wasEnabled] 记下发起卸载前是否启用：卸载时插件会被挪到停用位置，
     * 撤销时按这个值决定是否挪回启用位置。
     */
    fun markPendingUninstall(context: Context, id: String, wasEnabled: Boolean) {
        mutate(context, id) {
            it.copy(
                pendingUninstall = true,
                pendingRestart = true,
                enabledBeforeUninstall = wasEnabled,
            )
        }
    }

    /** 撤销「待卸载」标记。 */
    fun clearPendingUninstall(context: Context, id: String) {
        mutate(context, id) { it.copy(pendingUninstall = false, pendingRestart = false) }
    }

    /** 内核启动 / 停止后调用：把所有「待重启」标记清掉（磁盘现状会在下次启动时整体加载）。 */
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
                item.put(
                    "layout",
                    if (plugin.layout == PluginLayout.BUNDLE) LAYOUT_BUNDLE else "legacy",
                )
                item.put("pendingRestart", plugin.pendingRestart)
                item.put("pendingUninstall", plugin.pendingUninstall)
                item.put("enabledBeforeUninstall", plugin.enabledBeforeUninstall)
                array.put(item)
            }
            root.put("plugins", array)
            AppPrefs.prefs().edit { putString(KEY_INSTALLED, root.toString()) }
            _revision.update { it + 1 }
        }
    }

    /**
     * 把本地 `plugins/` 目录下的实际内容与注册表对齐：
     * - 注册表里 fileName 对应的落盘内容（旧形态 `.so` / `.so.disable`，目录包的
     *   `plugins/<id>/` 或 `plugins/.disabled/<id>/`）都不在了 → 移除该条注册；
     * - 磁盘上有插件但注册表里没有 → 保留原状（可能是内置插件，不是商城装的）。
     */
    fun reconcile(context: Context): List<InstalledPlugin> {
        val pluginDir = KernelManager.pluginDir(context)
        // 落盘名：旧形态的 `.so` / `.so.disable`，以及目录包的目录名（启用 / 停用两处）。
        val onDisk = buildSet {
            pluginDir.listFiles()?.forEach { child ->
                if (child.isFile) {
                    add(child.name)
                    if (child.name.endsWith(DISABLE_SUFFIX, ignoreCase = true)) {
                        add(child.name.dropLast(DISABLE_SUFFIX.length))
                    }
                } else if (child.isDirectory && isBundleDir(child)) {
                    add(child.name)
                }
            }
            File(pluginDir, DISABLED_DIR).listFiles()?.forEach { child ->
                if (child.isDirectory && isBundleDir(child)) add(child.name)
            }
        }
        val list = installed(context).filter { plugin -> plugin.fileName in onDisk }
        if (list.size != installed(context).size) {
            persist(context, list)
        }
        return list
    }

    /** 删除某个插件落盘的全部内容（两种形态、启用 / 停用两个位置都试一遍）。 */
    fun deletePluginFiles(context: Context, plugin: InstalledPlugin) {
        if (plugin.fileName.isBlank()) return
        val dir = KernelManager.pluginDir(context)
        when (plugin.layout) {
            PluginLayout.LEGACY -> {
                File(dir, plugin.fileName).delete()
                File(dir, plugin.fileName + DISABLE_SUFFIX).delete()
            }
            PluginLayout.BUNDLE -> {
                File(dir, plugin.fileName).deleteRecursively()
                File(File(dir, DISABLED_DIR), plugin.fileName).deleteRecursively()
            }
        }
    }
}

/**
 * 从已下载解压的插件包里读出 `plugin.json` 里的元数据（id / version / kind / entry）。
 *
 * zip 结构（以 mongo 为例）：`bin/libmongo_plugin.so` + `plugin.json`，
 * `plugin.json` 里 `entry` 指向 `.so` 在包内的相对路径。
 */
data class PluginManifest(
    val id: String,
    val version: String,
    val kind: String,
    val entry: String,
) {
    companion object {
        /** 原生插件；`python` / `js` 是脚本插件，由 fuyubot.script 托管。 */
        const val KIND_NATIVE = "native"
    }
}

/**
 * 下载、校验、安装、卸载、启停插件的执行器。
 *
 * 安装流程：下载 zip → 校验 sha256 → 整包解压到 `plugins/<manifest id>/`
 * （`plugin.json` + `bin/` 里的入口与依赖原样保留，清单最后写）→ 写入注册表。
 * 内核在运行时做任何变更都标记「待重启」；未运行时变更即时生效。
 */
object PluginInstaller {

    private const val ZIP_CACHE = "plugin-download.zip"

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

                val manifest = readManifest(zipCache) ?: error("插件包缺少 plugin.json")
                if (manifest.kind.isNotEmpty() && manifest.kind != PluginManifest.KIND_NATIVE) {
                    error("脚本插件（kind=${manifest.kind}）需要 fuyubot.script 托管，本版暂不安装")
                }
                val entry = manifest.entry.trimStart('/', '\\').replace('\\', '/')
                val safeEntry = requireSafePath(entry)
                // 目录包：整包解压到 plugins/<id>/，bin/ 里的入口与依赖原样保留。
                val pluginDir = KernelManager.pluginDir(context)
                // 同一插件的旧形态 / 停用副本一并清掉，避免内核把同一插件加载两次。
                File(pluginDir, manifest.id + ".so").delete()
                File(pluginDir, manifest.id + ".so" + PluginStore.DISABLE_SUFFIX).delete()
                File(File(pluginDir, PluginStore.DISABLED_DIR), manifest.id).deleteRecursively()
                val target = File(pluginDir, manifest.id)
                target.deleteRecursively()
                target.mkdirs()
                val canonicalTarget = target.canonicalFile
                ZipFile(zipCache).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val zipEntry = entries.nextElement()
                        if (zipEntry.isDirectory) continue
                        val name = requireSafePath(zipEntry.name)
                        if (name == PluginStore.MANIFEST_FILE) continue
                        val output = File(target, name)
                        require(
                            output.canonicalPath.startsWith(canonicalTarget.path + File.separator)
                        ) { "插件包中存在非法路径：${zipEntry.name}" }
                        output.parentFile?.mkdirs()
                        zip.getInputStream(zipEntry).use { input ->
                            output.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                    // 清单最后写：中途失败时目录里没有 plugin.json，内核不会把它当插件。
                    val manifestEntry = zip.getEntry(PluginStore.MANIFEST_FILE)
                        ?: error("插件包缺少 plugin.json")
                    zip.getInputStream(manifestEntry).use { input ->
                        File(target, PluginStore.MANIFEST_FILE)
                            .outputStream().use { out -> input.copyTo(out) }
                    }
                }
                require(File(target, safeEntry).isFile) { "插件包缺少 entry：$safeEntry" }
                // 内核要求插件代码只读（targetSdk 34+），和内置插件的处理保持一致。
                markReadOnly(target)
                onProgress(100)

                val installed = InstalledPlugin(
                    id = id,
                    version = manifest.version,
                    fileName = manifest.id,
                    layout = PluginLayout.BUNDLE,
                    pendingRestart = kernelRunning,
                    pendingUninstall = false,
                    enabled = true,
                )
                PluginStore.put(context, installed)
                installed
            } finally {
                zipCache.delete()
            }
        }
    }

    /**
     * 卸载插件：
     * - 内核未运行 → 直接删除插件文件并移除注册条目；
     * - 内核运行中 → 标记「待卸载」，并立刻把插件挪到停用位置：内核下次重启按磁盘扫描时
     *   就不会再加载它；文件删除与注册移除推迟到内核停止后。
     */
    fun uninstall(context: Context, installed: InstalledPlugin, kernelRunning: Boolean): Result<Unit> =
        runCatching {
            if (kernelRunning) {
                // 只有注册表里的插件能记「待卸载」；磁盘上的系统插件没有注册条目，
                // 保持原有行为（不在这里动它）。
                val registered = PluginStore.find(context, installed.id) != null
                PluginStore.markPendingUninstall(context, installed.id, installed.enabled)
                if (registered && installed.enabled) {
                    // 挪到停用位置，重启内核时就不会再被挂载 / 加载。
                    movePluginDir(context, installed, enabled = false)
                }
            } else {
                PluginStore.deletePluginFiles(context, installed)
                PluginStore.remove(context, installed.id)
            }
        }

    /**
     * 撤销「待卸载」：恢复到发起卸载前的启用状态。
     *
     * 文件挪回启用位置；内核运行中再标一次「待重启」，下次重启才会重新加载。
     */
    fun undoUninstall(
        context: Context,
        installed: InstalledPlugin,
        kernelRunning: Boolean,
    ): Result<Unit> = runCatching {
        val restoreEnabled = installed.enabledBeforeUninstall
        PluginStore.clearPendingUninstall(context, installed.id)
        if (restoreEnabled) {
            movePluginDir(context, installed, enabled = true)
            if (kernelRunning) PluginStore.markPendingRestart(context, installed.id)
        }
    }

    /**
     * 启用 / 停用插件（只挪文件，不碰注册表）。
     *
     * - 旧形态：在 `<id>.so` 与 `<id>.so.disable` 之间改名——内核只认 `.so` 后缀，
     *   改完它就扫不到这个插件了；
     * - 目录包：整包在 `plugins/<id>/` 与 `plugins/.disabled/<id>/` 之间挪动——内核只扫
     *   `plugins/` 这一层，挪走即等于停用，而且不会报「跳过」。
     */
    fun setEnabled(
        context: Context,
        installed: InstalledPlugin,
        enabled: Boolean,
        kernelRunning: Boolean,
    ): Result<Unit> = runCatching {
        movePluginDir(context, installed, enabled)
        // 内核运行中变更会标记「待重启」，因为磁盘变化不影响已经加载进内存的实例。
        if (kernelRunning) {
            PluginStore.markPendingRestart(context, installed.id)
        }
    }

    /** 把插件在启用 / 停用两个位置之间挪动；按磁盘现状判断，重复调用是安全的。 */
    private fun movePluginDir(context: Context, installed: InstalledPlugin, enabled: Boolean) {
        val dir = KernelManager.pluginDir(context)
        when (installed.layout) {
            PluginLayout.LEGACY -> {
                val so = File(dir, installed.fileName)
                val disabled = File(dir, installed.fileName + PluginStore.DISABLE_SUFFIX)
                when {
                    enabled && !so.isFile && disabled.isFile -> disabled.renameTo(so)
                    !enabled && so.isFile -> so.renameTo(disabled)
                    else -> Unit
                }
            }
            PluginLayout.BUNDLE -> {
                val on = File(dir, installed.fileName)
                val off = File(File(dir, PluginStore.DISABLED_DIR), installed.fileName)
                when {
                    enabled && !on.isDirectory && off.isDirectory -> {
                        on.parentFile?.mkdirs()
                        off.renameTo(on)
                    }
                    !enabled && on.isDirectory -> {
                        off.parentFile?.mkdirs()
                        on.renameTo(off)
                    }
                    else -> Unit
                }
            }
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
                kind = json.optString("kind"),
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

    /** 插件代码按内核要求设为只读（targetSdk 34+）；失败不影响安装结果。 */
    private fun markReadOnly(dir: File) {
        dir.walkTopDown().filter { it.isFile }.forEach { it.setReadOnly() }
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
