package com.xunyyds.kelaile.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.AGENDA_NOTE_MAX
import com.xunyyds.kelaile.data.AGENDA_TITLE_MAX
import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.ScheduleEvent
import com.xunyyds.kelaile.data.TodoItem
import com.xunyyds.kelaile.data.eventsOn
import com.xunyyds.kelaile.data.newId
import com.xunyyds.kelaile.data.todosOn
import java.time.LocalDate
import java.time.LocalTime

/*
 * 日程页。
 *
 * 只做「今天」这一页：上方是日期，下面分「待办」与「事件」两组玻璃卡。
 * 待办是勾选式的轻清单，事件带起止时间与可选备注，两者都通过 KelaileDialog
 * 弹出输入框新增；勾选、删除直接交给上层回调，页面自身不持有业务数据。
 */

private val agendaWeekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

@Composable
fun AgendaPage(
    data: AppData,
    bottomInset: Dp,
    onSaveTodo: (TodoItem) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onSaveEvent: (ScheduleEvent) -> Unit,
    onDeleteEvent: (String) -> Unit,
) {
    // 进页时取一次「今天」，避免每帧调用系统时钟；跨天后重进页面自然刷新
    val today = remember { LocalDate.now() }
    val todos = todosOn(data, today)
    val events = eventsOn(data, today)
    val dateLine = "${today.monthValue} 月 ${today.dayOfMonth} 日 · ${agendaWeekdays[today.dayOfWeek.value - 1]}"

    var todoOpen by remember { mutableStateOf(false) }
    var todoDraft by remember { mutableStateOf("") }
    var todoError by remember { mutableStateOf<String?>(null) }

    var eventOpen by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventStart by remember { mutableStateOf("") }
    var eventEnd by remember { mutableStateOf("") }
    var eventNote by remember { mutableStateOf("") }
    var eventError by remember { mutableStateOf<String?>(null) }

    fun openTodo() {
        todoDraft = ""
        todoError = null
        todoOpen = true
    }
    // 新事件的起止时间预填「下一个半点 + 一小时」，多数情况只需改标题
    fun openEvent() {
        val now = LocalTime.now()
        val start = (((now.hour * 60 + now.minute) / 30) + 1) * 30
        val begin = start.coerceIn(0, 23 * 60 + 30)
        eventTitle = ""
        eventNote = ""
        eventStart = clock(begin)
        eventEnd = clock((begin + 60).coerceAtMost(24 * 60))
        eventError = null
        eventOpen = true
    }

    Column(Modifier.fillMaxSize()) {
        // 根页大标题：与设置页一致，裸放在壁纸上，不套玻璃胶囊
        Text(
            "日程",
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            "今日日程 · $dateLine",
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section("待办") {
                if (todos.isEmpty()) {
                    KelaileEmptyRow("今天还没有待办，加一条给自己一点方向")
                } else {
                    todos.forEach { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { onToggleTodo(todo.id) },
                            onDelete = { onDeleteTodo(todo.id) },
                        )
                    }
                }
                KelaileAddRow("添加待办") { openTodo() }
            }
            Section("事件") {
                if (events.isEmpty()) {
                    KelaileEmptyRow("今天没有安排事件，空着也挺好")
                } else {
                    events.forEach { event ->
                        EventRow(event = event, onDelete = { onDeleteEvent(event.id) })
                    }
                }
                KelaileAddRow("添加事件") { openEvent() }
            }
            KelailePageHint("待办与事件都记在当天日期上，日程页只展示今天这一页。")
            Spacer(Modifier.height(18.dp + bottomInset))
        }
    }

    // 新增待办
    KelaileDialog(
        visible = todoOpen,
        onDismiss = { todoOpen = false },
        title = "添加待办",
        message = "记在 $dateLine 的待办，完成后勾选即可。",
        confirmLabel = "添加",
        onConfirm = {
            val title = todoDraft.trim()
            if (title.isEmpty()) {
                todoError = "请先写下待办内容"
            } else {
                onSaveTodo(TodoItem(newId(), title.take(AGENDA_TITLE_MAX), today))
                todoOpen = false
            }
        },
        dismissLabel = "取消",
        content = {
            OutlinedTextField(
                value = todoDraft,
                onValueChange = {
                    todoDraft = it.take(AGENDA_TITLE_MAX)
                    todoError = null
                },
                label = { Text("待办内容") },
                placeholder = { Text("例如：交高数作业") },
                singleLine = true,
                isError = todoError != null,
                modifier = Modifier.fillMaxWidth(),
            )
            todoError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
    )

    // 新增事件
    KelaileDialog(
        visible = eventOpen,
        onDismiss = { eventOpen = false },
        title = "添加事件",
        message = "时间按 24 小时制填写，例如 08:00。",
        confirmLabel = "添加",
        onConfirm = {
            val title = eventTitle.trim()
            val start = parseAgendaClock(eventStart)
            val end = parseAgendaClock(eventEnd)
            val note = eventNote.trim()
            eventError = when {
                title.isEmpty() -> "请先写下事件标题"
                start == null -> "开始时间格式应为 HH:mm"
                start > 23 * 60 + 59 -> "开始时间需在 00:00–23:59 之间"
                end == null -> "结束时间格式应为 HH:mm"
                start >= end -> "结束时间要晚于开始时间"
                else -> null
            }
            if (eventError == null && start != null && end != null) {
                onSaveEvent(ScheduleEvent(newId(), title.take(AGENDA_TITLE_MAX), today, start, end, note))
                eventOpen = false
            }
        },
        dismissLabel = "取消",
        content = {
            OutlinedTextField(
                value = eventTitle,
                onValueChange = {
                    eventTitle = it.take(AGENDA_TITLE_MAX)
                    eventError = null
                },
                label = { Text("事件标题") },
                placeholder = { Text("例如：小组会议") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgendaTimeField(
                    label = "开始",
                    value = eventStart,
                    isError = eventError != null,
                    onChange = { eventStart = it; eventError = null },
                    modifier = Modifier.weight(1f),
                )
                AgendaTimeField(
                    label = "结束",
                    value = eventEnd,
                    isError = eventError != null,
                    onChange = { eventEnd = it; eventError = null },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = eventNote,
                onValueChange = {
                    eventNote = it.take(AGENDA_NOTE_MAX)
                    eventError = null
                },
                label = { Text("备注（可选）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            eventError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** 把 "HH:mm" 解析成当天分钟数；结束时间允许 24:00（等于当日末尾）。 */
/** 事件时间输入：既能手打 HH:mm，也能点右侧时钟走系统时间选择器。 */
@Composable
private fun AgendaTimeField(
    label: String,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(5)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        modifier = modifier,
        trailingIcon = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { choosing = true }
                    .semantics { contentDescription = "$label 选择时间" },
                contentAlignment = Alignment.Center,
            ) {
                KelaileGlyph(
                    KelaileGlyphKind.Clock,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 20.dp,
                )
            }
        },
    )
    if (choosing) {
        DisposableEffect(context) {
            val minutes = parseAgendaClock(value) ?: 8 * 60
            val picker = TimePickerDialog(
                context,
                { _, hour, minute ->
                    onChange(clock(hour * 60 + minute))
                    choosing = false
                },
                minutes / 60,
                minutes % 60,
                true,
            )
            picker.setOnDismissListener { choosing = false }
            picker.show()
            onDispose {
                picker.setOnDismissListener(null)
                picker.dismiss()
            }
        }
    }
}

private fun parseAgendaClock(raw: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").find(raw.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (minute !in 0..59) return null
    if (hour == 24) return if (minute == 0) 24 * 60 else null
    if (hour !in 0..23) return null
    return hour * 60 + minute
}

/**
 * 分区里的一条待办。
 *
 * 左侧圆圈是勾选态，中间正文已完成时加删除线并淡化，右侧是删除钮；
 * 点正文同样可以切换完成状态，比只点小圆圈好按。
 */
@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SectionRow {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodoCheck(done = todo.done, onToggle = onToggle)
            Spacer(Modifier.width(6.dp))
            Text(
                todo.title,
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle()
                    }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (todo.done) muted.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.width(6.dp))
            KelaileDeleteButton("删除待办", onDelete)
        }
    }
}

/** 待办左侧的勾选圆圈：空心圈表示未完成，实心勾表示已完成。 */
@Composable
private fun TodoCheck(done: Boolean, onToggle: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .semantics { contentDescription = if (done) "标记为未完成" else "标记为已完成" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.7.dp.toPx()
            drawCircle(
                color = if (done) tint else tint.copy(alpha = 0.55f),
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
        }
        if (done) KelaileGlyph(KelaileGlyphKind.Check, tint = tint, size = 13.dp)
    }
}

/** 分区里的一条事件：时间段用等宽感的标签样式，备注最多两行。 */
@Composable
private fun EventRow(event: ScheduleEvent, onDelete: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SectionRow {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "${clock(event.startMinutes)} – ${clock(event.endMinutes)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                if (event.note.isNotBlank()) {
                    Text(
                        event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            KelaileDeleteButton("删除事件", onDelete)
        }
    }
}

/**
 * 分区里的空状态行。
 *
 * 也走 [SectionRow]，这样占位文案与正常条目共用同一条发丝分隔线和入场动画。
 */
@Composable
internal fun KelaileEmptyRow(text: String) {
    SectionRow {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            )
        }
    }
}

/** 分区末尾的「添加」行：左侧小圆底 + 加号，文字用主题色。 */
@Composable
internal fun KelaileAddRow(label: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val tint = MaterialTheme.colorScheme.primary
    SectionRow {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .heightIn(min = 54.dp)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { KelaileGlyph(KelaileGlyphKind.Plus, tint = tint, size = 15.dp) }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

/** 行尾的删除圆钮：尺寸与设置页头部的返回钮同一套浅色底。 */
@Composable
internal fun KelaileDeleteButton(description: String, onClick: () -> Unit) {
    val canvas = LocalKelaileCanvas.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (canvas.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            KelaileGlyph(
                KelaileGlyphKind.Close,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 13.dp,
            )
        }
    }
}

/** 页面末尾的灰字说明，与设置页的 SectionHint 同一观感。 */
@Composable
internal fun KelailePageHint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = KelaileSpacing.md).padding(top = 2.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
    )
}
