package com.CatMoeCircle.fuyutd.data.kernel

import android.os.SystemClock
import com.CatMoeCircle.fuyutd.data.FuyuBotConfigRepository
import com.catmoecircle.fuyubot.runtime.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 运行时的生命周期状态，对应 `Native.Callback.onState` 的 state 值。 */
enum class FuyuBotState {
    STARTING,
    READY,
    STOPPING,
    STOPPED,
    ERROR,
    ;

    companion object {
        fun fromInt(value: Int): FuyuBotState = entries.getOrElse(value) { ERROR }
    }
}

/** 登录提问类型，对应 `Native.Callback.onAsk` 的 kind 值。 */
enum class AskKind {
    BOT_TOKEN,
    PHONE_NUMBER,
    VERIFICATION_CODE,
    TWO_STEP_PASSWORD,
    LOGIN_METHOD,
    OTHER,
    ;

    companion object {
        fun fromInt(value: Int): AskKind = entries.getOrElse(value) { OTHER }
    }
}

/** 一次待用户回答的登录提问。 */
data class FuyuBotAsk(
    val id: Int,
    val kind: AskKind,
    val prompt: String,
)

/**
 * 内核插件报告里的计数，取自 `fuyubot_plugins()` / `onPlugins` 的 JSON。
 *
 * - [scanned]：内核在插件目录里扫到的候选总数（= 交给内核的 + [skipped]）；
 * - [active]：已经加载并生效的插件；
 * - [failed]：进了内核但 apply 失败的插件（每条的原因在报告的 `reason` 里）；
 * - [skipped]：连内核都没进去的候选（打开动态库失败、脚本放错目录等）。
 *
 * [failed] 与 [skipped] 是两个不同的失败桶：前者进了内核但没起来，后者根本没进内核。
 */
data class FuyuBotPluginStats(
    val scanned: Int,
    val active: Int,
    val failed: Int,
    val skipped: Int,
)

/** 内核眼里一个插件的加载状态，对应报告里的 `state` 字段。 */
enum class KernelPluginState {
    ACTIVE,
    PENDING,
    ACTIVATING,
    FAILED,
    UNKNOWN,
    ;

    companion object {
        /** 认不出的新状态一律按 [UNKNOWN] 处理，免得内核加状态就把插件页弄崩。 */
        fun fromString(value: String): KernelPluginState = when (value.lowercase()) {
            "active" -> ACTIVE
            "pending" -> PENDING
            "activating" -> ACTIVATING
            "failed" -> FAILED
            else -> UNKNOWN
        }
    }
}

/**
 * 内核插件报告 `plugins[]` 里的一项：内核**真的**拿到并接管了这个插件。
 *
 * [state] 是内核给出的加载结果；[missing] 只在 pending 时有意义（还缺哪些能力），
 * [reason] 只在 failed 时有意义（apply 报的错）。这两项是「为什么没起来」的唯一来源。
 */
data class KernelPluginInfo(
    /** 插件名（点分服务名，例如 `fuyubot.start`；由 `plugin_init` 报出）。 */
    val name: String,
    /** 插件 manifest 里的版本；没写版本时为空串。 */
    val version: String,
    /** 插件编进去的 ABI 版本。 */
    val abiVersion: Int,
    val state: KernelPluginState,
    /** 硬依赖（`inject`）：少了就不加载。 */
    val inject: List<String>,
    /** 软依赖（`soft_inject`）：少了也能加载，只是相关功能不可用。 */
    val softInject: List<String>,
    /** 插件文件的绝对路径。 */
    val path: String,
    /** 插件文件名；内核没报时取 [path] 的最后一段。 */
    val file: String,
    /** 仅 `state = pending`：还缺哪些能力。 */
    val missing: List<String>,
    /** 仅 `state = failed`：apply 报的错。 */
    val reason: String,
)

/** 内核报告 `skipped_plugins[]` 里的一项：连内核都没进去的候选（打开动态库失败等）。 */
data class KernelSkippedPlugin(
    val path: String,
    val reason: String,
)

/**
 * 内核插件报告全文（`fuyubot_plugins()` 与 `onPlugins` 推送是同一个形状）。
 *
 * 顶层计数缺失时按数组内容兜底，与 [FuyuBotPluginStats] 的口径一致；
 * 内核没起来时是一份空表（计数全 0、两个数组为空），不是错误。
 */
data class KernelPluginReport(
    /** 交给内核的插件数（= [plugins] 数组长度）。 */
    val count: Int = 0,
    /** 目录里扫到的候选数（= [count] + [skipped]）。 */
    val scanned: Int = 0,
    val active: Int = 0,
    val pending: Int = 0,
    val activating: Int = 0,
    val failed: Int = 0,
    val unknown: Int = 0,
    /** 没进内核的候选数（= [skippedPlugins] 数组长度）。 */
    val skipped: Int = 0,
    /** 内核接管的插件（每种 state 都可能出现）。 */
    val plugins: List<KernelPluginInfo> = emptyList(),
    /** 内核在插件目录里扫到但没加载的候选。 */
    val skippedPlugins: List<KernelSkippedPlugin> = emptyList(),
)

/** TDLib 授权状态快照，取自 `nativeAuthState()`。 */
data class FuyuBotAuthState(
    /** 有没有收到过授权状态；false 时 [state] 为 null。 */
    val known: Boolean,
    /** 原始构造器名（`authorizationStateReady` 等）；没收到过为 null。 */
    val state: String?,
    /** 是不是 `authorizationStateReady`。 */
    val ready: Boolean,
    /**
     * 是不是「要用户再出一次凭据」。
     *
     * 登录窗口里它本来就是 true（正常步骤）；只有**已经 ready 之后**又变 true
     * 才是被下线 / 封禁——判断这一点要配合 [FuyuBotRuntimeState.everReady]。
     */
    val needsReLogin: Boolean,
)

/** TDLib 连接状态快照，取自 `nativeConnectionState()`。 */
data class FuyuBotConnectionState(
    val known: Boolean,
    /** 原始构造器名（`connectionStateReady` 等）；没收到过为 null。 */
    val state: String?,
    /** 只有 `connectionStateReady` 才是 true；`Updating`（同步中）不算。 */
    val ready: Boolean,
)

/**
 * 当前账户信息，取自 TDLib 的 `getMe`。
 *
 * 只保留首页要展示的那几项；头像给的是**本地文件绝对路径**，
 * 是否真的存在由 [avatarPath] 的取值保证（不存在就是 null）。
 */
data class FuyuBotAccount(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    /** 头像文件的本地绝对路径；没设头像或还没下载下来时为 null。 */
    val avatarPath: String?,
) {
    /** 优先「名 姓」；都没有就退回用户名，再退回 id。 */
    val displayName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { username.ifBlank { id.toString() } }
}

/** 对外暴露的运行状态快照。 */
data class FuyuBotRuntimeState(
    val state: FuyuBotState = FuyuBotState.STOPPED,
    val stateDetail: String = "",
    val ask: FuyuBotAsk? = null,
    val qrLink: String? = null,
    /**
     * 本次启动的时刻（[android.os.SystemClock.elapsedRealtime]）；
     * 未启动或已停止时为 null，用于计算运行时长。
     */
    val startedAtElapsedRealtime: Long? = null,
    /**
     * 内核报的插件计数；null 表示还没拿到报告（未启动 / 刚启动还没扫完）。
     *
     * 登录**之前**内核就会推一次（`onPlugins`），登录完成后再主动拉一次兜底。
     */
    val pluginStats: FuyuBotPluginStats? = null,
    /**
     * 内核报的插件报告全文（每条插件的加载结果、失败原因、被跳过的候选）；
     * null 表示还没拿到报告（未启动 / 刚启动还没扫完 / 已停止）。
     */
    val pluginReport: KernelPluginReport? = null,
    /** TDLib 授权状态；null = 还没拉过。 */
    val authState: FuyuBotAuthState? = null,
    /** TDLib 连接状态；null = 还没拉过。 */
    val connectionState: FuyuBotConnectionState? = null,
    /**
     * 本次运行里是否登录成功过。
     *
     * 用来区分「登录窗口里的 needs_re_login（正常步骤）」和「登录后被下线」。
     */
    val everReady: Boolean = false,
)

/**
 * libfuyubotjni.so 的进程内运行时封装。
 *
 * 负责：加载 `.so`、注册回调、把工作线程回调转发到 [StateFlow]。
 * 不声明任何 Android 组件 / 权限 / 唤醒锁，保活由上层（前台 Service）负责。
 */
class FuyuBotRuntime {

    private val _state = MutableStateFlow(FuyuBotRuntimeState())
    val state: StateFlow<FuyuBotRuntimeState> = _state.asStateFlow()

    private var native: Native? = null
    private val callback = object : Native.Callback {
        override fun onState(state: Int, detail: String) {
            val stateValue = FuyuBotState.fromInt(state)
            // 首次进入 STARTING / READY 时记下起点；停止或出错后清空，
            // 这样首页的运行时长与真实运行区间一致。
            val startedAt = when (stateValue) {
                FuyuBotState.STARTING, FuyuBotState.READY ->
                    _state.value.startedAtElapsedRealtime
                        ?: SystemClock.elapsedRealtime()
                FuyuBotState.STOPPING, FuyuBotState.STOPPED, FuyuBotState.ERROR -> null
            }
            // 授权 / 连接状态只在运行期间有意义；开始与结束时清掉，免得显示上一轮的。
            val keepTdlibStatus = stateValue == FuyuBotState.READY ||
                stateValue == FuyuBotState.STOPPING
            _state.value = _state.value.copy(
                state = stateValue,
                stateDetail = detail,
                ask = null,
                qrLink = null,
                startedAtElapsedRealtime = startedAt,
                // 只有真正跑起来时报告才算数；开始 / 停止都清掉，免得显示上一轮的数字。
                pluginStats = _state.value.pluginStats
                    .takeIf { stateValue == FuyuBotState.READY },
                pluginReport = _state.value.pluginReport
                    .takeIf { stateValue == FuyuBotState.READY },
                authState = _state.value.authState.takeIf { keepTdlibStatus },
                connectionState = _state.value.connectionState.takeIf { keepTdlibStatus },
                everReady = if (stateValue == FuyuBotState.STARTING) {
                    false
                } else {
                    _state.value.everReady
                },
            )
            // 登录完成后再主动拉一次：onPlugins 是可选回调，宿主没实现时靠这里兜底。
            if (stateValue == FuyuBotState.READY) refreshPluginStats()
        }

        override fun onPlugins(reportJson: String) {
            publishPluginStats(reportJson)
        }

        override fun onAsk(id: Int, kind: Int, prompt: String) {
            _state.value = _state.value.copy(
                ask = FuyuBotAsk(id, AskKind.fromInt(kind), prompt),
                qrLink = null,
            )
        }

        override fun onQr(link: String) {
            _state.value = _state.value.copy(qrLink = link, ask = null)
        }
    }

    /**
     * 拉一次 TDLib 的授权 / 连接状态快照。
     *
     * 两个接口都只是一次 JSON 序列化、不发 TDLib 请求，可以在任意线程（含主线程）调；
     * 内核还没加载时直接返回，什么都不改。解析失败时保留上一次的值。
     */
    fun refreshTdlibStatus() {
        val handle = native ?: return
        val auth = runCatching { handle.nativeAuthState() }.getOrNull()?.let(::parseAuthState)
        val connection = runCatching { handle.nativeConnectionState() }.getOrNull()
            ?.let(::parseConnectionState)
        if (auth == null && connection == null) return
        _state.value = _state.value.copy(
            authState = auth ?: _state.value.authState,
            connectionState = connection ?: _state.value.connectionState,
            everReady = _state.value.everReady || auth?.ready == true,
        )
    }

    /**
     * 主动拉一次插件报告。
     *
     * `nativePlugins()` 只是一次 JSON 序列化，不阻塞，可以在回调线程上调；
     * 内核没起来时拿到的是空表（三个计数都是 0），照常写进状态。
     */
    private fun refreshPluginStats() {
        val handle = native ?: return
        runCatching { handle.nativePlugins() }.getOrNull()?.let(::publishPluginStats)
    }

    /**
     * 发布一次插件报告：首页要的计数与插件页要的完整报告一起写进状态。
     *
     * `onPlugins` 推送与启动后的主动拉取都走这里，两处口径一致。
     */
    private fun publishPluginStats(reportJson: String) {
        val stats = parsePluginStats(reportJson) ?: return
        val report = parsePluginReport(reportJson)
        _state.value = _state.value.copy(pluginStats = stats, pluginReport = report)
    }

    val isRunning: Boolean
        get() = native?.nativeIsRunning() == true

    /**
     * 内核写的最后一次错误说明。
     *
     * **线程局部**：必须和那次失败调用在同一个线程上、紧接着读，否则拿到的是空串。
     * 因此只在该线程自己「刚失败」时调用，别存下来跨线程用。
     */
    private fun Native.lastErrorOrNull(): String? =
        runCatching { nativeLastError() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 确保 `.so` 已加载并完成回调注册；重复调用是安全的。 */
    fun ensureLoaded(context: android.content.Context): Result<Unit> = runCatching {
        if (native != null) return@runCatching
        val tdlib = KernelManager.tdlibPath(context)
            ?: error("缺少 libtdjson.so，请先安装内核")
        val main = KernelManager.mainLibraryPath(context)
            ?: error("缺少 libfuyubotjni.so，请先安装内核")
        val instance = Native(tdlibPath = tdlib, mainLibraryPath = main)
        val abi = instance.nativeVersion()
        require(abi >= Native.ABI_VERSION) {
            "内核 ABI 版本过旧（$abi，需要 ${Native.ABI_VERSION}），请更新内核"
        }
        val registered = instance.nativeRegister(callback)
        // 回调没注册上就收不到 onState / onAsk，运行起来也不受控，必须当失败。
        require(registered == Native.RESULT_OK) {
            "nativeRegister 返回 $registered" +
                (instance.lastErrorOrNull()?.let { "：$it" } ?: "")
        }
        native = instance
    }

    /**
     * 用内核模板建 / 改 `fuyubot.toml`；不要求运行时在跑，也不影响正在跑的实例。
     *
     * 失败时异常里带内核的 `last_error()`；旧内核没有这个方法时会抛
     * `UnsatisfiedLinkError`，调用方据此回退到 App 自带的模板。
     */
    fun initConfig(json: String): Result<Int> = runCatching {
        val handle = native ?: error("内核未加载")
        val code = handle.nativeInitConfig(json)
        if (code != Native.RESULT_OK) {
            error(handle.lastErrorOrNull() ?: "nativeInitConfig 返回 $code")
        }
        code
    }

    /** 启动运行时；返回底层返回码。 */
    suspend fun start(
        context: android.content.Context,
        transientBotToken: String? = null,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 数据、日志和用户插件放在应用专属外置目录；内核只从私有只读副本加载插件。
                KernelManager.prepareRuntime(context).getOrThrow()
                val config = FuyuBotConfigRepository(context)
                    .buildRuntimeConfig(
                        paths = KernelManager.paths(context),
                        transientBotToken = transientBotToken,
                    )
                    ?: error("缺少 api_id / api_hash")
                ensureLoaded(context).getOrThrow()
                val handle = native!!
                if (!handle.nativeIsRunning()) {
                    KernelManager.syncRuntimePlugins(context, config.pluginDir).getOrThrow()
                }
                val code = handle.nativeStart(config.optionsJson)
                // 返回码只说「线程起来没有」；真正的原因在内核的 last_error 里，
                // 且它是线程局部的，必须就地读。BUSY 按成功处理：内核 start 是幂等的。
                if (code != Native.RESULT_OK && code != Native.RESULT_BUSY) {
                    error(handle.lastErrorOrNull() ?: "nativeStart 返回 $code")
                }
                // 启动成功（含 BUSY）后拉起前台保活服务：常驻通知 + 唤醒锁兜住进程。
                KernelService.start(context)
                code
            }
        }

    /** 停止运行时；返回底层返回码。 */
    fun stop(): Result<Int> = runCatching {
        val handle = native ?: return@runCatching 0
        val code = handle.nativeStop()
        // 兜底：底层若没回调 STOPPED（例如还没真正启动），也要清掉计时起点。
        if (!isRunning) {
            _state.value = _state.value.copy(startedAtElapsedRealtime = null)
        }
        if (code != Native.RESULT_OK) {
            // v1 只区分「成功 / 超时 / 内部错」；原因在内核的 last_error 里，就地读。
            error(handle.lastErrorOrNull() ?: "nativeStop 返回 $code")
        }
        code
    }

    /** 回答一次登录提问；返回码非 OK（如提问 id 已失效）时异常里带上内核的 last_error。 */
    fun answer(id: Int, text: String?): Result<Int> = runCatching {
        val handle = native ?: return@runCatching 0
        val code = handle.nativeAnswer(id, text)
        if (code != Native.RESULT_OK) {
            error(handle.lastErrorOrNull() ?: "nativeAnswer 返回 $code")
        }
        code
    }

    /**
     * 取当前账户信息（`nativeTdRequest` 发 `getMe`）。
     *
     * 头像按「有路径就用路径、没路径就让 TDLib 下载」处理，见 [resolveAvatarPath]。
     * 未登录 / 内核没起来 / 请求失败都返回 null，调用方按「未知」处理即可，
     * 界面不应因此报错。**头像下载没有总时限**：断网时会一直挂着，网络恢复后
     * TDLib 自己下完就返回，因此只能在协程里调、且不要去 await 它。
     */
    suspend fun account(): FuyuBotAccount? = withContext(Dispatchers.IO) {
        runCatching {
            val handle = native ?: return@runCatching null
            val me = handle.tdRequest("""{"@type":"getMe"}""")
                ?: return@runCatching null
            val photo = me.optJSONObject("profile_photo")
            // 新版 TDLib 把用户名收进了 usernames 对象，`username` 字段已不存在；
            // 老字段仍在时优先用它，否则取 usernames 里的第一个活跃用户名。
            val username = me.optString("username").ifBlank {
                me.optJSONObject("usernames")
                    ?.optJSONArray("active_usernames")
                    ?.optString(0)
                    .orEmpty()
            }
            FuyuBotAccount(
                id = me.optLong("id"),
                firstName = me.optString("first_name"),
                lastName = me.optString("last_name"),
                username = username,
                avatarPath = handle.resolveAvatarPath(photo),
            )
        }.getOrNull()
    }

    /** 发一条 td_api 请求；失败或 TDLib 回错误对象时返回 null。 */
    private fun Native.tdRequest(
        requestJson: String,
        timeoutMs: Long = NATIVE_DEFAULT_TIMEOUT_MS,
    ): JSONObject? {
        val raw = nativeTdRequest(requestJson, timeoutMs) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return json.takeIf { it.optString("@type") != "error" }
    }

    /**
     * 头像文件在本地磁盘上的绝对路径；没有头像返回 null。
     *
     * 先看 `big`（640×640）；没有就退回 `small`。本地已有**完整**副本时直接用它的
     * `local.path`，否则发一次 `downloadFile` 后**一直等到下完**。
     *
     * 这里刻意不用 `synchronous = true`：那条请求要阻塞到下载结束才应答，
     * 而 `nativeTdRequest` 单次调用最长只能等 600 秒，断网时会被钳住并失败。
     * 改成异步发起 + 轮询 `getFile`：每轮只问「下到哪了」，网络恢复 TDLib 会自己续上，
     * 所以等待没有总时限，也不占着一次长请求。
     */
    private suspend fun Native.resolveAvatarPath(photo: JSONObject?): String? {
        val file = photo?.optJSONObject("big")
            ?: photo?.optJSONObject("small")
            ?: return null
        file.completedLocalPath()?.let { return it }
        val fileId = file.optInt("id", 0)
        if (fileId == 0) return null
        // 拉起下载；返回值里的 file 可能还没下完，后续以 getFile 的结果为准。
        val started = tdRequest(
            """{"@type":"downloadFile","file_id":$fileId,"priority":1,""" +
                """"offset":0,"limit":0,"synchronous":false}""",
        )
        started?.completedLocalPath()?.let { return it }
        while (true) {
            // 每次轮询都是一次新的短请求，不会触发 600 秒上限。
            val current = tdRequest("""{"@type":"getFile","file_id":$fileId}""")
                ?: return null
            current.completedLocalPath()?.let { return it }
            // 内核被停掉 / TDLib 换会话时这次下载不会再继续，别空转到天荒地老。
            if (!nativeIsRunning()) return null
            delay(AVATAR_POLL_MS)
        }
    }

    /** `file.local.path`，仅当这份本地副本确实下完了才算数。 */
    private fun JSONObject.completedLocalPath(): String? {
        val local = optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed")) return null
        return local.optString("path").takeIf { it.isNotBlank() }
    }

    companion object {
        /** 进程级实例；用 [instance] 取，别直接碰。 */
        @Volatile
        private var shared: FuyuBotRuntime? = null

        /**
         * 取进程级运行时。
         *
         * 放在伴生对象里而不是各 ViewModel 自己 new：前台 [KernelService] 与所有
         * [HomeViewModel] 实例（Activity 重建会换新实例）必须共用同一份运行时，
         * 否则重建后 [state] 归零、回调也接不到。
         *
         * 配置在每次启动时按当前 Context 从 `fuyubot.toml` 读取，因此实例本身无状态。
         */
        fun instance(): FuyuBotRuntime =
            shared ?: synchronized(this) {
                shared ?: FuyuBotRuntime().also { shared = it }
            }

        /**
         * 交给内核的等响应预算。取内核允许的**上限** 600 秒：
         * 常规请求都是毫秒级应答，这里只是兜底，防止某次调用把工作线程挂死。
         */
        private const val NATIVE_DEFAULT_TIMEOUT_MS = 600_000L

        /** 头像下载进度轮询间隔；只问状态，代价很低，不必更密。 */
        private const val AVATAR_POLL_MS = 1_000L
    }
}

/**
 * 从插件报告 JSON 里取首页要用的几个计数。
 *
 * 报告形状见内核 `include/fuyubot_jni.h`：顶层有 `scanned` / `active` / `failed` / `skipped`，
 * 另有 `plugins[]` 与 `skipped_plugins[]`。顶层计数缺失时按数组内容兜底，
 * 这样即使换回只有 `count` + `plugins[]` 的旧内核，也能算出同样的几个数。
 */
private fun parsePluginStats(json: String): FuyuBotPluginStats? = runCatching {
    val root = JSONObject(json)
    val plugins = root.optJSONArray("plugins")

    fun countInState(state: String): Int {
        if (plugins == null) return 0
        var count = 0
        for (index in 0 until plugins.length()) {
            if (plugins.optJSONObject(index)?.optString("state") == state) count++
        }
        return count
    }

    val skippedPlugins = root.optJSONArray("skipped_plugins")
    val skippedCount = skippedPlugins?.length() ?: 0

    FuyuBotPluginStats(
        // 顶层 scanned 缺失时按「交给内核的 + 跳过的」算，与头文件里的定义一致。
        scanned = root.optInt("scanned", (plugins?.length() ?: 0) + skippedCount),
        active = root.optInt("active", countInState("active")),
        failed = root.optInt("failed", countInState("failed")),
        skipped = root.optInt("skipped", skippedCount),
    )
}.getOrNull()

/**
 * 从插件报告 JSON 里取完整报告：每条插件的加载结果 / 缺的能力 / 失败原因 + 被跳过的候选。
 *
 * 形状见内核 `include/fuyubot_jni.h`。顶层计数缺失时按数组内容兜底（与 [parsePluginStats] 同口径），
 * 单个字段缺失按空值处理，整份 JSON 坏了返回 null（调用方保留上一次的报告）。
 */
private fun parsePluginReport(json: String): KernelPluginReport? = runCatching {
    val root = JSONObject(json)

    fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }

    val pluginsJson = root.optJSONArray("plugins")
    val plugins = buildList {
        if (pluginsJson != null) {
            for (index in 0 until pluginsJson.length()) {
                val item = pluginsJson.optJSONObject(index) ?: continue
                val path = item.optString("path")
                add(
                    KernelPluginInfo(
                        name = item.optString("name"),
                        version = item.optString("version"),
                        abiVersion = item.optInt("abi_version"),
                        state = KernelPluginState.fromString(item.optString("state")),
                        inject = stringList(item.optJSONArray("inject")),
                        softInject = stringList(item.optJSONArray("soft_inject")),
                        path = path,
                        // 旧内核可能不报 file；用路径最后一段兜底，界面与注册表匹配都要用。
                        file = item.optString("file").ifBlank { path.substringAfterLast('/') },
                        missing = stringList(item.optJSONArray("missing")),
                        reason = item.optString("reason"),
                    )
                )
            }
        }
    }

    val skippedJson = root.optJSONArray("skipped_plugins")
    val skippedPlugins = buildList {
        if (skippedJson != null) {
            for (index in 0 until skippedJson.length()) {
                val item = skippedJson.optJSONObject(index) ?: continue
                add(
                    KernelSkippedPlugin(
                        path = item.optString("path"),
                        reason = item.optString("reason"),
                    )
                )
            }
        }
    }

    KernelPluginReport(
        count = root.optInt("count", plugins.size),
        scanned = root.optInt("scanned", plugins.size + skippedPlugins.size),
        active = root.optInt("active", plugins.count { it.state == KernelPluginState.ACTIVE }),
        pending = root.optInt("pending", plugins.count { it.state == KernelPluginState.PENDING }),
        activating = root.optInt(
            "activating",
            plugins.count { it.state == KernelPluginState.ACTIVATING },
        ),
        failed = root.optInt("failed", plugins.count { it.state == KernelPluginState.FAILED }),
        unknown = root.optInt("unknown", plugins.count { it.state == KernelPluginState.UNKNOWN }),
        skipped = root.optInt("skipped", skippedPlugins.size),
        plugins = plugins,
        skippedPlugins = skippedPlugins,
    )
}.getOrNull()

/** 解析 `nativeAuthState()` 的 JSON；解析不了返回 null（调用方保留上一次的值）。 */
private fun parseAuthState(json: String): FuyuBotAuthState? = runCatching {
    val root = JSONObject(json)
    FuyuBotAuthState(
        known = root.optBoolean("known"),
        state = root.optString("state").takeIf { it.isNotBlank() },
        ready = root.optBoolean("ready"),
        needsReLogin = root.optBoolean("needs_re_login"),
    )
}.getOrNull()

/** 解析 `nativeConnectionState()` 的 JSON；解析不了返回 null（调用方保留上一次的值）。 */
private fun parseConnectionState(json: String): FuyuBotConnectionState? = runCatching {
    val root = JSONObject(json)
    FuyuBotConnectionState(
        known = root.optBoolean("known"),
        state = root.optString("state").takeIf { it.isNotBlank() },
        ready = root.optBoolean("ready"),
    )
}.getOrNull()
