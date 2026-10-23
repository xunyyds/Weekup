package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private val settings = AppSettings(periods = listOf(
        PeriodTime(1, 8 * 60, 8 * 60 + 40),
        PeriodTime(2, 8 * 60 + 45, 9 * 60 + 25),
        PeriodTime(3, 9 * 60 + 40, 10 * 60 + 20),
        PeriodTime(4, 10 * 60 + 25, 11 * 60 + 5),
        PeriodTime(5, 11 * 60 + 10, 11 * 60 + 50),
    ))

    private val courses = listOf(
        CourseSession("c1", "高等数学", 1, 1, 1, (1..16).toList(), location = "教一楼203"),
        CourseSession("c2", "大学物理", 3, 3, 4, (2..18).toList(), location = "实验楼305"),
        CourseSession("c3", "游泳", 5, 5, 5, listOf(1, 3, 5, 7, 9, 11, 13, 15), location = "体育馆"),
    )

    private fun data(courses: List<CourseSession>) = AppData(settings = settings.copy(weekCount = 20), courses = courses)

    private fun read(bytes: ByteArray): List<List<String?>> =
        ByteArrayInputStream(bytes).use { XlsxReader.read(it) }

    @Test
    fun writesRequiredParts() {
        val bytes = XlsxWriter.writeTimetable(data(courses))
        val names = mutableSetOf<String>()
        ByteArrayInputStream(bytes).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                }
            }
        }
        assertTrue(names.containsAll(listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/worksheets/sheet1.xml",
        )))
    }

    @Test
    fun roundTripsThroughReaderAndParser() {
        val grid = read(XlsxWriter.writeTimetable(data(courses)))
        assertEquals("节次", grid[0][0])
        assertEquals("星期一", grid[0][1])
        assertEquals("星期日", grid[0][7])

        val parsed = XlsxTimetable.parse(grid).courses
        assertEquals(3, parsed.size)

        val math = parsed.first { it.name == "高等数学" }
        assertEquals(1, math.weekday)
        assertEquals(1, math.startPeriod)
        assertEquals(1, math.endPeriod)
        assertEquals((1..16).toList(), math.weeks)
        assertEquals("教一楼203", math.location)

        val physics = parsed.first { it.name == "大学物理" }
        assertEquals(3, physics.weekday)
        assertEquals(3, physics.startPeriod)
        assertEquals(4, physics.endPeriod)
        assertEquals((2..18).toList(), physics.weeks)
        assertEquals("实验楼305", physics.location)

        val swim = parsed.first { it.name == "游泳" }
        assertEquals(5, swim.weekday)
        assertEquals(5, swim.startPeriod)
        assertEquals(5, swim.endPeriod)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), swim.weeks)
        assertEquals("体育馆", swim.location)
    }

    @Test
    fun periodLabelsCarryTimes() {
        val parsed = XlsxTimetable.parse(read(XlsxWriter.writeTimetable(data(courses))))
        val first = parsed.periods.first { it.period == 1 }
        assertEquals(8 * 60, first.startMinutes)
        assertEquals(8 * 60 + 40, first.endMinutes)

        val span = parsed.periods.filter { it.period in 3..4 }
        assertEquals(2, span.size)
        assertEquals(9 * 60 + 40, span.first().startMinutes)
        assertEquals(11 * 60 + 5, span.last().endMinutes)
    }

    @Test
    fun emptyScheduleIsStillReadable() {
        val bytes = XlsxWriter.writeTimetable(data(emptyList()))
        val grid = read(bytes)
        assertEquals(1 + settings.periods.size, grid.size)
        assertEquals("节次", grid[0][0])
        assertEquals("第1节 08:00-08:40", grid[1][0])
        assertThrows(IllegalArgumentException::class.java) { XlsxTimetable.parse(grid) }
    }

    @Test
    fun escapingKeepsNamesIntact() {
        val tricky = listOf(CourseSession("t1", "编译原理<>\"&'", 2, 2, 2, listOf(4), location = "教学楼A&B"))
        val grid = read(XlsxWriter.writeTimetable(data(tricky)))
        val parsed = XlsxTimetable.parse(grid).courses.single()
        assertEquals("编译原理<>\"&'", parsed.name)
        assertEquals(2, parsed.weekday)
        assertEquals(listOf(4), parsed.weeks)
        assertEquals("教学楼A&B", parsed.location)
    }

    @Test
    fun outputIsDeterministic() {
        val first = XlsxWriter.writeTimetable(data(courses))
        val second = XlsxWriter.writeTimetable(data(courses))
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun everyPartIsWellFormedXml() {
        val parts = mutableMapOf<String, String>()
        ByteArrayInputStream(XlsxWriter.writeTimetable(data(courses))).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    parts[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
                }
            }
        }
        assertTrue(parts.size >= 5)
        for ((name, body) in parts) {
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.newSAXParser().parse(body.byteInputStream(), object : org.xml.sax.helpers.DefaultHandler() {})
            assertTrue("$name should declare UTF-8", body.startsWith("<?xml"))
        }
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("t=\"inlineStr\""))
    }

    @Test
    fun collisionsInOneRowStillRoundTrip() {
        val crowded = listOf(
            CourseSession("a", "线性代数", 2, 2, 2, listOf(3, 4), location = "教二楼101"),
            CourseSession("b", "体育", 2, 2, 2, listOf(5, 6), location = "操场"),
        )
        val parsed = XlsxTimetable.parse(read(XlsxWriter.writeTimetable(data(crowded)))).courses
        assertEquals(listOf("线性代数", "体育").toSet(), parsed.map { it.name }.toSet())
        assertTrue(parsed.all { it.weekday == 2 && it.startPeriod == 2 && it.endPeriod == 2 })
        assertEquals(listOf(3, 4), parsed.first { it.name == "线性代数" }.weeks)
        assertEquals(listOf(5, 6), parsed.first { it.name == "体育" }.weeks)
    }

    @Test
    fun absentCoursesStillProduceValidPackage() {
        val parts = mutableMapOf<String, String>()
        ByteArrayInputStream(XlsxWriter.writeTimetable(data(emptyList()))).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    parts[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
                }
            }
        }
        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
            ),
            parts.keys,
        )
        assertEquals(1, Regex("<sheet ").findAll(parts.getValue("xl/workbook.xml")).count())
    }
}
