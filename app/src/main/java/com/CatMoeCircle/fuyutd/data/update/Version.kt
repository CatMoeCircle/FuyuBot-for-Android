package com.CatMoeCircle.fuyutd.data.update

/**
 * 比较两个版本号字符串（如 `0.1.2` / `v0.1.2` / `1.0`）：返回 &gt;0 表示 [a] 更新。
 *
 * 只按数字段比较，`v` 前缀与预发布后缀（`-beta`）都被忽略；缺省段按 0 处理。
 */
fun compareVersion(a: String, b: String): Int {
    fun segments(value: String): List<Int> =
        value.removePrefix("v").removePrefix("V")
            .split('.', '-', '_', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    val left = segments(a)
    val right = segments(b)
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val diff = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
