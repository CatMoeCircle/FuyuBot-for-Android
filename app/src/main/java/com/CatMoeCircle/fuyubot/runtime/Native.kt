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
 * 方法分三批：v1 的六个（内核 0.1.2 起就有）、ABI v2 的四个、ABI v3 的内存查询。
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

    // ---- ABI v2（只加不改，v1 的签名与语义不动） ----

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

    /** 发一条原始 td_api JSON 并阻塞等响应，返回响应 JSON。 */
    external fun nativeTdRequest(requestJson: String, timeoutMs: Long): String?

    // ---- ABI v3 ----

    /**
     * 进程 / TDLib / 各插件的内存占用 JSON，单位一律**字节**。
     *
     * 一次调用会全量解析 `/proc/self/smaps`（毫秒级），**别放进每秒刷新的循环**。
     * 结构见内核 `include/fuyubot_jni.h`。
     */
    external fun nativeMemory(): String

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
    }

    companion object {
        private const val TAG = "FuyuBotNative"

        /** 本 App 期望的内核 ABI；低于它就说明内核该更新了。 */
        const val ABI_VERSION = 3

        /** 返回码 `FUYUBOT_OK`。 */
        const val RESULT_OK = 0
    }
}
