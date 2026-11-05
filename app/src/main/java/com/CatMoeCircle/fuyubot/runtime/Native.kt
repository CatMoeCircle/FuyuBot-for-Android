package com.catmoecircle.fuyubot.runtime

import android.util.Log

/**
 * libfuyubotjni.so 的 JNI 绑定。
 *
 * **包名 / 类名 / 方法名 / 签名必须与库里 `JNI_OnLoad` 里 RegisterNatives 注册的
 * 完全一致**，否则第一次调用就抛 `UnsatisfiedLinkError`。注册名是
 * `com/catmoecircle/fuyubot/runtime/Native`（已从 so 的字符串表里核对）。
 *
 * 绑定在运行期按方法名做，因此 R8 必须保留本类（见 proguard-rules.pro）。
 *
 * 库不随 APK 打包，而是安装到 `filesDir/fuyubot-kernel/`，所以这里用
 * [System.load] 绝对路径加载，而不是 `System.loadLibrary`。
 *
 * 方法共十个：生命周期 / 问答六个，加上插件列表、插件详情、TDLib JSON 请求与本线程
 * 最后一次错误——新版内核把这一整批都记作 ABI 1（见 `include/fuyubot_jni.h`）。
 * 内核版本可能比 APK 旧，用 [nativeVersion] 核对 [ABI_VERSION] 后再用新方法；
 * 绑定是逐个方法注册的，类里缺哪个只影响那一个。
 */
class Native(
    tdlibPath: String,
    mainLibraryPath: String,
) {
    init {
        // 崩溃根因排查用：记录「哪个线程、以什么顺序」加载内核库。
        // 崩溃栈显示崩在 pthread_key_clean_all（线程退出执行 TLS destructor），
        // 需要知道是哪些线程跑过这些库，以及加载是否只发生一次、永不卸载。
        Log.i(TAG, "load tdlib begin thread=${Thread.currentThread().name} path=$tdlibPath")
        System.load(tdlibPath)
        Log.i(TAG, "load tdlib done  thread=${Thread.currentThread().name} path=$tdlibPath")
        Log.i(TAG, "load main  begin thread=${Thread.currentThread().name} path=$mainLibraryPath")
        System.load(mainLibraryPath)
        Log.i(TAG, "load main  done  thread=${Thread.currentThread().name} path=$mainLibraryPath")
    }

    /** 运行时 ABI 版本，等于内核头文件里的 `FUYUBOT_JNI_ABI`。 */
    external fun nativeVersion(): Int

    /** 注册回调对象；参数类型在 JNI 层是 `Object`，Kotlin 声明为 [Any]。 */
    external fun nativeRegister(cb: Any): Int

    /** 启动运行时；`optionsJson` 是 UTF-8 的 options JSON（见 fuyubot_jni.h）。 */
    external fun nativeStart(optionsJson: String): Int

    /** 优雅停车，上限 8 秒。 */
    external fun nativeStop(): Int

    external fun nativeIsRunning(): Boolean

    /** 回答一次登录提问；`text = null` 表示取消。 */
    external fun nativeAnswer(id: Int, text: String?): Int

    // ---- 运行时查询 ----

    /**
     * 内核最后一次错误的文字说明；没有错就是空串。
     *
     * **线程局部**：只在同一个线程上、紧接着那次失败调用之后读才有意义。
     */
    external fun nativeLastError(): String

    /** 当前插件列表 JSON；运行时的启动线程没走完时是空表，不是错误。 */
    external fun nativePlugins(): String

    /** 单个插件的信息 JSON；没有这个插件或运行时没起来返回 null。 */
    external fun nativePluginInfo(name: String): String?

    /**
     * TDLib 授权状态 JSON：`{"known":…,"state":…,"ready":…,"needs_re_login":…}`。
     *
     * 登录窗口里也能查；没起来 / 还没收到 = `known:false`、`state` 为 null。
     */
    external fun nativeAuthState(): String

    /**
     * TDLib 连接状态 JSON：`{"known":…,"state":…,"ready":…}`。
     *
     * 只有 `connectionStateReady` 才算 ready，`Updating`（同步中）不算。
     */
    external fun nativeConnectionState(): String

    /** 发一条原始 td_api JSON 并阻塞等响应，返回响应 JSON。 */
    external fun nativeTdRequest(requestJson: String, timeoutMs: Long): String?

    /**
     * 回调对象：JNI 层按方法名 + 签名反射调用，不认具体接口类型，
     * 因此实现类放哪个包都行。回调发生在**工作线程**，不是主线程。
     */
    interface Callback {
        /** state: 0 STARTING 1 READY 2 STOPPING 3 STOPPED 4 ERROR */
        fun onState(state: Int, detail: String)

        /** kind: 0 bot token / 1 手机号 / 2 验证码 / 3 两步密码 / 4 登录方式 / 5 其它 */
        fun onAsk(id: Int, kind: Int, prompt: String)

        fun onQr(link: String)

        /**
         * 插件报告 JSON（与 `nativePlugins()` 同形）；内核在**登录之前**推一次。
         *
         * 可选回调：宿主不声明这个方法也不影响其它回调，只是拿不到这次推送。
         */
        fun onPlugins(reportJson: String)
    }

    companion object {
        private const val TAG = "FuyuBotNative"

        /**
         * 本 App 期望的内核 ABI 下限。
         *
         * 新版内核把「生命周期 + 运行时查询」这十个方法统一记作 ABI 1，因此这里是 1：
         * 老内核（曾报 3）与当前内核都能过，只有低于 1 的才提示更新。
         */
        const val ABI_VERSION = 1

        /** 返回码 `FUYUBOT_OK`。 */
        const val RESULT_OK = 0

        /** 返回码 `FUYUBOT_ARG`：参数错（JSON 非法、缺字段、root 不是绝对路径…）。 */
        const val RESULT_ARG = 1

        /** 返回码 `FUYUBOT_BUSY`：已经在跑（`nativeStart` 幂等，多半可以直接当成功）。 */
        const val RESULT_BUSY = 2

        /** 返回码 `FUYUBOT_TIMEOUT`：`nativeStop` 等收摊超时（内部上限 8 秒）。 */
        const val RESULT_TIMEOUT = 3

        /** 返回码 `FUYUBOT_PANIC`：内核内部 panic（已兜住，进程还在）。 */
        const val RESULT_PANIC = 4

        /** 返回码 `FUYUBOT_INTERNAL`：内部错（TDLib 未就绪、登录失败…），细节看 `nativeLastError()`。 */
        const val RESULT_INTERNAL = 5
    }
}
