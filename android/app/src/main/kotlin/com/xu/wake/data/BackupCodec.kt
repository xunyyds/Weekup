package com.xu.wake.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

                                                                                           
object BackupCodec {
    const val MAX_CHARS = 2_000_000

    fun encode(data: AppData): String {
        data.validate()
        val s = data.settings
        val settings = JSONObject().apply {
            put("school", s.school); put("schoolUrl", s.schoolUrl)
            put("theme", s.theme); put("navigationStyle", s.navigationStyle)
            put("themePalette", s.themePalette); put("dynamicColor", s.dynamicColor)
            put("motionSpeed", s.motionSpeed); put("reduceMotion", s.reduceMotion); put("transitionStyle", s.transitionStyle); put("autoHideBars", s.autoHideBars)
            put("fontScale", s.fontScale); put("density", s.density); put("cellTextAlign", s.cellTextAlign)
            put("firstDay", s.firstDay); put("lastDay", s.lastDay); put("weekStart", s.weekStart)
            put("currentWeek", s.currentWeek); put("weekCount", s.weekCount)
            put("defaultColor", s.defaultColor); put("reminderMinutes", s.reminderMinutes); put("reminders", s.reminders)
            put("start", "${s.start}T00:00:00.000"); put("end", "${s.end}T00:00:00.000")
            put("periods", JSONArray().apply { s.periods.forEach { p -> put(JSONObject().put("period", p.period).put("startMinutes", p.startMinutes).put("endMinutes", p.endMinutes)) } })
        }
        return JSONObject().put("version", 1).put("settings", settings)
            .put("courses", JSONArray().apply { data.courses.forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("weekday", c.weekday)
                    .put("startPeriod", c.startPeriod).put("endPeriod", c.endPeriod)
                    .put("weeks", JSONArray(c.weeks.distinct().sorted())).put("teacher", c.teacher).put("location", c.location))
            } }).put("colors", JSONObject(data.colors)).toString().also { require(it.length <= MAX_CHARS) { "课表文件过大" } }
    }

    fun decode(source: String): AppData {
        require(source.length <= MAX_CHARS) { "课表文件过大" }
        try {
            
            val tokener = org.json.JSONTokener(source)
            val root = tokener.nextValue() as? JSONObject ?: error("课表必须是对象")
            require(tokener.nextClean() == '\u0000') { "JSON 尾部存在额外内容" }
            require(root.integer("version") == 1) { "不支持的课表文件版本" }
            val j = root.getJSONObject("settings")
            val defaults = AppSettings()
            val periods = if (!j.has("periods")) defaults.periods else j.getJSONArray("periods").let { array ->
                if (array.length() == 0) defaults.periods else (0 until array.length()).map { i ->
                    val p = array.getJSONObject(i)
                    PeriodTime(p.integer("period"), p.integer("startMinutes"), p.integer("endMinutes"))
                }
            }
            val s = AppSettings(
                school = j.string("school", ""), schoolUrl = j.string("schoolUrl", ""),
                theme = j.string("theme", "system"), navigationStyle = j.string("navigationStyle", "default").let { if (it in listOf("default", "floating", "liquid")) it else "default" },
                fontScale = j.number("fontScale", 1.0), density = j.number("density", 0.85),
                cellTextAlign = j.string("cellTextAlign", defaults.cellTextAlign)
                    .let { if (it in listOf("start", "center", "end")) it else defaults.cellTextAlign },
                firstDay = j.intOr("firstDay", 1), lastDay = j.intOr("lastDay", 7), weekStart = j.intOr("weekStart", 1),
                currentWeek = j.intOr("currentWeek", 1), weekCount = j.intOr("weekCount", 20),
                defaultColor = j.longOr("defaultColor", 0xffd9e9deL), reminderMinutes = j.intOr("reminderMinutes", 10),
                reminders = if (j.has("reminders")) (j.get("reminders") as? Boolean ?: error("reminders 必须为布尔值")) else false,
                start = if (j.has("start")) date(j.string("start", "")) else defaults.start,
                end = if (j.has("end")) date(j.string("end", "")) else defaults.end,
                periods = periods,
                motionSpeed = j.number("motionSpeed", defaults.motionSpeed),
                reduceMotion = if(j.has("reduceMotion")) (j.get("reduceMotion") as? Boolean ?: error("reduceMotion 必须为布尔值")) else false,
                transitionStyle = j.string("transitionStyle", defaults.transitionStyle),
            autoHideBars = if(j.has("autoHideBars")) (j.get("autoHideBars") as? Boolean ?: error("autoHideBars 必须为布尔值")) else false,
                themePalette = j.string("themePalette", defaults.themePalette),
                dynamicColor = if (j.has("dynamicColor")) (j.get("dynamicColor") as? Boolean ?: error("dynamicColor 必须为布尔值")) else defaults.dynamicColor,
            )
            val array = root.getJSONArray("courses")
            val courses = (0 until array.length()).map { i ->
                val c = array.getJSONObject(i)
                val weeks = c.getJSONArray("weeks")
                CourseSession(c.string("id"), c.string("name"), c.integer("weekday"), c.integer("startPeriod"), c.integer("endPeriod"),
                    (0 until weeks.length()).map { n -> exactLong(weeks.get(n)).also { require(it in 1..60) }.toInt() }.distinct().sorted(),
                    c.string("teacher", ""), c.string("location", ""))
            }
            val colors = if (!root.has("colors")) emptyMap() else root.getJSONObject("colors").let { c ->
                c.keys().asSequence().associateWith { exactLong(c.get(it)) }
            }
            return AppData(s, courses, colors)
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的课表文件：${e.message}", e)
        }
    }

    private fun date(value: String): LocalDate {
        
        require(Regex("\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?)?").matches(value))
        return LocalDate.parse(value.take(10))
    }
    private fun exactLong(value: Any): Long {
        require(value is Byte || value is Short || value is Int || value is Long) { "必须为整数" }
        return (value as Number).toLong()
    }
    private fun JSONObject.integer(key: String): Int = exactLong(get(key)).also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JSONObject.intOr(key: String, default: Int) = if (has(key)) integer(key) else default
    private fun JSONObject.longOr(key: String, default: Long) = if (has(key)) exactLong(get(key)) else default
    private fun JSONObject.string(key: String): String = get(key) as? String ?: error("$key 必须为字符串")
    private fun JSONObject.string(key: String, default: String) = if (has(key)) string(key) else default
    private fun JSONObject.number(key: String, default: Double): Double = if (!has(key)) default else (get(key) as? Number ?: error("$key 必须为数字")).toDouble()
}
