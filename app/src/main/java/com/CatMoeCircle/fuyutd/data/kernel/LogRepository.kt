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
 * 读取内核写在 `Android/data/<包名>/files/logs/fuyubot.log` 的运行日志。
 *
 * 日志目录固定在内核安装目录之外（见 [KernelManager.paths]），内核升级不会清掉它。
 * 内核用 tracing-subscriber 追加写该文件，应用进程无法收到回调，
 * 因此这里按「读文件尾部」的方式取最新内容。
 */
object LogRepository {

    private const val LOG_FILE = "fuyubot.log"

    private const val RUNTIME_DIR = ".fuyubot"
    private const val RUNTIME_LEVEL_FILE = "log-level"

    /** 只读文件尾部这么多字节，避免日志变长后一次读爆内存。 */
    private const val MAX_READ_BYTES = 256 * 1024

    /** 最多保留的行数（展示用，再早的丢掉）。 */
    private const val MAX_ENTRIES = 2000

    fun logFile(context: Context): File = File(KernelManager.logDir(context), LOG_FILE)

    /**
     * 运行期插件日志级别文件：`<root>/.fuyubot/log-level`。
     *
     * 内核每 500ms 看一次，改完不用重启；只控制插件日志，不影响宿主与 TDLib。
     * 这里直接写用户可访问的存储根目录，和内核经 `root` 符号链接看到的是同一个文件。
     */
    fun runtimeLevelFile(context: Context): File =
        File(KernelManager.storageDir(context), "$RUNTIME_DIR/$RUNTIME_LEVEL_FILE")

    /** 读当前运行期级别；文件不存在或内容不可识别返回 null。 */
    fun readRuntimeLevel(context: Context): LogLevel? = runCatching {
        val file = runtimeLevelFile(context)
        if (!file.isFile) return@runCatching null
        LogLevel.parse(file.readText().trim()).takeIf { it != LogLevel.UNKNOWN }
    }.getOrNull()

    /** 写运行期级别；下一轮 500ms 内生效。 */
    fun writeRuntimeLevel(context: Context, level: LogLevel): Result<Unit> = runCatching {
        require(level != LogLevel.UNKNOWN) { "未知日志级别" }
        val file = runtimeLevelFile(context)
        file.parentFile?.mkdirs()
        file.writeText(level.name.lowercase() + "\n")
    }

    /** 读取并解析日志；文件不存在时返回空列表。 */
    fun read(context: Context): List<LogEntry> {
        val file = logFile(context)
        if (!file.isFile) return emptyList()
        val text = readTail(file) ?: return emptyList()
        return parse(text).takeLast(MAX_ENTRIES)
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
}
