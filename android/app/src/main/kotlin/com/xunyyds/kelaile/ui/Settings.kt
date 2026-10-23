package com.xunyyds.kelaile.ui

import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.*
import com.xunyyds.kelaile.ui.search.KelaileSearchPill
import com.xunyyds.kelaile.ui.update.UpdateController
import com.xunyyds.kelaile.ui.update.UpdateDialog
import java.time.LocalDate
import kotlinx.coroutines.delay

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
        Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.SettingsPage(
    data: AppData,
    scroll: ScrollState,
    route: String,
    highlightRequest: String?,
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
    var periodDialog by remember { mutableStateOf(false) }
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
    }
    fun update(settings:()->AppSettings) { try { onChange(data.copy(settings=settings())) } catch(e:Exception) { error=e.message ?: "设置无效" } }
    fun text(title:String,value:String,save:(String)->Unit) { edit=EditSetting(title,value,save) }
    fun back() {
        highlighted = null
        onRoute(current.parent.key)
    }

    fun confirmLink(plan: SemesterLink.Plan, label: String) {
        if (plan.isEmpty) { update { SemesterLink.apply(s, plan) }; return }
        linkPrompt = LinkPrompt(label, plan)
    }
    fun linkWeek(week: Int) = confirmLink(SemesterLink.fromCurrentWeek(s, week), "当前周 → 第 $week 周")
    fun date(start:Boolean) {
        val currentDate=if(start)s.start else s.end
        DatePickerDialog(context,{_,y,m,d ->
            val picked = LocalDate.of(y,m+1,d)
            confirmLink(
                if (start) SemesterLink.fromStart(s, picked) else SemesterLink.fromEnd(s, picked),
                (if (start) "学期开始 → $picked" else "学期结束 → $picked"),
            )
        },currentDate.year,currentDate.monthValue-1,currentDate.dayOfMonth).show()
    }
    BackHandler(!atRoot) { back() }
    Scaffold(
        modifier=Modifier.fillMaxSize(),
        containerColor=MaterialTheme.colorScheme.background,
        contentWindowInsets=WindowInsets(0,0,0,0),
        topBar={
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).then(if(atRoot) Modifier.navigationBarsPadding() else Modifier)) {
                if(atRoot) {

                    KelaileTopBar(
                        title = "设置",
                        collapseFraction = if (atRoot) (scroll.value.toFloat() / SETTINGS_COLLAPSE_PX).coerceIn(0f, 1f) else 0f,
                    )
                    KelaileSearchPill(
                        collapsed=!searchExpanded,
                        onOpen=onSearch,
                        modifier=Modifier.padding(horizontal=16.dp).padding(top=2.dp,bottom=10.dp),
                    )
                } else {
                    TopAppBar(
                        title={ Text(current.title,maxLines=1) },
                        navigationIcon={
                            IconButton(onClick={ back() },modifier=Modifier.semantics { contentDescription="返回上一级" }) {
                                KelaileGlyph(KelaileGlyphKind.Back, MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        colors=TopAppBarDefaults.topAppBarColors(
                            containerColor=MaterialTheme.colorScheme.background,
                            scrolledContainerColor=MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            }
        },
    ) { padding ->
      CompositionLocalProvider(LocalSettingsHighlight provides highlighted) {
       MotionContent(route,Modifier.padding(padding)) { destination ->
        val localScroll=if(destination=="root")scroll else rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(localScroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            when(current) {
                Route.Root -> {
                    Section("偏好设置") {
                        SettingsItem("外观","主题、莫奈取色、底栏、课表配色与显示布局") { onRoute(Route.Appearance.key) }
                        SettingsItem("显示设置","格内 @ 与老师、对齐与密度") { onRoute(Route.Display.key) }
                        SettingsItem("学期与课表","学期信息、日期、周次与节次时间") { onRoute(Route.Semester.key) }
                        SettingsItem("课程","课程管理与默认颜色") { onRoute(Route.Courses.key) }
                        SettingsItem("上课提醒","通知权限与提前时间") { onRoute(Route.Reminders.key) }
                        SettingsItem("数据与备份","整应用备份恢复、课表导入导出、教务浏览器") { onRoute(Route.Data.key) }
                    }
                    Section("关于") { SettingsItem("关于与更新","版本、更新检测、GitHub 与开源许可") { onRoute(Route.About.key) } }
                }
                Route.Appearance -> Section("外观") {
                    ChoiceSetting("明暗模式",s.theme,linkedMapOf("system" to "跟随系统","light" to "浅色","dark" to "深色")) { update { s.copy(theme=it) } }
                    SettingsItem("主题颜色",if(s.dynamicColor)"莫奈 · 系统壁纸取色" else paletteNames[s.themePalette].orEmpty()) { onRoute(Route.Palette.key) }
                    ChoiceSetting("底栏样式",s.navigationStyle,linkedMapOf("default" to "默认","floating" to "悬浮","liquid" to "液态玻璃")) { update { s.copy(navigationStyle=it) } }
                    SettingsItem("课程表外观","背景图、表格颜色、文字颜色与网格线") { onRoute(Route.Grid.key) }
                    SettingsItem("显示布局","字体大小、课表密度") { onRoute(Route.Layout.key) }
                    SettingsItem("动效设置","动画速度、减少动画与转场风格") { onRoute(Route.Motion.key) }
                }
                Route.Grid -> {
                    Section("课程表背景") {
                        SettingsItem("背景图片",s.timetableBackground.ifBlank { "未设置，使用主题背景" }) { onPickBackground() }
                        if (s.timetableBackground.isNotBlank()) {
                            SettingsItem("清除背景图","恢复为纯色背景") { update { s.copy(timetableBackground = "") } }
                        }
                    }
                    Section("表格颜色") {
                        ColorSettingRow("表头背景",s.tableHeaderColor,"表头与节次栏") { update { s.copy(tableHeaderColor=it) } }
                        ColorSettingRow("单元格背景",s.cellBackgroundColor,"课表格子底色") { update { s.copy(cellBackgroundColor=it) } }
                    }
                    Section("文字与线") {
                        ColorSettingRow("表格文字颜色",s.cellTextColor,"表头与格内文字") { update { s.copy(cellTextColor=it) } }
                        ColorSettingRow("格子边框颜色",s.gridLineColor,"网格线颜色") { update { s.copy(gridLineColor=it) } }
                        ChoiceSetting("网格线样式",s.gridLineStyle,linkedMapOf(
                            GRID_LINE_DASHED to "虚线",
                            GRID_LINE_TRANSPARENT to "透明线（保留间距）",
                            GRID_LINE_NONE to "无样式（与背景同色）",
                        )) { update { s.copy(gridLineStyle=it) } }
                        SectionHint("虚线为默认观感；透明线保留格子间距但不着色；无样式去掉间距，视觉上没有网格线。")
                    }
                }
                Route.Display -> {
                    Section("格内显示") {
                        SettingsSwitch("地点前显示 @","格内地点前加一个 @ 前缀",s.showAtSign) { update { s.copy(showAtSign=it) } }
                        SettingsSwitch("格子内显示老师","在课程格子里额外显示教师姓名",s.showTeacher) { update { s.copy(showTeacher=it) } }
                    }
                    Section("格内对齐") {
                        ChoiceSetting("水平对齐",s.cellTextAlign,linkedMapOf("start" to "靠左","center" to "居中","end" to "靠右")) { update { s.copy(cellTextAlign=it) } }
                        ChoiceSetting("竖直对齐",s.cellVerticalAlign,linkedMapOf(
                            CELL_VERTICAL_TOP to "靠上",
                            CELL_VERTICAL_CENTER to "居中",
                            CELL_VERTICAL_BOTTOM to "靠下",
                        )) { update { s.copy(cellVerticalAlign=it) } }
                    }
                    Section("布局") {
                        SettingsItem("显示布局","字体大小、课表密度") { onRoute(Route.Layout.key) }
                    }
                }
                Route.Palette -> {
                    Section("莫奈动态色") {
                        SettingsTarget("从系统壁纸取色") {
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("从系统壁纸取色"); Text(if(Build.VERSION.SDK_INT>=31)"自动跟随系统色板与明暗模式" else "Android 12 及以上可用",style=MaterialTheme.typography.bodySmall) }
                                Switch(s.dynamicColor,{ update { s.copy(dynamicColor=it) } },enabled=Build.VERSION.SDK_INT>=31)
                            }
                        }
                    }
                    Section("预设颜色") {
                        paletteNames.forEach { (key,name) -> Row(Modifier.fillMaxWidth().clickable { update { s.copy(themePalette=key,dynamicColor=false) } }.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).background(Color(paletteColors.getValue(key)),CircleShape)); Spacer(Modifier.width(16.dp)); Text(name,Modifier.weight(1f)); RadioButton(!s.dynamicColor && s.themePalette==key,{update { s.copy(themePalette=key,dynamicColor=false) }})
                        } }
                    }
                }
                Route.Layout -> Section("显示布局") {
                    SettingsTarget("字体大小") {
                        Text("字体大小 · ${(fontDraft*100).toInt()}%",Modifier.padding(18.dp))
                        Slider(fontDraft,{fontDraft=it},onValueChangeFinished={update { s.copy(fontScale=fontDraft.toDouble()) }},valueRange=.8f..1.6f,steps=7,modifier=Modifier.padding(horizontal=18.dp))
                    }
                    SettingsTarget("课表密度") {
                        Text("课表密度 · ${(densityDraft*100).toInt()}%",Modifier.padding(18.dp))
                        Slider(densityDraft,{densityDraft=it},onValueChangeFinished={update { s.copy(density=densityDraft.toDouble()) }},valueRange=.6f..1.6f,steps=19,modifier=Modifier.padding(horizontal=18.dp))

                        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(.7f to "紧凑",.85f to "标准",1f to "宽松",1.2f to "大格").forEach { (preset,label) ->
                                FilterChip(
                                    selected = kotlin.math.abs(densityDraft-preset)<.02f,
                                    onClick = { densityDraft=preset; update { s.copy(density=preset.toDouble()) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text("越小一屏放下越多；行高与格子同步收缩。课表上双指捏合也能直接缩放。",Modifier.padding(horizontal=18.dp).padding(top=6.dp,bottom=14.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ChoiceSetting("格内文字对齐",s.cellTextAlign,linkedMapOf("start" to "靠左","center" to "居中","end" to "靠右")) { update { s.copy(cellTextAlign=it) } }
                    SettingsTarget("自动隐藏栏") {
                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("滚动时自动隐藏")
                                Text("向下滚动课表时收起顶栏、底栏与状态栏，向上滚动恢复",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(s.autoHideBars,{update { s.copy(autoHideBars=it) }})
                        }
                    }
                }
                Route.Motion -> Section("动效设置") {
                    SettingsTarget("动画速度") {
                        Text("动画速度 · ${String.format(java.util.Locale.getDefault(), "%.1f", motionDraft)}×",Modifier.padding(18.dp))
                        Slider(motionDraft,{motionDraft=it},onValueChangeFinished={update { s.copy(motionSpeed=motionDraft.toDouble()) }},valueRange=.5f..2f,steps=14,modifier=Modifier.padding(horizontal=18.dp))
                    }
                    SettingsTarget("减少动画") {
                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("减少动画")
                                Text("减少位移与缩放，使用更平静的切换",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(s.reduceMotion,{update { s.copy(reduceMotion=it) }})
                        }
                    }
                    ChoiceSetting("转场风格",s.transitionStyle,linkedMapOf("standard" to "标准","shared" to "共享轴","fade" to "淡入淡出")) { update { s.copy(transitionStyle=it) } }
                }
                Route.Semester -> Section("学期与课表") {
                    SettingsItem("学校名称",s.school.ifBlank { "导入时自动识别，失败后再填写" }) { text("学校名称",s.school) { update { s.copy(school=it.trim()) } } }
                    SettingsItem("课表标题",s.gradeLabel.ifBlank { "选择周次" }) { text("课表标题",s.gradeLabel) { update { s.copy(gradeLabel=it.trim().take(12)) } } }
                    SettingsItem("学期日期","${s.start} — ${s.end}") { onRoute(Route.Calendar.key) }
                    SettingsItem("周次与星期","第 ${s.currentWeek} 周 / 共 ${s.weekCount} 周") { onRoute(Route.Week.key) }
                    SettingsItem("每日节数","${s.periods.size} 节") {
                        periodDraft = s.periods.size
                        periodError = null
                        periodDialog = true
                    }
                    SettingsItem("节次时间","已配置 ${s.periods.size} 节") { onPeriods() }
                    if (periodDialog) AlertDialog(
                        onDismissRequest = { periodDialog = false },
                        title = { Text("每日节数") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("课表左侧的节次数量。增加会沿用现有间隔继续外推，减少会截掉末尾。")
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    FilledTonalButton(onClick = { if (periodDraft > 1) periodDraft-- }) { Text("−") }
                                    Text(periodDraft.toString(), style = MaterialTheme.typography.titleLarge)
                                    FilledTonalButton(onClick = { if (periodDraft < 100) periodDraft++ }) { Text("＋") }
                                }
                                periodError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {

                                val maxUsed = data.courses.maxOfOrNull { it.endPeriod } ?: 0
                                if (periodDraft < maxUsed) {
                                    periodError = "第 " + maxUsed + " 节已有课程，不能减到 " + periodDraft + " 节"
                                } else {
                                    try {
                                        val next = resizePeriods(s.periods, periodDraft)
                                        update { s.copy(periods = next) }
                                        periodDialog = false
                                    } catch (e: Exception) { periodError = e.message ?: "无法应用" }
                                }
                            }) { Text("应用") }
                        },
                        dismissButton = { TextButton(onClick = { periodDialog = false }) { Text("取消") } },
                    )
                }
                Route.Calendar -> Section("学期日期") {
                    SettingsItem("学期开始",s.start.toString()) { date(true) }
                    SettingsItem("学期结束",s.end.toString()) { date(false) }
                    SectionHint("改开学日期会自动推算当前周与结束日，确认后生效。")
                }
                Route.Week -> Section("周次与星期") {
                    NumberChoice("总周数",s.weekCount,1..60) { update { s.copy(weekCount=it,currentWeek=s.currentWeek.coerceAtMost(it)) } }
                    NumberChoice("当前周",s.currentWeek,1..s.weekCount) { linkWeek(it) }
                    NumberChoice("周起始日",s.weekStart,1..7,true) { update { s.copy(weekStart=it) } }
                    NumberChoice("显示开始星期",s.firstDay,1..s.lastDay,true) { update { s.copy(firstDay=it) } }
                    NumberChoice("显示结束星期",s.lastDay,s.firstDay..7,true) { update { s.copy(lastDay=it) } }
                    SectionHint("改「当前周」会按今天反推开学日期，确认后生效。")
                }
                Route.Courses -> Section("课程") {
                    SettingsItem("新增课程") { onAdd() }
                    SettingsItem("默认课程颜色","#${s.defaultColor.toString(16).padStart(8,'0').uppercase()}") { colorOpen=true }
                    Box(Modifier.padding(18.dp).fillMaxWidth().height(48.dp).background(Color(s.defaultColor),MaterialTheme.shapes.medium))
                    Text("已保存 ${data.courses.size} 门课程，点击课表课程卡片编辑",Modifier.padding(18.dp),style=MaterialTheme.typography.bodyMedium)
                }
                Route.Reminders -> {
                    Section("提醒") {
                        SettingsTarget("上课提醒") {
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("上课提醒"); Text("非精确提醒，省电策略可能延迟",style=MaterialTheme.typography.bodySmall) }; Switch(s.reminders,onReminder) }
                        }
                        ReminderMinutesSetting(s.reminderMinutes) { update { s.copy(reminderMinutes=it) } }
                    }
                    SectionHint("常用档位直接点选；需要特殊值可自定义输入 0–60 分钟。")
                }
                Route.Data -> {
                    Section("应用数据备份与恢复") {
                        SettingsItem("备份应用数据","导出设置、课表、备注与颜色等全部数据") { onExportBackup() }
                        SettingsItem("恢复应用数据","从备份文件整体覆盖当前数据") { onImportBackup() }
                        SectionHint("整应用备份是 JSON，包含全部设置与备注；恢复会覆盖当前全部数据，建议先导出一份。")
                    }
                    Section("课表导入导出") {
                        SettingsItem("导入课表（Excel）","仅支持 .xlsx 表格；旧版 JSON 备份不支持") { onImportTimetable() }
                        SettingsItem("导出课表（Excel）","仅导出课表表格，不含应用设置与备注") { onExportTimetable() }
                        SectionHint("课表导入不兼容旧数据格式：旧版 JSON 备份请改用上面的「恢复应用数据」。")
                    }
                    Section("教务导入") {
                        SettingsItem("教务网址",s.schoolUrl.ifBlank { "填写学校实际教务入口" }) { text("教务网址",s.schoolUrl) { update { s.copy(schoolUrl=it.trim()) } } }
                        SettingsItem("浏览器导入课表","桌面模式 · 自动识别学校 · 预览确认") { onBrowser() }
                    }
                }
                Route.About -> AboutContent(update)
            }
            Spacer(Modifier.height(16.dp + bottomInset))
        }
    }
      }
    }
    if(colorOpen) ColorPickerDialog(s.defaultColor,{colorOpen=false}) { color -> update { s.copy(defaultColor=color) }; colorOpen=false }
    linkPrompt?.let { prompt ->

        AlertDialog(
            onDismissRequest = { linkPrompt = null },
            title = { Text("联动修改学期设置") },
            text = {
                Column {
                    Text(prompt.label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    prompt.plan.changes.forEach { line ->
                        Text("· $line", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "确认后仅修改以上字段，其他设置保持不变。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    update { SemesterLink.apply(s, prompt.plan) }
                    linkPrompt = null
                }) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { linkPrompt = null }) { Text("取消") } },
        )
    }
    edit?.let { item ->
        var value by remember(item) { mutableStateOf(item.initial) }
        AlertDialog(onDismissRequest={edit=null},title={Text(item.title)},text={OutlinedTextField(value,{value=it},singleLine=true)},confirmButton={TextButton(onClick={try {item.save(value);edit=null} catch(e:Exception){error=e.message ?: "输入无效"}}){Text("保存")}},dismissButton={TextButton(onClick={edit=null}){Text("取消")}})
    }
    error?.let { message -> AlertDialog(onDismissRequest={error=null},title={Text("无法修改")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("知道了")}}) }
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

private val SETTINGS_COLLAPSE_PX = 6f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsTarget(title:String,content:@Composable ()->Unit) {
    val selected=LocalSettingsHighlight.current==title
    val requester=remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if(selected) {
            delay(350)
            requester.bringIntoView()
        }
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester).clip(MaterialTheme.shapes.medium)
        .background(if(selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) { content() }
}

@Composable
private fun SettingsItem(title:String,subtitle:String="",onClick:()->Unit)=SettingsTarget(title) {
    SettingRow(title,subtitle,onClick)
}

private val paletteNames=linkedMapOf("blue" to "晴空蓝","green" to "松柏绿","purple" to "鸢尾紫","orange" to "暖阳橙","rose" to "蔷薇粉","teal" to "湖水青")
private val paletteColors=mapOf("blue" to 0xFF2869D8,"green" to 0xFF477A69,"purple" to 0xFF7954A1,"orange" to 0xFF9C6128,"rose" to 0xFFAD4B70,"teal" to 0xFF167F86)

@Composable
private fun NumberChoice(title:String,value:Int,range:IntRange,weekday:Boolean=false,onChange:(Int)->Unit) {
    ChoiceSetting(title,value.toString(),range.associate { it.toString() to if(weekday) listOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日")[it-1] else it.toString() }) { onChange(it.toInt()) }
}

@Composable
private fun ChoiceSetting(title:String,value:String,options:Map<String,String>,onChange:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsItem(title,options[value].orEmpty()) { open=true }
    if(open) ChoiceSheet(
        title=title,
        value=value,
        options=options,
        onDismiss={ open=false },
        onSelect={ key -> onChange(key); open=false },
    )
}

private val reminderPresets = listOf(0, 5, 10, 15, 20, 30)

/**
 * 提前分钟：常用档位横向排列 + 自定义输入。
 * 仍然写回 `settings.reminderMinutes`，存储键与读取逻辑不变。
 */
@Composable
private fun ReminderMinutesSetting(value: Int, onChange: (Int) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.toString()) }
    var invalid by remember { mutableStateOf<String?>(null) }
    SettingsTarget("提前分钟") {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("提前分钟 · $value 分钟")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    if (customOpen) AlertDialog(
        onDismissRequest = { customOpen = false },
        title = { Text("自定义提前分钟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter(Char::isDigit).take(5) },
                    label = { Text("分钟（0–60）") },
                    singleLine = true,
                    isError = invalid != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                invalid?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = draft.toIntOrNull()
                if (minutes == null || minutes !in 0..60) invalid = "请输入 0 到 60 之间的整数"
                else { onChange(minutes); customOpen = false }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { customOpen = false }) { Text("取消") } },
    )
}

/** 课表颜色项：未设置时显示「跟随主题」，点开调色盘后写入具体颜色。 */
@Composable
private fun ColorSettingRow(title: String, value: Long, subtitle: String, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (value == GRID_COLOR_AUTO) "跟随主题" else "#${value.toString(16).padStart(8, '0').uppercase()}"
    SettingsTarget(title) {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (value == GRID_COLOR_AUTO) MaterialTheme.colorScheme.surfaceContainerHigh else Color(value)),
            )
        }
    }
    if (open) ColorPickerDialog(if (value == GRID_COLOR_AUTO) 0xffffffffL else value, { open = false }) {
        onChange(it); open = false
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsTarget(title) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChange)
        }
    }
}
