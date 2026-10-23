package com.xunyyds.kelaile.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val indent: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val index: Int, val indent: Int, val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String? = null,
)

private const val MAX_SOURCE_LENGTH = 40_000
private const val ELLIPSIS_MARKER = "…"
private const val MAX_INLINE_DEPTH = 6
private const val LINK_TAG = "URL"

fun parseMarkdown(source: String): List<MarkdownBlock> {
    if (source.isBlank()) return emptyList()
    val truncated = source.length > MAX_SOURCE_LENGTH
    val head = if (!truncated) source else {
        val cut = source.substring(0, MAX_SOURCE_LENGTH)
        val lastBreak = cut.lastIndexOf('\n')
        if (lastBreak > 0) cut.substring(0, lastBreak) else cut
    }
    val lines = head.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = ArrayList<MarkdownBlock>(16)
    val paragraph = StringBuilder()
    val quote = StringBuilder()
    val code = StringBuilder()
    var fenced = false

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += MarkdownBlock.Paragraph(paragraph.toString())
        paragraph.setLength(0)
    }

    fun flushQuote() {
        if (quote.isEmpty()) return
        blocks += MarkdownBlock.Quote(quote.toString())
        quote.setLength(0)
    }

    fun flushText() {
        flushParagraph()
        flushQuote()
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        val content = line.trimStart(' ', '\t')
        if (content.startsWith("```")) {
            if (fenced) {
                blocks += MarkdownBlock.Code(code.toString())
                code.setLength(0)
                fenced = false
            } else {
                flushText()
                fenced = true
            }
            continue
        }
        if (fenced) {
            if (code.isNotEmpty()) code.append('\n')
            code.append(line)
            continue
        }
        if (content.isEmpty()) {
            flushText()
            continue
        }
        if (isDivider(content)) {
            flushText()
            blocks += MarkdownBlock.Divider
            continue
        }
        val heading = headingOf(content)
        if (heading != null) {
            flushText()
            blocks += heading
            continue
        }
        if (content.startsWith(">")) {
            flushParagraph()
            val body = content.removePrefix(">").trimStart().trimEnd()
            if (quote.isNotEmpty()) quote.append(' ')
            quote.append(body)
            continue
        }
        val indent = if (leadingWidth(line) >= 2) 1 else 0
        if (isBullet(content)) {
            flushText()
            blocks += MarkdownBlock.BulletItem(indent, content.substring(2).trim())
            continue
        }
        val numbered = numberedOf(content)
        if (numbered != null) {
            flushText()
            blocks += MarkdownBlock.NumberedItem(numbered.first, indent, numbered.second)
            continue
        }
        flushQuote()
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(line.trim())
    }
    if (fenced && code.isNotEmpty()) blocks += MarkdownBlock.Code(code.toString())
    flushText()
    if (truncated) blocks += MarkdownBlock.Paragraph(ELLIPSIS_MARKER)
    return blocks
}

fun inlineSpans(text: String): List<MarkdownSpan> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<MarkdownSpan>(8)
    readInline(text, out, 0)
    return mergeSpans(out)
}

private fun readInline(text: String, out: MutableList<MarkdownSpan>, depth: Int) {
    if (text.isEmpty() || depth > MAX_INLINE_DEPTH) {
        if (text.isNotEmpty()) out += MarkdownSpan(text)
        return
    }
    val plain = StringBuilder()
    fun flush() {
        if (plain.isEmpty()) return
        out += MarkdownSpan(plain.toString())
        plain.setLength(0)
    }
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i + 1) {
                    flush()
                    out += MarkdownSpan(text.substring(i + 1, close), code = true)
                    i = close + 1
                } else {
                    plain.append(ch)
                    i++
                }
            }
            ch == '[' -> {
                val parsed = readLink(text, i)
                if (parsed == null) {
                    plain.append(ch)
                    i++
                } else {
                    flush()
                    val (label, target, next) = parsed
                    val visible = label.ifEmpty { target }
                    val nested = ArrayList<MarkdownSpan>(4)
                    readInline(visible, nested, depth + 1)
                    val safe = safeUrl(target)
                    nested.forEach { out += if (safe == null) it else it.copy(linkUrl = safe) }
                    i = next
                }
            }
            ch == '*' || ch == '_' -> {
                val paired = i + 1 < text.length && text[i + 1] == ch
                val marker = if (paired) "$ch$ch" else ch.toString()
                val from = i + marker.length
                val close = text.indexOf(marker, from)
                val inner = if (close > from) text.substring(from, close) else null
                if (inner != null && inner.isNotBlank() && !inner.first().isWhitespace() && !inner.last().isWhitespace()) {
                    flush()
                    val nested = ArrayList<MarkdownSpan>(4)
                    readInline(inner, nested, depth + 1)
                    nested.forEach { out += if (paired) it.copy(bold = true) else it.copy(italic = true) }
                    i = close + marker.length
                } else {
                    plain.append(ch)
                    i++
                }
            }
            else -> {
                plain.append(ch)
                i++
            }
        }
    }
    flush()
}

private fun readLink(text: String, start: Int): Triple<String, String, Int>? {
    val labelEnd = text.indexOf(']', start + 1)
    if (labelEnd < 0 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
    val targetEnd = text.indexOf(')', labelEnd + 2)
    if (targetEnd < 0) return null
    val label = text.substring(start + 1, labelEnd)
    val target = text.substring(labelEnd + 2, targetEnd).trim().substringBefore(' ').trim('<', '>')
    return Triple(label, target, targetEnd + 1)
}

private fun safeUrl(raw: String): String? {
    val url = raw.trim()
    if (url.length < 9) return null
    if (url.any { it.isWhitespace() || it.isISOControl() }) return null
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    if (lower.length <= if (lower.startsWith("https://")) 8 else 7) return null
    return url
}

private fun mergeSpans(spans: List<MarkdownSpan>): List<MarkdownSpan> {
    if (spans.isEmpty()) return emptyList()
    val out = ArrayList<MarkdownSpan>(spans.size)
    spans.forEach { span ->
        if (span.text.isEmpty()) return@forEach
        val last = out.lastOrNull()
        if (last != null && last.bold == span.bold && last.italic == span.italic && last.code == span.code && last.linkUrl == span.linkUrl) {
            out[out.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            out += span
        }
    }
    return out
}

private fun leadingWidth(line: String): Int {
    var width = 0
    var i = 0
    while (i < line.length) {
        when (line[i]) {
            ' ' -> width++
            '\t' -> width += 4
            else -> break
        }
        i++
    }
    return width
}

private fun isDivider(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.length < 3) return false
    val ch = trimmed[0]
    if (ch != '-' && ch != '*' && ch != '_') return false
    return trimmed.all { it == ch }
}

private fun isBullet(line: String): Boolean {
    if (line.length < 2) return false
    val marker = line[0]
    if (marker != '-' && marker != '*' && marker != '+') return false
    return line[1] == ' ' || line[1] == '\t'
}

private fun numberedOf(line: String): Pair<Int, String>? {
    var i = 0
    while (i < line.length && i < 9 && line[i].isDigit()) i++
    if (i == 0 || i >= line.length) return null
    if (line[i] != '.' && line[i] != ')') return null
    val rest = line.substring(i + 1)
    if (rest.isEmpty() || !rest[0].isWhitespace()) return null
    return (line.substring(0, i).toIntOrNull() ?: return null) to rest.trim()
}

private fun headingOf(line: String): MarkdownBlock.Heading? {
    var run = 0
    while (run < line.length && line[run] == '#') run++
    if (run !in 1..6) return null
    val rest = line.substring(run)
    if (rest.isNotEmpty() && !rest[0].isWhitespace()) return null
    return MarkdownBlock.Heading(minOf(run, 3), stripClosingHashes(rest.trim()))
}

private fun stripClosingHashes(text: String): String {
    var end = text.length
    while (end > 0 && text[end - 1] == '#') end--
    if (end == 0) return ""
    if (end < text.length && text[end - 1].isWhitespace()) return text.substring(0, end).trimEnd()
    return text
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block -> MarkdownBlockView(block) }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = inlineText(block.text),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleMedium
                2 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        is MarkdownBlock.Paragraph -> Text(
            text = inlineText(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        is MarkdownBlock.BulletItem -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = (block.indent * 16).dp),
        ) {
            Text(
                text = "•",
                modifier = Modifier.width(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = inlineText(block.text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownBlock.NumberedItem -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = (block.indent * 16).dp),
        ) {
            Text(
                text = "${block.index}.",
                modifier = Modifier.width(26.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = inlineText(block.text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        is MarkdownBlock.Code -> Text(
            text = block.text,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
        is MarkdownBlock.Quote -> Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = inlineText(block.text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MarkdownBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun inlineText(text: String): AnnotatedString {
    val scheme = MaterialTheme.colorScheme
    val linkStyle = remember(scheme.primary) {
        SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline)
    }
    val codeBackground = remember(scheme.onSurface) { scheme.onSurface.copy(alpha = 0.08f) }
    return remember(text, linkStyle, codeBackground) { inlineToAnnotated(text, linkStyle, codeBackground) }
}

private fun inlineToAnnotated(text: String, linkStyle: SpanStyle, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        inlineSpans(text).forEach { span ->
            val start = length
            append(span.text)
            val end = length
            addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) codeBackground else Color.Unspecified,
                ),
                start,
                end,
            )
            val url = span.linkUrl ?: return@forEach
            addStyle(linkStyle, start, end)
            addStringAnnotation(LINK_TAG, url, start, end)
            addLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle)), start, end)
        }
    }
