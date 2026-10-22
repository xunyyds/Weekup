package com.xunyyds.kelaile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMatchesTest {

    private val hit = SpanStyle(color = Color(0xFF2869D8), fontWeight = FontWeight.Bold)

    private fun spans(text: String, terms: List<String>) =
        highlightMatches(text, terms, hit).spanStyles.map { it.start to it.end }

    @Test
    fun emptyTermsReturnsPlainText() {
        val out = highlightMatches("明暗模式", emptyList(), hit)
        assertEquals("明暗模式", out.text)
        assertTrue("no styling expected", out.spanStyles.isEmpty())
    }

    @Test
    fun emptyTextIsSafe() {
        val out = highlightMatches("", listOf("主题"), hit)
        assertEquals("", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun overlappingTermsMergeIntoOneSpan() {

        val result = spans("动效设置", listOf("动效", "效设"))
        assertEquals(listOf(0 to 3), result)
    }

    @Test
    fun adjacentTermsMergeIntoOneSpan() {

        val result = spans("主题颜色", listOf("主题", "颜色"))
        assertEquals(listOf(0 to 4), result)
    }

    @Test
    fun matchIsCaseInsensitive() {
        val result = spans("Dark Mode", listOf("dark", "MODE"))
        assertEquals(listOf(0 to 4, 5 to 9), result)
    }

    @Test
    fun termLongerThanTextIsIgnored() {
        val out = highlightMatches("短", listOf("很长的一个词"), hit)
        assertEquals("短", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun textIsPreservedExactly() {
        val text = "第 3 周 / 共 20 周"
        assertEquals(text, highlightMatches(text, listOf("周"), hit).text)
    }

    @Test
    fun everyOccurrenceIsHighlighted() {
        val result = spans("周次周次", listOf("周"))
        assertEquals(listOf(0 to 1, 2 to 3), result)
    }

    @Test
    fun noMatchLeavesTextUnstyled() {
        val out = highlightMatches("明暗模式", listOf("zzz"), hit)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun emptyTermInListIsSkipped() {
        val result = spans("外观", listOf("", "外"))
        assertEquals(listOf(0 to 1), result)
    }

    @Test
    fun appliedSpanStyleIsTheGivenOne() {
        val out = highlightMatches("外观", listOf("外观"), hit)
        assertEquals(1, out.spanStyles.size)
        assertEquals(hit, out.spanStyles.first().item)
    }
}

class SearchTermsTest {

    @Test
    fun splitsOnWhitespace() {
        assertEquals(listOf("主题", "颜色"), searchTerms("  主题   颜色 "))
    }

    @Test
    fun blankInputYieldsNoTerms() {
        assertEquals(emptyList<String>(), searchTerms("   "))
    }
}

class SearchScoreTest {

    private val entry = SearchText("明暗模式", "外观", "深色 浅色 夜间")

    @Test
    fun titlePrefixOutranksKeyword() {
        val exact = searchScore(entry, listOf("明暗"))
        val keyword = searchScore(entry, listOf("夜间"))
        assertTrue("title prefix should outrank keyword", exact > keyword)
    }

    @Test
    fun allTermsMustMatch() {
        assertEquals(-1, searchScore(entry, listOf("明暗", "代码")))
    }

    @Test
    fun pathOnlyMatchStillScores() {
        assertTrue(searchScore(entry, listOf("外观")) > 0)
    }

    @Test
    fun emptyTermsScoreZero() {
        assertEquals(0, searchScore(entry, emptyList()))
    }
}
