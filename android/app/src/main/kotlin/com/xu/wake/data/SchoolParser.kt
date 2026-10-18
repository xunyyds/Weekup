package com.xu.wake.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.UUID

                                                                                              
                                                                                          
                                                                                              
   
object SchoolParser {
    private val weekday = Regex("^(?:星期|周)([一二三四五六日天])$")


    private val slot = Regex("^(?:第\\s*)?(\\d{1,2})\\s*(?:[-—~～至]\\s*(\\d{1,2}))?\\s*节")


    private val slotChinese = Regex("^第\\s*([一二三四五六七八九十]+)\\s*节")


    private val slotHint = Regex("\\(\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*小节\\s*\\)")



    private fun periodRange(label: String): Pair<Int, Int>? {
        slotHint.find(label)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..100 && b in a..100) return a to b
        }
        slot.find(label)?.let { m ->
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


    private val weekExpression = Regex("(\\d+(?:\\s*[-—~～至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-—~～至]\\s*\\d+)?)*)\\s*(?:(?:[（(]\\s*([单双])?\\s*周\\s*[）)])|(?:周\\s*(?:[（(]\\s*([单双])\\s*周?\\s*[）)])?)|(?:([单双])\\s*周))")

    fun parse(source: String, existingSchool: String? = null): SchoolImport {
        require(source.length <= 2_000_000) { "课表页面过大" }
        val doc = Jsoup.parse(source)
        val candidates = doc.select("table").filter { table ->
            table.select("tr").filter { it.closest("table") == table }.any { row -> cells(row).count { weekday.matches(it.text().trim()) } >= 2 }
        }
        require(candidates.isNotEmpty()) { "未找到唯一可识别的课表；需要明确的星期表头" }


        val failures = mutableListOf<String>()
        val parsed = candidates.mapNotNull { table ->
            try {
                parseTable(table)
            } catch (e: IllegalArgumentException) {
                (e.message ?: "").takeIf { it.isNotBlank() }?.let { failures += it }
                null
            }
        }
        val ranked = parsed.sortedByDescending { it.first.size }
        require(ranked.isNotEmpty()) { failures.firstOrNull() ?: "未识别到课程" }
        require(ranked.size == 1 || ranked[0].first.size > ranked[1].first.size) {
            "未找到唯一可识别的课表；需要明确的星期表头"
        }
        val (courses, periods) = ranked.first()
        return trailingMetadata(doc, courses, periods, existingSchool)
    }



    private fun parseTable(table: Element): Pair<List<CourseSession>, List<PeriodTime>> {
        require(table.select("table").none { it !== table }) { "不支持嵌套课表" }
        val rows = table.select("tr")
        var columns: Map<Int, Int>? = null
        var width = 0
        val courses = mutableListOf<CourseSession>()


        val skipped = mutableListOf<String>()
        val periods = linkedMapOf<Int, PeriodTime>()
        rows.forEach { row ->
            val cells = cells(row)
            val headings = cells.mapIndexedNotNull { index, cell ->
                weekday.matchEntire(cell.text().trim())?.let { index to ("一二三四五六日".indexOf(it.groupValues[1].replace('天', '日')) + 1) }
            }.toMap()
            if (headings.isNotEmpty()) {
                require(headings.size >= 2 && headings.values.distinct().size == headings.size && 0 !in headings) { "星期表头不完整或重复" }
                require(columns == null || columns == headings) { "课表包含冲突表头" }
                columns = headings; width = cells.size
                return@forEach
            }
            val mapping = columns ?: return@forEach
            if (cells.isEmpty()) return@forEach
            val first = cells.first().text().trim()


            val noteLike = first.startsWith("备注") || first.startsWith("说明")
            val spansRow = cells.any { columnSpan(it) >= maxOf(2, width - 1) }
            if (noteLike || spansRow) return@forEach
            val range = periodRange(first)
            if (range == null) {
                require(cells.drop(1).all { it.text().isBlank() }) { "存在无法识别节次的非空行" }
                return@forEach
            }


            require(cells.all { rowSpan(it) == 1 && columnSpan(it) == 1 }) { "暂不支持合并单元格课表，请使用文件导入" }
            require(cells.size == width) { "课表列数不一致" }
            val (start, end) = range
            val times = Regex("(\\d{1,2})[:：](\\d{2})\\s*[-—~～至]\\s*(\\d{1,2})[:：](\\d{2})").find(first)


            if (times != null) {
                val (h1, m1, h2, m2) = times.destructured
                require(h1.toInt() in 0..23 && h2.toInt() in 0..23 && m1.toInt() in 0..59 && m2.toInt() in 0..59) { "无效时间" }
                val from = h1.toInt() * 60 + m1.toInt()
                val to = h2.toInt() * 60 + m2.toInt()
                require(to > from) { "无效时间" }
                val span = end - start + 1
                val step = (to - from) / span
                for (p in start..end) {
                    val period = PeriodTime(p, from + step * (p - start), if (p == end) to else from + step * (p - start + 1))
                    require(periods[p] == null || periods[p] == period) { "节次时间冲突" }
                    periods[p] = period
                }
            }


            val spansWide = cells.any { c ->
                (c.attr("colspan").ifEmpty { "1" }.toIntOrNull() ?: 1) >= 3 ||
                    (c.attr("rowspan").ifEmpty { "1" }.toIntOrNull() ?: 1) >= 3
            }
            if (spansWide) return@forEach
            require(cells.withIndex().all { (index, cell) -> index == 0 || index in mapping || cell.text().isBlank() }) { "非星期列含有未知内容" }
            mapping.forEach { (index, day) ->
                val cell = cells[index]


                val blocks = cell.select("[class=kbcontent], .kbcontent:not([class*=kbcontent1])")
                    .let { if (it.isEmpty()) listOf(cell) else it.toList() }
                blocks.forEach blockLoop@ { block ->
                    val copy = block.clone()
                    copy.select("script,style").remove()
                    copy.select("br").forEach { it.after("\n") }
                    val lines = copy.wholeText().replace('\u00a0', ' ').split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty() || lines.all { it in listOf("-", "--", "无", "暂无课程") }) return@blockLoop


                    if (lines.first().let { it.startsWith("备注") || it.startsWith("说明") }) return@blockLoop


                    val segments = splitByWeek(lines)


                    val useLabels = segments.size == 1
                    for (segment in segments) {
                        val course = buildCourse(segment, day, start, end, block, useLabels)
                        if (course != null) courses += course else skipped += segment.joinToString(" ")
                    }
                }
            }
        }
        require(courses.isNotEmpty()) {
            val sample = skipped.firstOrNull()?.take(90)
            if (sample.isNullOrBlank()) "未识别到课程" else "未识别到课程；格子内容示例：$sample"
        }
        val sorted = periods.values.sortedBy { it.period }
        require(sorted.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes }) { "节次时间重叠" }
        return courses.toList() to sorted
    }

    private fun trailingMetadata(doc: org.jsoup.nodes.Document, courses: List<CourseSession>, periods: List<PeriodTime>, existingSchool: String?): SchoolImport {
        val text = doc.body()?.text().orEmpty()
        fun metadata(pattern: String): Int? {
            val matches = Regex(pattern).findAll(text).map { it.groupValues[1].toInt() }.distinct().toList()
            require(matches.size <= 1 && matches.all { it in 1..60 }) { "学期周数信息冲突或超出范围" }
            return matches.firstOrNull()
        }
        val total = metadata("(?:总周数|学期周数)\\s*[:：]?\\s*(\\d{1,2})(?!\\d)")
        val current = metadata("(?:当前周|教学周)\\s*[:：]?\\s*(?:第\\s*)?(\\d{1,2})(?!\\d)")
        require(total == null || courses.all { it.weeks.all { w -> w <= total } } && (current == null || current <= total)) { "周次超出学期" }
        return SchoolImport(courses, total, current, periods,
            existingSchool?.trim()?.takeIf { it.isNotEmpty() } ?: recognizeSchool(doc))
    }

                                                                                                    
    private fun recognizeSchool(doc: org.jsoup.nodes.Document): String? {
        val evidence = mutableListOf(doc.title())
        doc.select("header,h1").filter { it.closest("form,[hidden],[aria-hidden=true]") == null }.forEach { element ->
            val copy = element.clone()
            copy.select("input,textarea,select,button,form,script,style,[hidden],[aria-hidden=true]").remove()
            evidence += copy.text()
        }
        doc.select("meta[name],meta[property]").forEach { meta ->
            val key = meta.attr("name").ifBlank { meta.attr("property") }.lowercase()
            if (key in setOf("school", "school-name", "school_name", "university", "og:site_name")) evidence += meta.attr("content")
        }
        val names = evidence.flatMap { value ->
            value.split(Regex("[|｜—–\\-:：/\\s·•,，;；()（）【】\\[\\]]+")).mapNotNull { part ->
                
                val match = Regex("^([\\p{IsHan}]{2,30}?(?:大学|学院|学校))(?:教务(?:处|系统|管理系统|管理平台)?|智慧校园|统一身份认证|信息门户|官网|官方网站|课表查询|课程表|学生课表)?$").matchEntire(part)
                val name = match?.groupValues?.get(1) ?: return@mapNotNull null
                if (Regex("教务|管理系统|未知|某某|示例|测试|你的|您的|本校|我的").containsMatchIn(name) ||
                    name in setOf("大学", "学院", "学校", "高等学校", "职业学校", "教学学校")) null else name
            }
        }.distinct()
        return names.singleOrNull()
    }



    private fun columnSpan(cell: Element) = cell.attr("colspan").ifEmpty { "1" }.toIntOrNull() ?: 1

    private fun rowSpan(cell: Element) = cell.attr("rowspan").ifEmpty { "1" }.toIntOrNull() ?: 1



    private fun splitByWeek(lines: List<String>): List<List<String>> {
        val body = lines.filterNot { isDivider(it) }
        val segments = mutableListOf<List<String>>()
        var pending = mutableListOf<String>()


        var closed = false
        for (line in body) {
            val matches = weekExpression.findAll(line).toList()
            if (matches.isEmpty()) {
                if (closed && segments.isNotEmpty()) {
                    segments[segments.lastIndex] = segments.last() + line
                    closed = false
                } else pending += line
                continue
            }
            var cursor = 0
            for (m in matches) {
                segments += pending + line.substring(cursor, m.range.last + 1).trim()
                pending = mutableListOf()
                cursor = m.range.last + 1
                closed = true
            }


            val rest = line.substring(cursor).trim()
            if (rest.isNotEmpty()) segments[segments.lastIndex] = segments.last() + rest
        }
        if (pending.isNotEmpty() && segments.isNotEmpty()) segments[segments.lastIndex] = segments.last() + pending
        return segments
    }



    private fun isDivider(line: String) = Regex("^[-—_=*·•~]{3,}$").matches(line.trim())


    private fun isMetaLine(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("（") || t.startsWith("(")) return true
        if (Regex("编号|讲课|学分|备注|说明").containsMatchIn(t)) return true
        return Regex("^\\[?\\d+\\s*[-—~～至]\\s*\\d+\\s*节\\]?$").matches(t)
    }



    private fun buildCourse(lines: List<String>, day: Int, start: Int, end: Int, block: Element, useLabels: Boolean): CourseSession? {
        val text = lines.joinToString(" ")
        val matches = weekExpression.findAll(text).toList()
        if (matches.size != 1) return null
        val wm = matches.single()
        val parity = wm.groupValues[2].ifEmpty { wm.groupValues[3] }
        val weeks = sortedSetOf<Int>()
        for (piece in wm.groupValues[1].split(Regex("[,，、]"))) {
            val numbers = Regex("\\d+").findAll(piece).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) continue
            val a = numbers.first(); val b = numbers.last()
            if (a !in 1..60 || b !in a..60) return null
            for (w in a..b) if (parity.isEmpty() || w % 2 == if (parity == "单") 1 else 0) weeks += w
        }
        if (weeks.isEmpty()) return null


        val head = lines.takeWhile { !weekExpression.containsMatchIn(it) }
        val headText = text.substring(0, wm.range.first).trim()
        val name = (head.firstOrNull() ?: headText.split(' ').firstOrNull().orEmpty()).trim()
        if (name.isEmpty() || weekExpression.containsMatchIn(name)) return null


        val labelledTeacher = if (useLabels) block.selectFirst("[title=老师],[title=教师]")?.text()?.trim().orEmpty() else ""
        val labelledRoom = if (useLabels) block.selectFirst("[title=教室],[title=地点]")?.text()?.trim().orEmpty() else ""
        val teacher = labelledTeacher.ifEmpty {
            head.drop(1).firstOrNull()
                ?: headText.split(' ').drop(1).joinToString(" ").takeIf { it.isNotBlank() }
                ?: ""
        }.trim()
        val tail = lines.dropWhile { !weekExpression.containsMatchIn(it) }.drop(1)
        val positionalRoom = tail.firstOrNull { it.isNotBlank() && !isMetaLine(it) }
            ?: lines.lastOrNull()?.takeIf { it != name && !weekExpression.containsMatchIn(it) && !isMetaLine(it) }
            ?: text.substring(wm.range.last + 1).trim().takeIf { it.isNotBlank() && !isMetaLine(it) }
        val location = labelledRoom.ifEmpty { positionalRoom.orEmpty() }.trim()
        return CourseSession("网页-${UUID.randomUUID()}", name, day, start, end, weeks.toList(), teacher, location)
    }

    private fun cells(row: Element): List<Element> = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
}
