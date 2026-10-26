package com.xunyyds.kelaile.domain

import com.xunyyds.kelaile.data.*

object ScheduleActions {
    fun saveCourse(data:AppData, course:CourseSession, color:Long):AppData = data.copy(
        courses=data.courses.filterNot { it.id==course.id }+course,
        colors=data.colors+(course.id to color),
    )
    fun deleteCourse(data:AppData,id:String):AppData=data.copy(courses=data.courses.filterNot { it.id==id },colors=data.colors-id)

    /** 只更新备注，保留课程其余字段与颜色。 */
    fun setNote(data: AppData, id: String, note: String): AppData = data.copy(
        courses = data.courses.map { if (it.id == id) it.copy(note = note.take(NOTE_MAX)) else it },
    )
    fun importSchool(data:AppData,imported:SchoolImport):AppData {
        require(imported.courses.isNotEmpty()) { "没有可导入的课程" }
        val count=maxOf(imported.weekCount ?: data.settings.weekCount,imported.courses.maxOf { it.weeks.max() })

        val periods=if(imported.periods.isEmpty())data.settings.periods else run {
            val base=imported.periods.sortedBy { it.period }
            val used=base.map { it.period }.toSet()
            val ceiling=base.maxOf { it.period }
            val lastEnd=base.maxOf { it.endMinutes }
            val extra=data.settings.periods
                .filter { it.period !in used && it.startMinutes>=lastEnd }
                .sortedBy { it.startMinutes }
            base+extra.mapIndexed { index, period -> period.copy(period=ceiling+1+index) }
        }
        return data.copy(settings=data.settings.copy(
            school=imported.school?.takeIf { it.isNotBlank() } ?: data.settings.school,
            weekCount=count,currentWeek=(imported.currentWeek ?: data.settings.currentWeek).coerceIn(1,count),periods=periods,
        ),courses=imported.courses,colors=emptyMap())
    }

    enum class MoveMode { SWAP, OVERWRITE }

    private fun occupying(data: AppData, day: Int, start: Int, end: Int) =
        data.courses.filter { it.weekday == day && it.startPeriod <= end && it.endPeriod >= start }

    fun moveCourse(data: AppData, id: String, day: Int, startPeriod: Int, mode: MoveMode): AppData {
        val course = data.courses.firstOrNull { it.id == id } ?: return data
        require(day in 1..7 && startPeriod in 1..100) { "课程参数无效" }
        val span = course.endPeriod - course.startPeriod
        val end = (startPeriod + span).coerceAtMost(100)
        if (course.weekday == day && course.startPeriod == startPeriod) return data
        val targets = occupying(data, day, startPeriod, end).filter { it.id != id }
        var courses = data.courses
        when (mode) {
            MoveMode.OVERWRITE -> if (targets.isNotEmpty()) {
                val gone = targets.map { it.id }.toSet()
                courses = courses.filterNot { it.id in gone }
            }

            MoveMode.SWAP -> targets.minByOrNull { it.startPeriod }?.let { target ->
                courses = courses.map {
                    when (it.id) {
                        target.id -> it.copy(weekday = course.weekday, startPeriod = course.startPeriod,
                            endPeriod = (course.startPeriod + (target.endPeriod - target.startPeriod)).coerceAtMost(100))
                        else -> it
                    }
                }
            }
        }
        return data.copy(courses = courses.map {
            if (it.id == id) it.copy(weekday = day, startPeriod = startPeriod, endPeriod = end) else it
        })
    }

    fun moveDay(data: AppData, from: Int, to: Int, mode: MoveMode): AppData {
        require(from in 1..7 && to in 1..7) { "星期参数无效" }
        if (from == to) return data
        val kept = if (mode == MoveMode.OVERWRITE) data.courses.filterNot { it.weekday == to } else data.courses
        val moved = kept.map {
            when (it.weekday) {
                from -> it.copy(weekday = to)
                to -> if (mode == MoveMode.SWAP) it.copy(weekday = from) else it
                else -> it
            }
        }
        return data.copy(courses = moved)
    }

    fun moveRow(data: AppData, fromPeriod: Int, toPeriod: Int, mode: MoveMode): AppData {
        require(fromPeriod in 1..100 && toPeriod in 1..100) { "节次参数无效" }
        if (fromPeriod == toPeriod) return data
        val delta = toPeriod - fromPeriod
        val kept = if (mode == MoveMode.OVERWRITE) data.courses.filterNot { it.startPeriod == toPeriod } else data.courses
        val moved = kept.mapNotNull { course ->
            when (course.startPeriod) {
                fromPeriod -> {
                    val start = (course.startPeriod + delta).coerceIn(1, 100)
                    val end = (course.endPeriod + delta).coerceIn(start, 100)
                    course.copy(startPeriod = start, endPeriod = end)
                }
                toPeriod -> if (mode == MoveMode.SWAP) {
                    val back = course.startPeriod - delta
                    val start = back.coerceIn(1, 100)
                    course.copy(startPeriod = start, endPeriod = (course.endPeriod - delta).coerceIn(start, 100))
                } else course
                else -> course
            }
        }
        return data.copy(courses = moved)
    }

    fun hasConflict(data: AppData, day: Int, startPeriod: Int, endPeriod: Int): Boolean =
        occupying(data, day, startPeriod, endPeriod).isNotEmpty()

    fun hasDayConflict(data: AppData, day: Int): Boolean = data.courses.any { it.weekday == day }

    fun hasRowConflict(data: AppData, period: Int): Boolean = data.courses.any { it.startPeriod == period }
}
