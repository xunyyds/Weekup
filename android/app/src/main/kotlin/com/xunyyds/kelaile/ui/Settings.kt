package com.xunyyds.kelaile.ui

import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.AppSettings
import com.xunyyds.kelaile.data.CELL_VERTICAL_BOTTOM
import com.xunyyds.kelaile.data.CELL_VERTICAL_CENTER
import com.xunyyds.kelaile.data.CELL_VERTICAL_TOP
import com.xunyyds.kelaile.data.GRID_COLOR_AUTO
import com.xunyyds.kelaile.data.GRID_LINE_DASHED
import com.xunyyds.kelaile.data.GRID_LINE_NONE
import com.xunyyds.kelaile.data.GRID_LINE_TRANSPARENT
import com.xunyyds.kelaile.data.SemesterLink
import com.xunyyds.kelaile.data.resizePeriods
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.search.KelaileSearchPill
import com.xunyyds.kelaile.ui.update.UpdateController
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Route(val key: String) {
    Root("root"),
    Appearance("appearance"),
    Palette("palette"),
    Layout("layout"),
    Motion("motion"),
    Grid("grid"),
    Display("display"),
    Semester("semester"),
    Calendar("calendar"),
    Week("week"),
    Courses("courses"),
    Reminders("reminders"),
    Data("data"),
    About("about");

    companion object {
        fun of(key: String): Route = entries.firstOrNull { it.key == key } ?: Root
    }
}

private data class EditSetting(val title: String, val initial: String, val save: (String) -> Unit)

private data class LinkPrompt(val label: String, val plan: SemesterLink.Plan)

@Composable
private fun SectionHint(text: String) {
    Text(
        text,
        Modifier
            .padding(horizontal = KelaileSpacing.xxl)
            .padding(top = 2.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
    )
}

/**
 * 设置页。
 *
 * 一级页是 iOS 风格的「分类标签 + 大圆角分组玻璃卡」；分组标题在卡片之外，
 * 每个二级页复用同一套分组与行组件，只替换内容。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.SettingsPage(
    data: AppData,
    scroll: ScrollState,
    route: String,
    highlightRequest: String?,
    onHighlightConsumed: () -> Unit = {},
    searchExpanded: Boolean,
    onRoute: (String) -> Unit,
    onChange: (AppData) -> Unit,
    onAdd: () -> Unit,
    onPeriods: () -> Unit,
    onBrowser: () -> Unit,
    onImportTimetable: () -> Unit,
    onExportTimetable: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
    onReminder: (Boolean) -> Unit,
    onSearch: () -> Unit,
    update: UpdateController,
    onPickBackground: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val s = data.settings
    val current = Route.of(route)
    var edit by remember { mutableStateOf<EditSetting?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var colorOpen by rememberSaveable { mutableStateOf(false) }
    var fontDraft by remember(s.fontScale) { mutableFloatStateOf(s.fontScale.toFloat()) }
    var densityDraft by remember(s.density) { mutableFloatStateOf(s.density.toFloat()) }
    var periodOpen by remember { mutableStateOf(false) }
    var periodDraft by remember { mutableIntStateOf(s.periods.size) }
    var periodError by remember { mutableStateOf<String?>(null) }
    var motionDraft by remember(s.motionSpeed) { mutableFloatStateOf(s.motionSpeed.toFloat()) }
    var highlighted by rememberSaveable { mutableStateOf<String?>(null) }
    var linkPrompt by remember { mutableStateOf<LinkPrompt?>(null) }

    val atRoot = current == Route.Root

    LaunchedEffect(highlightRequest) {
        if (highlightRequest == null) return@LaunchedEffect
        highlighted = highlightRequest
        delay(1600)
        if (highlighted == highlightRequest) highlighted = null
        onHighlightConsumed()
    }
    fun applySettings(settings: () -> AppSettings) {
        try { onChange(data.copy(settings = settings())) } catch (e: Exception) { error = e.message ?: "设置无效" }
    }
    fun text(title: String, value: String, save: (String) -> Unit) { edit = EditSetting(title, value, save) }
    fun back() {
        highlighted = null
        onHighlightConsumed()
        onRoute(current.parent.key)
    }
    fun confirmLink(plan: SemesterLink.Plan, label: String) {
        if (plan.isEmpty) { applySettings { SemesterLink.apply(s, plan) }; return }
        linkPrompt = LinkPrompt(label, plan)
    }
    fun linkWeek(week: Int) = confirmLink(SemesterLink.fromCurrentWeek(s, week), "当前周 → 第 $week 周")
    fun date(start: Boolean) {
        val currentDate = if (start) s.start else s.end
        DatePickerDialog(context, { _, y, m, d ->
            val picked = LocalDate.of(y, m + 1, d)
            confirmLink(
                if (start) SemesterLink.fromStart(s, picked) else SemesterLink.fromEnd(s, picked),
                (if (start) "学期开始 → $picked" else "学期结束 → $picked"),
            )
        }, currentDate.year, currentDate.monthValue - 1, currentDate.dayOfMonth).show()
    }
    BackHandler(!atRoot) { back() }

    Column(Modifier.fillMaxSize()) {
        if (atRoot) {
            // 根页标题不再套玻璃胶囊：大标题裸放在壁纸上，层级更清楚
            Text(
                "设置",
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
                style = MaterialTheme.typography.displaySmall,
            )
            KelaileSearchPill(
                collapsed = !searchExpanded,
                onOpen = onSearch,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp),
            )
        } else {
            SettingsSubHeader(title = current.title, onBack = ::back)
        }
        CompositionLocalProvider(LocalSettingsHighlight provides highlighted) {
            MotionContent(route, Modifier.weight(1f)) { destination ->
                val localScroll = if (destination == "root") scroll else rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(localScroll)
                        .padding(horizontal = 16.dp)
                        .padding(top = if (atRoot) 4.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (current) {
                        Route.Root -> {
                            Section("偏好设置") {
                                SettingsItem("外观", "主题、莫奈取色、底栏、课表配色与显示布局", KelaileGlyphKind.Palette) { onRoute(Route.Appearance.key) }
                                SettingsItem("显示设置", "格内 @ 与老师、对齐与密度", KelaileGlyphKind.Monitor) { onRoute(Route.Display.key) }
                                SettingsItem("学期与课表", "学期信息、日期、周次与节次时间", KelaileGlyphKind.Calendar) { onRoute(Route.Semester.key) }
                                SettingsItem("课程", "课程管理与默认颜色", KelaileGlyphKind.Book) { onRoute(Route.Courses.key) }
                                SettingsItem("上课提醒", "通知权限与提前时间", KelaileGlyphKind.Bell) { onRoute(Route.Reminders.key) }
                                SettingsItem("数据与备份", "整应用备份恢复、课表导入导出、教务浏览器", KelaileGlyphKind.Database) { onRoute(Route.Data.key) }
                            }
                            Section("关于") {
                                SettingsItem("关于与更新", "版本、更新检测、GitHub 与开源许可", KelaileGlyphKind.Info) { onRoute(Route.About.key) }
                            }
                        }
                        Route.Appearance -> Section("外观") {
                            ChoiceSetting("明暗模式", s.theme, linkedMapOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")) { applySettings { s.copy(theme = it) } }
                            SettingsItem("主题颜色", if (s.dynamicColor) "莫奈 · 系统壁纸取色" else paletteNames[s.themePalette].orEmpty()) { onRoute(Route.Palette.key) }
                            ChoiceSetting("底栏样式", s.navigationStyle, linkedMapOf("default" to "默认", "floating" to "悬浮", "liquid" to "液态玻璃")) { applySettings { s.copy(navigationStyle = it) } }
                            SettingsItem("课程表外观", "背景图、表格颜色、文字颜色与网格线") { onRoute(Route.Grid.key) }
                            SettingsItem("显示布局", "字体大小、课表密度") { onRoute(Route.Layout.key) }
                            SettingsItem("动效设置", "动画速度、减少动画与转场风格") { onRoute(Route.Motion.key) }
                        }
                        Route.Grid -> {
                            Section("课程表背景") {
                                SettingsItem("背景图片", s.timetableBackground.ifBlank { "未设置，使用主题背景" }) { onPickBackground() }
                                if (s.timetableBackground.isNotBlank()) {
                                    SettingsItem("清除背景图", "恢复为纯色背景") { applySettings { s.copy(timetableBackground = "") } }
                                }
                            }
                            Section("表格颜色") {
                                ColorSettingRow("表头背景", s.tableHeaderColor, "表头与节次栏") { applySettings { s.copy(tableHeaderColor = it) } }
                                ColorSettingRow("单元格背景", s.cellBackgroundColor, "课表格子底色") { applySettings { s.copy(cellBackgroundColor = it) } }
                                // 有课格子在没有单独设色的课程上取这个底色，跟着「单元格背景」之上叠一层
                                ColorSettingRow("有课表格", s.courseCellColor, "有课格子的底色（未单独设色的课程）") { applySettings { s.copy(courseCellColor = it) } }
                            }
                            Section("文字与线") {
                                ColorSettingRow("表格文字颜色", s.cellTextColor, "表头与格内文字") { applySettings { s.copy(cellTextColor = it) } }
                                ColorSettingRow("格子边框颜色", s.gridLineColor, "网格线颜色") { applySettings { s.copy(gridLineColor = it) } }
                                ChoiceSetting(
                                    "网格模式", s.gridLineStyle,
                                    linkedMapOf(
                                        GRID_LINE_DASHED to "虚线",
                                        GRID_LINE_TRANSPARENT to "透明线（保留间距）",
                                        GRID_LINE_NONE to "无网格（容器去圆角）",
                                    ),
                                ) { applySettings { s.copy(gridLineStyle = it) } }
                            }
                            SectionHint("虚线为默认观感；透明线保留格子间距但不着色；无网格去掉间距与容器圆角，整张表直接铺在壁纸上。")
                        }
                        Route.Display -> {
                            Section("格内显示") {
                                SettingsSwitch("地点前显示 @", "格内地点前加一个 @ 前缀", s.showAtSign) { applySettings { s.copy(showAtSign = it) } }
                                SettingsSwitch("格子内显示老师", "在课程格子里额外显示教师姓名", s.showTeacher) { applySettings { s.copy(showTeacher = it) } }
                            }
                            Section("格内对齐") {
                                ChoiceSetting("水平对齐", s.cellTextAlign, linkedMapOf("start" to "靠左", "center" to "居中", "end" to "靠右")) { applySettings { s.copy(cellTextAlign = it) } }
                                ChoiceSetting(
                                    "竖直对齐", s.cellVerticalAlign,
                                    linkedMapOf(
                                        CELL_VERTICAL_TOP to "靠上",
                                        CELL_VERTICAL_CENTER to "居中",
                                        CELL_VERTICAL_BOTTOM to "靠下",
                                    ),
                                ) { applySettings { s.copy(cellVerticalAlign = it) } }
                            }
                            Section("布局") {
                                SettingsItem("显示布局", "字体大小、课表密度") { onRoute(Route.Layout.key) }
                            }
                        }
                        Route.Palette -> {
                            Section("莫奈动态色") {
                                SettingsSwitch(
                                    "从系统壁纸取色",
                                    if (Build.VERSION.SDK_INT >= 31) "自动跟随系统色板与明暗模式" else "Android 12 及以上可用",
                                    s.dynamicColor,
                                ) { enabled ->
                                    if (Build.VERSION.SDK_INT >= 31) applySettings { s.copy(dynamicColor = enabled) }
                                }
                            }
                            Section("预设颜色") {
                                PaletteRow(s, ::applySettings)
                            }
                        }
                        Route.Layout -> {
                            Section("显示布局") {
                                SettingsSliderRow(
                                    title = "字体大小",
                                    value = "${(fontDraft * 100).toInt()}%",
                                    slider = fontDraft,
                                    range = .8f..1.6f,
                                    steps = 7,
                                    onChange = { fontDraft = it },
                                    onSettle = { applySettings { s.copy(fontScale = fontDraft.toDouble()) } },
                                )
                                SettingsPresetRow(
                                    title = "课表密度",
                                    value = "${(densityDraft * 100).toInt()}%",
                                    presets = listOf(.7f to "紧凑", .85f to "标准", 1f to "宽松", 1.2f to "大格"),
                                    current = densityDraft,
                                    slider = densityDraft,
                                    range = .6f..1.6f,
                                    steps = 19,
                                    onChange = { densityDraft = it },
                                    onSettle = { applySettings { s.copy(density = densityDraft.toDouble()) } },
                                    onPreset = { preset -> densityDraft = preset; applySettings { s.copy(density = preset.toDouble()) } },
                                )
                                ChoiceSetting("格内文字对齐", s.cellTextAlign, linkedMapOf("start" to "靠左", "center" to "居中", "end" to "靠右")) { applySettings { s.copy(cellTextAlign = it) } }
                                SettingsSwitch("自动隐藏栏", "向下滚动课表时收起顶栏、底栏与状态栏，向上滚动恢复", s.autoHideBars) { applySettings { s.copy(autoHideBars = it) } }
                            }
                            SectionHint("密度越小一屏放下越多；行高与格子同步收缩。课表上双指捏合也能直接缩放。")
                        }
                        Route.Motion -> Section("动效设置") {
                            SettingsSliderRow(
                                title = "动画速度",
                                value = String.format(java.util.Locale.getDefault(), "%.1f×", motionDraft),
                                slider = motionDraft,
                                range = .5f..2f,
                                steps = 14,
                                onChange = { motionDraft = it },
                                onSettle = { applySettings { s.copy(motionSpeed = motionDraft.toDouble()) } },
                            )
                            SettingsSwitch("减少动画", "减少位移与缩放，使用更平静的切换", s.reduceMotion) { applySettings { s.copy(reduceMotion = it) } }
                            ChoiceSetting("转场风格", s.transitionStyle, linkedMapOf("standard" to "标准", "shared" to "共享轴", "fade" to "淡入淡出")) { applySettings { s.copy(transitionStyle = it) } }
                            SettingsSwitch("弹窗背景模糊", "弹出选择框和确认框时对下层内容实时模糊", s.dialogBlur) { applySettings { s.copy(dialogBlur = it) } }
                            SettingsSwitch("省电模式", "检测到系统省电模式时自动减少动画", s.powerSaverMotion) { applySettings { s.copy(powerSaverMotion = it) } }
                        }
                        Route.Semester -> Section("学期与课表") {
                            SettingsItem("学校名称", s.school.ifBlank { "导入时自动识别，失败后再填写" }) { text("学校名称", s.school) { applySettings { s.copy(school = it.trim()) } } }
                            SettingsItem("课表标题", s.gradeLabel.ifBlank { "选择周次" }) { text("课表标题", s.gradeLabel) { applySettings { s.copy(gradeLabel = it.trim().take(12)) } } }
                            SettingsItem("学期日期", "${s.start} — ${s.end}") { onRoute(Route.Calendar.key) }
                            SettingsItem("周次与星期", "第 ${s.currentWeek} 周 / 共 ${s.weekCount} 周") { onRoute(Route.Week.key) }
                            SettingsItem("每日节数", "${s.periods.size} 节") {
                                periodDraft = s.periods.size
                                periodError = null
                                periodOpen = true
                            }
                            SettingsItem("节次时间", "已配置 ${s.periods.size} 节") { onPeriods() }
                        }
                        Route.Calendar -> {
                            Section("学期日期") {
                                SettingsItem("学期开始", s.start.toString()) { date(true) }
                                SettingsItem("学期结束", s.end.toString()) { date(false) }
                            }
                            SectionHint("改开学日期会自动推算当前周与结束日，确认后生效。")
                        }
                        Route.Week -> {
                            Section("周次与星期") {
                                NumberChoice("总周数", s.weekCount, 1..60) { applySettings { s.copy(weekCount = it, currentWeek = s.currentWeek.coerceAtMost(it)) } }
                                NumberChoice("当前周", s.currentWeek, 1..s.weekCount) { linkWeek(it) }
                                NumberChoice("周起始日", s.weekStart, 1..7, true) { applySettings { s.copy(weekStart = it) } }
                                NumberChoice("显示开始星期", s.firstDay, 1..s.lastDay, true) { applySettings { s.copy(firstDay = it) } }
                                NumberChoice("显示结束星期", s.lastDay, s.firstDay..7, true) { applySettings { s.copy(lastDay = it) } }
                            }
                            SectionHint("改「当前周」会按今天反推开学日期，确认后生效。")
                        }
                        Route.Courses -> Section("课程") {
                            SettingsItem("新增课程") { onAdd() }
                            SettingsItem("默认课程颜色", "#${s.defaultColor.toString(16).padStart(8, '0').uppercase()}") { colorOpen = true }
                            ColorSwatchRow(s.defaultColor)
                            SettingsHintRow("已保存 ${data.courses.size} 门课程，点击课表课程卡片编辑")
                        }
                        Route.Reminders -> {
                            Section("提醒") {
                                SettingsSwitch("上课提醒", "非精确提醒，省电策略可能延迟", s.reminders, onReminder)
                                ReminderMinutesSetting(s.reminderMinutes) { applySettings { s.copy(reminderMinutes = it) } }
                            }
                            SectionHint("常用档位直接点选；需要特殊值可自定义输入 0–60 分钟。")
                        }
                        Route.Data -> {
                            Section("应用数据备份与恢复") {
                                SettingsItem("备份应用数据", "导出设置、课表、备注与颜色等全部数据") { onExportBackup() }
                                SettingsItem("恢复应用数据", "从备份文件整体覆盖当前数据") { onImportBackup() }
                            }
                            SectionHint("整应用备份是 JSON，包含全部设置与备注；恢复会覆盖当前全部数据，建议先导出一份。")
                            Section("课表导入导出") {
                                SettingsItem("导入课表（Excel）", "仅支持 .xlsx 表格") { onImportTimetable() }
                                SettingsItem("导出课表（Excel）", "仅导出课表表格，不含应用设置与备注") { onExportTimetable() }
                            }
                            SectionHint("课表导入只认 Excel 表格；整套数据请走「备份/恢复应用数据」。")
                            Section("教务导入") {
                                SettingsItem("教务网址", s.schoolUrl.ifBlank { "填写学校实际教务入口" }) { text("教务网址", s.schoolUrl) { applySettings { s.copy(schoolUrl = it.trim()) } } }
                                SettingsItem("浏览器导入课表", "桌面模式 · 自动识别学校 · 预览确认") { onBrowser() }
                            }
                        }
                        Route.About -> AboutContent(update)
                    }
                    Spacer(Modifier.height(18.dp + bottomInset))
                }
            }
        }
    }

    if (colorOpen) {
        ColorPickerDialog(s.defaultColor, { colorOpen = false }) { color ->
            applySettings { s.copy(defaultColor = color) }
            colorOpen = false
        }
    }
    linkPrompt?.let { prompt ->
        KelaileDialog(
            visible = true,
            onDismiss = { linkPrompt = null },
            title = "联动修改学期设置",
            confirmLabel = "应用",
            onConfirm = {
                applySettings { SemesterLink.apply(s, prompt.plan) }
                linkPrompt = null
            },
            dismissLabel = "取消",
            content = {
                Text(prompt.label, style = MaterialTheme.typography.bodyMedium)
                prompt.plan.changes.forEach { line ->
                    Text("· $line", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "确认后仅修改以上字段，其他设置保持不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
    KelaileDialog(
        visible = periodOpen,
        onDismiss = { periodOpen = false },
        title = "每日节数",
        message = "课表左侧的节次数量。增加会沿用现有间隔继续外推，减少会截掉末尾。",
        confirmLabel = "应用",
        onConfirm = {
            val maxUsed = data.courses.maxOfOrNull { it.endPeriod } ?: 0
            if (periodDraft < maxUsed) {
                periodError = "第 $maxUsed 节已有课程，不能减到 $periodDraft 节"
            } else {
                try {
                    val next = resizePeriods(s.periods, periodDraft)
                    applySettings { s.copy(periods = next) }
                    periodOpen = false
                } catch (e: Exception) { periodError = e.message ?: "无法应用" }
            }
        },
        dismissLabel = "取消",
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassPillButton("−", onClick = { if (periodDraft > 1) periodDraft-- }, compact = true)
                Text("$periodDraft 节", style = MaterialTheme.typography.titleLarge)
                GlassPillButton("＋", onClick = { if (periodDraft < 100) periodDraft++ }, compact = true)
            }
            periodError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
    edit?.let { item ->
        var value by remember(item) { mutableStateOf(item.initial) }
        KelaileDialog(
            visible = true,
            onDismiss = { edit = null },
            title = item.title,
            confirmLabel = "保存",
            onConfirm = {
                try { item.save(value); edit = null } catch (e: Exception) { error = e.message ?: "输入无效" }
            },
            dismissLabel = "取消",
            content = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
    error?.let { message ->
        KelaileDialog(
            visible = true,
            onDismiss = { error = null },
            title = "无法修改",
            message = message,
            confirmLabel = "知道了",
        )
    }
}

/**
 * 二级页头部：只留一枚轻量返回圆钮 + 裸标题，不再套整条玻璃胶囊。
 *
 * 分组页标题本身就在卡片外，头部再包一层卡片会显得「气泡叠气泡」。
 */
@Composable
private fun SettingsSubHeader(title: String, onBack: () -> Unit) {
    val canvas = LocalKelaileCanvas.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回上一级" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (canvas.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
                    ),
                contentAlignment = Alignment.Center,
            ) { KelaileGlyph(KelaileGlyphKind.Back, MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}

private val Route.title: String
    get() = when (this) {
        Route.Root -> "设置"
        Route.Appearance -> "外观"
        Route.Palette -> "主题颜色"
        Route.Layout -> "显示布局"
        Route.Motion -> "动效设置"
        Route.Grid -> "课程表外观"
        Route.Display -> "显示设置"
        Route.Semester -> "学期与课表"
        Route.Calendar -> "学期日期"
        Route.Week -> "周次与星期"
        Route.Courses -> "课程"
        Route.Reminders -> "上课提醒"
        Route.Data -> "数据与备份"
        Route.About -> "关于与更新"
    }

private val Route.parent: Route
    get() = when (this) {
        Route.Root -> Route.Root
        Route.Palette, Route.Layout, Route.Motion, Route.Grid -> Route.Appearance
        Route.Calendar, Route.Week -> Route.Semester
        Route.Appearance, Route.Display, Route.Semester, Route.Courses, Route.Reminders, Route.Data, Route.About -> Route.Root
    }

private val LocalSettingsHighlight = staticCompositionLocalOf<String?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsTarget(title: String, content: @Composable () -> Unit) {
    val selected = LocalSettingsHighlight.current == title
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            delay(350)
            requester.bringIntoView()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(KelaileShapes.small),
                    )
                } else Modifier,
            ),
    ) { content() }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String = "",
    glyph: KelaileGlyphKind? = null,
    glyphTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) = SettingsTarget(title) {
    SettingRow(title, subtitle, glyph, glyphTint, onClick)
}

private val paletteNames = linkedMapOf(
    "blue" to "晴空蓝", "green" to "松柏绿", "purple" to "鸢尾紫",
    "orange" to "暖阳橙", "rose" to "蔷薇粉", "teal" to "湖水青",
)
private val paletteColors = mapOf(
    "blue" to 0xFF2869D8, "green" to 0xFF477A69, "purple" to 0xFF7954A1,
    "orange" to 0xFF9C6128, "rose" to 0xFFAD4B70, "teal" to 0xFF167F86,
)

@Composable
private fun PaletteRow(s: AppSettings, applySettings: (() -> AppSettings) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        paletteNames.entries.forEach { (key, name) ->
            val selected = !s.dynamicColor && s.themePalette == key
            SectionRow {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { applySettings { s.copy(themePalette = key, dynamicColor = false) } }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(paletteColors.getValue(key)))
                            .then(
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                                    ),
                                ),
                            ),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    CheckMark(selected)
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(color: Long) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(KelaileShapes.small))
                .background(Color(color)),
        )
    }
}

@Composable
private fun SettingsHintRow(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberChoice(
    title: String,
    value: Int,
    range: IntRange,
    weekday: Boolean = false,
    onChange: (Int) -> Unit,
) {
    ChoiceSetting(
        title,
        value.toString(),
        range.associate {
            it.toString() to if (weekday) listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[it - 1] else it.toString()
        },
    ) { onChange(it.toInt()) }
}

@Composable
private fun ChoiceSetting(title: String, value: String, options: Map<String, String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsItem(title, options[value].orEmpty()) { open = true }
    ChoiceSheet(
        title = title,
        value = value,
        options = options,
        visible = open,
        onDismiss = { open = false },
        onSelect = { key -> onChange(key); open = false },
    )
}

private val reminderPresets = listOf(0, 5, 10, 15, 20, 30)

@Composable
private fun ReminderMinutesSetting(value: Int, onChange: (Int) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.toString()) }
    var invalid by remember { mutableStateOf<String?>(null) }
    SettingsTarget("提前分钟") {
        SectionRow {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("提前分钟", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("$value 分钟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminderPresets.size) { index ->
                        val minutes = reminderPresets[index]
                        FilterChip(
                            selected = value == minutes,
                            onClick = { onChange(minutes) },
                            label = { Text(if (minutes == 0) "准点" else "$minutes 分钟") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = value !in reminderPresets,
                            onClick = { draft = value.toString(); invalid = null; customOpen = true },
                            label = { Text(if (value in reminderPresets) "自定义" else "自定义 · $value") },
                        )
                    }
                }
            }
        }
    }
    KelaileDialog(
        visible = customOpen,
        onDismiss = { customOpen = false },
        title = "自定义提前分钟",
        confirmLabel = "确定",
        onConfirm = {
            val minutes = draft.toIntOrNull()
            if (minutes == null || minutes !in 0..60) invalid = "请输入 0 到 60 之间的整数"
            else { onChange(minutes); customOpen = false }
        },
        dismissLabel = "取消",
        content = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.filter(Char::isDigit).take(5) },
                label = { Text("分钟（0–60）") },
                singleLine = true,
                isError = invalid != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            invalid?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
    )
}

/**
 * 颜色设置行：点击打开取色器，长按整行恢复默认（跟随主题）。
 *
 * 已经是「跟随主题」时长按不再写入同一个值，只给一次触觉反馈，
 * 让用户知道手势被识别到、无需重复操作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorSettingRow(title: String, value: Long, subtitle: String, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val label = if (value == GRID_COLOR_AUTO) "跟随主题" else "#${value.toString(16).padStart(8, '0').uppercase()}"
    SettingsTarget(title) {
        SectionRow {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { open = true },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (value != GRID_COLOR_AUTO) onChange(GRID_COLOR_AUTO)
                        },
                    )
                    .heightIn(min = 62.dp)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$subtitle · 长按恢复默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (value == GRID_COLOR_AUTO) MaterialTheme.colorScheme.surfaceContainerHigh else Color(value)),
                )
            }
        }
    }
    ColorPickerDialog(
        initialColor = if (value == GRID_COLOR_AUTO) 0xffffffffL else value,
        onDismiss = { open = false },
        visible = open,
    ) {
        onChange(it)
        open = false
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsTarget(title) {
        SectionRow {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = checked, onCheckedChange = onChange)
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: String,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onSettle: () -> Unit,
) {
    SettingsTarget(title) {
        SectionRow {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = slider,
                    onValueChange = onChange,
                    onValueChangeFinished = onSettle,
                    valueRange = range,
                    steps = steps,
                )
            }
        }
    }
}

@Composable
private fun SettingsPresetRow(
    title: String,
    value: String,
    presets: List<Pair<Float, String>>,
    current: Float,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onSettle: () -> Unit,
    onPreset: (Float) -> Unit,
) {
    SettingsTarget(title) {
        SectionRow {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = slider,
                    onValueChange = onChange,
                    onValueChangeFinished = onSettle,
                    valueRange = range,
                    steps = steps,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets.size) { index ->
                        val (preset, label) = presets[index]
                        FilterChip(
                            selected = abs(current - preset) < .02f,
                            onClick = { onPreset(preset) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsFooterPhrase(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KelaileSpacing.xxl, vertical = KelaileSpacing.lg)
            .alpha(0.9f),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    )
}
