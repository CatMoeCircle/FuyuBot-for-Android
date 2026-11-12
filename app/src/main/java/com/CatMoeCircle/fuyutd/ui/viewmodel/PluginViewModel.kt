package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.FuyuBotConfigRepository
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotRuntime
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import com.CatMoeCircle.fuyutd.data.kernel.KernelPluginInfo
import com.CatMoeCircle.fuyutd.data.kernel.KernelPluginReport
import com.CatMoeCircle.fuyutd.data.plugin.CURRENT_PLUGIN_ABI
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.data.plugin.PluginInfo
import com.CatMoeCircle.fuyutd.data.plugin.PluginInstaller
import com.CatMoeCircle.fuyutd.data.plugin.PluginRepository
import com.CatMoeCircle.fuyutd.data.plugin.PluginStore
import com.CatMoeCircle.fuyutd.data.plugin.PluginVersion
import com.CatMoeCircle.fuyutd.data.plugin.assetForDevice
import com.CatMoeCircle.fuyutd.data.plugin.isUsable
import com.CatMoeCircle.fuyutd.data.plugin.selectVersion
import com.CatMoeCircle.fuyutd.data.plugin.supportsDevice
import com.CatMoeCircle.fuyutd.ui.screen.plugin.InstalledPluginItem
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginLoadStatus
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginUiState
import com.CatMoeCircle.fuyutd.ui.screen.plugin.RepoPluginItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 插件页的状态中枢：本地已安装插件（模块页）+ 商城插件（仓库页）。
 *
 * 状态来源：
 * 1. 远程索引（[PluginRepository]）——商城插件的 id / 名称 / 简介 / 版本 / 平台；
 * 2. 本地注册表（[PluginStore]）——哪些已安装、是否启用、是否需重启生效。
 *
 * 内核在运行时做增删 / 启停都只改磁盘并标记「待重启」，由 [HomeViewModel.stop]
 * 在停稳后真正删除「待卸载」文件并清掉「待重启」标记；下次内核启动即按当前文件加载。
 */
class PluginViewModel(
    private val repository: PluginRepository = PluginRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var kernelRunning = false
    private var loaded = false

    /** 内核最近一次的插件报告；null = 内核未运行 / 还没拿到报告。 */
    private var kernelReport: KernelPluginReport? = null

    private var allPlugins: List<PluginInfo> = emptyList()

    /** 当前索引地址；分片相对它解析。 */
    private var registryUrl: String? = null

    /** 已拉到的明细分片（轻索引本身没有版本表）。 */
    private val detailCache = mutableMapOf<String, PluginInfo>()

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        // 内核报告随运行时状态发布（`onPlugins` 推送 + 就绪后主动拉一次），
        // 这里只跟着最新报告重建列表；内核没跑时它是 null，界面保持原样。
        viewModelScope.launch {
            FuyuBotRuntime.instance().state
                .map { it.pluginReport }
                .distinctUntilChanged()
                .collect { report ->
                    if (kernelReport == report) return@collect
                    kernelReport = report
                    if (loaded) rebuildItems()
                }
        }

        // 注册表被别处改写时跟着重建：内核停止 / 重启后由 [HomeViewModel] 清掉「待重启」标记，
        // 界面必须同步消失，否则会一直显示已经失效的「重启后生效」。
        viewModelScope.launch {
            PluginStore.revision.collect {
                if (loaded) rebuildItems()
            }
        }
    }

    /** 进入页面 / 回到前台时调用。 */
    fun bind(context: Context, kernelRunning: Boolean) {
        appContext = context.applicationContext
        this.kernelRunning = kernelRunning
        if (!loaded) {
            loaded = true
            reload()
        } else {
            rebuildItems()
        }
    }

    /** 内核状态变化时由外部通知。 */
    fun setKernelRunning(running: Boolean) {
        if (kernelRunning == running) return
        kernelRunning = running
        if (loaded) rebuildItems()
    }

    /**
     * 重新加载：本地优先，网络辅助。
     *
     * 先直接读磁盘上的插件（[PluginStore.installedIncludingSystem]）并出列表，
     * 不等待网络；远程索引只用来补全商城列表与本地插件的名称 / 简介。
     * 因此断网时页面仍有完整的本地插件，而不是一片空白。
     */
    fun reload() {
        val context = appContext ?: return
        _uiState.update {
            it.copy(loading = true, loadStatus = PluginLoadStatus.Loading, loadError = null)
        }
        // 本地立即出结果：断网 / 网络超时都只影响商城列表，不影响本地插件。
        PluginStore.reconcile(context)
        rebuildItems()
        // 本地数据已经出来了，刷新指示器停掉；远程索引继续在后台补全，不阻塞页面。
        _uiState.update { it.copy(loading = false) }
        // 商城地址跟 `fuyubot.toml` 的 [plugins].registry_url 走；显式留空 = 关闭商城。
        val registry = runCatching {
            FuyuBotConfigRepository(context).pluginRegistryUrl()
        }
        registry.exceptionOrNull()?.let { error ->
            allPlugins = emptyList()
            registryUrl = null
            detailCache.clear()
            _uiState.update {
                it.copy(
                    loadStatus = PluginLoadStatus.Failed,
                    loadError = error.message ?: error.javaClass.simpleName,
                    storeDisabled = false,
                    repoItems = emptyList(),
                )
            }
            return
        }
        val registryUrl = registry.getOrNull()
        this.registryUrl = registryUrl
        detailCache.clear()
        if (registryUrl == null) {
            allPlugins = emptyList()
            _uiState.update {
                it.copy(
                    loadStatus = PluginLoadStatus.Success,
                    loadError = null,
                    storeDisabled = true,
                    repoItems = emptyList(),
                )
            }
            return
        }
        _uiState.update { it.copy(storeDisabled = false) }
        viewModelScope.launch {
            repository.fetchIndex(registryUrl).fold(
                onSuccess = { plugins ->
                    allPlugins = plugins
                    PluginStore.reconcile(context)
                    _uiState.update {
                        it.copy(loadStatus = PluginLoadStatus.Success, loadError = null)
                    }
                    rebuildItems()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadStatus = PluginLoadStatus.Failed,
                            loadError = error.message ?: error.javaClass.simpleName,
                            repoItems = emptyList(),
                        )
                    }
                },
            )
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        rebuildItems()
    }

    /** 切换「只看支持当前设备的插件」过滤。 */
    fun toggleRepoOnlySupported() {
        _uiState.update { it.copy(repoOnlySupported = !it.repoOnlySupported) }
        rebuildItems()
    }

    /** 切换「按名称排序」。 */
    fun toggleRepoSortByName() {
        _uiState.update { it.copy(repoSortByName = !it.repoSortByName) }
        rebuildItems()
    }

    /**
     * 打开插件详情弹窗：先展示 description 保底，再异步拉取明细分片与仓库 README。
     *
     * 新版索引是轻索引，列表项没有描述和版本表，所以要在这里补一次分片请求；
     * 分片回来后更新弹窗（描述 / 版本）与商城列表。
     */
    fun openDetail(item: RepoPluginItem) {
        val fallback = item.info.description.ifBlank { item.info.summary }
        _uiState.update {
            it.copy(detailItem = item, detailMarkdown = fallback.ifBlank { null })
        }
        ensureDetail(
            info = item.info,
            onLoaded = { detail ->
                _uiState.update { state ->
                    val current = state.detailItem
                    if (current?.info?.id != detail.id) return@update state
                    // README 还没回来时先用分片里的 description 顶上。
                    val markdown = state.detailMarkdown
                        ?.takeIf { it != fallback && it.isNotBlank() }
                        ?: detail.description.ifBlank { detail.summary }.ifBlank { null }
                    state.copy(
                        detailItem = current.copy(
                            info = detail,
                            deviceSupported = detail.supportsDevice(),
                        ),
                        detailMarkdown = markdown,
                    )
                }
            },
        )
        val repo = item.info.repo
        if (repo.isBlank()) return
        viewModelScope.launch {
            val readme = repository.fetchReadme(repo)
            if (readme != null && _uiState.value.detailItem?.info?.id == item.info.id) {
                _uiState.update { it.copy(detailMarkdown = readme) }
            }
        }
    }

    /** 关闭插件详情弹窗。 */
    fun dismissDetail() {
        _uiState.update { it.copy(detailItem = null, detailMarkdown = null) }
    }

    /** 展开 / 收起一张商城插件卡。 */
    fun toggleExpanded(item: RepoPluginItem) {
        _uiState.update { state ->
            state.copy(
                repoItems = state.repoItems.map {
                    if (it.info.id == item.info.id) it.copy(expanded = !it.expanded) else it
                },
            )
        }
    }

    /**
     * 下载 + 校验 + 安装一个商城插件，版本按商店规则自动选：
     * 未下架 + 插件 ABI ≤ 宿主 ABI + 有本平台包里最新的那个。
     *
     * 轻索引没有版本表，安装前先补一次明细分片。
     */
    fun install(item: RepoPluginItem) {
        val context = appContext ?: return
        val id = item.info.id
        if (activeJobs[id]?.isActive == true) return

        setItemProgress(id, 0, error = null)
        activeJobs[id] = viewModelScope.launch {
            val detail = resolveDetail(item.info)
            if (detail == null) {
                activeJobs.remove(id)
                setItemError(id, "插件版本信息加载失败")
                setVersionsError(id, "插件版本信息加载失败")
                return@launch
            }
            val version = detail.selectVersion()
            val asset = version?.assetForDevice()?.second
            if (version == null || asset == null) {
                activeJobs.remove(id)
                val message = "没有适配当前内核 ABI（$CURRENT_PLUGIN_ABI）的版本"
                setItemError(id, message)
                setVersionsError(id, message)
                return@launch
            }
            PluginInstaller.install(
                context = context,
                id = id,
                asset = asset,
                kernelRunning = kernelRunning,
                onProgress = { progress -> setItemProgress(id, progress, error = null) },
            ).fold(
                onSuccess = { installed ->
                    activeJobs.remove(id)
                    _uiState.update { state ->
                        state.copy(
                            repoItems = state.repoItems.map {
                                if (it.info.id == id) {
                                    it.copy(installed = installed, installProgress = null, error = null)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                    rebuildItems()
                    clearVersionsProgress(id)
                },
                onFailure = { error ->
                    activeJobs.remove(id)
                    setItemError(id, error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /**
     * 打开「所有版本」页：先把轻索引里缺的版本表补上。
     *
     * 分片已在缓存里时同步出结果，不闪加载态。
     */
    fun loadVersions(id: String) {
        val info = allPlugins.firstOrNull { it.id == id }
        if (info == null) {
            _uiState.update {
                it.copy(
                    versionsInfo = null,
                    versionsInstalled = null,
                    versionsProgress = null,
                    versionsLoading = false,
                    versionsError = "插件已不在索引里",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                versionsInfo = info,
                versionsInstalled = appContext?.let { context -> PluginStore.find(context, id) },
                versionsProgress = null,
                versionsLoading = info.versions.isEmpty() && info.detail.isNotBlank(),
                versionsError = null,
            )
        }
        ensureDetail(
            info = info,
            onLoaded = { detail ->
                _uiState.update { state ->
                    if (state.versionsInfo?.id != detail.id) return@update state
                    state.copy(versionsInfo = detail, versionsLoading = false)
                }
            },
            onError = { message -> setVersionsError(id, message) },
        )
    }

    /** 在「所有版本」页安装指定版本（已下架 / ABI 不兼容的版本会被拒绝）。 */
    fun installVersion(version: PluginVersion) {
        val context = appContext ?: return
        val info = _uiState.value.versionsInfo ?: return
        val id = info.id
        if (activeJobs[id]?.isActive == true) return
        if (!version.isUsable()) {
            setVersionsError(id, "这个版本不能装在本机（已下架或 ABI 高于内核）")
            return
        }
        val asset = version.assetForDevice()?.second ?: run {
            setVersionsError(id, "这个版本没有本平台的包")
            return
        }

        setItemProgress(id, 0, error = null)
        activeJobs[id] = viewModelScope.launch {
            PluginInstaller.install(
                context = context,
                id = id,
                asset = asset,
                kernelRunning = kernelRunning,
                onProgress = { progress -> setItemProgress(id, progress, error = null) },
            ).fold(
                onSuccess = {
                    activeJobs.remove(id)
                    rebuildItems()
                    clearVersionsProgress(id)
                },
                onFailure = { error ->
                    activeJobs.remove(id)
                    setVersionsError(id, error.message ?: error.javaClass.simpleName)
                    setItemError(id, error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /**
     * 点插件卡片上的「卸载」。
     *
     * - 内核运行中：标记「待卸载」，并把插件挪到停用位置——重启内核后不会再加载它；
     *   文件等内核停止后再删，界面可「撤销」；
     * - 内核未运行：先把卡片划掉、按钮变成「确认卸载」（[uninstallPending]），
     *   真正删除要等用户点「确认卸载」并在弹窗里再确认一次。
     */
    fun uninstall(installed: InstalledPlugin) {
        val context = appContext ?: return
        val id = installed.id
        if (activeJobs[id]?.isActive == true) return
        // 已经是待卸载 / 待确认状态，重复点「卸载」不应覆盖原状态（例如仓库页详情）。
        if (installed.pendingUninstall || installed.id == _uiState.value.uninstallPending?.id) return

        if (!kernelRunning) {
            _uiState.update {
                it.copy(uninstallPending = installed, uninstallDialogVisible = false)
            }
            // 重新算一遍列表，让卡片立刻变成「划掉 + 确认卸载」。
            rebuildItems()
            return
        }

        viewModelScope.launch {
            PluginInstaller.uninstall(context, installed, kernelRunning = true).fold(
                onSuccess = { rebuildItems() },
                onFailure = { error -> setItemError(id, error.message ?: error.javaClass.simpleName) },
            )
        }
    }

    /** 点「确认卸载」：为已划掉的插件弹出确认弹窗。 */
    fun requestUninstallConfirm() {
        if (_uiState.value.uninstallPending == null) return
        _uiState.update { it.copy(uninstallDialogVisible = true) }
    }

    /** 关掉「确认卸载」弹窗；已划掉的状态保留，可稍后再点「确认卸载」。 */
    fun dismissUninstallDialog() {
        _uiState.update { it.copy(uninstallDialogVisible = false) }
    }

    /** 点「还原」：取消划掉/待确认状态，插件恢复原样。 */
    fun restoreUninstall() {
        if (_uiState.value.uninstallPending == null) return
        _uiState.update { it.copy(uninstallPending = null, uninstallDialogVisible = false) }
        rebuildItems()
    }

    /**
     * 确认删除：内核未运行时真正删掉插件目录与注册信息。
     *
     * 确认期间内核若已经被启动，则退回「待卸载」流程，等内核停止后再删。
     */
    fun confirmUninstall() {
        val context = appContext ?: return
        val installed = _uiState.value.uninstallPending ?: return
        _uiState.update { it.copy(uninstallPending = null, uninstallDialogVisible = false) }
        // 先恢复卡片（万一下面的删除失败，界面也不会一直停在「确认卸载」）。
        rebuildItems()

        viewModelScope.launch {
            PluginInstaller.uninstall(context, installed, kernelRunning).fold(
                onSuccess = { rebuildItems() },
                onFailure = {
                    setItemError(installed.id, it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    /** 撤销「待卸载」：把插件挪回启用位置；内核运行中若已不再加载它，需要重启才生效。 */
    fun undoUninstall(installed: InstalledPlugin) {
        val context = appContext ?: return
        val pluginDirPath = KernelManager.runtimePluginDir(context).absolutePath
        // 卸载时插件已被挪走；如果内核报告里还有它，说明当前会话仍加载着它，撤销不需要重启。
        val stillLoaded = kernelReport?.plugins
            ?.any { it.matches(pluginDirPath, installed) } == true
        viewModelScope.launch {
            PluginInstaller.undoUninstall(
                context = context,
                installed = installed,
                kernelRunning = kernelRunning && !stillLoaded,
            ).fold(
                onSuccess = { rebuildItems() },
                onFailure = { },
            )
        }
    }

    /** 启用 / 停用插件（`.so` ↔ `.so.disable` 改名）。 */
    fun setEnabled(installed: InstalledPlugin, enabled: Boolean) {
        val context = appContext ?: return
        val id = installed.id
        if (activeJobs[id]?.isActive == true) return

        viewModelScope.launch {
            PluginInstaller.setEnabled(context, installed, enabled, kernelRunning).fold(
                onSuccess = { rebuildItems() },
                onFailure = { error -> setItemError(id, error.message ?: error.javaClass.simpleName) },
            )
        }
    }

    private fun rebuildItems() {
        val context = appContext ?: return
        val state = _uiState.value
        val query = state.query.trim()
        val installedById = PluginStore.installed(context).associateBy { it.id }
        val transientById = state.repoItems.associateBy { it.info.id }

        val pluginById = allPlugins.associateBy { it.id }

        val repoItems = allPlugins
            .map { plugin ->
                val previous = transientById[plugin.id]
                RepoPluginItem(
                    info = plugin,
                    installed = installedById[plugin.id],
                    installProgress = previous?.installProgress,
                    error = previous?.error,
                    deviceSupported = plugin.supportsDevice(),
                    expanded = previous?.expanded ?: false,
                )
            }
            .filter { item ->
                query.isEmpty() ||
                        item.info.name.contains(query, ignoreCase = true) ||
                        item.info.summary.contains(query, ignoreCase = true) ||
                        item.info.id.contains(query, ignoreCase = true) ||
                        item.info.tags.any { it.contains(query, ignoreCase = true) }
            }
            .let { items ->
                if (state.repoOnlySupported) items.filter { it.deviceSupported } else items
            }
            .let { items ->
                if (state.repoSortByName) {
                    items.sortedBy { it.info.name.lowercase() }
                } else {
                    items
                }
            }

        // 本地已安装插件：注册表里的商城插件 + 磁盘上的系统 / 内置插件，
        // 用索引回填名称/简介（系统插件索引里没有，界面回退显示 id）。
        // 同时把内核报告里的条目按文件名 / 路径前缀匹配上来，供界面叠加真实状态。
        val kernelPlugins = kernelReport?.plugins.orEmpty()
        val pluginDirPath = KernelManager.runtimePluginDir(context).absolutePath
        val matchedKernel = mutableSetOf<KernelPluginInfo>()
        val stagedId = state.uninstallPending?.id
        val installedItems = PluginStore.installedIncludingSystem(context).map { registered ->
            val kernelInfo = kernelPlugins
                .firstOrNull { it !in matchedKernel && it.matches(pluginDirPath, registered) }
                ?.also { matchedKernel += it }
            InstalledPluginItem(
                installed = registered,
                info = pluginById[registered.id],
                kernel = kernelInfo,
                confirmUninstall = registered.id == stagedId,
            )
        }
        // 内核里有、注册表里对应不上的条目：单独列出，不要因为匹配不上就丢掉。
        val kernelOrphans = kernelPlugins.filterNot { it in matchedKernel }

        val rebootNeeded = installedItems.any { it.installed.pendingRestart || it.installed.pendingUninstall }

        // 划掉待确认的插件跟着最新注册表刷新；已经不在列表里（例如别处删掉了）就收起弹窗。
        val uninstallPending = installedItems.firstOrNull { it.confirmUninstall }?.installed
        val uninstallDialogVisible = uninstallPending != null && state.uninstallDialogVisible

        _uiState.update {
            it.copy(
                installed = installedItems,
                kernelSkipped = kernelReport?.skippedPlugins.orEmpty(),
                kernelOrphans = kernelOrphans,
                repoItems = repoItems,
                rebootNeeded = rebootNeeded,
                uninstallPending = uninstallPending,
                uninstallDialogVisible = uninstallDialogVisible,
                // 版本页开着时跟着最新列表 / 注册表刷新，安装完能立刻显示新版本号。
                versionsInfo = state.versionsInfo?.let { info -> pluginById[info.id] ?: info },
                versionsInstalled = state.versionsInfo?.let { info -> installedById[info.id] },
            )
        }
    }

    /**
     * 轻索引补版本表：已有版本或没有分片地址时直接回调，否则拉一次分片并写回索引缓存。
     *
     * 成功后重建列表，让 [PluginInfo] 换成带版本表的版本；每个调用方各自拿到结果
     * （同一插件可能同时被详情弹窗与版本页打开，重复一次请求比回调丢失更简单可控）。
     */
    private fun ensureDetail(
        info: PluginInfo,
        onLoaded: (PluginInfo) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        if (info.versions.isNotEmpty() || info.detail.isBlank()) {
            onLoaded(info)
            return
        }
        detailCache[info.id]?.let {
            onLoaded(it)
            return
        }
        val base = registryUrl ?: return
        viewModelScope.launch {
            repository.fetchDetail(base, info.detail).fold(
                onSuccess = { detail ->
                    detailCache[detail.id] = detail
                    allPlugins = allPlugins.map { if (it.id == detail.id) detail else it }
                    rebuildItems()
                    onLoaded(detail)
                },
                onFailure = { error ->
                    onError(error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /** 安装前取带版本表的 [PluginInfo]（轻索引要补一次分片）；失败返回 null。 */
    private suspend fun resolveDetail(info: PluginInfo): PluginInfo? {
        if (info.versions.isNotEmpty() || info.detail.isBlank()) return info
        detailCache[info.id]?.let { return it }
        val base = registryUrl ?: return null
        val detail = repository.fetchDetail(base, info.detail).getOrNull() ?: return null
        detailCache[detail.id] = detail
        allPlugins = allPlugins.map { if (it.id == detail.id) detail else it }
        return detail
    }

    /**
     * 内核报告里的这一项是不是这个已安装插件。
     *
     * - 目录包：内核报的 `path` 是 `<pluginDir>/<注册表 fileName>/bin/lib….so`，按路径前缀认；
     * - 旧形态：`path` 就是 `<pluginDir>/<fileName>`，按路径相等或文件名相等认。
     */
    private fun KernelPluginInfo.matches(
        pluginDirPath: String,
        installed: InstalledPlugin,
    ): Boolean {
        val fileName = installed.fileName
        if (fileName.isBlank()) return false
        val base = "$pluginDirPath/$fileName"
        return path == base || path.startsWith("$base/") || file == fileName
    }

    private fun setItemProgress(id: String, progress: Int, error: String?) {
        _uiState.update { state ->
            val onVersionsPage = state.versionsInfo?.id == id
            state.copy(
                repoItems = state.repoItems.map {
                    if (it.info.id == id) it.copy(installProgress = progress, error = error) else it
                },
                versionsProgress = if (onVersionsPage) progress else state.versionsProgress,
                versionsError = if (onVersionsPage) error else state.versionsError,
            )
        }
    }

    private fun setVersionsError(id: String, message: String) {
        _uiState.update { state ->
            if (state.versionsInfo?.id != id) {
                state
            } else {
                state.copy(versionsProgress = null, versionsError = message)
            }
        }
    }

    private fun clearVersionsProgress(id: String) {
        _uiState.update { state ->
            if (state.versionsInfo?.id != id) {
                state
            } else {
                state.copy(versionsProgress = null, versionsError = null)
            }
        }
    }

    private fun setItemError(id: String, message: String) {
        _uiState.update { state ->
            val onVersionsPage = state.versionsInfo?.id == id
            state.copy(
                repoItems = state.repoItems.map {
                    if (it.info.id == id) it.copy(installProgress = null, error = message) else it
                },
                versionsProgress = if (onVersionsPage) null else state.versionsProgress,
                versionsError = if (onVersionsPage) message else state.versionsError,
            )
        }
    }
}
