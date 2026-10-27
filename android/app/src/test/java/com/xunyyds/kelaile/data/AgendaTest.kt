package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaTest {
    private val day = LocalDate.of(2026, 3, 2)

    private fun sample(): AppData = AppData(
        todos = listOf(
            TodoItem("t1", "交作业", day),
            TodoItem("t2", "复习", day, done = true),
            TodoItem("t3", "明天的", day.plusDays(1)),
        ),
        events = listOf(
            ScheduleEvent("e1", "组会", day, 600, 660, "带材料"),
            ScheduleEvent("e2", "早课", day, 480, 520),
            ScheduleEvent("e3", "明天的事", day.plusDays(1), 600, 620),
        ),
        memos = listOf(
            Memo("m1", "购物清单", "牛奶", 100L),
            Memo("m2", "想法", "做个小工具", 200L),
        ),
    )

    @Test fun todosOnOnlyReturnsRequestedDay() {
        val today = todosOn(sample(), day)
        assertEquals(listOf("t1", "t2"), today.map { it.id })
    }

    @Test fun todosOnPutsUndoneFirst() {
        val today = todosOn(sample(), day)
        assertEquals(false, today.first().done)
    }

    @Test fun eventsOnSortsByStartTime() {
        val today = eventsOn(sample(), day)
        assertEquals(listOf("e1", "e2").sorted(), today.map { it.id }.sorted())
        assertTrue(today.first().startMinutes <= today.last().startMinutes)
        assertEquals("e2", today.first().id)
    }

    @Test fun sortedMemosNewestFirst() {
        assertEquals(listOf("m2", "m1"), sortedMemos(sample()).map { it.id })
    }

    @Test fun backupRoundTripKeepsAgenda() {
        val data = sample()
        val restored = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data.todos, restored.todos)
        assertEquals(data.events, restored.events)
        assertEquals(data.memos, restored.memos)
    }

    @Test fun backupWithoutAgendaFieldsLoadsEmpty() {
        val legacy = """{"version":1,"settings":{},"courses":[],"colors":{}}"""
        val restored = BackupCodec.decode(legacy)
        assertEquals(emptyList<TodoItem>(), restored.todos)
        assertEquals(emptyList<ScheduleEvent>(), restored.events)
        assertEquals(emptyList<Memo>(), restored.memos)
    }

    @Test fun invalidTodoTitleRejected() {
        assertThrows(IllegalArgumentException::class.java) { TodoItem("x", "  ", day) }
    }

    @Test fun invalidEventRangeRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScheduleEvent("x", "事件", day, 660, 600) }
    }
}
