package com.xu.wake.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.UUID

                                                                                              
                                                                                          
                                                                                              
   
object SchoolParser {
    private val weekday = Regex("^(?:星期|周)([一二三四五六日天])$")
    /*
     * Period row labels come in several shapes across campus systems:
     *   "第一节"  "第1节"  "1-2节"  "第一二节 (01,02小节) 08:20-10:00"
     * The last form is common and uses Chinese numerals, which the original ASCII-only pattern
     * could not match, so every row was rejected as an unrecognised slot.
     */
    private val slot = Regex("^(?:第\\s*)?(\\d{1,2})\\s*(?:[-—~～至]\\s*(\\d{1,2}))?\\s*节")
    /** "第一二节" / "第十二节" style labels, as Chinese numerals. */
    private val slotChinese = Regex("^第\\s*([一二三四五六七八九十]+)\\s*节")
    /** "第一二节 (01,02小节)" gives the authoritative ASCII period numbers. */
    private val slotHint = Regex("\\(\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*小节\\s*\\)")

    /** Resolves a period row label to (start, end); null when the row is not a period row. */
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
            /* "第一二节" means periods 1 and 2; "第十二节" means 12 (1 then 2 read as digits). Two
               single-digit numerals read as a range, otherwise treat them as a multi-digit number. */
            return if (digits.size == 2 && digits.all { it in 1..9 }) digits[0] to digits[1]
            else digits.fold(0) { acc, d -> acc * 10 + d.coerceAtMost(9) }.let { it to it }
        }
        return null
    }
    /*
     * Week ranges as actually written by campus systems, with the parity marker in any of the
     * three positions seen in the wild:
     *   "6-19(周)"   "1-18周"        no parity
     *   "1-6(双周)"  "1-16(单周)"     parity inside the parentheses
     *   "1-6周(单)"  "3-15周(双)"     parity after the 周
     * Requiring a fixed shape made the parser reject perfectly ordinary timetables.
     */
    private val weekExpression = Regex("(\\d+(?:\\s*[-—~～至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-—~～至]\\s*\\d+)?)*)\\s*(?:(?:[（(]\\s*([单双])?\\s*周\\s*[）)])|(?:周\\s*(?:[（(]\\s*([单双])\\s*周?\\s*[）)])?)|(?:([单双])\\s*周))")

    fun parse(source: String, existingSchool: String? = null): SchoolImport {
        require(source.length <= 2_000_000) { "课表页面过大" }
        val doc = Jsoup.parse(source)
        val candidates = doc.select("table").filter { table ->
            table.select("tr").filter { it.closest("table") == table }.any { row -> cells(row).count { weekday.matches(it.text().trim()) } >= 2 }
        }
        require(candidates.size == 1) { "未找到唯一可识别的课表；需要明确的星期表头" }
        val table = candidates.single()
        require(table.select("table").none { it !== table }) { "不支持嵌套课表" }
        val rows = table.select("tr")
        var columns: Map<Int, Int>? = null
        var width = 0
        val courses = mutableListOf<CourseSession>()
        /* 无法解析的格子文本样本，仅在最终一门课都没解析出来时用于给出可诊断的提示。 */
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
            /*
             * 备注行先于其他判断跳过。它有两种形态：首格是「备注:」这类字样，或某个格子
             * 横跨整行。这类行不含课程，若继续往下走，其中的多条周次会被当成多门课，
             * 也会被合并单元格守卫拦下——两种结果都会让整次导入失败。
             */
            val noteLike = first.startsWith("备注") || first.startsWith("说明")
            val spansRow = cells.any { columnSpan(it) >= maxOf(2, width - 1) }
            if (noteLike || spansRow) return@forEach
            val range = periodRange(first)
            if (range == null) {
                require(cells.drop(1).all { it.text().isBlank() }) { "存在无法识别节次的非空行" }
                return@forEach
            }
            /* 真正承载课程的合并单元格仍然拒绝：行列模型会失真。 */
            require(cells.all { rowSpan(it) == 1 && columnSpan(it) == 1 }) { "暂不支持合并单元格课表，请使用文件导入" }
            require(cells.size == width) { "课表列数不一致" }
            val (start, end) = range
            val times = Regex("(\\d{1,2})[:：](\\d{2})\\s*[-—~～至]\\s*(\\d{1,2})[:：](\\d{2})").find(first)
            /* A merged label such as "第一二节 (01,02小节) 08:20-10:00" gives one window for two
               periods. Split it evenly so each period gets its own timing; the old code only
               recorded the window when start == end, so merged rows produced no timings at all. */
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
            /* A summary band spanning most of the row (military training, labour practice, 备注) is
               not a course slot. It carries several week ranges, so the per-course check below
               would reject the import over it; skip the entire row instead. */
            val spansWide = cells.any { c ->
                (c.attr("colspan").ifEmpty { "1" }.toIntOrNull() ?: 1) >= 3 ||
                    (c.attr("rowspan").ifEmpty { "1" }.toIntOrNull() ?: 1) >= 3
            }
            if (spansWide) return@forEach
            require(cells.withIndex().all { (index, cell) -> index == 0 || index in mapping || cell.text().isBlank() }) { "非星期列含有未知内容" }
            mapping.forEach { (index, day) ->
                val cell = cells[index]
                /* Select the exact class: the ".kbcontent" selector also matches ".kbcontent1",
                   which is a hidden duplicate of the same slot and would double the week matches. */
                val blocks = cell.select("[class=kbcontent], .kbcontent:not([class*=kbcontent1])")
                    .let { if (it.isEmpty()) listOf(cell) else it.toList() }
                blocks.forEach blockLoop@ { block ->
                    val copy = block.clone()
                    copy.select("script,style").remove()
                    copy.select("br").forEach { it.after("\n") }
                    val lines = copy.wholeText().replace('\u00a0', ' ').split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty() || lines.all { it in listOf("-", "--", "无", "暂无课程") }) return@blockLoop
                    /* 整块就是一条备注时直接跳过，避免其中并列的多条周次被拆成多门课。 */
                    if (lines.first().let { it.startsWith("备注") || it.startsWith("说明") }) return@blockLoop
                    /* 一个格子里常常并排多门课，整学期导出时尤其明显。按周次出现的位置把文本
                       切成若干段，每段一门课；切不出来的段落记录下来跳过，而不是让个别异常
                       格子废掉整次导入。 */
                    for (segment in splitByWeek(lines)) {
                        val course = buildCourse(segment, day, start, end, block)
                        if (course != null) courses += course else skipped += segment.joinToString(" ")
                    }
                }
            }
        }
        require(courses.isNotEmpty()) {
            val sample = skipped.firstOrNull()?.take(90)
            if (sample.isNullOrBlank()) "未识别到课程" else "未识别到课程；格子内容示例：$sample"
        }
        val text = doc.body()?.text().orEmpty()
        fun metadata(pattern: String): Int? {
            val matches = Regex(pattern).findAll(text).map { it.groupValues[1].toInt() }.distinct().toList()
            require(matches.size <= 1 && matches.all { it in 1..60 }) { "学期周数信息冲突或超出范围" }
            return matches.firstOrNull()
        }
        val total = metadata("(?:总周数|学期周数)\\s*[:：]?\\s*(\\d{1,2})(?!\\d)")
        val current = metadata("(?:当前周|教学周)\\s*[:：]?\\s*(?:第\\s*)?(\\d{1,2})(?!\\d)")
        require(total == null || courses.all { it.weeks.all { w -> w <= total } } && (current == null || current <= total)) { "周次超出学期" }
        val sorted = periods.values.sortedBy { it.period }
        require(sorted.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes }) { "节次时间重叠" }
        return SchoolImport(courses.toList(), total, current, sorted,
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

    /** A full-width note cell such as "备注: ..." — carries no timetable data. */
    private fun columnSpan(cell: Element) = cell.attr("colspan").ifEmpty { "1" }.toIntOrNull() ?: 1

    private fun rowSpan(cell: Element) = cell.attr("rowspan").ifEmpty { "1" }.toIntOrNull() ?: 1

    /**
     * 把一段格子文本按周次出现的位置切成若干段，每段对应一门课。
     *
     * 教务系统在一个格子里并排多门课是常态（同一时段有多门课，或整学期导出时把各周
     * 的课叠在一起）。每一段都自带一条周次，因此以周次为界切分即可还原成多门课。
     */
    private fun splitByWeek(lines: List<String>): List<List<String>> {
        val body = lines.filterNot { isDivider(it) }
        val segments = mutableListOf<List<String>>()
        var pending = mutableListOf<String>()
        /* 刚结束一门课：紧随其后的那一行是它的教室，而不是下一门课的课名。 */
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
            /* 同一行里周次之后的内容（如 "[01-02节]"）属于刚结束的那门课。 */
            val rest = line.substring(cursor).trim()
            if (rest.isNotEmpty()) segments[segments.lastIndex] = segments.last() + rest
        }
        if (pending.isNotEmpty() && segments.isNotEmpty()) segments[segments.lastIndex] = segments.last() + pending
        return segments
    }

    /** 分隔两门课的横线（----------------------、===== 等）。 */
    private fun isDivider(line: String) = Regex("^[-—_=*·•~]{3,}$").matches(line.trim())
    /** 一段文本 -> 一门课；缺少周次、周次非法或找不到课名时返回 null，由调用方跳过。 */
    private fun buildCourse(lines: List<String>, day: Int, start: Int, end: Int, block: Element): CourseSession? {
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
        /* 课名取周次之前的第一行；课名与周次同行时取该行周次之前的首个词。 */
        val head = lines.takeWhile { !weekExpression.containsMatchIn(it) }
        val headText = text.substring(0, wm.range.first).trim()
        val name = (head.firstOrNull() ?: headText.split(' ').firstOrNull().orEmpty()).trim()
        if (name.isEmpty() || weekExpression.containsMatchIn(name)) return null
        val teacher = (head.drop(1).firstOrNull()
            ?: headText.split(' ').drop(1).joinToString(" ").takeIf { it.isNotBlank() }
            ?: block.selectFirst("[title=老师],[title=教师]")?.text()?.trim().orEmpty()).trim()
        /* 教室是这一段的最后一行；它既不是课名，也不含周次。 */
        val last = lines.lastOrNull()?.takeIf { it != name && !weekExpression.containsMatchIn(it) }
        val location = (last
            ?: text.substring(wm.range.last + 1).trim().takeIf { it.isNotBlank() }
            ?: block.selectFirst("[title=教室],[title=地点]")?.text()?.trim().orEmpty()).trim()
        return CourseSession("网页-${UUID.randomUUID()}", name, day, start, end, weeks.toList(), teacher, location)
    }

    private fun cells(row: Element): List<Element> = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
}
