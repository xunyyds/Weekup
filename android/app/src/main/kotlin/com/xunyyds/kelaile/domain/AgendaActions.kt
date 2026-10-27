package com.xunyyds.kelaile.domain

import com.xunyyds.kelaile.data.*

/**
 * 日程与备忘的纯业务动作。
 *
 * 与 [ScheduleActions] 一样不依赖 Android：所有函数接收 [AppData] 返回新 [AppData]，
 * 交给 ScheduleService 落盘。编号由调用方生成，方便测试固定数据。
 */
object AgendaActions {

    fun saveTodo(data: AppData, todo: TodoItem): AppData = data.copy(
        todos = data.todos.filterNot { it.id == todo.id } + todo,
    )

    fun toggleTodo(data: AppData, id: String): AppData = data.copy(
        todos = data.todos.map { if (it.id == id) it.copy(done = !it.done) else it },
    )

    fun deleteTodo(data: AppData, id: String): AppData =
        data.copy(todos = data.todos.filterNot { it.id == id })

    fun saveEvent(data: AppData, event: ScheduleEvent): AppData = data.copy(
        events = data.events.filterNot { it.id == event.id } + event,
    )

    fun deleteEvent(data: AppData, id: String): AppData =
        data.copy(events = data.events.filterNot { it.id == id })

    /**
     * 新增或覆盖备忘。空白内容直接删除，避免列表里留下打不开的空条目。
     * [now] 由调用方传入，测试时可固定。
     */
    fun saveMemo(data: AppData, id: String, title: String, body: String, now: Long): AppData {
        val cleanTitle = title.trim().take(MEMO_TITLE_MAX)
        val cleanBody = body.trim().take(MEMO_BODY_MAX)
        if (cleanTitle.isEmpty() && cleanBody.isEmpty()) return deleteMemo(data, id)
        val memo = Memo(id, cleanTitle, cleanBody, now)
        return data.copy(memos = data.memos.filterNot { it.id == id } + memo)
    }

    fun deleteMemo(data: AppData, id: String): AppData =
        data.copy(memos = data.memos.filterNot { it.id == id })
}
