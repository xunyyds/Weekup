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
}
