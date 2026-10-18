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
                /* .xlsx 走表格导入，其余按 JSON 备份处理。 */
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
    BackHandler(page != "home" && !searchOpen) { page = "home" }
    XwakeTheme(data.settings.theme, data.settings.fontScale.toFloat(), data.settings.themePalette, data.settings.dynamicColor, data.settings.motionSpeed, data.settings.reduceMotion, data.settings.transitionStyle) {
        GlassHost {
        var splashVisible by remember { mutableStateOf(true) }
        LaunchedEffect(ready) { if (ready) splashVisible = false }
        Box(Modifier.fillMaxSize()) {
        if (ready) SharedTransitionLayout {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (page == "home") Column(Modifier.navigationBarsPadding()) {
                XwakeDock(
                    data.settings.navigationStyle, tab, { tab = it },
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                                commit(com.xu.wake.domain.ScheduleActions.importSchool(data,imported)) { page = "home" }
                            } catch(e: Exception) { message("导入参数不一致：${e.message}") }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            MotionContent("tab$tab", Modifier.weight(1f)) { target ->
                                if(target == "tab0") Timetable(data, scheduleScroll, { w -> commit(data.copy(settings = data.settings.copy(currentWeek = w))) }, { details = it }, { editingId = null; page = "course" }, locatedCourseId)
                                else SettingsPage(data, settingsScroll, settingsRoute, highlightRequest, searchOpen, { settingsRoute = it }, { candidate -> commit(candidate) }, { editingId = null; page = "course" }, { page = "periods" }, {
                                    openBrowser()
                                }, { importFile.launch(arrayOf("application/json","text/plain","application/octet-stream","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }, { exportFile.launch("xwake-课表.json") }, ::toggleReminder, { searchOpen = true })
                            }
                        }
                    }
                }
            }
        }
        if (busy) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha=.12f)).clickable(interactionSource=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication=null) {}, contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        XwakeSplash(visible = splashVisible)
        XwakeSearchOverlay(
            visible = searchOpen,
            data = data,
            onDismiss = { searchOpen = false },
            /* Searching is a navigation aid: land on the course in the timetable (the week it
               actually meets) and let the user decide whether to edit it. Opening the editor
               straight from a search result was a destructive surprise. */
            onCourse = { id ->
                searchOpen = false
                val target = data.courses.firstOrNull { it.id == id }
                if (target != null) {
                    val week = target.weeks.firstOrNull() ?: data.settings.currentWeek
                    if (week != data.settings.currentWeek) commit(data.copy(settings = data.settings.copy(currentWeek = week)))
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
        details?.let { course ->
            var confirmDelete by remember { mutableStateOf(false) }
            ModalBottomSheet(onDismissRequest = { details = null }) {
                Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(course.name, style = MaterialTheme.typography.headlineSmall)
                    Text("教师：${course.teacher}\n地点：${course.location}\n${dayNames[course.weekday-1]} · 第 ${course.startPeriod}—${course.endPeriod} 节\n周次：${course.weeks.joinToString("、")}")
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

