package com.xunyyds.kelaile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.MEMO_BODY_MAX
import com.xunyyds.kelaile.data.MEMO_TITLE_MAX
import com.xunyyds.kelaile.data.Memo
import com.xunyyds.kelaile.data.newId
import com.xunyyds.kelaile.data.sortedMemos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * 备忘页。
 *
 * 列表按「最近更新优先」排，每条只露标题、两行正文摘要与更新时间；
 * 点条目弹出编辑框，新增与编辑共用一个 KelaileDialog，删除在条目右侧。
 * 空白内容由 saveMemo 自己处理成删除，页面只管把原文交上去。
 */

private val memoTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 更新时间摘要：今天只给时刻，今年内给月日，跨年才带年份。 */
private fun memoStamp(updatedAt: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(updatedAt).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        time.toLocalDate() == today -> "今天 " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
        time.year == today.year -> time.format(memoTimeFormatter)
        else -> time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}

/** 列表标题：标题留空时退到正文首行，两者都空时给一句占位。 */
private fun memoHeadline(memo: Memo): String {
    if (memo.title.isNotBlank()) return memo.title
    val firstLine = memo.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
    return firstLine.ifBlank { "无标题备忘" }
}

@Composable
fun MemoPage(
    data: AppData,
    bottomInset: Dp,
    onSaveMemo: (id: String, title: String, body: String) -> Unit,
    onDeleteMemo: (String) -> Unit,
) {
    val memos = sortedMemos(data)

    // 编辑中的备忘号码：null 表示没开弹窗，空串表示新建
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }
    var draftError by remember { mutableStateOf<String?>(null) }

    fun openNew() {
        editingId = ""
        draftTitle = ""
        draftBody = ""
        draftError = null
    }
    fun openEdit(memo: Memo) {
        editingId = memo.id
        draftTitle = memo.title
        draftBody = memo.body
        draftError = null
    }

    Column(Modifier.fillMaxSize()) {
        // 根页大标题：与设置页、日程页同一套写法
        Text(
            "备忘",
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            if (memos.isEmpty()) "随手记下要记住的事" else "共 ${memos.size} 条 · 最近的排在前面",
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
            Section("全部备忘") {
                if (memos.isEmpty()) {
                    KelaileEmptyRow("还没有备忘，点下面新建一条")
                } else {
                    memos.forEach { memo ->
                        MemoRow(
                            memo = memo,
                            onOpen = { openEdit(memo) },
                            onDelete = { onDeleteMemo(memo.id) },
                        )
                    }
                }
                KelaileAddRow("新建备忘") { openNew() }
            }
            KelailePageHint("备忘跟着整应用备份一起走；标题留空时列表会显示正文首行。")
            Spacer(Modifier.height(18.dp + bottomInset))
        }
    }

    KelaileDialog(
        visible = editingId != null,
        onDismiss = { editingId = null },
        title = if (editingId == "") "新建备忘" else "编辑备忘",
        message = "内容全部留空保存会删掉这一条。",
        confirmLabel = "保存",
        onConfirm = {
            val id = editingId ?: return@KelaileDialog
            val title = draftTitle.trim()
            val body = draftBody.trim()
            if (title.isEmpty() && body.isEmpty()) {
                // 新建时留空没有意义，直接提示；编辑已有条目留空则按删除语义交出
                if (id.isEmpty()) draftError = "写点什么再保存" else { onSaveMemo(id, title, body); editingId = null }
            } else {
                onSaveMemo(if (id.isEmpty()) newId() else id, title, body)
                editingId = null
            }
        },
        dismissLabel = "取消",
        content = {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = {
                    draftTitle = it.take(MEMO_TITLE_MAX)
                    draftError = null
                },
                label = { Text("标题（可留空）") },
                placeholder = { Text("例如：选修课要带的材料") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draftBody,
                onValueChange = {
                    draftBody = it.take(MEMO_BODY_MAX)
                    draftError = null
                },
                label = { Text("正文") },
                minLines = 4,
                maxLines = 9,
                isError = draftError != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${draftBody.length} / $MEMO_BODY_MAX",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            draftError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** 列表里的一条备忘：标题、两行正文摘要与更新时间，点整行进入编辑。 */
@Composable
private fun MemoRow(memo: Memo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SectionRow {
        Row(
            Modifier
                .fillMaxWidth()
                // 整行可点进编辑；右侧删除钮自带 clickable，会先消费自己的点击
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpen()
                }
                .heightIn(min = 66.dp)
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    memoHeadline(memo),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (memo.body.isNotBlank()) {
                    Text(
                        memo.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    memoStamp(memo.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted.copy(alpha = 0.78f),
                )
            }
            Spacer(Modifier.width(8.dp))
            KelaileDeleteButton("删除这条备忘", onDelete)
        }
    }
}
