package com.xunyyds.kelaile.data

import java.time.LocalDate
import java.util.UUID

/**
 * 日程与备忘的本地模型。
 *
 * 全部按日期挂在本地，不依赖网络；[AppData] 一起序列化，跟着整应用备份走。
 */

const val AGENDA_TITLE_MAX = 120

const val MEMO_TITLE_MAX = 80

const val MEMO_BODY_MAX = 5000

const val AGENDA_NOTE_MAX = 500

data class TodoItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val done: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "待办编号不能为空" }
        require(title.isNotBlank() && title.length <= AGENDA_TITLE_MAX) { "待办内容须为 1–$AGENDA_TITLE_MAX 字" }
    }
}

data class ScheduleEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val note: String = "",
) {
    init {
        require(id.isNotBlank()) { "事件编号不能为空" }
        require(title.isNotBlank() && title.length <= AGENDA_TITLE_MAX) { "事件标题须为 1–$AGENDA_TITLE_MAX 字" }
        require(startMinutes in 0..1439 && endMinutes in 1..1440 && startMinutes < endMinutes) { "事件时间无效" }
        require(note.length <= AGENDA_NOTE_MAX) { "事件备注最多 $AGENDA_NOTE_MAX 字" }
    }
}

data class Memo(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
) {
    init {
        require(id.isNotBlank()) { "备忘编号不能为空" }
        require(title.length <= MEMO_TITLE_MAX) { "备忘标题最多 $MEMO_TITLE_MAX 字" }
        require(body.length <= MEMO_BODY_MAX) { "备忘正文最多 $MEMO_BODY_MAX 字" }
        require(title.isNotBlank() || body.isNotBlank()) { "备忘不能为空" }
    }
}

fun newId(): String = UUID.randomUUID().toString()

fun todosOn(data: AppData, date: LocalDate): List<TodoItem> =
    data.todos.filter { it.date == date }.sortedWith(compareBy({ it.done }, { it.title }))

fun eventsOn(data: AppData, date: LocalDate): List<ScheduleEvent> =
    data.events.filter { it.date == date }.sortedBy { it.startMinutes }

/** 备忘列表：最近更新的排前面。 */
fun sortedMemos(data: AppData): List<Memo> = data.memos.sortedByDescending { it.updatedAt }
