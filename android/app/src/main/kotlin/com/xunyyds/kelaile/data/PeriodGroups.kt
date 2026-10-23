package com.xunyyds.kelaile.data

object PeriodGroups {

    data class Group(
        val startIndex: Int,
        val endIndex: Int,
        val startMinutes: Int,
        val endMinutes: Int,
    ) {
        val periodCount: Int get() = endIndex - startIndex + 1
    }

    fun of(periods: List<PeriodTime>): List<Group> {
        if (periods.isEmpty()) return emptyList()
        val groups = mutableListOf<Group>()
        var startIndex = 0
        for (i in 1..periods.size) {
            val broken = i == periods.size || periods[i].startMinutes != periods[i - 1].endMinutes
            if (broken) {
                groups += Group(
                    startIndex = startIndex,
                    endIndex = i - 1,
                    startMinutes = periods[startIndex].startMinutes,
                    endMinutes = periods[i - 1].endMinutes,
                )
                startIndex = i
            }
        }
        return groups
    }

    fun indexMap(periods: List<PeriodTime>): Map<Int, Group> =
        of(periods).flatMap { g -> (g.startIndex..g.endIndex).map { it to g } }.toMap()
}
