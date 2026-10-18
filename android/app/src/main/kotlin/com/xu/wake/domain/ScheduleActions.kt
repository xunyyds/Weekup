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
        val periods=(data.settings.periods.associateBy { it.period }+imported.periods.associateBy { it.period }).values.sortedBy { it.period }
        return data.copy(settings=data.settings.copy(
            school=imported.school?.takeIf { it.isNotBlank() } ?: data.settings.school,
            weekCount=count,currentWeek=(imported.currentWeek ?: data.settings.currentWeek).coerceIn(1,count),periods=periods,
        ),courses=imported.courses,colors=emptyMap())
    }
}
