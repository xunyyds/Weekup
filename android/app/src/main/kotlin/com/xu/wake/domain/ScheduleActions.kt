package com.xu.wake.domain

import com.xu.wake.data.*

                                                                                          
object ScheduleActions {
    fun saveCourse(data:AppData, course:CourseSession, color:Long):AppData = data.copy(
        courses=data.courses.filterNot { it.id==course.id }+course,
        colors=data.colors+(course.id to color),
    )
    fun deleteCourse(data:AppData,id:String):AppData=data.copy(courses=data.courses.filterNot { it.id==id },colors=data.colors-id)
    fun importSchool(data:AppData,imported:SchoolImport):AppData {
        require(imported.courses.isNotEmpty()) { "没有可导入的课程" }
        val count=maxOf(imported.weekCount ?: data.settings.weekCount,imported.courses.maxOf { it.weeks.max() })
        /*
         * 导入的作息来自站点，优先采信。本地多出来的节次只在排在导入节次之后时才保留；
         * 直接合并会让本地较早的节次与导入节次时间重叠，触发设置校验，整次导入以
         * 「节次时间重叠」失败。
         */
        val periods=if(imported.periods.isEmpty())data.settings.periods else run {
            val merged=imported.periods.associateBy { it.period }.toMutableMap()
            val lastEnd=imported.periods.maxOf { it.endMinutes }
            data.settings.periods.filter { it.period !in merged && it.startMinutes>=lastEnd }
                .forEach { merged[it.period]=it }
            merged.values.sortedBy { it.period }
        }
        return data.copy(settings=data.settings.copy(
            school=imported.school?.takeIf { it.isNotBlank() } ?: data.settings.school,
            weekCount=count,currentWeek=(imported.currentWeek ?: data.settings.currentWeek).coerceIn(1,count),periods=periods,
        ),courses=imported.courses,colors=emptyMap())
    }

    /** 拖动排课时的冲突处理方式。 */
    enum class MoveMode { SWAP, OVERWRITE }

    private fun occupying(data: AppData, day: Int, start: Int, end: Int) =
        data.courses.filter { it.weekday == day && it.startPeriod <= end && it.endPeriod >= start }

    /**
     * 把一门课挪到 [day] 的第 [startPeriod] 节，保持原有节次跨度。
     *
     * SWAP 与目标位置上的课互换（各自保留自己的跨度），OVERWRITE 先清掉目标位置上的课。
     */
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
            /* 交换只针对一门课：有多个目标时与最先开始的那门互换，语义仍然明确。 */
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

    /** 整列移动：把 [from] 日的课与 [to] 日对调（SWAP）或覆盖过去（OVERWRITE）。 */
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

    /**
     * 整行移动：把起始于第 [fromPeriod] 节的课平移到第 [toPeriod] 节，跨度保持不变。
     *
     * SWAP 让目标行整行换到起始行，OVERWRITE 先清掉目标行。
     */
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

    /** 目标位置上是否有课，用于决定要不要弹冲突提示。 */
    fun hasConflict(data: AppData, day: Int, startPeriod: Int, endPeriod: Int): Boolean =
        occupying(data, day, startPeriod, endPeriod).isNotEmpty()

    fun hasDayConflict(data: AppData, day: Int): Boolean = data.courses.any { it.weekday == day }

    fun hasRowConflict(data: AppData, period: Int): Boolean = data.courses.any { it.startPeriod == period }
}
