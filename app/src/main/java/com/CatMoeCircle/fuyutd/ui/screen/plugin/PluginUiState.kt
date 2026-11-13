package com.CatMoeCircle.fuyutd.ui.screen.plugin

import com.CatMoeCircle.fuyutd.data.kernel.KernelPluginInfo
import com.CatMoeCircle.fuyutd.data.kernel.KernelSkippedPlugin
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.data.plugin.PluginInfo
import com.CatMoeCircle.fuyutd.data.plugin.PluginVersion

/**
 * 插件商城索引的加载状态。
 */
enum class PluginLoadStatus { Loading, Success, Failed }

/**
 * 商城（仓库）列表中的一项：索引信息 + 本地安装状态 + 进行中的安装进度。
 */
data class RepoPluginItem(
    val info: PluginInfo,
    val installed: InstalledPlugin? = null,
    /** 安装进行中的进度 0..100；null 表示未在安装。 */
    val installProgress: Int? = null,
    /** 最近一次安装 / 卸载失败的错误描述；成功或未操作时为 null。 */
    val error: String? = null,
    /** 该插件是否支持当前设备。 */
    val deviceSupported: Boolean = true,
    /** 卡片是否展开（展示简介与安装按钮）。 */
    val expanded: Boolean = false,
)

/**
 * 本地已安装插件的一项：本地注册信息 + 从商城索引匹配到的展示信息。
 *
 * 本地注册表只存 id / 版本 / 文件名，名称与简介需要回查索引；
 * 索引里没有（例如旧版本下架）时 [info] 为 null，界面退回显示 id。
 */
data class InstalledPluginItem(
    val installed: InstalledPlugin,
    val info: PluginInfo? = null,
    /** 内核报告里与这个插件匹配到的条目；内核未运行 / 没匹配上为 null。 */
    val kernel: KernelPluginInfo? = null,
    /**
     * 是否处于「已划掉、等待确认卸载」状态（仅内核停止时使用）。
     *
     * 此时卡片划掉、按钮变成「确认卸载」，点它才会弹出确认弹窗。
     */
    val confirmUninstall: Boolean = false,
    /**
     * 商城索引里比本地版本新、且按商店规则能在本机安装的版本；没有更新为 null。
     *
     * 由 PluginViewModel 选版后填入，界面据此显示「更新」。
     */
    val updateVersion: PluginVersion? = null,
    /** 更新安装进度 0..100；null 表示没在更新。 */
    val updateProgress: Int? = null,
    /** 最近一次更新失败的错误；成功或未操作时为 null。 */
    val updateError: String? = null,
)

/**
 * 插件页状态。
 *
 * 分两段：
 * - [installed]：本地已安装插件（带启用开关 / 卸载 / 重启）；
 * - [repoItems]：商城插件（点击展开详情 + 安装）。
 */
data class PluginUiState(
    val loading: Boolean = true,
    val loadStatus: PluginLoadStatus = PluginLoadStatus.Loading,
    /** 索引加载失败的错误；加载成功后为 null。 */
    val loadError: String? = null,
    /** `[plugins].registry_url` 被显式留空时为 true：内核侧商城关闭，这里也不去联网。 */
    val storeDisabled: Boolean = false,
    val query: String = "",
    /** 本地已安装插件。 */
    val installed: List<InstalledPluginItem> = emptyList(),
    /**
     * 内核跳过、根本没加载的候选（内核在插件目录里扫到但没进去）。
     * 内核未运行 / 还没拿到报告时为空，此时界面不显示这一区块。
     */
    val kernelSkipped: List<KernelSkippedPlugin> = emptyList(),
    /**
     * 内核已加载、但本机注册表里匹配不上的插件（例如手工放进插件目录的）。
     * 内核未运行 / 还没拿到报告 / 全部匹配上时为空。
     */
    val kernelOrphans: List<KernelPluginInfo> = emptyList(),
    /** 商城插件（已过滤）。 */
    val repoItems: List<RepoPluginItem> = emptyList(),
    /** 是否只看支持当前设备的插件。 */
    val repoOnlySupported: Boolean = false,
    /** 是否按名称排序（否则按索引原始顺序）。 */
    val repoSortByName: Boolean = false,
    /** 是否有本地插件因增删/启停而需要重启内核生效。 */
    val rebootNeeded: Boolean = false,
    /**
     * 内核停止时已点过「卸载」、等待确认删除的插件 id 集合（可以同时划掉多个）。
     *
     * 与运行中的 [InstalledPlugin.pendingUninstall] 不同：这里只是界面上的待确认状态，
     * 不会落盘，也不参与「内核停止后自动删除」；只有点「确认卸载」弹窗里的「确认」才会真正删除。
     */
    val uninstallPendingIds: Set<String> = emptySet(),
    /** 正在显示「确认卸载」弹窗的插件 id；null 表示弹窗没开。 */
    val uninstallDialogId: String? = null,
    /** 详情弹窗当前展示的插件；null 表示未打开详情。 */
    val detailItem: RepoPluginItem? = null,
    /** 详情弹窗里展示的 Markdown 正文（README 或保底 description）。 */
    val detailMarkdown: String? = null,
    /**
     * 「所有版本」页当前展示的插件；null 表示没打开版本页。
     *
     * 轻索引里这个对象只有摘要，版本表要等明细分片回来才补齐。
     */
    val versionsInfo: PluginInfo? = null,
    /** 「所有版本」页是否正在拉明细分片。 */
    val versionsLoading: Boolean = false,
    /** 「所有版本」页的加载 / 安装错误；成功或未操作时为 null。 */
    val versionsError: String? = null,
    /** 「所有版本」页对应插件的本地安装信息；未安装为 null。 */
    val versionsInstalled: InstalledPlugin? = null,
    /** 「所有版本」页的安装进度 0..100；null 表示没在安装。 */
    val versionsProgress: Int? = null,
)

/**
 * 插件页的动作集合，两套主题共用。
 */
data class PluginScreenActions(
    val onQueryChange: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onReboot: () -> Unit,
    val onOpenRepo: () -> Unit,
    val onToggleEnabled: (InstalledPlugin, Boolean) -> Unit,
    val onUninstall: (InstalledPlugin) -> Unit,
    /** 内核停止时，已划掉的插件点「确认卸载」：按插件 id 弹出确认弹窗。 */
    val onRequestConfirmUninstall: (String) -> Unit,
    /** 内核停止时，把划掉的卡片还原回正常状态。 */
    val onRestoreUninstall: (String) -> Unit,
    val onUndoUninstall: (InstalledPlugin) -> Unit,
    /** 本地插件有新版时点「更新」：升级到 [InstalledPluginItem.updateVersion]。 */
    val onUpdate: (InstalledPluginItem) -> Unit,
    val onToggleExpanded: (RepoPluginItem) -> Unit,
    val onInstall: (RepoPluginItem) -> Unit,
    val onToggleRepoOnlySupported: () -> Unit,
    val onToggleRepoSortByName: () -> Unit,
    val onOpenDetail: (RepoPluginItem) -> Unit,
    val onDismissDetail: () -> Unit,
)
