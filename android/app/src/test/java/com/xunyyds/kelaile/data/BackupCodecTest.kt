package com.xunyyds.kelaile.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private fun sample() = AppData(
        AppSettings(start = LocalDate.of(2026, 2, 23), end = LocalDate.of(2026, 7, 12)),
        listOf(CourseSession("old-id", "高数", 1, 1, 2, listOf(1, 3, 5), "教师", "A101")),
        mapOf("高数" to 0xffd9e9deL),
    )
    @Test fun roundTripVersionOne() {
        assertEquals(sample(), BackupCodec.decode(BackupCodec.encode(sample())))
    }
    @Test fun decodesOldFlutterBackup() {
        val old = """{"version":1,"settings":{"start":"2026-02-23T15:30:00.000","end":"2026-07-12T00:00:00.000","periods":[]},"courses":[{"id":"1","name":"数学","weekday":2,"startPeriod":1,"endPeriod":2,"weeks":[3,1,3]}],"colors":{"数学":4294967295}}"""
        val data = BackupCodec.decode(old)
        assertEquals(LocalDate.of(2026, 2, 23), data.settings.start)
        assertEquals(listOf(1, 3), data.courses.single().weeks)
        assertEquals(4294967295L, data.colors["数学"])
        assertEquals(defaultDayPeriods(), data.settings.periods)
        assertEquals("system", data.settings.theme)
        assertEquals("blue", data.settings.themePalette)
        assertFalse(data.settings.dynamicColor)
    }
    @Test fun roundTripsAllPalettesAndDynamicColorWithoutChangingTheme() {
        listOf("blue", "green", "purple", "orange", "rose", "teal").forEach { palette ->
            listOf("system", "light", "dark").forEach { theme ->
                val data = sample().copy(settings = sample().settings.copy(theme = theme, themePalette = palette, dynamicColor = true))
                assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
            }
        }
    }
    @Test fun rejectsUnknownPaletteAndNonBooleanDynamicColor() {
        val root = org.json.JSONObject(BackupCodec.encode(sample()))
        root.getJSONObject("settings").put("themePalette", "unknown")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
        root.getJSONObject("settings").put("themePalette", "blue").put("dynamicColor", "true")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(root.toString()) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(themePalette = "unknown") }
    }
    @Test fun roundTripsCellTextAlignAndDefaultsToStart() {
        listOf("start", "center", "end").forEach { align ->
            val data = sample().copy(settings = sample().settings.copy(cellTextAlign = align))
            assertEquals(align, BackupCodec.decode(BackupCodec.encode(data)).settings.cellTextAlign)
        }
        val root = org.json.JSONObject(BackupCodec.encode(sample()))
        root.getJSONObject("settings").put("cellTextAlign", "middle")
        assertEquals("start", BackupCodec.decode(root.toString()).settings.cellTextAlign)
    }
    @Test fun refusesMalformedOrUnsupportedBackup() {
        val valid = BackupCodec.encode(sample())
        listOf("{}", "[]", valid + " trailing", valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"weekday\":1", "\"weekday\":1.5"),
            valid.replace("2026-02-23", "2026-02-30"),
            valid.replace("\"weeks\":[1,3,5]", "\"weeks\":[61]"),
            valid.replace("\"colors\":{\"高数\":4292479454}", "\"colors\":{\"高数\":-1}") + "x"
        ).forEach { assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(it) } }
    }
}
