package com.xunyyds.kelaile.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.AppSettings
import com.xunyyds.kelaile.data.CourseSession
import com.xunyyds.kelaile.data.PeriodTime
import com.xunyyds.kelaile.data.adjustPeriods
import com.xunyyds.kelaile.data.defaultDayPeriods
import com.xunyyds.kelaile.ui.glass.glassPanel
import java.util.UUID

@Composable
fun CourseEditor(
    course: CourseSession?,
    settings: AppSettings,
    initialColor: Long,
    onBack: () -> Unit,
    onSave: (CourseSession, Long) -> Unit,
    /** 从空白格二次点击进来时预填的「星期、节次」；从菜单进来时为 null。 */
    presetCell: Pair<Int, Int>? = null,
) {
    val id = rememberSaveable(course?.id) { course?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable(course?.id) { mutableStateOf(course?.name.orEmpty()) }
    var teacher by rememberSaveable(course?.id) { mutableStateOf(course?.teacher.orEmpty()) }
    var location by rememberSaveable(course?.id) { mutableStateOf(course?.location.orEmpty()) }
    var weekday by rememberSaveable(course?.id) { mutableStateOf(course?.weekday ?: presetCell?.first ?: 1) }
    val availablePeriods = settings.periods.map { it.period }.distinct().sorted()
    var start by rememberSaveable(course?.id) {
        mutableStateOf(course?.startPeriod ?: presetCell?.second ?: availablePeriods.firstOrNull() ?: 1)
    }
    var end by rememberSaveable(course?.id) {
        mutableStateOf(course?.endPeriod ?: presetCell?.second ?: availablePeriods.firstOrNull() ?: 1)
    }
    var weeks by rememberSaveable(course?.id) { mutableStateOf(course?.weeks?.joinToString(",") ?: "1-${settings.weekCount}") }
    var parity by rememberSaveable(course?.id) { mutableStateOf(0) }
    var selectedColor by rememberSaveable(course?.id, initialColor) { mutableStateOf(initialColor and 0xffffffffL) }
    var showColorPicker by rememberSaveable(course?.id) { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val initialWeeks = course?.weeks?.joinToString(",").orEmpty()
    val dirty = name != course?.name.orEmpty() ||
        teacher != course?.teacher.orEmpty() ||
        location != course?.location.orEmpty() ||
        weekday != (course?.weekday ?: presetCell?.first ?: 1) ||
        start != (course?.startPeriod ?: presetCell?.second ?: availablePeriods.firstOrNull() ?: 1) ||
        end != (course?.endPeriod ?: presetCell?.second ?: availablePeriods.firstOrNull() ?: 1) ||
        weeks != initialWeeks ||
        selectedColor != (initialColor and 0xffffffffL)
    fun requestBack() {
        if (dirty) confirmLeave = true else onBack()
    }
    fun save() {
        val result = runCatching {
            require(name.isNotBlank()) { "请输入课程名" }
            val a = start
            val b = end
            require(a in 1..100 && b in a..100) { "请输入有效节次，结束节次不能早于开始节次" }
            val available = settings.periods.map { it.period }.toSet()
            require((a..b).all { it in available }) { "所选节次不存在，请先在作息设置中添加；可用节次：${available.sorted().joinToString()}" }
            val selected = sortedSetOf<Int>()
            weeks.replace('，', ',').replace('、', ',').split(',').forEach { piece ->
                val match = Regex("^\\s*(\\d{1,2})(?:\\s*[-至—~～]\\s*(\\d{1,2}))?\\s*$").matchEntire(piece)
                require(match != null) { "周次格式例如：1-16 或 1,3,5-8" }
                val first = match.groupValues[1].toInt()
                val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toInt()
                require(first in 1..settings.weekCount && last in first..settings.weekCount) { "周次必须在 1 至 ${settings.weekCount} 周内" }
                for (week in first..last) if (parity == 0 || week % 2 == parity % 2) selected += week
            }
            require(selected.isNotEmpty()) { "所选范围没有符合单双周的周次" }
            CourseSession(id, name.trim(), weekday, a, b, selected.toList(), teacher.trim(), location.trim()) to selectedColor
        }
        result.onSuccess { (value, color) -> error = null; onSave(value, color) }
            .onFailure { error = it.message ?: "请输入有效的课程信息" }
    }
    BackHandler(onBack = ::requestBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        EditorHeader(
            title = if (course == null) "新增课程" else "编辑课程",
            onBack = ::requestBack,
            onSave = ::save,
        )
        error?.let {
            Text(
                it,
                Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("基本信息") {
                EditorField("课程名", name, { name = it })
                EditorField("教师", teacher, { teacher = it })
                EditorField("地点", location, { location = it })
            }
            Section("时间") {
                WeekdayPicker(weekday) { weekday = it }
                EditorDropdown("开始节次", start, availablePeriods, { "第 $it 节" }) {
                    start = it
                    if (end < it) end = it
                }
                EditorDropdown("结束节次", end, availablePeriods.filter { it >= start }, { "第 $it 节" }) { end = it }
                if (availablePeriods.isEmpty()) {
                    Text(
                        "请先在作息设置中添加节次。",
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Section("周次") {
                EditorField("周次范围（1-${settings.weekCount}）", weeks, { weeks = it })
                Text(
                    "可输入 1-16 或 1,3,5-8；单双周将在此范围内筛选。",
                    Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditorDropdown("单双周", parity, listOf(0, 1, 2), { listOf("每周", "单周", "双周")[it] }) { parity = it }
            }
            Section("外观") {
                CourseColorCard(
                    name = name,
                    weekday = weekday,
                    start = start,
                    end = end,
                    location = location,
                    teacher = teacher,
                    color = selectedColor,
                ) {
                    showColorPicker = true
                }
            }
            Text(
                "课程编号：$id",
                Modifier.padding(horizontal = KelaileSpacing.xl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showColorPicker) {
        ColorPickerDialog(selectedColor, onDismiss = { showColorPicker = false }) {
            selectedColor = it
            showColorPicker = false
        }
    }
    KelaileDialog(
        visible = confirmLeave,
        onDismiss = { confirmLeave = false },
        title = "还有未保存的修改",
        message = "保存后退出，或放弃本次修改。点空白处继续编辑。",
        confirmLabel = "保存并退出",
        onConfirm = { confirmLeave = false; save() },
        dismissLabel = "放弃修改",
        onDismissAction = { confirmLeave = false; onBack() },
    )
}

/**
 * 课程颜色卡：用真实课程卡片的玻璃材质做预览，改色时所见即所得。
 */
@Composable
private fun CourseColorCard(
    name: String,
    weekday: Int,
    start: Int,
    end: Int,
    location: String,
    teacher: String,
    color: Long,
    onClick: () -> Unit,
) {
    val canvas = LocalKelaileCanvas.current
    val dark = canvas.isDark
    val tint = Color(color)
    val blended = courseCardTint(tint, dark)
    val ink = courseCardInk(blended, dark)
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(KelaileShapes.small))
                .background(blended)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.12f else 0.38f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) 0.10f else 0.035f),
                    ),
                )
                .clickable(onClick = onClick)
                .padding(14.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    name.ifBlank { "课程卡片预览" },
                    color = ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(
                    "星期${"一二三四五六日"[weekday - 1]} · 第 $start–$end 节",
                    color = ink.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
                val meta = listOf(location, teacher).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    meta.ifBlank { "地点 · 教师" },
                    color = ink.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(color, Modifier.size(30.dp).clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("课程颜色", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "#%08X · 支持透明度".format(color),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GlassPillButton("调色", onClick = onClick, compact = true)
        }
    }
}

@Composable
private fun WeekdayPicker(weekday: Int, onChange: (Int) -> Unit) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            val selected = day == weekday
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(KelaileShapes.small))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else LocalKelaileCanvas.current.fillSunken,
                    )
                    .clickable { onChange(day) }
                    .heightIn(min = 42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun PeriodEditor(settings: AppSettings, onBack: () -> Unit, onSave: (List<PeriodTime>) -> Unit) {
    val periodSaver = androidx.compose.runtime.saveable.listSaver<List<PeriodTime>, Int>(
        save = { list -> list.flatMap { listOf(it.period, it.startMinutes, it.endMinutes) } },
        restore = { values -> values.chunked(3).map { PeriodTime(it[0], it[1], it[2]) } },
    )
    var periods by rememberSaveable(stateSaver = periodSaver) { mutableStateOf(settings.periods.sortedBy { it.period }) }
    var cascade by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showDefault by rememberSaveable { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var dialogError by rememberSaveable { mutableStateOf<String?>(null) }
    fun open(index: Int?) {
        editing = index
        val existing = index?.let { periods[it] }
        val nextNumber = ((periods.maxOfOrNull { it.period } ?: 0) + 1).takeIf { it <= 100 }
            ?: (1..100).firstOrNull { candidate -> periods.none { it.period == candidate } } ?: 100
        number = (existing?.period ?: nextNumber).toString()
        val a = existing?.startMinutes ?: (periods.lastOrNull()?.endMinutes?.plus(10) ?: 480)
        start = editorTime(a.coerceAtMost(1439))
        end = editorTime(existing?.endMinutes ?: (a + 45).coerceAtMost(1440))
        dialogError = null
        showEdit = true
    }
    fun validate(values: List<PeriodTime>): List<PeriodTime> {
        require(values.isNotEmpty()) { "至少保留一个节次" }
        require(values.map { it.period }.distinct().size == values.size) { "节次编号重复" }
        val sorted = values.sortedBy { it.period }
        require(sorted.zipWithNext().all { (a, b) -> a.endMinutes <= b.startMinutes }) { "节次须按编号顺序排列，时间不可重叠或跨日" }
        return sorted
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        EditorHeader("节次与作息", onBack) {
            runCatching { validate(periods) }.onSuccess { error = null; onSave(it.toList()) }.onFailure { error = it.message }
        }
        error?.let {
            Text(
                it,
                Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("说明") {
                Text(
                    "请以学校实际作息为准。删除节次会保留其他编号，不会悄悄移动课程；保存时已有课程仍须使用有效节次。",
                    Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Section("联动") {
                SectionRow {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("联动后续节次", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "修改结束时间时，后续节次按结束时间差平移；不会改变后续课程时长。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = cascade, onCheckedChange = { cascade = it })
                    }
                }
                SectionRow {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPillButton("使用全天默认作息", onClick = { showDefault = true }, compact = true)
                        GlassPillButton(
                            "添加节次",
                            onClick = { open(null) },
                            compact = true,
                            tint = if (periods.size < 100) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val canvas = LocalKelaileCanvas.current
            periods.forEachIndexed { index, period ->
                val shape = RoundedCornerShape(KelaileShapes.small)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(KelaileElevation.soft(3.dp, shape, 0.02f, 0.05f))
                        .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("第 ${period.period} 节", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${editorTime(period.startMinutes)} – ${editorTime(period.endMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { open(index) }) { Text("修改") }
                    TextButton(onClick = { periods = periods.filterIndexed { i, _ -> i != index }; error = null }) { Text("删除") }
                }
            }
            if (periods.isEmpty()) {
                Text("尚未添加节次，保存前请至少添加一节。", Modifier.padding(horizontal = KelaileSpacing.xl))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    KelaileDialog(
        visible = showDefault,
        onDismiss = { showDefault = false },
        title = "替换当前作息？",
        message = "将使用 08:00–21:00 默认模板，不代表学校实际作息。请核对已有课程节次。",
        confirmLabel = "替换",
        onConfirm = { periods = defaultDayPeriods(); error = null; showDefault = false },
        dismissLabel = "取消",
    )
    KelaileDialog(
        visible = showEdit,
        onDismiss = { showEdit = false },
        title = if (editing == null) "添加节次" else "修改节次",
        confirmLabel = "确定",
        onConfirm = {
            runCatching {
                val n = number.trim().toIntOrNull()
                require(n != null && n in 1..100) { "节次编号须在 1–100 之间" }
                val a = parseEditorTime(start, false)
                val b = parseEditorTime(end, true)
                require(a < b) { "结束时间须晚于开始时间" }
                val index = editing
                val updated = if (index == null) periods + PeriodTime(n, a, b) else {
                    adjustPeriods(periods, index, a, b, cascade).mapIndexed { i, p -> if (i == index) p.copy(period = n) else p }
                }
                validate(updated)
            }.onSuccess { periods = it; error = null; showEdit = false }
                .onFailure { dialogError = it.message ?: "时间无效，联动后不可跨日或重叠" }
        },
        dismissLabel = "取消",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodNumberStepper(number.toIntOrNull() ?: 1) { number = it.toString() }
                EditorTimeButton("开始时间", start, false) { start = it }
                EditorTimeButton("结束时间", end, true) { end = it }
                Text(
                    "24:00 表示当天结束，不会视为次日 00:00。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

private fun editorTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun parseEditorTime(text: String, allowMidnight: Boolean): Int {
    val match = Regex("^(\\d{1,2})[:：](\\d{2})$").matchEntire(text.trim())
    require(match != null) { "时间格式须为 HH:mm" }
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    require(minute in 0..59 && (hour in 0..23 || allowMidnight && hour == 24 && minute == 0)) { "时间不能跨日；24:00 仅可作为结束时间" }
    return hour * 60 + minute
}

@Composable
private fun EditorDropdown(label: String, value: Int, choices: List<Int>, display: (Int) -> String, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val canvas = LocalKelaileCanvas.current
    SectionRow(alwaysDivider = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = choices.isNotEmpty()) { expanded = true }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                display(value),
                style = MaterialTheme.typography.bodyMedium,
                color = if (choices.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Chevron(canvas.divider)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(display(choice)) }, onClick = { onChange(choice); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun PeriodNumberStepper(value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("节次编号（1–100）", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPillButton("−", onClick = { onChange(value - 1) }, compact = true)
            Text("第 $value 节", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            GlassPillButton("＋", onClick = { onChange(value + 1) }, compact = true)
        }
    }
}

@Composable
private fun EditorTimeButton(label: String, value: String, allowMidnight: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KelaileShapes.small))
                .background(LocalKelaileCanvas.current.fillSunken)
                .clickable { choosing = true }
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("选择时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (allowMidnight) {
            Spacer(Modifier.height(6.dp))
            FilterChip(selected = value == "24:00", onClick = { onChange("24:00") }, label = { Text("当天结束 24:00") })
        }
    }
    if (choosing) {
        DisposableEffect(context) {
            val minutes = runCatching { parseEditorTime(value, allowMidnight) }.getOrDefault(480).coerceAtMost(1439)
            val picker = TimePickerDialog(context, { _, hour, minute ->
                onChange(editorTime(hour * 60 + minute))
                choosing = false
            }, minutes / 60, minutes % 60, true)
            picker.setOnDismissListener { choosing = false }
            picker.show()
            onDispose { picker.setOnDismissListener(null); picker.dismiss() }
        }
    }
}

@Composable
private fun EditorHeader(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.capsule)
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(KelaileElevation.soft(6.dp, shape, 0.03f, 0.07f))
            .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
            .height(50.dp)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { KelaileGlyph(KelaileGlyphKind.Back, MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.width(4.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        GlassPillButton("保存", onClick = onSave, compact = true)
    }
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(KelaileShapes.small),
    )
}
