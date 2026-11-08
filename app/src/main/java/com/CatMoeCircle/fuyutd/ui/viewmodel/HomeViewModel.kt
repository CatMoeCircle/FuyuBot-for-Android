package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.FuyuBotConfigRepository
import com.CatMoeCircle.fuyutd.data.kernel.AskKind
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotAccountStore
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotRuntime
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotState
import com.CatMoeCircle.fuyutd.data.kernel.KernelManager
import com.CatMoeCircle.fuyutd.data.kernel.LogRepository
import com.CatMoeCircle.fuyutd.data.plugin.PluginStore
import com.CatMoeCircle.fuyutd.ui.screen.home.HomeUiState
import com.CatMoeCircle.fuyutd.ui.screen.home.TdlibAuthState
import com.CatMoeCircle.fuyutd.ui.screen.home.TdlibConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 首页状态与启动编排。
 *
 * 启动按「授权状态」分步弹窗：
 * 1. 内核未安装 → 提示去「检查更新」下载；
 * 2. api_id / api_hash 未填 → 弹配置弹窗（首次授权）；
 * 3. 配置齐了 → 真正启动。bot_token 留空即真人登录，不再阻止启动；
 * 4. 运行中遇到 onAsk 提问再弹登录弹窗：手机号 → 验证码 → 两步验证密码；
 *    「登录方式」提问自动选手机号（暂不支持扫码），详见 [autoSelectPhoneLogin]。
 */
class HomeViewModel : ViewModel() {

    private var config: FuyuBotConfigRepository? = null
    private var transientBotToken: String? = null
    // 进程级单例：前台 Service 与 Activity 重建后的新 ViewModel 共用同一个运行时，
    // 状态与回调不会随界面重建而丢失。
    private val runtime = FuyuBotRuntime.instance()
    private var appContext: Context? = null

    private val _configDialog = MutableStateFlow(false)
    val configDialog: StateFlow<Boolean> = _configDialog.asStateFlow()

    private val _error = MutableStateFlow<HomeError?>(null)
    val error: StateFlow<HomeError?> = _error.asStateFlow()

    /**
     * 运行时的登录提问，用于弹对应的提问弹窗。
     *
     * 「登录方式（phone / qr）」这一问不弹窗：内核支持扫码，但本 App 暂不做二维码界面，
     * 由 [autoSelectPhoneLogin] 直接替用户选手机号。其余提问原样透传，
     * 由弹窗按 [AskKind] 决定标题、键盘与掩码。
     */
    val ask: StateFlow<com.CatMoeCircle.fuyutd.data.kernel.FuyuBotAsk?> =
        runtime.state
            .map { it.ask?.takeUnless { pending -> pending.kind == AskKind.LOGIN_METHOD } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    init {
        autoSelectPhoneLogin()
    }

    /**
     * 收到「登录方式」提问就自动回答 `phone`，不弹窗。
     *
     * 同一个提问只答一次（记录 [autoAnsweredLoginMethodId]）：内核处理完才会把 ask 清空，
     * 期间状态流量里会一直带着它，重复回答只会白拿一个 `FUYUBOT_ARG`。
     */
    private fun autoSelectPhoneLogin() {
        viewModelScope.launch {
            runtime.state.collect { state ->
                val pending = state.ask ?: return@collect
                if (pending.kind != AskKind.LOGIN_METHOD) return@collect
                if (pending.id == autoAnsweredLoginMethodId) return@collect
                runtime.answer(pending.id, LOGIN_METHOD_PHONE)
                autoAnsweredLoginMethodId = pending.id
            }
        }
    }

    private var startJob: Job? = null
    private var statusJob: Job? = null
    private var prepareJob: Job? = null
    private var accountJob: Job? = null
    private var restartJob: Job? = null

    /** 打开 App 时内核自动准备进度（0..100）；null 表示未在准备。 */
    private val preparingProgress = MutableStateFlow<Int?>(null)

    /** 已安装内核的插件数量；内核未安装时为 null。 */
    private val pluginCount = MutableStateFlow<Int?>(null)

    /** `getMe` 拿到的账户信息；null 表示还没取到（未登录 / 请求失败）。 */
    private val account = MutableStateFlow(FuyuBotAccountStore.load())

    /** 已经自动回答过「手机号登录」的提问 id；null 表示本次运行还没答过。 */
    private var autoAnsweredLoginMethodId: Int? = null

    /** 除内核准备进度外的首页状态聚合（combine 上限 5 路，进度单独在下游合并）。 */
    private val baseState: Flow<BaseHomeState> = combine(
        runtime.state,
        ticker(),
        pluginCount,
    ) { runtimeState, _, pluginsOnDisk ->
        val auth = runtimeState.authState
        val connection = runtimeState.connectionState
        BaseHomeState(
            isRunning = runtimeState.state == FuyuBotState.READY ||
                    runtimeState.state == FuyuBotState.STARTING,
            // 起点由运行时记录，这里只负责按当前时刻换算，避免自己维护计时器状态。
            uptime = runtimeState.startedAtElapsedRealtime
                ?.let { start -> formatUptime(SystemClock.elapsedRealtime() - start) }
                ?: IDLE_UPTIME,
            // 运行中优先用内核扫到的数；还没拿到报告（未启动 / 刚启动）时退回磁盘文件数。
            pluginCount = runtimeState.pluginStats?.scanned ?: pluginsOnDisk,
            loadedPluginCount = runtimeState.pluginStats?.active,
            failedPluginCount = runtimeState.pluginStats?.failed,
            skippedPluginCount = runtimeState.pluginStats?.skipped,
            authState = auth?.state?.let(TdlibAuthState::fromRaw) ?: TdlibAuthState.NOT_REPORTED,
            // needs_re_login 在登录窗口里也是 true，只有「曾经 ready 过」才算被下线。
            needsReLogin = auth?.needsReLogin == true && runtimeState.everReady,
            connectionState = connection?.state?.let(TdlibConnectionState::fromRaw)
                ?: TdlibConnectionState.NOT_REPORTED,
            tdlibDetail = runtimeState.stateDetail,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        preparingProgress,
        account,
    ) { base, preparing, acc ->
        HomeUiState(
            isRunning = base.isRunning,
            preparingProgress = preparing,
            name = acc?.displayName,
            avatarPath = acc?.avatarPath,
            uptime = base.uptime,
            pluginCount = base.pluginCount,
            loadedPluginCount = base.loadedPluginCount,
            failedPluginCount = base.failedPluginCount,
            skippedPluginCount = base.skippedPluginCount,
            authState = base.authState,
            needsReLogin = base.needsReLogin,
            connectionState = base.connectionState,
            tdlibDetail = base.tdlibDetail,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    /**
     * 运行中每秒发一次心跳，驱动运行时长刷新；未运行时降低频率空转，
     * 只用于感知「何时开始运行」。
     */
    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val running = runtime.state.value.startedAtElapsedRealtime != null
            delay(if (running) 1_000L else IDLE_TICK_MS)
        }
    }

    /** 读取本地内核的插件数量（进入前台、安装 / 更新内核后调用）。 */
    fun bind(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
        config = FuyuBotConfigRepository(appContext)
        prepareKernel(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            pluginCount.value = KernelManager.pluginCount(appContext)
        }
        watchTdlibStatus()
        // 进程里可能早就跑起来了（例如界面被重建）：直接试一次，
        // 还没登录时 getMe 会失败并保持「未知」，不影响其它状态。
        if (runtime.isRunning) refreshAccount()
    }

    /** 重读本地内核的插件数量（切换到首页、增删 / 启停插件后调用）。 */
    fun refreshPluginCount() {
        val appContext = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            pluginCount.value = KernelManager.pluginCount(appContext)
        }
    }

    /**
     * 打开 App 时自动准备内置内核：
     * 未安装（新装）或版本落后于 App 内置版本（升级）时自动安装 / 升级，
     * 期间上报进度驱动启动按钮显示环形进度条；已就绪则保持按钮原样，无进度动画。
     */
    private fun prepareKernel(context: Context) {
        if (prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            // 只有真正发生安装 / 升级时 onProgress 才会被回调，据此区分
            // 「刚装完要转圈」与「本来就好好的，什么也不做」。
            var worked = false
            val result = KernelManager.ensureCurrent(context) { percent ->
                worked = true
                preparingProgress.value = percent
            }
            result.fold(
                onSuccess = {
                    if (worked) {
                        preparingProgress.value = 100
                    }
                    // 未安装新内核时也可能刚完成旧目录迁移，必须重读外置插件目录。
                    viewModelScope.launch(Dispatchers.IO) {
                        pluginCount.value = KernelManager.pluginCount(context)
                    }
                },
                onFailure = { error ->
                    preparingProgress.value = null
                    _error.value = HomeError.Runtime(
                        "内核安装失败：" + (error.message ?: error.javaClass.simpleName)
                    )
                },
            )
        }
    }

    /**
     * 取一次账户信息（`getMe`，头像在本地没有时会下载）。
     *
     * 只在授权状态走到 `authorizationStateReady` 后有意义：登录前 TDLib 没有当前用户，
     * `getMe` 会直接失败。头像下载**没有时限**，断网时会一直挂着等 TDLib 回，
     * 所以这里靠 [accountJob] 保证同一时刻只有一次在跑，不会越积越多。
     *
     * 拿到结果先落盘（[FuyuBotAccountStore]）：名称与头像路径下次冷启动直接可用，
     * 不用再干等一轮登录。写盘是毫秒级的 SharedPreferences 提交，放主线程也不碍事，
     * 但这里本来就在 viewModelScope 的协程里，顺手做掉即可。
     */
    fun refreshAccount() {
        if (accountJob?.isActive == true) return
        accountJob = viewModelScope.launch {
            val fresh = runtime.account() ?: return@launch
            FuyuBotAccountStore.save(fresh)
            account.value = fresh
        }
    }

    /**
     * 每秒拉一次内核记的 TDLib 授权 / 连接状态。
     *
     * 两个接口都是**内存快照**（不发 TDLib 请求、不阻塞），比之前「读日志尾巴 + 正则」
     * 轻得多；而且登录窗口里也能拿到状态，不依赖日志行。
     */
    private fun watchTdlibStatus() {
        if (statusJob?.isActive == true) return
        statusJob = viewModelScope.launch {
            while (true) {
                val wasReady = runtime.state.value.authState?.ready == true
                runtime.refreshTdlibStatus()
                // 首次进入 Ready 才有「当前用户」，这时取一次名字与头像。
                if (!wasReady && runtime.state.value.authState?.ready == true) refreshAccount()
                delay(AUTH_TICK_MS)
            }
        }
    }

    /** 把毫秒时长格式化成 `HH:MM:SS`，超过一天时小时数继续累加。 */
    private fun formatUptime(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** 首页点「启动」入口。 */
    fun onStart(context: Context) {
        if (startJob?.isActive == true) return
        // 内核已内置并自动准备；按钮在准备中会禁用，不会走到这里。
        // 这里不再检查内核是否安装，直接进入授权配置与启动编排。
        startJob = viewModelScope.launch {
            val repository = configFor(context)
            val configurationError = withContext(Dispatchers.IO) {
                repository.validationError()
            }
            if (configurationError != null) {
                _error.value = HomeError.Runtime(
                    "fuyubot.toml 配置错误：$configurationError"
                )
                return@launch
            }
            val authorized = withContext(Dispatchers.IO) { repository.isAuthorized }
            when {
                // 只有 api_id / api_hash 是硬性前提；bot_token 留空本来就表示走真人登录，
                // 不该因为没填 token 又把配置弹窗弹回来挡住启动。
                !authorized -> {
                    _configDialog.value = true
                }

                else -> doStart(context)
            }
        }
    }

    private suspend fun doStart(context: Context) {
        // 每次启动都从干净日志开始：内核是追加写 fuyubot.log 的，不主动清掉的话，
        // 上一次运行的报错会一直混在本次日志里，看上去像这次的错。
        withContext(Dispatchers.IO) { LogRepository.clear(context) }
        // 新的运行时会有新的提问 id，旧的那条不该再压制自动应答。
        autoAnsweredLoginMethodId = null
        // 启动前内核已经在跑时 nativeStart 会返回 BUSY，不会重新按磁盘加载插件：
        // 那种情况下「待重启」仍然成立，不能清标记。
        val wasRunning = runtime.isRunning
        runtime.start(context, transientBotToken).fold(
            onSuccess = {
                // 内核按磁盘现状重新加载插件后，之前的「待重启」标记随之失效。
                // 不管这次启动是首页「启动」还是插件页「重启」发起的，都走这里清掉。
                if (!wasRunning) {
                    runCatching { PluginStore.clearAllPendingRestart(context) }
                }
            },
            onFailure = { error ->
                _error.value = HomeError.Runtime(
                    error.message ?: error.javaClass.simpleName
                )
            },
        )
    }

    /** 保存配置并继续启动。 */
    fun saveConfig(context: Context, apiId: Int, apiHash: String, botToken: String) {
        val repository = configFor(context)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.writeAccount(apiId, apiHash, botToken)
            }
            if (saved.isFailure) {
                val error = saved.exceptionOrNull()
                _error.value = HomeError.Runtime(
                    "写入 fuyubot.toml 失败：${
                        error?.message ?: error?.javaClass?.simpleName ?: "未知错误"
                    }"
                )
                return@launch
            }
            transientBotToken = botToken.trim().takeIf { it.isNotEmpty() }
            _configDialog.value = false
            if (withContext(Dispatchers.IO) { repository.isAuthorized }) {
                startJob = viewModelScope.launch { doStart(context) }
            }
        }
    }

    fun dismissConfigDialog() {
        _configDialog.value = false
    }

    fun dismissError() {
        _error.value = null
    }

    /** 回答登录提问；失败（提问 id 已失效 / 内核已停）经错误弹窗告知用户。 */
    fun answer(id: Int, text: String?) {
        runtime.answer(id, text).onFailure { error ->
            _error.value = HomeError.Runtime(error.message ?: error.javaClass.simpleName)
        }
    }

    fun stop() {
        // nativeStop 内部会优雅收摊（最多等 8 秒），放到 IO 线程避免阻塞主线程。
        viewModelScope.launch(Dispatchers.IO) {
            val stopped = runtime.stop()
            stopped.onFailure { error ->
                // 收摊超时 / 内部错：内核可能卡在关闭的某一步，必须让用户看见。
                _error.value = HomeError.Runtime(error.message ?: error.javaClass.simpleName)
            }
            // 内核停稳后，把运行中标记的「待卸载」插件真正删掉。
            val context = appContext ?: return@launch
            runCatching { PluginStore.finalizePendingUninstall(context) }
            if (stopped.isSuccess) {
                // 内核已经停了，磁盘现状会在下次启动时整体加载，不存在「待重启」这回事。
                runCatching { PluginStore.clearAllPendingRestart(context) }
            }
        }
    }

    /**
     * 重启内核：先停（收摊），再重新启动。
     *
     * 供插件页在「增删 / 启停插件后需要重启生效」时调用。停与启之间留一点间隔，
     * 等 nativeStop 的优雅收摊真正走完，避免 start 撞上还在收尾的旧会话。
     *
     * 重启**不删除**「待卸载」的插件，但它发起卸载时已经被挪到停用位置，
     * 所以重启后内核不会再加载它；文件要等内核真正停止（首页「停止」）时才删除。
     */
    fun restartKernel() {
        if (restartJob?.isActive == true) return
        restartJob = viewModelScope.launch(Dispatchers.IO) {
            val context = appContext ?: return@launch
            if (runtime.isRunning) {
                runtime.stop().onFailure { error ->
                    // 停止失败照样继续重启，但要让用户看到卡在哪里（通常是一次收摊超时）。
                    _error.value = HomeError.Runtime(error.message ?: error.javaClass.simpleName)
                }
                // nativeStop 上限 8 秒；给它留足收尾时间再起。
                delay(1_200L)
            }
            if (withContext(Dispatchers.IO) { configFor(context).isAuthorized }) {
                // doStart 成功后自行清掉「待重启」标记（与首页直接启动同一套逻辑）。
                doStart(context)
            }
        }
    }

    private fun configFor(context: Context): FuyuBotConfigRepository =
        config ?: FuyuBotConfigRepository(context.applicationContext).also { config = it }

    private companion object {
        /** 未运行时的空转间隔：只为及时发现「开始运行」，无需每秒刷新。 */
        const val IDLE_TICK_MS = 2_000L

        /** 授权 / 连接状态的轮询间隔；两个接口都只是内存快照，代价很低。 */
        const val AUTH_TICK_MS = 1_000L

        /** 未运行时的运行时长占位。 */
        const val IDLE_UPTIME = "00:00:00"

        /** 内核「登录方式」提问的应答值：走手机号登录（本 App 不支持二维码）。 */
        const val LOGIN_METHOD_PHONE = "phone"
    }
}

/** 除「内核准备进度」外的首页状态聚合结果，供 [HomeViewModel.uiState] 二次合并。 */
private data class BaseHomeState(
    val isRunning: Boolean,
    val uptime: String,
    val pluginCount: Int?,
    /** 内核报的三个插件计数；还没拿到报告时为 null。 */
    val loadedPluginCount: Int?,
    val failedPluginCount: Int?,
    val skippedPluginCount: Int?,
    val authState: TdlibAuthState,
    val needsReLogin: Boolean,
    val connectionState: TdlibConnectionState,
    val tdlibDetail: String,
)

/** 启动流程中需要提示给用户的错误。 */
sealed interface HomeError {
    /** 运行时加载 / 启动失败，附带底层错误描述。 */
    data class Runtime(val message: String) : HomeError
}
