package com.CatMoeCircle.fuyutd.ui.component.markdown

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.CatMoeCircle.fuyutd.ui.theme.isInDarkTheme
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * 极简 Markdown 渲染：用 commonmark 把 Markdown 转成 HTML，再交给 WebView 展示。
 *
 * WebView 采用「有界高度 + 内部滚动」：测量到真实内容高度后，在 [minHeight]~[maxHeight]
 * 之间自适应伸缩——短内容用紧凑高度，长内容撑到上限后由 WebView 内部滚动。这样只有 WebView
 * 一个滚动源，不会和外层容器抢手势，也不会挤压外部按钮。内容始终可见，不做淡入，避免加载
 * 时序导致内容不可见。
 *
 * 高度测量不能只做一次：`onPageFinished` 触发时文档通常还没排版完，`contentHeight` 会等于
 * 当前视口高度，长正文因此会被压成 [minHeight] 那一小块。改为在 [pollContentHeight] 里轮询
 * 到高度稳定再回报。
 *
 * @param maxHeight 内容撑开的上限，由调用方按可用空间动态给出。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = 480.dp,
) {
    val dark = isInDarkTheme()
    // 刻意不带 key：WebViewClient 只在 factory 里创建一次，若按 markdown 做 key，
    // 内容变化后 client 会写进旧的 state，新内容就永远停在 minHeight。
    var measuredHeight by remember { mutableStateOf<Dp?>(null) }
    // 正文变化时先回到 minHeight，等新内容测量完成再撑开。
    LaunchedEffect(markdown) { measuredHeight = null }

    val html = remember(markdown, dark) {
        val parser = Parser.builder()
            .extensions(
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.builder().requireTwoTildes(true).build(),
                    AutolinkExtension.create(),
                )
            )
            .build()
        val renderer = HtmlRenderer.builder()
            .extensions(
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.builder().requireTwoTildes(true).build(),
                    AutolinkExtension.create(),
                )
            )
            .escapeHtml(true)
            .build()
        val body = renderer.render(parser.parse(markdown))
        buildHtml(body, dark)
    }

    val upper = maxOf(minHeight, maxHeight)
    val height = (measuredHeight ?: minHeight).coerceIn(minHeight, upper)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.pollContentHeight { cssPx -> measuredHeight = cssPx.dp }
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}

/**
 * 轮询 `contentHeight` 直到连续两次采样一致（或超时）后回报最终值。
 *
 * `contentHeight` 是 CSS 像素、且与视口宽度有关的文档高度；`onPageFinished` 时它往往还是
 * 视口高度，排版与图片撑开发生在之后的若干帧里，所以必须多采几次。
 */
private fun WebView.pollContentHeight(onMeasured: (Int) -> Unit) {
    var attempt = 0
    var last = 0
    var stable = 0

    fun sample() {
        val height = contentHeight
        if (height > 0 && height == last) stable++ else stable = 0
        if (height > 0) last = height
        if (stable >= CONTENT_HEIGHT_STABLE_SAMPLES || ++attempt >= CONTENT_HEIGHT_MAX_ATTEMPTS) {
            if (last > 0) onMeasured(last)
            return
        }
        postDelayed({ sample() }, CONTENT_HEIGHT_POLL_MS)
    }

    sample()
}

/** 连续多少次采样相同才认为高度稳定。 */
private const val CONTENT_HEIGHT_STABLE_SAMPLES = 2

/** 采样总次数上限，约 [CONTENT_HEIGHT_MAX_ATTEMPTS] × [CONTENT_HEIGHT_POLL_MS]。 */
private const val CONTENT_HEIGHT_MAX_ATTEMPTS = 12

private const val CONTENT_HEIGHT_POLL_MS = 60L

private fun buildHtml(body: String, dark: Boolean): String {
    val bg = if (dark) "transparent" else "transparent"
    val fg = if (dark) "#e6e6e6" else "#1f1f1f"
    val muted = if (dark) "#9a9a9a" else "#6b6b6b"
    val codeBg = if (dark) "#2a2a2a" else "#f2f2f2"
    val border = if (dark) "#3a3a3a" else "#e0e0e0"
    val link = if (dark) "#7cb8ff" else "#0b57d0"

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root {
                color-scheme: ${if (dark) "dark" else "light"};
            }
            html, body {
                margin: 0;
                padding: 0;
                background: $bg;
                color: $fg;
                font-family: -apple-system, "Segoe UI", Roboto, "Noto Sans CJK SC", sans-serif;
                font-size: 14px;
                line-height: 1.6;
                word-wrap: break-word;
            }
            body { padding: 2px 4px; }
            h1, h2, h3, h4, h5, h6 { margin: 16px 0 8px; font-weight: 600; }
            h1 { font-size: 1.4em; border-bottom: 1px solid $border; padding-bottom: 6px; }
            h2 { font-size: 1.25em; border-bottom: 1px solid $border; padding-bottom: 4px; }
            p { margin: 8px 0; }
            a { color: $link; text-decoration: none; }
            code {
                background: $codeBg;
                padding: 1px 5px;
                border-radius: 4px;
                font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
                font-size: 0.9em;
            }
            pre {
                background: $codeBg;
                padding: 10px 12px;
                border-radius: 8px;
                overflow-x: auto;
            }
            pre code { background: transparent; padding: 0; }
            blockquote {
                margin: 8px 0;
                padding: 2px 12px;
                border-left: 3px solid $border;
                color: $muted;
            }
            table { border-collapse: collapse; margin: 8px 0; display: block; overflow-x: auto; }
            th, td { border: 1px solid $border; padding: 6px 10px; }
            th { background: $codeBg; font-weight: 600; }
            img { max-width: 100%; }
            ul, ol { padding-left: 24px; margin: 8px 0; }
            hr { border: none; border-top: 1px solid $border; margin: 16px 0; }
            del { color: $muted; }
        </style>
        </head>
        <body>
        <main class="markdown-body">$body</main>
        </body>
        </html>
    """.trimIndent()
}
