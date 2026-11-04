package com.CatMoeCircle.fuyutd.data.kernel

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/** 日志级别；`UNKNOWN` 用于解析不了的行（例如 panic 输出）。 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    UNKNOWN,
    ;

    /** 数值越大越严重，用于「最小级别」筛选。 */
    val severity: Int
        get() = when (this) {
            TRACE, UNKNOWN -> 0
            DEBUG -> 1
            INFO -> 2
            WARN -> 3
            ERROR -> 4
        }

    companion object {
        fun parse(token: String): LogLevel =
            entries.firstOrNull { it.name.equals(token, ignoreCase = true) } ?: UNKNOWN
    }
}

/** 一行日志。 */
data class LogEntry(
    /** 原始时间戳（ISO 8601），解析不到时为空串。 */
    val time: String,
    val level: LogLevel,
    /** 输出该日志的 Rust 模块路径，可能为空。 */
    val module: String,
    val message: String,
) {
    /** 只取 `HH:MM:SS` 用于展示。 */
    val displayTime: String
        get() = if (time.length >= 19) time.substring(11, 19) else time

    /** 单行日志的原始文本，点击复制时用。 */
    val copyText: String
        get() {
            val sb = StringBuilder()
            if (time.isNotEmpty()) sb.append(time).append(' ')
            sb.append(level.name).append(' ')
            if (module.isNotEmpty()) sb.append(module).append(": ")
            sb.append(message)
            return sb.toString()
        }
}

/**
 * 读取内核写在 `<root>/logs/fuyubot.log` 的运行日志。
 *
 * 内核用 tracing-subscriber 追加写该文件，应用进程无法收到回调，
 * 因此这里按「读文件尾部」的方式取最新内容。
 */
object LogRepository {

    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "fuyubot.log"

    /** 只读文件尾部这么多字节，避免日志变长后一次读爆内存。 */
    private const val MAX_READ_BYTES = 256 * 1024

    /** 最多保留的行数（展示用，再早的丢掉）。 */
    private const val MAX_ENTRIES = 2000

    /** 探测授权状态时只读这么点尾巴：状态行很近，没必要每秒扫 256KB。 */
    private const val AUTH_SCAN_BYTES = 64 * 1024

    fun logFile(context: Context): File =
        File(KernelManager.kernelDir(context), "$LOG_DIR/$LOG_FILE")

    /** 读取并解析日志；文件不存在时返回空列表。 */
    fun read(context: Context): List<LogEntry> {
        val file = logFile(context)
        if (!file.isFile) return emptyList()
        val text = readTail(file) ?: return emptyList()
        return parse(text).takeLast(MAX_ENTRIES)
    }

    /** 日志文件当前长度；不存在时返回 0，用于记住「本次启动从哪开始看」。 */
    fun length(context: Context): Long = logFile(context).takeIf { it.isFile }?.length() ?: 0L

    /**
     * 日志里最后一条授权状态的原始名字（如 `authorizationStateReady`）。
     *
     * 内核把 TDLib 的 `updateAuthorizationState` 打成一行 `授权状态：xxx`，
     * 这是**唯一**能拿到真实授权状态的渠道——JNI 只回调内核线程的起停。
     * [sinceOffset] 之前的内容不看，避免读到上一次运行留下的旧状态。
     */
    fun latestAuthState(context: Context, sinceOffset: Long): String? {
        val file = logFile(context)
        if (!file.isFile) return null
        val text = readTail(file, sinceOffset) ?: return null
        // 末尾那一段可能正在被写入（还没换行），状态名或许不完整，整段跳过。
        // 起点被截断的首行缺「授权状态：」前缀，本来就匹配不上，无需额外处理。
        val complete = if (text.endsWith('\n')) text else text.substringBeforeLast('\n', "")
        return AUTH_STATE_PATTERN.findAll(complete).lastOrNull()?.groupValues?.get(1)
    }

    /** 清空日志文件；失败返回 false。 */
    fun clear(context: Context): Boolean = runCatching {
        val file = logFile(context)
        if (!file.exists()) return@runCatching true
        RandomAccessFile(file, "rw").use { it.setLength(0) }
        true
    }.getOrDefault(false)

    private fun readTail(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val start = (length - MAX_READ_BYTES).coerceAtLeast(0)
            val bytes = ByteArray((length - start).toInt())
            raf.seek(start)
            raf.readFully(bytes)
            // 截断写入后文件头部可能留下 NUL 空洞，读的时候直接丢掉。
            String(bytes, Charsets.UTF_8)
                .replace("\u0000", "")
                .let { text ->
                    // 从中间开始读时第一行大概率是半截，丢掉。
                    if (start > 0) text.substringAfter('\n', "") else text
                }
        }
    }.getOrNull()

    /**
     * 读 [sinceOffset] 之后的尾部内容；用于只关心「本次启动新写了什么」的场景。
     *
     * [sinceOffset] 大于当前文件长度说明日志被清空 / 轮转过，按「从头读」处理，
     * 否则会一直停在文件末尾读不到新内容。
     */
    private fun readTail(file: File, sinceOffset: Long): String? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val floor = (length - AUTH_SCAN_BYTES).coerceAtLeast(0L)
            val start = if (sinceOffset in 0..length) maxOf(sinceOffset, floor) else floor
            val bytes = ByteArray((length - start).toInt())
            raf.seek(start)
            raf.readFully(bytes)
            // 起点落在行中间时首行是半截，但「授权状态：」前缀在前半段被截掉后
            // 就匹配不上了，所以这里不需要再丢一行。
            String(bytes, Charsets.UTF_8).replace("\u0000", "")
        }
    }.getOrNull()

    /**
     * 解析 tracing-subscriber 的默认输出格式：
     * `2026-11-02T19:32:11.123456Z  INFO fuyubot_android::run: message`
     *
     * 没有时间戳/级别的续行会拼到上一条的 message 后面，避免多行日志被拆碎。
     */
    private fun parse(text: String): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            // 内核关闭了 timer 时会输出 `<unknown time>`，这里两种都认。
            val match = LINE_PATTERN.find(line) ?: LEVEL_ONLY_PATTERN.find(line)
            if (match == null) {
                val last = entries.lastOrNull()
                if (last == null) {
                    entries += LogEntry("", LogLevel.UNKNOWN, "", line)
                } else {
                    entries[entries.lastIndex] = last.copy(message = last.message + "\n" + line)
                }
                continue
            }
            val (time, levelToken, rest) = match.destructured
            val module = MODULE_PATTERN.find(rest)
            entries += LogEntry(
                time = time,
                level = LogLevel.parse(levelToken),
                module = module?.groupValues?.get(1).orEmpty(),
                message = if (module != null) {
                    rest.substring(module.range.last + 1).trimStart()
                } else {
                    rest
                },
            )
        }
        return entries
    }

    private val LINE_PATTERN = Regex(
        """^(\d{4}-\d{2}-\d{2}T[0-9:.]+Z)\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(.*)$"""
    )

    /** 没有时间戳、只有级别前缀的行（内核 `log_level` 关掉 timer 时会出现）。 */
    private val LEVEL_ONLY_PATTERN = Regex(
        """^()\s*(TRACE|DEBUG|INFO|WARN|ERROR)\s+(.*)$"""
    )

    /** 形如 `fuyubot_android::run: 正文` 里的模块名部分。 */
    private val MODULE_PATTERN = Regex("""^([A-Za-z_][\w:]*):\s""")

    /**
     * 内核打的那行 `授权状态：authorizationStateWaitCode`。
     *
     * 只认 `authorizationState` 开头的整词，避免把正文里随便提到的名字当成状态。
     */
    private val AUTH_STATE_PATTERN = Regex("""授权状态：(authorizationState\w+)""")
}
