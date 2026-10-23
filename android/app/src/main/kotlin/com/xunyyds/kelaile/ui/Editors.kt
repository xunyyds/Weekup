package com.xunyyds.kelaile.ui

import androidx.activity.compose.BackHandler
import android.app.TimePickerDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.*
import java.util.UUID

@Composable
fun CourseEditor(course: CourseSession?, settings: AppSettings, initialColor: Long, onBack: () -> Unit, onSave: (CourseSession, Long) -> Unit) {
    val id = rememberSaveable(course?.id) { course?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable(course?.id) { mutableStateOf(course?.name.orEmpty()) }
    var teacher by rememberSaveable(course?.id) { mutableStateOf(course?.teacher.orEmpty()) }
    var location by rememberSaveable(course?.id) { mutableStateOf(course?.location.orEmpty()) }
    var weekday by rememberSaveable(course?.id) { mutableStateOf(course?.weekday ?: 1) }
    val availablePeriods = settings.periods.map { it.period }.distinct().sorted()
    var start by rememberSaveable(course?.id) { mutableStateOf(course?.startPeriod ?: availablePeriods.firstOrNull() ?: 1) }
    var end by rememberSaveable(course?.id) { mutableStateOf(course?.endPeriod ?: availablePeriods.firstOrNull() ?: 1) }
    var weeks by rememberSaveable(course?.id) { mutableStateOf(course?.weeks?.joinToString(",") ?: "1-${settings.weekCount}") }
    var parity by rememberSaveable(course?.id) { mutableStateOf(0) }
    var selectedColor by rememberSaveable(course?.id, initialColor) { mutableStateOf(initialColor and 0xffffffffL) }
    var showColorPicker by rememberSaveable(course?.id) { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        EditorHeader(if (course == null) "新增课程" else "编辑课程", onBack, ::save)
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EditorField("课程名", name, { name = it })
            EditorField("教师", teacher, { teacher = it })
            EditorField("地点", location, { location = it })
            Section("星期") {
                (1..7).forEach { day ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = weekday == day, onClick = { weekday = day })
                        TextButton(onClick = { weekday = day }) { Text("星期${"一二三四五六日"[day - 1]}") }
                    }
                }
            }
            EditorDropdown("开始节次", start, availablePeriods, { "第 $it 节" }) {
                start = it
                if (end < it) end = it
            }
            EditorDropdown("结束节次", end, availablePeriods.filter { it >= start }, { "第 $it 节" }) { end = it }
            if (availablePeriods.isEmpty()) Text("请先在作息设置中添加节次。", color = MaterialTheme.colorScheme.error)
            EditorField("周次范围（1-${settings.weekCount}）", weeks, { weeks = it })
            Text("可输入 1-16 或 1,3,5-8；单双周将在此范围内筛选。")
            EditorDropdown("单双周", parity, listOf(0, 1, 2), { listOf("每周", "单周", "双周")[it] }) { parity = it }
            OutlinedCard(onClick = { showColorPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorSwatch(selectedColor, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("课程颜色", style = MaterialTheme.typography.titleMedium)
                        Text("#%08X".format(selectedColor), style = MaterialTheme.typography.bodyMedium)
                        Text("点击打开调色盘 · 支持透明度", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("调色", color = MaterialTheme.colorScheme.primary)
                }

                Surface(color = Color(selectedColor.toInt()), modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp)) {
                    val opacity = ((selectedColor ushr 24) and 255) / 255f
                    val surface = MaterialTheme.colorScheme.surface
                    val tint = Color(selectedColor.toInt())
                    val red = tint.red * opacity + surface.red * (1 - opacity)
                    val green = tint.green * opacity + surface.green * (1 - opacity)
                    val blue = tint.blue * opacity + surface.blue * (1 - opacity)
                    val ink = if (red * 0.299f + green * 0.587f + blue * 0.114f > 0.55f) Color(0xff202124) else Color.White
                    Column(Modifier.padding(16.dp)) {
                        Text(name.ifBlank { "课程卡片预览" }, color = ink, style = MaterialTheme.typography.titleMedium)
                        Text("星期${"一二三四五六日"[weekday - 1]} · 第 $start–$end 节", color = ink)
                        Text(listOf(location, teacher).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "地点 · 教师" }, color = ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("课程编号：$id", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (showColorPicker) ColorPickerDialog(selectedColor, onDismiss = { showColorPicker = false }) {
        selectedColor = it
        showColorPicker = false
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
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("请以学校实际作息为准。删除节次保留其他编号，不会悄悄移动课程；保存时已有课程仍须使用有效节次。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = cascade, onCheckedChange = { cascade = it })
                Text("联动后续节次", Modifier.padding(start = 12.dp))
            }
            Text("修改结束时间时，后续节次按结束时间差平移；不会改变后续课程时长。")
            OutlinedButton(onClick = { showDefault = true }) { Text("使用全天默认作息") }
            periods.forEachIndexed { index, period ->
                Section("第 ${period.period} 节") {
                    Text("${editorTime(period.startMinutes)} – ${editorTime(period.endMinutes)}", Modifier.padding(16.dp))
                    Row {
                        TextButton(onClick = { open(index) }) { Text("修改") }
                        TextButton(onClick = { periods = periods.filterIndexed { i, _ -> i != index }; error = null }) { Text("删除") }
                    }
                }
            }
            if (periods.isEmpty()) Text("尚未添加节次，保存前请至少添加一节。")
            OutlinedButton(onClick = { open(null) }, enabled = periods.size < 100) { Text("添加节次") }
        }
    }
    if (showDefault) AlertDialog(
        onDismissRequest = { showDefault = false }, title = { Text("替换当前作息？") },
        text = { Text("将使用 08:00–21:00 默认模板，不代表学校实际作息。请核对已有课程节次。") },
        confirmButton = { TextButton(onClick = { periods = defaultDayPeriods(); error = null; showDefault = false }) { Text("替换") } },
        dismissButton = { TextButton(onClick = { showDefault = false }) { Text("取消") } },
    )
    if (showEdit) AlertDialog(
        onDismissRequest = { showEdit = false }, title = { Text(if (editing == null) "添加节次" else "修改节次") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodNumberStepper(number.toIntOrNull() ?: 1) { number = it.toString() }
                EditorTimeButton("开始时间", start, false) { start = it }
                EditorTimeButton("结束时间", end, true) { end = it }
                Text("24:00 表示当天结束，不会视为次日 00:00。", style = MaterialTheme.typography.labelSmall)
                dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
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
        }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { showEdit = false }) { Text("取消") } },
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
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = choices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("${display(value)}  ▾")
            }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > 1) { Text("−") }
            Text("第 $value 节", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < 100) { Text("＋") }
        }
    }
}

@Composable
private fun EditorTimeButton(label: String, value: String, allowMidnight: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current

    var choosing by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$value  ·  选择时间")
        }
        if (allowMidnight) FilterChip(selected = value == "24:00", onClick = { onChange("24:00") }, label = { Text("当天结束 24:00") })
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onSave) { Text("保存") }
    }
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text), modifier = Modifier.fillMaxWidth())
}
