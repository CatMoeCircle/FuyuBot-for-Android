package com.CatMoeCircle.fuyutd.data.kernel

import android.content.Context
import android.content.res.AssetManager
import com.CatMoeCircle.fuyutd.data.plugin.PluginStore
import com.CatMoeCircle.fuyutd.data.update.CURRENT_KERNEL_VERSION
import com.CatMoeCircle.fuyutd.data.update.compareVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置内核（Fuyu-Bot 运行时）的本地安装管理。
 *
 * 内核以 **JNI 动态库**形式分发（`libfuyubotjni.so` + `libtdjson.so` + 插件目录下的若干 so），
 * 而不是可执行文件——Android 10+ 的 W^X 限制禁止执行应用私有目录下的二进制，
 * 但允许 `System.load()` 加载同一目录下的 `.so`，所以这条路线不需要 root。
 *
 * 内核文件随 App 打包在 `assets/fuyubot-kernel/`，**不再从网络下载，也不再压成 zip**：
 * 打开 App 时由 [ensureCurrent] 判断——未安装（新装）或版本落后于 [CURRENT_KERNEL_VERSION]
 * （App 升级内置了新内核）时，自动从资产复制安装，其余情况跳过。
 *
 * 安装产物放在 `filesDir/fuyubot-kernel/`，**只放内核本体**（升级时整个目录会被清空）：
 * ```
 * libfuyubotjni.so      ← 主运行时
 * libtdjson.so          ← TDLib，必须与前者同 ABI
 * include/ README.txt   ← 随内核分发的文档
 * kernel-version        ← 本次安装的版本号
 * ```
 *
 * 运行期数据、日志与插件放在**安装目录之外**，内核升级不会丢：
 * - `filesDir/fuyubot-data/`     ← TDLib 数据库（登录态）
 * - `filesDir/fuyubot-plugins/`  ← 插件（内置插件也装到这里，已存在则不覆盖）
 * - `filesDir/logs/`             ← 运行日志
 *
 * 旧版把这些都塞在内核安装目录里，[migrateLegacyLayout] 会一次性把它们搬出来。
 */
object KernelManager {

    private const val ASSET_DIR = "fuyubot-kernel"
    private const val KERNEL_DIR = "fuyubot-kernel"
    private const val VERSION_FILE = "kernel-version"
    private const val MAIN_LIBRARY = "libfuyubotjni.so"
    private const val TDLIB_LIBRARY = "libtdjson.so"

    /** 插件目录名：新布局放在 `filesDir/fuyubot-plugins`，也是 options 里的 `plugin_dir`。 */
    const val PLUGIN_DIR_NAME = "fuyubot-plugins"

    /** TDLib 数据目录名：新布局放在 `filesDir/fuyubot-data`，也是 options 里的 `data_dir`。 */
    const val DATA_DIR_NAME = "fuyubot-data"

    /** 日志目录名：`filesDir/logs`。 */
    private const val LOG_DIR = "logs"

    /** 资产里插件所在子目录（`assets/fuyubot-kernel/plugins/`）。 */
    private const val ASSET_PLUGIN_DIR = "plugins"

    /** 旧布局：这些子目录原先都塞在内核安装目录里。 */
    private const val LEGACY_DATA_DIR = "data"
    private const val LEGACY_PLUGIN_DIR = "plugins"

    /**
     * 内核运行期用到的目录。
     *
     * - [root]：交给内核的 `root`，必须是 `filesDir`——TDLib 只能写 app 私有目录，
     *   且 `plugin_dir` / `data_dir` 都是相对它的路径；
     * - [libDir]：内核 `.so` 的安装目录，升级时会被清空，所以数据不能放在里面。
     */
    data class KernelPaths(
        val root: String,
        val pluginDir: String,
        val dataDir: String,
        val libDir: String,
    )

    fun kernelDir(context: Context): File = File(context.filesDir, KERNEL_DIR)

    /** 当前布局的目录集合；见 [KernelPaths]。 */
    fun paths(context: Context): KernelPaths {
        val filesDir = context.filesDir
        return KernelPaths(
            root = filesDir.absolutePath,
            pluginDir = File(filesDir, PLUGIN_DIR_NAME).absolutePath,
            dataDir = File(filesDir, DATA_DIR_NAME).absolutePath,
            libDir = File(filesDir, KERNEL_DIR).absolutePath,
        )
    }

    /** 内核日志目录（`filesDir/logs`），同样在内核安装目录之外。 */
    fun logDir(context: Context): File = File(context.filesDir, LOG_DIR)

    fun isInstalled(context: Context): Boolean =
        File(kernelDir(context), MAIN_LIBRARY).isFile

    /**
     * 已安装内核的版本号；未安装返回 null。
     *
     * 版本号缺失时（例如用户手工把内核文件放进了目录）回落到
     * [CURRENT_KERNEL_VERSION]，也就是随 App 源码维护的默认版本。
     */
    fun installedVersion(context: Context): String? {
        if (!isInstalled(context)) return null
        val recorded = File(kernelDir(context), VERSION_FILE)
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            .orEmpty()
        return recorded.ifEmpty { CURRENT_KERNEL_VERSION }
    }

    /** 内核主库的绝对路径，供 `System.load()` 使用；未安装返回 null。 */
    fun mainLibraryPath(context: Context): String? =
        File(kernelDir(context), MAIN_LIBRARY).takeIf { it.isFile }?.absolutePath

    /** TDLib 的绝对路径，供 `System.load()` 使用；未安装返回 null。 */
    fun tdlibPath(context: Context): String? =
        File(kernelDir(context), TDLIB_LIBRARY).takeIf { it.isFile }?.absolutePath

    /** 插件目录（`filesDir/fuyubot-plugins`），内核未安装时也返回其预期路径。 */
    fun pluginDir(context: Context): File = File(context.filesDir, PLUGIN_DIR_NAME)

    /**
     * 已安装的本地插件数量：旧形态的 `.so`（含 `.so.disable`）+ 目录包
     * （`plugins/<id>/` 与 `plugins/.disabled/<id>/`）。
     *
     * 内核未安装时返回 null 表示「未知」；已安装但没有插件目录按 0 处理。
     */
    fun pluginCount(context: Context): Int? {
        if (!isInstalled(context)) return null
        val dir = pluginDir(context)
        if (!dir.isDirectory) return 0
        val legacy = dir.listFiles().orEmpty().count { file ->
            file.isFile && (file.name.endsWith(".so", ignoreCase = true) ||
                    file.name.endsWith(".so" + PluginStore.DISABLE_SUFFIX, ignoreCase = true))
        }
        val bundles = dir.listFiles().orEmpty().count { child ->
            child.isDirectory && File(child, PluginStore.MANIFEST_FILE).isFile
        }
        val disabledBundles = File(dir, PluginStore.DISABLED_DIR).listFiles().orEmpty().count { child ->
            child.isDirectory && File(child, PluginStore.MANIFEST_FILE).isFile
        }
        return legacy + bundles + disabledBundles
    }

    /**
     * 把旧版布局（数据 / 日志 / 插件都在内核安装目录里）一次性搬到新位置。
     *
     * 旧版内核把 TDLib 数据库放在 `<kernelDir>/data`、日志放在 `<kernelDir>/logs`、
     * 插件放在 `<kernelDir>/plugins`；而 [ensureCurrent] 升级内核时会清空整个安装目录，
     * 登录态、日志、用户插件会一起丢。这里在升级前先把三者搬出去：
     * - `data` / `logs`：新位置还没有同名目录时才移动（已经有新数据就以新数据为准）；
     * - `plugins`：合并进新插件目录，同名文件不覆盖（保留用户自己装的插件）。
     *
     * 每一步都各自兜住异常：迁移失败不应阻断内核启动，最多是旧目录随后被升级清掉。
     */
    fun migrateLegacyLayout(context: Context) {
        val legacyRoot = kernelDir(context)
        if (!legacyRoot.isDirectory) return
        val paths = paths(context)
        runCatching {
            moveDirectoryIfAbsent(File(legacyRoot, LEGACY_DATA_DIR), File(paths.dataDir))
            moveDirectoryIfAbsent(File(legacyRoot, LOG_DIR), logDir(context))
            mergeDirectory(File(legacyRoot, LEGACY_PLUGIN_DIR), File(paths.pluginDir))
        }
    }

    /** 移动目录；目标已存在时保持原状（说明新布局已经有数据，旧数据不再搬）。 */
    private fun moveDirectoryIfAbsent(source: File, target: File) {
        if (!source.isDirectory || target.exists()) return
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = false)
            source.deleteRecursively()
        }
    }

    /** 把旧插件目录合并进新插件目录：同名文件 / 目录保留目标已有的，不覆盖。 */
    private fun mergeDirectory(source: File, target: File) {
        if (!source.isDirectory) return
        target.mkdirs()
        source.listFiles().orEmpty().forEach { child ->
            val dest = File(target, child.name)
            if (dest.exists()) return@forEach
            if (!child.renameTo(dest)) {
                child.copyRecursively(dest, overwrite = false)
                child.deleteRecursively()
            }
        }
        source.deleteRecursively()
    }

    /**
     * 确保内置内核就绪：
     * - 已安装且版本不落后于 [CURRENT_KERNEL_VERSION] → 直接成功（幂等），**不回调进度**。
     * - 未安装（新装）或版本过低（App 升级）→ 从 assets 重新复制安装，
     *   期间逐段回调 0..100 进度。
     *
     * 替换安装目录前先做一次旧布局迁移，见 [migrateLegacyLayout]。
     */
    suspend fun ensureCurrent(
        context: Context,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            migrateLegacyLayout(context)
            if (isInstalled(context) &&
                compareVersion(installedVersion(context).orEmpty(), CURRENT_KERNEL_VERSION) >= 0
            ) {
                return@runCatching
            }
            installFromAssets(context, onProgress)
        }
    }

    /**
     * 从 `assets/fuyubot-kernel/` 复制安装内置内核。
     *
     * 先复制到缓存目录并校验主库存在，成功后再替换现有安装目录，
     * 这样复制中途失败不会破坏已经装好的内核。
     */
    private fun installFromAssets(context: Context, onProgress: (Int) -> Unit) {
        val staging = File(context.cacheDir, "fuyubot-kernel-staging")
        try {
            onProgress(0)
            val assetFiles = listAssetFiles(context.assets, ASSET_DIR)
            require(assetFiles.isNotEmpty()) { "资产中缺少内核目录：assets/$ASSET_DIR" }

            staging.deleteRecursively()
            staging.mkdirs()
            val canonicalRoot = staging.canonicalFile
            var packagedPlugins = 0

            assetFiles.forEachIndexed { index, assetPath ->
                val path = assetPath.removePrefix("$ASSET_DIR/")
                require(path.isNotEmpty() && path.split('/').none { it == ".." }) {
                    "内核资产中存在非法路径：$assetPath"
                }
                if (path.startsWith("$ASSET_PLUGIN_DIR/") && path.endsWith(".so")) {
                    packagedPlugins++
                }
                // 防目录穿越：归一化后的路径必须落在目标目录内。
                val output = File(staging, path)
                require(
                    output.canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
                ) {
                    "内核资产中存在非法路径：$assetPath"
                }
                output.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    output.outputStream().buffered().use { out -> input.copyTo(out) }
                }
                // 资产里的文件大多被 APK 压缩，读不到原始大小，按文件数推进进度。
                onProgress(((index + 1) * 100 / assetFiles.size).coerceAtMost(99))
            }

            require(File(staging, MAIN_LIBRARY).isFile) { "内核资产中缺少 $MAIN_LIBRARY" }
            require(File(staging, TDLIB_LIBRARY).isFile) { "内核资产中缺少 $TDLIB_LIBRARY" }
            // 资产里有插件就必须落到 plugins/ 下；落不了说明资产内的目录结构异常，
            // 与其装成一个「插件全丢」的内核，不如当场失败。
            if (packagedPlugins > 0) {
                val installed = File(staging, ASSET_PLUGIN_DIR)
                    .listFiles()
                    ?.count { it.isFile && it.name.endsWith(".so") }
                    ?: 0
                require(installed > 0) {
                    "插件未落到 $ASSET_PLUGIN_DIR 目录，请检查内核资产内的目录结构"
                }
            }

            // 内置插件装到安装目录**之外**的新插件目录，再把 staging 里的插件目录删掉，
            // 保证安装目录只剩内核本体，内核升级时不会连带清掉插件。
            installPackagedPlugins(File(staging, ASSET_PLUGIN_DIR), pluginDir(context))
            File(staging, ASSET_PLUGIN_DIR).deleteRecursively()

            File(staging, VERSION_FILE).writeText(CURRENT_KERNEL_VERSION)

            val target = kernelDir(context)
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            onProgress(100)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * 把资产里的内置插件复制到插件目录。
     *
     * 同名文件已存在时**不覆盖**——那通常是用户从插件仓库装的更新版本，冲掉会丢用户的选择。
     * 新落盘的插件按上游要求设为只读（targetSdk 34+ 下可执行代码不应可写）。
     */
    private fun installPackagedPlugins(source: File, target: File) {
        if (!source.isDirectory) return
        target.mkdirs()
        source.listFiles()?.filter { it.isFile }?.forEach { file ->
            val dest = File(target, file.name)
            if (dest.exists()) return@forEach
            file.copyTo(dest, overwrite = false)
            dest.setReadOnly()
        }
    }

    /**
     * 递归列出资产目录下的全部文件，返回相对 assets 根的路径
     * （如 `fuyubot-kernel/libtdjson.so`）。
     *
     * [AssetManager] 没有「是否目录」的查询：对目录调 [AssetManager.list] 返回子项，
     * 对文件返回空数组，因此按「有子项即目录」递归。内核资产里没有空目录。
     */
    private fun listAssetFiles(assets: AssetManager, dir: String): List<String> =
        assets.list(dir).orEmpty().flatMap { name ->
            val path = "$dir/$name"
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) listOf(path) else listAssetFiles(assets, path)
        }
}
