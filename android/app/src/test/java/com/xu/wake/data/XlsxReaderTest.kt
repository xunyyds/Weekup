package com.xu.wake.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxReaderTest {

    private fun open(): java.io.InputStream {
        val stream = javaClass.classLoader!!.getResourceAsStream("sample-timetable.xlsx")
        requireNotNull(stream) { "测试资源缺失" }
        return stream
    }

    @Test
    fun readsSharedStringsIntoTypedGrid() {
        val grid = open().use { XlsxReader.read(it) }
        assertTrue("至少要有表头和数据行", grid.size >= 3)
        assertEquals("节次", grid[0][0])
        assertEquals("星期一", grid[0][1])
        assertEquals("星期二", grid[0][2])
        assertEquals("第一二节 08:20-10:00", grid[1][0])
        assertEquals("JAVA程序设计基础 6-19周 致广楼313", grid[1][1])
    }

    @Test
    fun gridFeedsTimetableParser() {
        val grid = open().use { XlsxReader.read(it) }
        val result = XlsxTimetable.parse(grid)

        assertEquals(3, result.courses.size)
        val java = result.courses.first { it.name.contains("JAVA") }
        assertEquals(1, java.weekday)
        assertEquals((6..19).toList(), java.weeks)
        val politics = result.courses.first { it.name.contains("思想道德") }
        assertEquals(2, politics.weekday)
    }

    @Test
    fun nonXlsxIsRejected() {
        val bad = runCatching {
            java.io.ByteArrayInputStream("not a zip".toByteArray()).use { XlsxReader.read(it) }
        }
        assertTrue("乱码输入应被拒绝", bad.isFailure)
    }
}
