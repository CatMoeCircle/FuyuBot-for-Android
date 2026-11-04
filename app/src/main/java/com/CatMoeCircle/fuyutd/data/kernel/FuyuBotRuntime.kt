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
)

/**
 * libfuyubotjni.so 的进程内运行时封装。
 *
 * 负责：加载 `.so`、注册回调、把工作线程回调转发到 [StateFlow]。
 * 不声明任何 Android 组件 / 权限 / 唤醒锁，保活由上层（前台 Service）负责。
 */
class FuyuBotRuntime(
    private val config: FuyuBotConfigRepository,
) {

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
            _state.value = _state.value.copy(
                state = stateValue,
                stateDetail = detail,
                ask = null,
                qrLink = null,
                startedAtElapsedRealtime = startedAt,
            )
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
        instance.nativeRegister(callback)
        native = instance
    }

    /** 启动运行时；返回底层返回码。 */
    suspend fun start(context: android.content.Context): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val options = config.buildOptionsJson(KernelManager.kernelDir(context).absolutePath)
                    ?: error("缺少 api_id / api_hash")
                ensureLoaded(context).getOrThrow()
                val handle = native!!
                val code = handle.nativeStart(options)
                // 返回码只说「线程起来没有」；真正的原因在内核的 last_error 里，
                // 且它是线程局部的，必须就地读。
                if (code != Native.RESULT_OK) {
                    error(handle.lastErrorOrNull() ?: "nativeStart 返回 $code")
                }
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

    /** 回答一次登录提问。 */
    fun answer(id: Int, text: String?): Result<Int> = runCatching {
        native?.nativeAnswer(id, text) ?: 0
    }

    /**
     * 取当前账户信息（ABI v2 的 `nativeTdRequest` 发 `getMe`）。
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

    private companion object {
        /**
         * 交给内核的等响应预算。取内核允许的**上限** 600 秒：
         * 常规请求都是毫秒级应答，这里只是兜底，防止某次调用把工作线程挂死。
         */
        const val NATIVE_DEFAULT_TIMEOUT_MS = 600_000L

        /** 头像下载进度轮询间隔；只问状态，代价很低，不必更密。 */
        const val AVATAR_POLL_MS = 1_000L
    }
}

