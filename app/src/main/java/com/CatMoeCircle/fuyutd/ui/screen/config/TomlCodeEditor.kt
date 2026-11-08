package com.CatMoeCircle.fuyutd.ui.screen.config

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TomlEditorColors(
    val background: Color,
    val gutterBackground: Color,
    val divider: Color,
    val text: Color,
    val lineNumber: Color,
    val cursor: Color,
    val comment: Color,
    val section: Color,
    val key: Color,
    val string: Color,
    val value: Color,
    val matchBackground: Color,
    val activeMatchBackground: Color,
)

/**
 * 全屏 TOML 代码编辑器：左侧行号槽与代码共用一个纵向滚动容器，
 * 因此行号始终与正文对齐；代码区单独横向滚动，行号固定不动。
 */
@Composable
fun TomlCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    colors: TomlEditorColors,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    val lineHeight = 20.sp
    val verticalPadding = 10.dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    val textStyle = TextStyle(
        color = colors.text,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = lineHeight,
        lineHeightStyle = lineHeightStyle,
    )
    val lineCount = remember(value.text) {
        value.text.count { it == '\n' } + 1
    }
    val lineNumbers = remember(lineCount) {
        buildString {
            for (number in 1..lineCount) {
                if (number > 1) append('\n')
                append(number)
            }
        }
    }
    val lineNumberStyle = TextStyle(
        color = colors.lineNumber,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = lineHeight,
        lineHeightStyle = lineHeightStyle,
        textAlign = TextAlign.End,
    )
    // 用实际字体（含 fontScale / 中文 / Tab）测量最宽行，禁止长行软换行，
    // 否则正文会比逻辑行高，行号就会提前结束、和正文错位。
    val textWidth = remember(value.text, textStyle, textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString(value.text),
            style = textStyle,
            softWrap = false,
            constraints = Constraints(),
        ).size.width
    }
    // 行号槽按最大的行号实测宽度，字体放大时也不会把数字挤到换行。
    val numberWidth = remember(lineCount, lineNumberStyle, textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString(lineCount.toString()),
            style = lineNumberStyle,
            softWrap = false,
            constraints = Constraints(),
        ).size.width
    }
    val searchMatches = remember(value.text, searchQuery) {
        if (searchQuery.isEmpty()) emptyList() else findTomlMatches(value.text, searchQuery)
    }
    val activeSearchMatch = searchMatches.firstOrNull { match ->
        match.first == value.selection.min && match.last + 1 == value.selection.max
    }
    val highlight = remember(colors, searchMatches, activeSearchMatch) {
        TomlSyntaxTransformation(colors, searchMatches, activeSearchMatch)
    }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    val gutterWidth = with(density) { numberWidth.toDp() } + 20.dp
    val codeWidth = with(density) { textWidth.toDp() } + 32.dp
    val lineHeightDp = with(density) { lineHeight.toDp() }
    val contentHeight = lineHeightDp * lineCount + verticalPadding * 2

    // 选区变化（查找结果、跳行）时把目标行滚到可视区域，
    // 因为搜索框持有焦点时编辑器自身不会自动滚动。
    LaunchedEffect(value.selection, value.text, viewportHeight) {
        if (viewportHeight <= 0) return@LaunchedEffect
        val offset = value.selection.min.coerceIn(0, value.text.length)
        val line = value.text.take(offset).count { it == '\n' }
        val lineHeightPx = with(density) { lineHeightDp.toPx() }
        val paddingPx = with(density) { verticalPadding.toPx() }
        val lineTop = line * lineHeightPx + paddingPx
        val lineBottom = lineTop + lineHeightPx
        val viewportTop = verticalScroll.value.toFloat()
        if (lineTop < viewportTop || lineBottom > viewportTop + viewportHeight) {
            verticalScroll.scrollTo((lineTop - lineHeightPx).coerceAtLeast(0f).toInt())
        }
    }

    BoxWithConstraints(modifier = modifier.background(colors.background)) {
        val fieldHeight = if (constraints.hasBoundedHeight) maxOf(maxHeight, contentHeight) else contentHeight
        val fieldWidth = if (constraints.hasBoundedWidth) maxOf(codeWidth, maxWidth - gutterWidth) else codeWidth
        val codeViewportWidth = with(density) {
            (maxWidth - gutterWidth).coerceAtLeast(0.dp).toPx()
        }
        // 命中项在长行靠右时，同样横向滚到可见范围。
        LaunchedEffect(activeSearchMatch, value.text, codeViewportWidth) {
            val match = activeSearchMatch ?: return@LaunchedEffect
            if (codeViewportWidth <= 0f) return@LaunchedEffect
            val lineStart = value.text.lastIndexOf('\n', (match.first - 1).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
            val prefix = value.text.substring(lineStart, match.first.coerceIn(lineStart, value.text.length))
            val prefixWidth = textMeasurer.measure(
                text = AnnotatedString(prefix),
                style = textStyle,
                softWrap = false,
                constraints = Constraints(),
            ).size.width
            val matchEnd = (match.last + 1).coerceAtMost(value.text.length)
            val matchWidth = textMeasurer.measure(
                text = AnnotatedString(value.text.substring(match.first, matchEnd)),
                style = textStyle,
                softWrap = false,
                constraints = Constraints(),
            ).size.width
            val paddingPx = with(density) { 12.dp.toPx() }
            val matchStart = paddingPx + prefixWidth
            val matchStop = matchStart + matchWidth
            val visibleStart = horizontalScroll.value.toFloat()
            val visibleEnd = visibleStart + codeViewportWidth
            when {
                matchStart < visibleStart ->
                    horizontalScroll.scrollTo((matchStart - paddingPx).coerceAtLeast(0f).toInt())

                matchStop > visibleEnd ->
                    horizontalScroll.scrollTo(
                        (matchStart - codeViewportWidth * 0.25f).coerceAtLeast(0f).toInt()
                    )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val gutterEnd = gutterWidth.toPx()
                    drawRect(
                        color = colors.gutterBackground,
                        size = Size(gutterEnd, size.height),
                    )
                    drawLine(
                        color = colors.divider,
                        start = Offset(gutterEnd, 0f),
                        end = Offset(gutterEnd, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(verticalScroll),
        ) {
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .padding(vertical = verticalPadding),
            ) {
                // 行号必须作为多行文本一次性布局：单行 BasicText 会忽略 lineHeight，
                // 行距只有字体自然行高，行号就会越往下越对不上正文。
                BasicText(
                    text = lineNumbers,
                    style = lineNumberStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .width(fieldWidth)
                        .heightIn(min = fieldHeight)
                        .padding(horizontal = 12.dp, vertical = verticalPadding),
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.cursor),
                    visualTransformation = highlight,
                )
            }
        }
    }
}

private class TomlSyntaxTransformation(
    private val colors: TomlEditorColors,
    private val searchMatches: List<IntRange>,
    private val activeMatch: IntRange?,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        var lineStart = 0
        text.text.split('\n').forEach { line ->
            highlightLine(builder, lineStart, line)
            lineStart += line.length + 1
        }
        searchMatches.forEach { match ->
            builder.addStyle(
                SpanStyle(background = colors.matchBackground),
                match.first.coerceIn(0, text.length),
                (match.last + 1).coerceIn(0, text.length),
            )
        }
        activeMatch?.let { match ->
            builder.addStyle(
                SpanStyle(background = colors.activeMatchBackground),
                match.first.coerceIn(0, text.length),
                (match.last + 1).coerceIn(0, text.length),
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun highlightLine(builder: AnnotatedString.Builder, start: Int, line: String) {
        val trimmedStart = line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return
        if (line[trimmedStart] == '#') {
            builder.addStyle(
                SpanStyle(color = colors.comment),
                start + trimmedStart,
                start + line.length,
            )
            return
        }

        if (line[trimmedStart] == '[') {
            builder.addStyle(
                SpanStyle(color = colors.section),
                start + trimmedStart,
                start + line.length,
            )
            return
        }

        val equals = line.indexOf('=')
        if (equals > 0) {
            builder.addStyle(
                SpanStyle(color = colors.key),
                start + trimmedStart,
                start + equals,
            )
            val valueStart = line.indexOfFirst { it != ' ' && it != '\t' }
                .takeIf { it > equals }
                ?: (equals + 1)
            if (valueStart < line.length) {
                builder.addStyle(
                    SpanStyle(color = colors.value),
                    start + valueStart,
                    start + line.length,
                )
            }
        }

        val stringPattern = Regex(""""(?:\\.|[^"\\])*"|'[^']*'""")
        stringPattern.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = colors.string),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }

        val comment = line.indexOf('#').takeIf { index ->
            index >= 0 && line.take(index).count { it == '"' } % 2 == 0
        }
        if (comment != null) {
            builder.addStyle(
                SpanStyle(color = colors.comment),
                start + comment,
                start + line.length,
            )
        }
    }
}

/** 查找全部非重叠匹配；区间为闭区间，便于直接转成选区。 */
internal fun findTomlMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var start = text.indexOf(query)
    while (start >= 0) {
        matches += start until start + query.length
        start = text.indexOf(query, start + query.length)
    }
    return matches
}
