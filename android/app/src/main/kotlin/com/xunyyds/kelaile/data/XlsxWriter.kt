package com.xunyyds.kelaile.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {

    private const val FIXED_TIME = 315532800000L
    private const val MAX_COURSES = 2048
    private const val MAX_TEXT = 4000
    private const val MAX_ROWS = 4096
    private const val MAX_SHEET = 2_000_000
    private const val STYLE_HEADER = 1
    private const val STYLE_BODY = 2
    private const val STYLE_LABEL = 3

    private val weekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    private val whitespace = Regex("\\s+")
    private val control = Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]")
    private val weekLike = Regex(
        "\\d+(?:\\s*[-—~～至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-—~～至]\\s*\\d+)?)*\\s*" +
            "(?:(?:[（(]\\s*[单双]?\\s*周\\s*[）)])|(?:周\\s*(?:[（(]\\s*[单双]\\s*周?\\s*[）)])?)|(?:[单双]\\s*周))",
    )

    fun writeTimetable(data: AppData): ByteArray {
        require(data.courses.size <= MAX_COURSES) { "课程过多，无法导出" }
        for (course in data.courses) {
            require(course.name.length <= MAX_TEXT && course.teacher.length <= MAX_TEXT && course.location.length <= MAX_TEXT) {
                "课程信息过长，无法导出"
            }
        }
        val periods = data.settings.periods.sortedBy { it.period }
        val courses = data.courses.sortedWith(
            compareBy({ it.weekday }, { it.startPeriod }, { it.endPeriod }, { it.name }, { it.id }),
        )
        val rows = buildRows(periods, courses)
        require(rows.size + 1 <= MAX_ROWS) { "课表行数过多，无法导出" }
        return zip(
            "[Content_Types].xml" to CONTENT_TYPES,
            "_rels/.rels" to ROOT_RELS,
            "xl/workbook.xml" to WORKBOOK,
            "xl/_rels/workbook.xml.rels" to WORKBOOK_RELS,
            "xl/styles.xml" to STYLES,
            "xl/worksheets/sheet1.xml" to sheetXml(rows),
        )
    }

    private class Row(val label: String, val cells: Map<Int, String>)

    private fun buildRows(periods: List<PeriodTime>, courses: List<CourseSession>): List<Row> {
        val byPeriod = periods.associateBy { it.period }
        val byStart = courses.groupBy { it.startPeriod }
        val rows = mutableListOf<Row>()
        for (period in periods) {
            val starting = byStart[period.period].orEmpty()
            if (starting.isEmpty()) {
                rows += Row(label(period, period, false), emptyMap())
                continue
            }
            for ((end, group) in starting.groupBy { it.endPeriod }.toSortedMap()) {
                val last = byPeriod[end] ?: period
                val perDay = group.groupBy { it.weekday }.toSortedMap()
                val depth = perDay.values.maxOf { it.size }
                for (index in 0 until depth) {
                    val cells = perDay.mapNotNull { (day, list) -> list.getOrNull(index)?.let { day to cellText(it) } }.toMap()
                    rows += Row(label(period, last, index > 0), cells)
                }
            }
        }
        return rows
    }

    private fun label(first: PeriodTime, last: PeriodTime, continued: Boolean): String {
        val head = if (last.period > first.period) "第${first.period}-${last.period}节" else "第${first.period}节"
        val suffix = if (continued) "（续）" else ""
        return "$head ${clock(first.startMinutes)}-${clock(last.endMinutes)}$suffix"
    }

    private fun clock(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        return (if (hour < 10) "0$hour" else "$hour") + ":" + (if (minute < 10) "0$minute" else "$minute")
    }

    private fun cellText(course: CourseSession): String {
        val name = clean(course.name).ifEmpty { "课程" }
        val location = clean(course.location)
        val teacher = clean(course.teacher)
        val text = buildString {
            append(name).append(' ').append(weekText(course.weeks))
            if (location.isNotEmpty()) append(' ').append(location)
            if (teacher.isNotEmpty()) append(' ').append(teacher)
        }
        require(text.length <= MAX_TEXT) { "课程信息过长，无法导出" }
        return text
    }

    private fun clean(raw: String): String =
        raw.replace(control, " ").replace(weekLike, " ").replace(whitespace, " ").trim()

    private fun weekText(weeks: List<Int>): String {
        val sorted = weeks.distinct().sorted()
        require(sorted.isNotEmpty()) { "课程缺少周次" }
        if (sorted.size > 1 && sorted.zipWithNext().all { (a, b) -> b - a == 2 }) {
            val parity = if (sorted.first() % 2 == 1) "单" else "双"
            return "${sorted.first()}-${sorted.last()}周($parity)"
        }
        val pieces = mutableListOf<String>()
        var start = 0
        for (index in 1..sorted.size) {
            if (index == sorted.size || sorted[index] - sorted[index - 1] != 1) {
                val from = sorted[start]
                val to = sorted[index - 1]
                pieces += if (from == to) "$from" else "${from}-${to}"
                start = index
            }
        }
        return pieces.joinToString(",") + "周"
    }

    private fun sheetXml(rows: List<Row>): String {
        val sb = StringBuilder(1024 + rows.size * 160)
        sb.append(XML_DECL)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<cols><col min=\"1\" max=\"1\" width=\"24\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"8\" width=\"18\" customWidth=\"1\"/></cols>")
        sb.append("<sheetData>")
        sb.append("<row r=\"1\" ht=\"26\" customHeight=\"1\">")
        appendCell(sb, 0, 1, "节次", STYLE_HEADER)
        weekdays.forEachIndexed { index, day -> appendCell(sb, index + 1, 1, day, STYLE_HEADER) }
        sb.append("</row>")
        rows.forEachIndexed { index, row ->
            val number = index + 2
            sb.append("<row r=\"").append(number).append("\">")
            appendCell(sb, 0, number, row.label, STYLE_LABEL)
            for ((day, text) in row.cells) appendCell(sb, day, number, text, STYLE_BODY)
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        require(sb.length <= MAX_SHEET) { "课表内容过大，无法导出" }
        return sb.toString()
    }

    private fun appendCell(sb: StringBuilder, column: Int, row: Int, text: String, style: Int) {
        sb.append("<c r=\"").append(columnName(column)).append(row)
        sb.append("\" t=\"inlineStr\" s=\"").append(style).append("\"><is><t xml:space=\"preserve\">")
        appendEscaped(sb, text)
        sb.append("</t></is></c>")
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val letters = StringBuilder()
        while (value > 0) {
            letters.append('A' + (value - 1) % 26)
            value = (value - 1) / 26
        }
        return letters.reverse().toString()
    }

    private fun appendEscaped(sb: StringBuilder, text: String) {
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            index += Character.charCount(point)
            when {
                point == '&'.code -> sb.append("&amp;")
                point == '<'.code -> sb.append("&lt;")
                point == '>'.code -> sb.append("&gt;")
                point == '"'.code -> sb.append("&quot;")
                point == '\''.code -> sb.append("&apos;")
                point == 0x9 || point == 0xA || point == 0xD -> sb.appendCodePoint(point)
                point in 0x20..0xD7FF || point in 0xE000..0xFFFD || point in 0x10000..0x10FFFF -> sb.appendCodePoint(point)
            }
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, body) in entries) {
                val entry = ZipEntry(name)
                entry.time = FIXED_TIME
                zip.putNextEntry(entry)
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private val CONTENT_TYPES = XML_DECL +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private val ROOT_RELS = XML_DECL +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private val WORKBOOK = XML_DECL +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"课程表\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private val WORKBOOK_RELS = XML_DECL +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private val STYLES = XML_DECL +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"3\">" +
        "<font><sz val=\"11\"/><name val=\"等线\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"等线\"/></font>" +
        "<font><sz val=\"10\"/><name val=\"等线\"/></font>" +
        "</fonts>" +
        "<fills count=\"3\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE7F0EA\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
        "</fills>" +
        "<borders count=\"2\">" +
        "<border><left/><right/><top/><bottom/><diagonal/></border>" +
        "<border><left style=\"thin\"><color rgb=\"FFB9CBBE\"/></left><right style=\"thin\"><color rgb=\"FFB9CBBE\"/></right>" +
        "<top style=\"thin\"><color rgb=\"FFB9CBBE\"/></top><bottom style=\"thin\"><color rgb=\"FFB9CBBE\"/></bottom><diagonal/></border>" +
        "</borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"4\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
        "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\">" +
        "<alignment horizontal=\"left\" vertical=\"top\" wrapText=\"1\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
        "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"常规\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}
