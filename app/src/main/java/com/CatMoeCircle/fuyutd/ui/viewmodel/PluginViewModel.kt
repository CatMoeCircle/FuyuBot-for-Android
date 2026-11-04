package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.data.plugin.PluginInfo
import com.CatMoeCircle.fuyutd.data.plugin.PluginInstaller
import com.CatMoeCircle.fuyutd.data.plugin.PluginRepository
import com.CatMoeCircle.fuyutd.data.plugin.PluginStore
import com.CatMoeCircle.fuyutd.data.plugin.deviceAsset
import com.CatMoeCircle.fuyutd.data.plugin.supportsDevice
import com.CatMoeCircle.fuyutd.ui.screen.plugin.InstalledPluginItem
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginLoadStatus
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginUiState
import com.CatMoeCircle.fuyutd.ui.screen.plugin.RepoPluginItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * 在停稳后真正删除「待卸载」文件；下次内核启动即按当前文件加载。
 */
class PluginViewModel(
    private val repository: PluginRepository = PluginRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var kernelRunning = false
    private var loaded = false

    private var allPlugins: List<PluginInfo> = emptyList()

    private val indexUrl = "https://catmoecircle.github.io/FuyuBot-plugins/index.json"

    private val activeJobs = mutableMapOf<String, Job>()

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

    /** 重新拉取远程索引。 */
    fun reload() {
        val context = appContext ?: return
        _uiState.update {
            it.copy(loading = true, loadStatus = PluginLoadStatus.Loading, loadError = null)
        }
        viewModelScope.launch {
            repository.fetchIndex(indexUrl).fold(
                onSuccess = { plugins ->
                    allPlugins = plugins
                    PluginStore.reconcile(context)
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

    /** 打开插件详情弹窗：先展示 description 保底，再异步拉取仓库 README。 */
    fun openDetail(item: RepoPluginItem) {
        val fallback = item.info.description.ifBlank { item.info.summary }
        _uiState.update {
            it.copy(detailItem = item, detailMarkdown = fallback.ifBlank { null })
        }
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

    /** 下载 + 校验 + 安装一个商城插件。 */
    fun install(item: RepoPluginItem) {
        val context = appContext ?: return
        val id = item.info.id
        if (activeJobs[id]?.isActive == true) return

        val (_, asset) = item.info.deviceAsset() ?: run {
            setItemError(id, "当前设备不受支持")
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
                },
                onFailure = { error ->
                    activeJobs.remove(id)
                    setItemError(id, error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }

    /** 卸载一个本地插件；内核运行中则标记「待卸载」，未运行则删文件。 */
    fun uninstall(installed: InstalledPlugin) {
        val context = appContext ?: return
        val id = installed.id
        if (activeJobs[id]?.isActive == true) return

        viewModelScope.launch {
            PluginInstaller.uninstall(context, installed, kernelRunning).fold(
                onSuccess = { rebuildItems() },
                onFailure = { error -> setItemError(id, error.message ?: error.javaClass.simpleName) },
            )
        }
    }

    /** 撤销「待卸载」。 */
    fun undoUninstall(installed: InstalledPlugin) {
        val context = appContext ?: return
        viewModelScope.launch {
            PluginInstaller.undoUninstall(context, installed).fold(
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
        val installedItems = PluginStore.installedIncludingSystem(context).map { registered ->
            InstalledPluginItem(
                installed = registered,
                info = pluginById[registered.id],
            )
        }

        val rebootNeeded = installedItems.any { it.installed.pendingRestart || it.installed.pendingUninstall }

        _uiState.update {
            it.copy(
                loading = false,
                loadStatus = PluginLoadStatus.Success,
                loadError = null,
                installed = installedItems,
                repoItems = repoItems,
                rebootNeeded = rebootNeeded,
            )
        }
    }

    private fun setItemProgress(id: String, progress: Int, error: String?) {
        _uiState.update { state ->
            state.copy(
                repoItems = state.repoItems.map {
                    if (it.info.id == id) it.copy(installProgress = progress, error = error) else it
                },
            )
        }
    }

    private fun setItemError(id: String, message: String) {
        _uiState.update { state ->
            state.copy(
                repoItems = state.repoItems.map {
                    if (it.info.id == id) it.copy(installProgress = null, error = message) else it
                },
            )
        }
    }
}
