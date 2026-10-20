package com.xu.wake.data

/**
 * 把相邻节次合并成连续时间段（课表左侧时间栏的显示口径）。
 *
 * 参照时光课表的做法：1、2 节之间没有课间空隙时合成一段，只在段的**首行**
 * 标开始时间、**末行**标结束时间，中间行不再重复标时间；3、4 节、5、6 节
 * 依此类推。这样左栏从「每节各标一行时间」变成「一组 08:20 起、10:00 止」，
 * 一眼能看出上午第一大节。
 *
 * 判定「连续」只看节次是否相接：
 * - 上一节的结束时刻恰好等于下一节的开始时刻 → 连续，合成一段；
 * - 中间有空隙（课间）或时间有重叠 → 断开，各段独立标注。
 *
 * 节次序号不要求连续（`period` 可以跳号，例如 1、2、5），只要时间相接就算一段。
 */
object PeriodGroups {

    /**
     * 一个连续时间段。
     *
     * @param startIndex 段内首节在 `periods` 中的下标
     * @param endIndex   段内末节在 `periods` 中的下标（含）
     * @param startMinutes 段开始时刻
     * @param endMinutes   段结束时刻
     */
    data class Group(
        val startIndex: Int,
        val endIndex: Int,
        val startMinutes: Int,
        val endMinutes: Int,
    ) {
        val periodCount: Int get() = endIndex - startIndex + 1
    }

    /**
     * 按 [periods] 的顺序切分连续时间段。
     *
     * [periods] 需已按上课先后排序；乱序时按传入顺序处理。
     */
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

    /** 每个下标所属段的起止下标，便于按行查「这一行要不要标时间」。 */
    fun indexMap(periods: List<PeriodTime>): Map<Int, Group> =
        of(periods).flatMap { g -> (g.startIndex..g.endIndex).map { it to g } }.toMap()
}
