package com.xunyyds.kelaile.domain

import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.CourseSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveActionsTest {
    private fun course(id: String, day: Int, start: Int, end: Int) =
        CourseSession(id, "课程" + id, day, start, end, listOf(1, 2, 3))

    private fun data(vararg cs: CourseSession) = AppData(courses = cs.toList())

    @Test fun overwriteClearsTheTargetSlot() {
        val d = data(course("a", 1, 1, 2), course("b", 3, 1, 2))
        val moved = ScheduleActions.moveCourse(d, "a", 3, 1, ScheduleActions.MoveMode.OVERWRITE)
        assertEquals(1, moved.courses.size)
        assertEquals(3, moved.courses.single().weekday)
        assertEquals("a", moved.courses.single().id)
    }

    @Test fun swapExchangesTheTwoSlots() {
        val d = data(course("a", 1, 1, 2), course("b", 3, 5, 6))
        val moved = ScheduleActions.moveCourse(d, "a", 3, 5, ScheduleActions.MoveMode.SWAP)
        val a = moved.courses.first { it.id == "a" }
        val b = moved.courses.first { it.id == "b" }
        assertEquals(3, a.weekday); assertEquals(5, a.startPeriod)
        assertEquals(1, b.weekday); assertEquals(1, b.startPeriod)

        assertEquals(2, b.endPeriod)
        assertEquals("跨度保持不变", 1, b.endPeriod - b.startPeriod)
    }

    @Test fun movingIntoAnEmptySlotKeepsBothCourses() {
        val d = data(course("a", 1, 1, 2), course("b", 3, 5, 6))
        val moved = ScheduleActions.moveCourse(d, "a", 5, 3, ScheduleActions.MoveMode.SWAP)
        assertEquals(2, moved.courses.size)
        val a = moved.courses.first { it.id == "a" }
        assertEquals(5, a.weekday); assertEquals(3, a.startPeriod); assertEquals(4, a.endPeriod)
    }

    @Test fun daySwapMovesBothColumns() {
        val d = data(course("a", 1, 1, 2), course("b", 2, 3, 4))
        val moved = ScheduleActions.moveDay(d, 1, 2, ScheduleActions.MoveMode.SWAP)
        assertEquals(2, moved.courses.first { it.id == "a" }.weekday)
        assertEquals(1, moved.courses.first { it.id == "b" }.weekday)
    }

    @Test fun dayOverwriteClearsTheTargetColumn() {
        val d = data(course("a", 1, 1, 2), course("b", 2, 3, 4), course("c", 2, 5, 6))
        val moved = ScheduleActions.moveDay(d, 1, 2, ScheduleActions.MoveMode.OVERWRITE)
        assertEquals(1, moved.courses.size)
        assertEquals("a", moved.courses.single().id)
        assertEquals(2, moved.courses.single().weekday)
    }

    @Test fun rowMoveShiftsByThePeriodDelta() {
        val d = data(course("a", 1, 3, 4), course("b", 2, 5, 6))
        val moved = ScheduleActions.moveRow(d, 3, 7, ScheduleActions.MoveMode.SWAP)
        val a = moved.courses.first { it.id == "a" }
        assertEquals(7, a.startPeriod); assertEquals(8, a.endPeriod)
    }

    @Test fun rowOverwriteClearsTheTargetRow() {
        val d = data(course("a", 1, 3, 4), course("b", 2, 7, 8))
        val moved = ScheduleActions.moveRow(d, 3, 7, ScheduleActions.MoveMode.OVERWRITE)
        assertEquals(1, moved.courses.size)
        assertEquals("a", moved.courses.single().id)
        assertEquals(7, moved.courses.single().startPeriod)
    }

    @Test fun conflictDetectionMatchesTheOccupiedSpan() {
        val d = data(course("a", 1, 1, 2))
        assertTrue(ScheduleActions.hasConflict(d, 1, 1, 2))
        assertTrue("跨越也要算冲突", ScheduleActions.hasConflict(d, 1, 2, 3))
        assertFalse(ScheduleActions.hasConflict(d, 1, 3, 4))
        assertFalse(ScheduleActions.hasConflict(d, 2, 1, 2))
        assertTrue(ScheduleActions.hasDayConflict(d, 1))
        assertFalse(ScheduleActions.hasDayConflict(d, 4))
        assertTrue(ScheduleActions.hasRowConflict(d, 1))
        assertFalse(ScheduleActions.hasRowConflict(d, 9))
    }

    @Test fun droppingWhereItAlreadyIsChangesNothing() {
        val d = data(course("a", 1, 1, 2))
        assertEquals(d.courses, ScheduleActions.moveCourse(d, "a", 1, 1, ScheduleActions.MoveMode.SWAP).courses)
        assertEquals(d.courses, ScheduleActions.moveDay(d, 1, 1, ScheduleActions.MoveMode.SWAP).courses)
        assertEquals(d.courses, ScheduleActions.moveRow(d, 1, 1, ScheduleActions.MoveMode.SWAP).courses)
    }
}
