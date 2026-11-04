package com.CatMoeCircle.fuyutd.ui.screen.home

/**
 * TDLib 的授权状态，**直接取自内核日志里的「授权状态：xxx」一行**。
 *
 * 不再由运行时生命周期推导：`onState` 报的是内核线程的起停，不代表 TDLib 登没登上。
 * 名字与 TDLib 的 `authorizationState*` 一一对应（见内核 `fuyubot-tdlib/src/authorize.rs`）。
 */
enum class TdlibAuthState(val rawName: String?) {
    /** 本次启动还没从日志里读到任何授权状态。 */
    NOT_REPORTED(null),

    /** 初始化：TDLib 在准备数据库 / 参数，还没开始问用户。 */
    WAIT_TDLIB_PARAMETERS("authorizationStateWaitTdlibParameters"),

    /** 等手机号——真人登录的第一步，由 [com.CatMoeCircle.fuyutd.data.kernel.AskKind.PHONE_NUMBER] 提问。 */
    WAIT_PHONE_NUMBER("authorizationStateWaitPhoneNumber"),

    /** 等用户在**另一台设备**上确认扫码登录；本版不支持扫码，只展示。 */
    WAIT_OTHER_DEVICE_CONFIRMATION("authorizationStateWaitOtherDeviceConfirmation"),

    /** 等登录验证码（短信 / 来电 / Telegram 内消息）。 */
    WAIT_CODE("authorizationStateWaitCode"),

    /** 等两步验证（二级密码）。 */
    WAIT_PASSWORD("authorizationStateWaitPassword"),

    /** 这个号码还没注册。 */
    WAIT_REGISTRATION("authorizationStateWaitRegistration"),

    /** 邮箱登录流程。 */
    WAIT_EMAIL_ADDRESS("authorizationStateWaitEmailAddress"),

    /** 邮箱验证码。 */
    WAIT_EMAIL_CODE("authorizationStateWaitEmailCode"),

    /** 需要 premium 才能继续。 */
    WAIT_PREMIUM_PURCHASE("authorizationStateWaitPremiumPurchase"),

    /** 已获得授权，登录完成，可以用了。 */
    READY("authorizationStateReady"),
    LOGGING_OUT("authorizationStateLoggingOut"),
    CLOSING("authorizationStateClosing"),
    CLOSED("authorizationStateClosed"),

    /** 内核报了，但本 App 不认识（多半是 TDLib 比这里新）。 */
    UNKNOWN(null),
    ;

    /**
     * 本 App 暂不支持、只能提示用户换条路的状态：目前只有扫码登录。
     *
     * 内核支持扫码（`requestQrCodeAuthentication`），但本 App 不做二维码界面，
     * 所以「登录方式」提问会被自动答成手机号；真走到这个状态说明是别处发起的扫码会话，
     * 用户在这里输入什么都不会被接受。
     */
    val isUnsupported: Boolean
        get() = this == WAIT_OTHER_DEVICE_CONFIRMATION

    companion object {
        /** 按日志里的原始名字反查；认不出返回 [UNKNOWN]。 */
        fun fromRaw(name: String): TdlibAuthState =
            entries.firstOrNull { it.rawName == name } ?: UNKNOWN
    }
}

/**
 * 首页展示的数据。
 *
 * 运行时长、插件数量、授权状态已接入真实数据源，`null` 表示「尚未采集」，
 * 界面上会显示占位符而不是伪造的数值。
 */
data class HomeUiState(
    val isRunning: Boolean = false,
    /**
     * 打开 App 时内置内核的自动准备进度（0..100）。
     * 非 null 表示正在安装 / 升级内核，此时启动按钮显示为环形进度条。
     */
    val preparingProgress: Int? = null,
    /** 卡片上显示的名称；为 null 表示未知。 */
    val name: String? = null,
    /** 头像图片的本地绝对路径；为 null 表示未登录 / 没头像 / 还没下载下来。 */
    val avatarPath: String? = null,
    /** 形如 `01:23:45`；未运行时为 `00:00:00`。 */
    val uptime: String = "00:00:00",
    /** 本地已安装的插件数量（`plugins/` 下的 so）；为 null 表示内核未安装。 */
    val pluginCount: Int? = null,
    /** TDLib 当前授权状态，来自内核日志。 */
    val authState: TdlibAuthState = TdlibAuthState.NOT_REPORTED,
    /** 日志里原样的 `authorizationState*` 名字；未上报时为空串。 */
    val authStateRaw: String = "",
    /** 内核回调附带的状态描述，可能为空。 */
    val tdlibDetail: String = "",
    /** 加载成功的插件数；内核尚未开放接口，暂为空（界面显示占位符）。 */
    val loadedPluginCount: Int? = null,
    /** 加载失败的插件数；内核尚未开放接口，暂为空（界面显示占位符）。 */
    val failedPluginCount: Int? = null,
)
