package com.xunyyds.kelaile.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderTest {

    private fun joined(text: String) = inlineSpans(text).joinToString("") { it.text }

    @Test
    fun headingsClampDeeperLevels() {
        val blocks = parseMarkdown("# one\n## two\n### three\n#### four\n###### six")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "one"),
                MarkdownBlock.Heading(2, "two"),
                MarkdownBlock.Heading(3, "three"),
                MarkdownBlock.Heading(3, "four"),
                MarkdownBlock.Heading(3, "six"),
            ),
            blocks,
        )
    }

    @Test
    fun headingStripsTrailingHashes() {
        assertEquals(listOf(MarkdownBlock.Heading(2, "Title")), parseMarkdown("## Title ##"))
    }

    @Test
    fun hashesWithoutSpaceStayParagraph() {
        assertEquals(listOf(MarkdownBlock.Paragraph("#hashtag")), parseMarkdown("#hashtag"))
    }

    @Test
    fun bulletsCarryIndent() {
        val blocks = parseMarkdown("- a\n  - b\n\t- c\n* d\n+ e")
        assertEquals(
            listOf(
                MarkdownBlock.BulletItem(0, "a"),
                MarkdownBlock.BulletItem(1, "b"),
                MarkdownBlock.BulletItem(1, "c"),
                MarkdownBlock.BulletItem(0, "d"),
                MarkdownBlock.BulletItem(0, "e"),
            ),
            blocks,
        )
    }

    @Test
    fun numberedListsParseIndexAndIndent() {
        val blocks = parseMarkdown("1. one\n2) two\n   3. nested")
        assertEquals(
            listOf(
                MarkdownBlock.NumberedItem(1, 0, "one"),
                MarkdownBlock.NumberedItem(2, 0, "two"),
                MarkdownBlock.NumberedItem(3, 1, "nested"),
            ),
            blocks,
        )
    }

    @Test
    fun fencedCodeKeepsInnerLines() {
        val source = "```kotlin\nval x = 1\n  val y = 2\n```"
        assertEquals(listOf(MarkdownBlock.Code("val x = 1\n  val y = 2")), parseMarkdown(source))
    }

    @Test
    fun unterminatedFenceStillYieldsCode() {
        assertEquals(listOf(MarkdownBlock.Code("code()")), parseMarkdown("```\ncode()"))
    }

    @Test
    fun codeFenceContentIsNotParsedAsBlocks() {
        val blocks = parseMarkdown("```\n# not heading\n- not bullet\n```")
        assertEquals(listOf(MarkdownBlock.Code("# not heading\n- not bullet")), blocks)
    }

    @Test
    fun quoteLinesJoin() {
        assertEquals(listOf(MarkdownBlock.Quote("a b")), parseMarkdown("> a\n> b"))
    }

    @Test
    fun dividersAreRecognised() {
        val blocks = parseMarkdown("---\n***\n___")
        assertEquals(
            listOf(MarkdownBlock.Divider, MarkdownBlock.Divider, MarkdownBlock.Divider),
            blocks,
        )
    }

    @Test
    fun consecutivePlainLinesJoinIntoOneParagraph() {
        val blocks = parseMarkdown("first line\nsecond line\n\nsecond block")
        assertEquals(
            listOf(MarkdownBlock.Paragraph("first line second line"), MarkdownBlock.Paragraph("second block")),
            blocks,
        )
    }

    @Test
    fun crlfAndTabsAreTolerated() {
        val blocks = parseMarkdown("# Title\r\n\r\n- a\r\n\t- b\r\n")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "Title"),
                MarkdownBlock.BulletItem(0, "a"),
                MarkdownBlock.BulletItem(1, "b"),
            ),
            blocks,
        )
    }

    @Test
    fun emojiSurvivesParsing() {
        val blocks = parseMarkdown("## 🎉 更新\n- 修复 **闪退** 🐛")
        assertEquals(MarkdownBlock.Heading(2, "🎉 更新"), blocks.first())
        assertEquals(MarkdownBlock.BulletItem(0, "修复 **闪退** 🐛"), blocks.last())
    }

    @Test
    fun blankInputHasNoBlocks() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("   \n\t\n").isEmpty())
    }

    @Test
    fun overLongInputIsTruncatedWithMarker() {
        val huge = (1..5000).joinToString("\n") { "修复了一个很长很长的条目 $it" }
        assertTrue(huge.length > 40_000)
        val blocks = parseMarkdown(huge)
        assertTrue(blocks.isNotEmpty())
        val tail = blocks.last()
        assertTrue(tail is MarkdownBlock.Paragraph)
        assertEquals("…", (tail as MarkdownBlock.Paragraph).text)
        assertTrue(blocks.dropLast(1).all { it !is MarkdownBlock.Paragraph || it.text != "…" })
    }

    @Test
    fun singleVeryLongLineIsTruncated() {
        val blocks = parseMarkdown("x".repeat(90_000))
        assertTrue(blocks.isNotEmpty())
        assertEquals("…", (blocks.last() as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun inlineBoldItalicAndCode() {
        assertEquals(listOf(MarkdownSpan("b", bold = true)), inlineSpans("**b**"))
        assertEquals(listOf(MarkdownSpan("b", bold = true)), inlineSpans("__b__"))
        assertEquals(listOf(MarkdownSpan("i", italic = true)), inlineSpans("*i*"))
        assertEquals(listOf(MarkdownSpan("i", italic = true)), inlineSpans("_i_"))
        assertEquals(listOf(MarkdownSpan("c()", code = true)), inlineSpans("`c()`"))
    }

    @Test
    fun inlineSplitsSurroundingPlainText() {
        assertEquals(
            listOf(MarkdownSpan("a "), MarkdownSpan("b", bold = true), MarkdownSpan(" c")),
            inlineSpans("a **b** c"),
        )
    }

    @Test
    fun inlineLinkKeepsHttpsTarget() {
        assertEquals(
            listOf(MarkdownSpan("docs", linkUrl = "https://example.com/a")),
            inlineSpans("[docs](https://example.com/a)"),
        )
        assertEquals(
            listOf(MarkdownSpan("plain", linkUrl = "http://example.com")),
            inlineSpans("[plain](http://example.com)"),
        )
    }

    @Test
    fun inlineLinkLabelKeepsInnerEmphasis() {
        assertEquals(
            listOf(MarkdownSpan("fix", bold = true, linkUrl = "https://example.com")),
            inlineSpans("[**fix**](https://example.com)"),
        )
    }

    @Test
    fun unsafeLinkTargetsAreDropped() {
        listOf(
            "[x](javascript:alert)",
            "[x](JavaScript:alert)",
            "[x](data:text/html;base64,AAA)",
            "[x](file:///etc/passwd)",
            "[x](ftp://example.com)",
            "[x](vbscript:msgbox)",
        ).forEach { source ->
            val spans = inlineSpans(source)
            assertNull("unsafe target leaked from $source", spans.firstOrNull { it.linkUrl != null })
            assertTrue("label lost in $source", joined(source).contains("x"))
        }
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals("**bold", joined("**bold"))
        assertEquals("*ital", joined("*ital"))
        assertEquals("_x", joined("_x"))
        assertEquals("`code", joined("`code"))
        assertEquals("[label]", joined("[label]"))
        assertEquals("[label](no-paren-close", joined("[label](no-paren-close"))
        assertEquals("a * b", joined("a * b"))
    }

    @Test
    fun inlineTextIsNeverLost() {
        val source = "a **b** c _d_ `e` [f](https://g.co) h"
        assertEquals("a b c d e f h", joined(source))
    }

    @Test
    fun emptyInlineInputIsEmpty() {
        assertTrue(inlineSpans("").isEmpty())
    }

    @Test
    fun adjacentSameStyleSpansMerge() {
        assertEquals(listOf(MarkdownSpan("ab", bold = true)), inlineSpans("**a****b**"))
    }
}
