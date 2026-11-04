package com.CatMoeCircle.fuyutd.data.kernel

import android.content.Context
import android.content.res.AssetManager
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
 * 安装产物放在 `filesDir/fuyubot-kernel/`：
 * ```
 * libfuyubotjni.so      ← 主运行时
 * libtdjson.so          ← TDLib，必须与前者同 ABI
 * plugins/              ← 可选插件，需只读（targetSdk 34+ 的要求）
 * kernel-version        ← 本次安装的版本号
 * ```
 */
object KernelManager {

    private const val ASSET_DIR = "fuyubot-kernel"
    private const val KERNEL_DIR = "fuyubot-kernel"
    private const val VERSION_FILE = "kernel-version"
    private const val MAIN_LIBRARY = "libfuyubotjni.so"
    private const val TDLIB_LIBRARY = "libtdjson.so"
    private const val PLUGIN_DIR = "plugins"

    fun kernelDir(context: Context): File = File(context.filesDir, KERNEL_DIR)

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

    /** 插件目录，内核未安装时也返回其预期路径。 */
    fun pluginDir(context: Context): File = File(kernelDir(context), PLUGIN_DIR)

    /**
     * 已安装的本地插件数量（按 `plugins/` 下的 `.so` 文件计）。
     *
     * 内核未安装时返回 null 表示「未知」；已安装但没有插件目录按 0 处理。
     */
    fun pluginCount(context: Context): Int? {
        if (!isInstalled(context)) return null
        val dir = pluginDir(context)
        if (!dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile && it.name.endsWith(".so", ignoreCase = true) }
    }

    /**
     * 确保内置内核就绪：
     * - 已安装且版本不落后于 [CURRENT_KERNEL_VERSION] → 直接成功（幂等），**不回调进度**。
     * - 未安装（新装）或版本过低（App 升级）→ 从 assets 重新复制安装，
     *   期间逐段回调 0..100 进度。
     */
    suspend fun ensureCurrent(
        context: Context,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
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
                if (path.startsWith("$PLUGIN_DIR/") && path.endsWith(".so")) {
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
                val installed = File(staging, PLUGIN_DIR)
                    .listFiles()
                    ?.count { it.isFile && it.name.endsWith(".so") }
                    ?: 0
                require(installed > 0) {
                    "插件未落到 $PLUGIN_DIR 目录，请检查内核资产内的目录结构"
                }
            }

            File(staging, VERSION_FILE).writeText(CURRENT_KERNEL_VERSION)
            markPluginsReadOnly(File(staging, PLUGIN_DIR))

            val target = kernelDir(context)
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            markPluginsReadOnly(File(target, PLUGIN_DIR))
            onProgress(100)
        } finally {
            staging.deleteRecursively()
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

    /**
     * 插件目录按上游要求设为只读（targetSdk 34+ 下可执行代码不应可写）。
     *
     * 只处理这一层文件，不递归——`plugins/` 下不应再有子目录。
     */
    private fun markPluginsReadOnly(dir: File) {
        if (!dir.isDirectory) return
        dir.listFiles()?.filter { it.isFile }?.forEach { it.setReadOnly() }
    }
}
