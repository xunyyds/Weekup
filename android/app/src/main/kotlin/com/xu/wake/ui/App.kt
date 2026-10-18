package com.xu.wake.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xu.wake.data.*
import com.xu.wake.domain.ScheduleActions
import com.xu.wake.ui.glass.GlassHost
import com.xu.wake.ui.glass.glassSource
import com.xu.wake.ui.palette.PaletteActions
import com.xu.wake.ui.search.XwakeSearchOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun XwakeApp() {
    val context = LocalContext.current
    val service = remember { com.xu.wake.application.ScheduleService(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(AppData()) }
    var ready by remember { mutableStateOf(false) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("home") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsRoute by rememberSaveable { mutableStateOf("root") }
    val scheduleScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val editing = data.courses.firstOrNull { it.id == editingId }
    var details by remember { mutableStateOf<CourseSession?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }


    var viewWeek by remember { mutableIntStateOf(1) }
    var immersive by remember { mutableStateOf(false) }


    var editMode by remember { mutableStateOf(false) }
    val systemBars = rememberSystemBars()
    var highlightRequest by remember { mutableStateOf<String?>(null) }
    var locatedCourseId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun commit(candidate: AppData, onSaved: () -> Unit = {}) {
        if (busy) { message("正在保存，请稍候重试"); return }
        busy = true
        scope.launch {
            try {
                val result = service.save(candidate)
                data = result.data
                onSaved()
                if(result.reminderFailed) message("已保存，但提醒更新失败，请检查通知权限")
            } catch (e: Exception) { message("保存失败：${e.message ?: "请检查文件与存储空间"}") }
            finally { busy = false }
        }
    }

    var pendingMove by remember { mutableStateOf<Triple<String, String, Pair<Int, Int>>?>(null) }


    var offerPeriodEdit by remember { mutableStateOf(false) }
    fun applyMove(move: Triple<String, String, Pair<Int, Int>>, mode: ScheduleActions.MoveMode) {
        val (kind, id, ends) = move
        val next = when (kind) {
            "course" -> ScheduleActions.moveCourse(data, id, ends.first, ends.second, mode)
            "day" -> ScheduleActions.moveDay(data, ends.first, ends.second, mode)
            else -> ScheduleActions.moveRow(data, ends.first, ends.second, mode)
        }
        commit(next)
    }
    fun requestMove(kind: String, id: String, ends: Pair<Int, Int>, conflict: Boolean) {
        val move = Triple(kind, id, ends)
        if (conflict) pendingMove = move else applyMove(move, ScheduleActions.MoveMode.SWAP)
    }
    LaunchedEffect(Unit) {
        try {
            data = service.load()
        }
        catch (e: Exception) { startupError = "本地课表读取失败，原文件未覆盖：${e.message}" }
        ready = true
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {


                val (backup, sheet) = service.readImport(uri)
                if (sheet != null) {
                    commit(ScheduleActions.importSchool(data, sheet))
                    message("已从表格导入 ${sheet.courses.size} 门课程")
                    busy = false
                    return@launch
                }
                val candidate = backup ?: error("无法读取文件")
                if (startupError != null) {
                    val result = service.save(candidate,recovery=true)
                    data = result.data
                    startupError = null
                    if(result.reminderFailed) message("备份已恢复，但提醒排期失败，请检查权限")
                    busy = false
                    message("已从备份恢复，损坏原文件已保留")
                } else {
                    busy = false
                    commit(candidate)
                }
            } catch (e: Exception) { busy = false; message("导入失败：${e.message}") }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try { service.export(uri,data); message("课表已导出") }
            catch (e: Exception) { message("导出失败：${e.message}") }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) commit(data.copy(settings = data.settings.copy(reminders = true))) else message("未授予通知权限，提醒未开启")
    }
    fun toggleReminder(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else commit(data.copy(settings = data.settings.copy(reminders = enabled)))
    }
    fun openBrowser() {
        val uri = android.net.Uri.parse(data.settings.schoolUrl)
        if(uri.scheme !in listOf("http","https") || uri.host.isNullOrBlank()) message("请先填写有效的教务网址")
        else page = "browser"
    }
    fun toggleTheme() {
        val next = when(data.settings.theme) { "system" -> "dark"; "dark" -> "light"; else -> "system" }
        commit(data.copy(settings = data.settings.copy(theme = next)))
    }


    val overlayDock = data.settings.navigationStyle != "default"
    LaunchedEffect(data.settings.currentWeek) { viewWeek = data.settings.currentWeek }
    LaunchedEffect(page, tab) {
        if (page != "home" || tab != 0) { immersive = false; systemBars(false) }
    }
    BackHandler(page != "home" && !searchOpen) { page = "home" }
    XwakeTheme(data.settings.theme, data.settings.fontScale.toFloat(), data.settings.themePalette, data.settings.dynamicColor, data.settings.motionSpeed, data.settings.reduceMotion, data.settings.transitionStyle) {
        GlassHost {
        var splashVisible by remember { mutableStateOf(true) }
        LaunchedEffect(ready) { if (ready) splashVisible = false }
        Box(Modifier.fillMaxSize()) {
        if (ready) SharedTransitionLayout {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            val hidden = immersive && data.settings.autoHideBars
            if (page == "home" && !overlayDock && !hidden) Column(Modifier.navigationBarsPadding()) {
                XwakeDock(
                    data.settings.navigationStyle, tab, { tab = it },
                    Modifier.fillMaxWidth(),
                )
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().glassSource().background(MaterialTheme.colorScheme.background).padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                startupError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                MotionContent(page, Modifier.weight(1f)) { route ->
                    when (route) {
                        "course" -> CourseEditor(editing, data.settings, editing?.let { data.colors[it.id] } ?: data.settings.defaultColor, { page = "home" }) { course, color ->
                            try { commit(com.xu.wake.domain.ScheduleActions.saveCourse(data,course,color)) { page = "home" } }
                            catch (e: Exception) { message(e.message ?: "课程参数无效") }
                        }
                        "periods" -> PeriodEditor(data.settings, { page = "home" }) { periods ->
                            try { commit(data.copy(settings = data.settings.copy(periods = periods))) { page = "home" } }
                            catch (e: Exception) { message(e.message ?: "已有课程使用被删除的节次") }
                        }
                        "browser" -> BrowserImport(data.settings.schoolUrl, { page = "home" }, existingSchool = data.settings.school) { imported ->
                            try {
                                val next = com.xu.wake.domain.ScheduleActions.importSchool(data, imported)
                                val changed = next.settings.periods != data.settings.periods
                                commit(next) { page = "home" }
                                if (changed && imported.periods.isNotEmpty()) offerPeriodEdit = true
                            } catch(e: Exception) { message("导入参数不一致：${e.message}") }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            MotionContent("tab$tab", Modifier.weight(1f)) { target ->
                                if(target == "tab0") Timetable(
                                    data = data,
                                    scroll = scheduleScroll,
                                    viewWeek = viewWeek,
                                    onViewWeek = { viewWeek = it },
                                    onCourse = { details = it },
                                    onAdd = { editingId = null; page = "course" },
                                    locatedCourseId = locatedCourseId,
                                    onImmersive = { hidden ->
                                        immersive = hidden
                                        systemBars(hidden && data.settings.autoHideBars)
                                    },
                                    onBrowser = { openBrowser() },
                                    onImportFile = { importFile.launch(arrayOf("application/json","text/plain","application/octet-stream","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                                    onExportFile = { exportFile.launch("xwake-课表.json") },
                                    onSearch = { searchOpen = true },
                                    onEditSchedule = { editMode = true },
                                    onMoveCourse = { id, day, period ->
                                        val course = data.courses.firstOrNull { it.id == id }
                                        if (course != null) {
                                            val span = course.endPeriod - course.startPeriod
                                            requestMove(
                                                "course", id, day to period,
                                                ScheduleActions.hasConflict(data, day, period, period + span),
                                            )
                                        }
                                    },
                                    onMoveDay = { from, to ->
                                        requestMove("day", "", from to to, ScheduleActions.hasDayConflict(data, to))
                                    },
                                    onMoveRow = { from, to ->
                                        requestMove("row", "", from to to, ScheduleActions.hasRowConflict(data, to))
                                    },
                                    editMode = editMode,
                                    onEditMode = { editMode = it },
                                )
                                else SettingsPage(data, settingsScroll, settingsRoute, highlightRequest, searchOpen, { settingsRoute = it }, { candidate -> commit(candidate) }, { editingId = null; page = "course" }, { page = "periods" }, {
                                    openBrowser()
                                }, { importFile.launch(arrayOf("application/json","text/plain","application/octet-stream","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }, { exportFile.launch("xwake-课表.json") }, ::toggleReminder, { searchOpen = true })
                            }
                        }
                    }
                }
            }
        }


        if (ready && page == "home" && overlayDock && !(immersive && data.settings.autoHideBars)) {
            XwakeDock(
                data.settings.navigationStyle, tab, { tab = it },
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (busy) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha=.12f)).clickable(interactionSource=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication=null) {}, contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        XwakeSplash(visible = splashVisible)
        XwakeSearchOverlay(
            visible = searchOpen,
            data = data,
            onDismiss = { searchOpen = false },


            onCourse = { id ->
                searchOpen = false
                val target = data.courses.firstOrNull { it.id == id }
                if (target != null) {
                    val week = target.weeks.firstOrNull() ?: data.settings.currentWeek
                    viewWeek = week
                    tab = 0
                    page = "home"
                    locatedCourseId = id
                }
            },
            onSetting = { route, title ->
                searchOpen = false
                highlightRequest = title
                settingsRoute = route
                tab = 1
                page = "home"
            },
            onAction = { action ->
                searchOpen = false
                when(action) {
                    PaletteActions.NEW_COURSE -> { editingId = null; page = "course" }
                    PaletteActions.EDIT_PERIODS -> page = "periods"
                    PaletteActions.BROWSER_IMPORT -> openBrowser()
                    PaletteActions.IMPORT_FILE -> importFile.launch(arrayOf("application/json","text/plain","application/octet-stream","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    PaletteActions.EXPORT_FILE -> exportFile.launch("xwake-课表.json")
                    PaletteActions.TOGGLE_THEME -> toggleTheme()
                    PaletteActions.OPEN_SETTINGS -> { tab = 1; page = "home" }
                }
            },
        )
        }
        if (offerPeriodEdit) AlertDialog(
            onDismissRequest = { offerPeriodEdit = false },
            title = { Text("作息时间与本地预设不同") },
            text = { Text("已按导入的时间更新。要不要现在核对一遍？") },
            confirmButton = { TextButton(onClick = { offerPeriodEdit = false; page = "periods" }) { Text("去编辑") } },
            dismissButton = { TextButton(onClick = { offerPeriodEdit = false }) { Text("稍后") } },
        )
        pendingMove?.let { move ->
            AlertDialog(
                onDismissRequest = { pendingMove = null },
                title = { Text("这个位置已经有课") },
                text = { Text("替换：两门课互换位置。覆盖：原有的课被删掉。") },
                confirmButton = {
                    TextButton(onClick = { applyMove(move, ScheduleActions.MoveMode.SWAP); pendingMove = null }) {
                        Text("替换")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { applyMove(move, ScheduleActions.MoveMode.OVERWRITE); pendingMove = null }) {
                            Text("覆盖")
                        }
                        TextButton(onClick = { pendingMove = null }) { Text("取消") }
                    }
                },
            )
        }
        details?.let { course ->
            var confirmDelete by remember { mutableStateOf(false) }
            ModalBottomSheet(onDismissRequest = { details = null }) {
                Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(course.name, style = MaterialTheme.typography.headlineSmall)
                    val from = data.settings.periods.firstOrNull { it.period == course.startPeriod }?.startMinutes
                    val to = data.settings.periods.firstOrNull { it.period == course.endPeriod }?.endMinutes


                    val span = if (from != null && to != null && to > from) "\n时间：${clock(from)}—${clock(to)}" else ""
                    Text("教师：${course.teacher}\n地点：${course.location}\n${dayNames[course.weekday-1]} · 第 ${course.startPeriod}—${course.endPeriod} 节$span\n周次：${course.weeks.joinToString("、")}")
                    Row { Button(onClick = { editingId = course.id; details = null; page = "course" }) { Text("编辑课程") }; TextButton(onClick = { confirmDelete = true }) { Text("删除课程") } }
                    Spacer(Modifier.height(20.dp))
                }
            }
            if(confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除课程？") }, text = { Text("此操作会删除 ${course.name}，建议先导出备份。") }, confirmButton = { TextButton(onClick = { commit(com.xu.wake.domain.ScheduleActions.deleteCourse(data,course.id)) { details = null } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
        }
        }
    }
    }
}

