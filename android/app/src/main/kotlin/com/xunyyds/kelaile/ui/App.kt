package com.xunyyds.kelaile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.CourseSession
import com.xunyyds.kelaile.data.NOTE_MAX
import com.xunyyds.kelaile.domain.ScheduleActions
import com.xunyyds.kelaile.ui.glass.GlassHost
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.palette.PaletteActions
import com.xunyyds.kelaile.ui.search.KelaileSearchOverlay
import com.xunyyds.kelaile.ui.update.UpdateDialog
import com.xunyyds.kelaile.ui.update.rememberUpdateController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KelaileApp() {
    val context = LocalContext.current
    val service = remember { com.xunyyds.kelaile.application.ScheduleService(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(AppData()) }
    var ready by remember { mutableStateOf(false) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("home") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsRoute by rememberSaveable { mutableStateOf("root") }
    val scheduleScroll = androidx.compose.foundation.rememberScrollState()
    val settingsScroll = androidx.compose.foundation.rememberScrollState()
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
                if (result.reminderFailed) message("已保存，但提醒更新失败，请检查通知权限")
            } catch (e: Exception) {
                message("保存失败：${e.message ?: "请检查文件与存储空间"}")
            } finally { busy = false }
        }
    }

    val update = rememberUpdateController(
        currentVersion = appVersionName(context),
        skippedVersion = data.settings.skippedVersion,
        onSkip = { version -> commit(data.copy(settings = data.settings.copy(skippedVersion = version)), {}) },
    )

    var pendingMove by remember { mutableStateOf<Triple<String, String, Pair<Int, Int>>?>(null) }

    var offerPeriodEdit by remember { mutableStateOf(false) }
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val dockOverlap = if (data.settings.navigationStyle == "default") 0.dp
        else with(density) { dockHeightPx.toDp() } + 10.dp

    var undoStack by remember { mutableStateOf<List<AppData>>(emptyList()) }
    fun pushUndo() { undoStack = (undoStack + data).takeLast(UNDO_LIMIT) }
    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        commit(previous)
    }

    fun applyMove(move: Triple<String, String, Pair<Int, Int>>, mode: ScheduleActions.MoveMode) {
        val (kind, id, ends) = move
        val next = when (kind) {
            "course" -> ScheduleActions.moveCourse(data, id, ends.first, ends.second, mode)
            "day" -> ScheduleActions.moveDay(data, ends.first, ends.second, mode)
            else -> ScheduleActions.moveRow(data, ends.first, ends.second, mode)
        }
        pushUndo()
        commit(next)
    }
    fun requestMove(kind: String, id: String, ends: Pair<Int, Int>, conflict: Boolean) {
        val move = Triple(kind, id, ends)
        if (conflict) pendingMove = move else applyMove(move, ScheduleActions.MoveMode.SWAP)
    }
    LaunchedEffect(Unit) {
        try {
            data = service.load()
            val settings = data.settings
            if (startupError == null) {
                val derived = com.xunyyds.kelaile.data.WeekDates
                    .weekOf(settings.start, LocalDate.now())
                    .coerceIn(1, settings.weekCount)
                if (derived != settings.currentWeek) {
                    val advanced = data.copy(settings = settings.copy(currentWeek = derived))
                    data = advanced
                    runCatching { service.save(advanced) }
                }
            }
        } catch (e: Exception) { startupError = "本地课表读取失败，原文件未覆盖：${e.message}" }
        ready = true
    }

    LaunchedEffect(ready) {
        if (ready && startupError == null) update.checkSilently()
    }

    var editSnapshot by remember { mutableStateOf<AppData?>(null) }
    fun enterEditMode(on: Boolean) {
        if (on && editSnapshot == null) editSnapshot = data
        if (!on) editSnapshot = null
        editMode = on
    }
    fun revertEdit() {
        val snapshot = editSnapshot ?: return
        if (snapshot == data) { message("课表没有变化"); return }
        pushUndo()
        commit(snapshot) { editSnapshot = snapshot }
        message("已回退到进入编辑模式前的课表")
    }
    val importTimetable = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val sheet = service.readTimetable(uri)
                commit(ScheduleActions.importSchool(data, sheet))
                message("已导入 ${sheet.courses.size} 门课程")
            } catch (e: Exception) { message("课表导入失败：${e.message ?: "无法读取表格"}") }
            finally { busy = false }
        }
    }
    val exportTimetable = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) scope.launch {
            try { service.exportTimetable(uri, data); message("课表已导出为 Excel") }
            catch (e: Exception) { message("导出失败：${e.message}") }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val candidate = service.readAppBackup(uri)
                if (startupError != null) {
                    val result = service.save(candidate, recovery = true)
                    data = result.data
                    startupError = null
                    if (result.reminderFailed) message("备份已恢复，但提醒排期失败，请检查权限")
                    message("已从备份恢复，损坏原文件已保留")
                } else {
                    commit(candidate)
                    message("已从备份恢复全部数据")
                }
            } catch (e: Exception) { message("恢复失败：${e.message}") }
            finally { busy = false }
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try { service.exportBackup(uri, data); message("应用数据已备份") }
            catch (e: Exception) { message("备份失败：${e.message}") }
        }
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try {
                val path = copyBackground(context, uri)
                commit(data.copy(settings = data.settings.copy(timetableBackground = path))) { message("已应用课程表背景") }
            } catch (e: Exception) { message("设置背景失败：${e.message ?: "无法读取图片"}") }
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
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) message("请先填写有效的教务网址")
        else page = "browser"
    }
    fun toggleTheme() {
        val next = when (data.settings.theme) { "system" -> "dark"; "dark" -> "light"; else -> "system" }
        commit(data.copy(settings = data.settings.copy(theme = next)))
    }

    val overlayDock = data.settings.navigationStyle != "default"
    LaunchedEffect(data.settings.currentWeek) { viewWeek = data.settings.currentWeek }
    LaunchedEffect(page, tab) {
        if (page != "home" || tab != 0) { immersive = false; systemBars(false) }
    }
    BackHandler(page != "home" && !searchOpen) { page = "home" }
    KelaileTheme(
        data.settings.theme,
        data.settings.fontScale.toFloat(),
        data.settings.themePalette,
        data.settings.dynamicColor,
        data.settings.motionSpeed,
        data.settings.reduceMotion,
        data.settings.transitionStyle,
    ) {
        GlassHost {
            var splashVisible by remember { mutableStateOf(true) }
            LaunchedEffect(ready) { if (ready) splashVisible = false }
            Box(Modifier.fillMaxSize()) {
                KelaileScreenBackground(Modifier.fillMaxSize()) {
                    if (ready) {
                        SharedTransitionLayout {
                            Column(Modifier.fillMaxSize()) {
                                if (busy) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        Modifier.fillMaxWidth().height(2.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.Transparent,
                                    )
                                }
                                startupError?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                                    )
                                }
                                MotionContent(page, Modifier.weight(1f)) { route ->
                                    when (route) {
                                        "course" -> CourseEditor(
                                            editing,
                                            data.settings,
                                            editing?.let { data.colors[it.id] } ?: data.settings.defaultColor,
                                            { page = "home" },
                                        ) { course, color ->
                                            try {
                                                pushUndo()
                                                commit(ScheduleActions.saveCourse(data, course, color)) { page = "home" }
                                            } catch (e: Exception) { message(e.message ?: "课程参数无效") }
                                        }
                                        "periods" -> PeriodEditor(data.settings, { page = "home" }) { periods ->
                                            try {
                                                commit(data.copy(settings = data.settings.copy(periods = periods))) { page = "home" }
                                            } catch (e: Exception) { message(e.message ?: "已有课程使用被删除的节次") }
                                        }
                                        "browser" -> BrowserImport(
                                            data.settings.schoolUrl,
                                            { page = "home" },
                                            existingSchool = data.settings.school,
                                        ) { imported ->
                                            try {
                                                val next = ScheduleActions.importSchool(data, imported)
                                                val changed = next.settings.periods != data.settings.periods
                                                commit(next) { page = "home" }
                                                if (changed && imported.periods.isNotEmpty()) offerPeriodEdit = true
                                            } catch (e: Exception) { message("导入参数不一致：${e.message}") }
                                        }
                                        else -> Column(Modifier.fillMaxSize()) {
                                            MotionContent("tab$tab", Modifier.weight(1f)) { target ->
                                                if (target == "tab0") Timetable(
                                                    data = data,
                                                    scroll = scheduleScroll,
                                                    viewWeek = viewWeek,
                                                    onViewWeek = { viewWeek = it },
                                                    onCourse = { details = it },
                                                    onAdd = { editingId = null; page = "course" },
                                                    locatedCourseId = locatedCourseId,
                                                    onLocateHandled = { locatedCourseId = null },
                                                    onImmersive = { hidden ->
                                                        immersive = hidden
                                                        systemBars(hidden && data.settings.autoHideBars)
                                                    },
                                                    onBrowser = { openBrowser() },
                                                    onImportFile = { importTimetable.launch(XLSX_MIME_TYPES) },
                                                    onExportFile = { exportTimetable.launch("课来了-课表.xlsx") },
                                                    onSearch = { searchOpen = true },
                                                    onEditSchedule = { enterEditMode(true) },
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
                                                    onEditMode = { enterEditMode(it) },
                                                    canUndo = undoStack.isNotEmpty(),
                                                    onUndo = { undo() },
                                                    onRevert = { revertEdit() },
                                                ) else SettingsPage(
                                                    data = data,
                                                    scroll = settingsScroll,
                                                    route = settingsRoute,
                                                    highlightRequest = highlightRequest,
                                                    onHighlightConsumed = { highlightRequest = null },
                                                    searchExpanded = searchOpen,
                                                    onRoute = { settingsRoute = it },
                                                    onChange = { candidate -> commit(candidate) },
                                                    onAdd = { editingId = null; page = "course" },
                                                    onPeriods = { page = "periods" },
                                                    onBrowser = { openBrowser() },
                                                    onImportTimetable = { importTimetable.launch(XLSX_MIME_TYPES) },
                                                    onExportTimetable = { exportTimetable.launch("课来了-课表.xlsx") },
                                                    onImportBackup = { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                                                    onExportBackup = { exportBackup.launch("课来了-备份.json") },
                                                    onReminder = ::toggleReminder,
                                                    onSearch = { searchOpen = true },
                                                    update = update,
                                                    onPickBackground = {
                                                        pickBackground.launch(
                                                            androidx.activity.result.PickVisualMediaRequest(
                                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                            ),
                                                        )
                                                    },
                                                    bottomInset = dockOverlap,
                                                )
                                            }
                                        }
                                    }
                                }
                                val dockVisible = ready && page == "home" &&
                                    !(immersive && data.settings.autoHideBars)
                                if (dockVisible && !overlayDock) {
                                    KelaileDock(
                                        data.settings.navigationStyle, tab, { tab = it },
                                        Modifier.fillMaxWidth()
                                            .navigationBarsPadding()
                                            .onSizeChanged { dockHeightPx = it.height },
                                    )
                                }
                            }

                            KelaileSearchOverlay(
                                visible = searchOpen,
                                data = data,
                                onDismiss = { searchOpen = false },
                                onCourse = { id ->
                                    searchOpen = false
                                    highlightRequest = null
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
                                    locatedCourseId = null
                                    highlightRequest = title
                                    settingsRoute = route
                                    tab = 1
                                    page = "home"
                                },
                                onAction = { action ->
                                    searchOpen = false
                                    highlightRequest = null
                                    locatedCourseId = null
                                    when (action) {
                                        PaletteActions.NEW_COURSE -> { editingId = null; page = "course" }
                                        PaletteActions.EDIT_PERIODS -> page = "periods"
                                        PaletteActions.BROWSER_IMPORT -> openBrowser()
                                        PaletteActions.IMPORT_FILE -> importTimetable.launch(XLSX_MIME_TYPES)
                                        PaletteActions.EXPORT_FILE -> exportTimetable.launch("课来了-课表.xlsx")
                                        PaletteActions.TOGGLE_THEME -> toggleTheme()
                                        PaletteActions.OPEN_SETTINGS -> { tab = 1; page = "home" }
                                    }
                                },
                            )
                        }
                    }

                }

                // 悬浮件必须在内容采样层之外：液态底栏折射的正是上面录制的「壁纸 + 正文」
                val dockVisible = ready && page == "home" && !searchOpen &&
                    !(immersive && data.settings.autoHideBars)
                if (dockVisible && overlayDock) {
                    KelaileDock(
                        data.settings.navigationStyle, tab, { tab = it },
                        Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .widthIn(max = if (data.settings.navigationStyle == "liquid") 360.dp else Dp.Infinity)
                            .onSizeChanged { dockHeightPx = it.height },
                    )
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (dockVisible && overlayDock) 88.dp else 16.dp),
                )
                GlassBusyOverlay(busy)
                KelaileSplash(visible = splashVisible)

                var confirmInstall by remember { mutableStateOf(false) }
                KelaileDialog(
                    visible = confirmInstall,
                    onDismiss = { confirmInstall = false },
                    title = "开始安装？",
                    message = "安装将关闭应用，是否继续？",
                    confirmLabel = "继续安装",
                    onConfirm = {
                        confirmInstall = false
                        if (update.install()) (context as? android.app.Activity)?.finish()
                    },
                    dismissLabel = "取消",
                )
                update.dialogInfo?.let { info ->
                    UpdateDialog(
                        info = info,
                        asset = update.dialogAsset,
                        currentVersion = appVersionName(context),
                        forced = update.dialogForced,
                        stage = update.stage,
                        progress = update.progress,
                        error = update.errorText,
                        stageLabel = update.stageLabel,
                        onDismiss = { update.dismissDialog() },
                        onUpdate = { update.startDownload() },
                        onSkipVersion = { update.skipCurrentVersion() },
                        onCancelDownload = { update.cancelDownload() },
                        onRetry = { update.retry() },
                        onInstall = { confirmInstall = true },
                        onOpenReleasePage = {
                            runCatching {
                                com.xunyyds.kelaile.core.platform.AppPlatform.openLink(
                                    context,
                                    com.xunyyds.kelaile.data.UpdateSource.RELEASE_PAGE,
                                )
                            }
                        },
                    )
                }
                KelaileDialog(
                    visible = update.notice != null,
                    onDismiss = { update.dismissNotice() },
                    title = if (update.notice?.startsWith("已是最新") == true) "已是最新版本" else "更新失败",
                    message = update.notice,
                    confirmLabel = "好",
                )
                KelaileDialog(
                    visible = offerPeriodEdit,
                    onDismiss = { offerPeriodEdit = false },
                    title = "作息时间与本地预设不同",
                    message = "已按导入的时间更新。要不要现在核对一遍？",
                    confirmLabel = "去编辑",
                    onConfirm = { offerPeriodEdit = false; page = "periods" },
                    dismissLabel = "稍后",
                )
                pendingMove?.let { move ->
                    KelaileDialog(
                        visible = true,
                        onDismiss = { pendingMove = null },
                        title = "这个位置已经有课",
                        message = "替换：两门课互换位置。覆盖：原有的课被删掉。",
                        confirmLabel = "替换",
                        onConfirm = { applyMove(move, ScheduleActions.MoveMode.SWAP); pendingMove = null },
                        dismissLabel = "覆盖",
                        onDismissAction = {
                            applyMove(move, ScheduleActions.MoveMode.OVERWRITE)
                            pendingMove = null
                        },
                    )
                }
                details?.let { course ->
                    CourseDetailSheet(
                        course = course,
                        data = data,
                        visible = true,
                        onDismiss = { details = null },
                        onEdit = { editingId = course.id; details = null; page = "course" },
                        onDelete = {
                            pushUndo()
                            commit(ScheduleActions.deleteCourse(data, course.id)) { details = null }
                        },
                        onSaveNote = { note ->
                            pushUndo()
                            commit(ScheduleActions.setNote(data, course.id, note)) { details = null }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseDetailSheet(
    course: CourseSession,
    data: AppData,
    visible: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveNote: (String) -> Unit,
) {
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.card)
    var confirmDelete by remember { mutableStateOf(false) }
    var noteDraft by remember(course.id) { mutableStateOf(course.note) }
    var editingNote by remember(course.id) { mutableStateOf(false) }
    GlassSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                course.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val from = data.settings.periods.firstOrNull { it.period == course.startPeriod }?.startMinutes
            val to = data.settings.periods.firstOrNull { it.period == course.endPeriod }?.endMinutes
            val clockSpan = if (from != null && to != null && to > from) " · ${clock(from)}—${clock(to)}" else ""
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassPanel(shape, canvas.fillSunken, Color.Transparent, Color.Transparent, 0.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailLine("时间", "${dayNames[course.weekday - 1]} · 第 ${course.startPeriod}—${course.endPeriod} 节$clockSpan")
                if (course.location.isNotBlank()) DetailLine("地点", course.location)
                if (course.teacher.isNotBlank()) DetailLine("教师", course.teacher)
                DetailLine("周次", course.weeks.joinToString("、"))
            }
            Section("备注") {
                if (editingNote) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it.take(NOTE_MAX) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("课程备注（最多 $NOTE_MAX 字）") },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            TextButton(onClick = { noteDraft = course.note; editingNote = false }) { Text("取消") }
                            GlassPillButton("保存备注", onClick = { onSaveNote(noteDraft) })
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editingNote = true }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            course.note.ifBlank { "还没有备注，点击添加" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (course.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (course.note.isBlank()) "添加备注" else "编辑备注",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassPillButton("编辑课程", onClick = onEdit, tint = MaterialTheme.colorScheme.primary)
                GlassPillButton(
                    "删除课程",
                    onClick = { confirmDelete = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    KelaileDialog(
        visible = confirmDelete,
        onDismiss = { confirmDelete = false },
        title = "删除课程？",
        message = "此操作会删除 ${course.name}，建议先导出备份。",
        confirmLabel = "删除",
        onConfirm = { confirmDelete = false; onDelete() },
        dismissLabel = "取消",
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.width(52.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val UNDO_LIMIT = 20

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

internal val XLSX_MIME_TYPES = arrayOf(XLSX_MIME)

internal fun appVersionName(context: android.content.Context): String = runCatching {
    context.applicationContext.packageManager
        .getPackageInfo(context.applicationContext.packageName, 0)
        .versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "0"

private suspend fun copyBackground(context: android.content.Context, uri: android.net.Uri): String =
    withContext(kotlinx.coroutines.Dispatchers.IO) {
        val app = context.applicationContext
        val directory = java.io.File(app.filesDir, "backgrounds")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建背景目录" }
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        val target = java.io.File(directory, "timetable-${System.currentTimeMillis()}.img")
        val input = app.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")
        input.use { source ->
            target.outputStream().use { output ->
                source.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        check(target.length() in 1..(24L * 1024 * 1024)) {
            target.delete()
            "图片过大，请选择 24 MB 以内的图片"
        }
        target.absolutePath
    }
