package com.xunyyds.kelaile.data

import java.util.UUID

object XlsxTimetable {

    private val weekday = Regex("^(?:星期|周)([一二三四五六日天])$")
    private val slotHint = Regex("\\(\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*小节\\s*\\)")
    private val slotAscii = Regex("^(?:第\\s*)?(\\d{1,2})\\s*(?:[-—~～至]\\s*(\\d{1,2}))?\\s*节")
    private val slotChinese = Regex("^第\\s*([一二三四五六七八九十]+)\\s*节")
    private val weekExpression = Regex("(\\d+(?:\\s*[-—~～至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-—~～至]\\s*\\d+)?)*)\\s*(?:(?:[（(]\\s*([单双])?\\s*周\\s*[）)])|(?:周\\s*(?:[（(]\\s*([单双])\\s*周?\\s*[）)])?)|(?:([单双])\\s*周))")
    private val timeRange = Regex("(\\d{1,2})[:：](\\d{2})\\s*[-—~～至]\\s*(\\d{1,2})[:：](\\d{2})")

    fun parse(grid: List<List<String?>>): SchoolImport {
        require(grid.isNotEmpty()) { "表格是空的" }

        var headerRow = -1
        var columns: Map<Int, Int> = emptyMap()
        for ((r, line) in grid.withIndex()) {
            val found = linkedMapOf<Int, Int>()
            for ((c, cell) in line.withIndex()) {
                val m = weekday.matchEntire(cell?.trim().orEmpty()) ?: continue
                found[c] = "一二三四五六日".indexOf(m.groupValues[1].replace('天', '日')) + 1
            }
            if (found.size >= 2 && found.values.distinct().size == found.size && 0 !in found) {
                headerRow = r; columns = found; break
            }
        }
        require(headerRow >= 0) { "未找到星期表头（需要「星期一」~「星期日」）" }

        val courses = mutableListOf<CourseSession>()
        val periods = linkedMapOf<Int, PeriodTime>()
        for (r in headerRow + 1 until grid.size) {
            val line = grid[r]
            if (line.isEmpty()) continue
            val label = line.firstOrNull()?.trim().orEmpty()

            if (isNoteRow(line)) continue
            val range = periodRange(label) ?: continue
            val (start, end) = range
            timeRange.find(label)?.let { m ->
                val from = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                val to = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
                if (to > from) {
                    val span = end - start + 1
                    val step = (to - from) / span
                    for (p in start..end) {
                        val period = PeriodTime(p, from + step * (p - start), if (p == end) to else from + step * (p - start + 1))
                        if (periods[p] == null) periods[p] = period
                    }
                }
            }
            for ((col, day) in columns) {
                val text = line.getOrNull(col)?.trim().orEmpty()
                if (text.isEmpty()) continue
                courses += parseCell2(text, day, start, end)
            }
        }
        require(courses.isNotEmpty()) { "表格里没有可识别的课程" }
        return SchoolImport(courses, periods = periods.values.toList())
    }

    private fun parseCell2(text: String, day: Int, start: Int, end: Int): List<CourseSession> {
        val flat = text.replace('\n', ' ').replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return emptyList()
        val found = weekExpression.findAll(flat).toList()
        if (found.isEmpty()) return emptyList()

        return found.mapIndexedNotNull { index, _ ->
            val from = if (index == 0) 0 else found[index - 1].range.last + 1
            val to = if (index == found.size - 1) flat.length else found[index + 1].range.first
            parseCell(flat.substring(from, to).trim(), day, start, end)
        }
    }

    private fun parseCell(text: String, day: Int, start: Int, end: Int): CourseSession? {
        val flat = text.replace('\n', ' ').replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return null
        val matches = weekExpression.findAll(flat).toList()
        if (matches.size != 1) return null
        val m = matches.single()
        val parity = m.groupValues[2].ifEmpty { m.groupValues[3] }
        val weeks = sortedSetOf<Int>()
        for (piece in m.groupValues[1].split(Regex("[,，、]"))) {
            val numbers = Regex("\\d+").findAll(piece).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) continue
            val a = numbers.first(); val b = numbers.last()
            if (a !in 1..60 || b !in a..60) continue
            for (w in a..b) if (parity.isEmpty() || w % 2 == if (parity == "单") 1 else 0) weeks += w
        }
        if (weeks.isEmpty()) return null

        val name = flat.substring(0, m.range.first).trim().ifEmpty { flat.take(24) }
        if (name.isBlank()) return null
        val location = locationOf(flat)
        return CourseSession(
            id = "xlsx-" + UUID.randomUUID(),
            name = name.take(60),
            weekday = day,
            startPeriod = start,
            endPeriod = end,
            weeks = weeks.toList(),
            teacher = "",
            location = location,
        )
    }

    private fun locationOf(flat: String): String {
        val tokens = flat.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
        val room = tokens.firstOrNull { it.length in 2..20 && Regex("[楼室馆场教]").containsMatchIn(it) && !Regex("周|节").containsMatchIn(it) }
        return room.orEmpty()
    }

    private fun isNoteRow(line: List<String?>): Boolean {
        val texts = line.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        if (texts.isEmpty()) return false
        val head = texts.first()
        if (Regex("^(备注|说明|注[:：])").containsMatchIn(head)) return true

        return texts.size == 1 && !periodRange(texts.first()).let { it != null }
    }

    private fun periodRange(label: String): Pair<Int, Int>? {
        slotHint.find(label)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..100 && b in a..100) return a to b
        }
        slotAscii.find(label)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
            if (a in 1..100 && b in a..100) return a to b
        }
        slotChinese.find(label)?.let { m ->
            val digits = m.groupValues[1].mapNotNull { ch -> "一二三四五六七八九十".indexOf(ch).let { if (it >= 0) it + 1 else null } }
            if (digits.isEmpty()) return null
            return if (digits.size == 2 && digits.all { it in 1..9 }) digits[0] to digits[1]
            else digits.fold(0) { acc, d -> acc * 10 + d.coerceAtMost(9) }.let { it to it }
        }
        return null
    }
}
