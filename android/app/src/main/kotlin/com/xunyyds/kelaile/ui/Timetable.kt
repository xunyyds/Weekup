package com.xunyyds.kelaile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

private val gridCellShape = androidx.compose.ui.graphics.RectangleShape

/**
 * 表格底部额外预留的空行数：约等于两节课的高度，
 * 让最后一节课滚得到底栏之上，不被悬浮底栏压住。
 */
private const val GRID_BOTTOM_ROWS = 2

private val gutterWidth = 42.dp

private val dayHeaderHeight = 38.dp

private data class CourseStyle(val background: Color, val ink: Color)

internal data class PlacedCourse(
    val course: CourseSession,
    val lane: Int,
    val laneCount: Int,
    val startIndex: Int,
    val endIndex: Int,
)

/**
 * 屏幕上的一格：星期、行下标与节次号。
 *
 * 空白格点击的「先出加号、再进编辑」两步状态就挂在这上面，
 * 用行号而不是节次号做失败比较，节次跳号时也不会串格。
 */
internal data class GridCell(val weekday: Int, val rowIndex: Int, val period: Int)

internal data class WeekProjection(
    val days: List<Int>,
    val placed: Map<Int, List<PlacedCourse>>,
)

internal fun projectWeek(
    courses: List<CourseSession>,
    week: Int,
    firstDay: Int,
    lastDay: Int,
    weekStart: Int,
    periods: List<PeriodTime>,
): WeekProjection {
    val days = (firstDay..lastDay).sortedBy { (it - weekStart + 7) % 7 }
    val weekCourses = courses.filter { week in it.weeks }
    val placed = LinkedHashMap<Int, List<PlacedCourse>>(days.size)
    days.forEach { day ->
        val dayCourses = weekCourses.filter { it.weekday == day }.sortedBy { it.startPeriod }
        placed[day] = dayCourses.map { course ->
            val startIndex = periods.indexOfFirst { it.period == course.startPeriod }
            val endIndex = periods.indexOfFirst { it.period == course.endPeriod }
            val conflicts = dayCourses.filter { it.startPeriod <= course.endPeriod && it.endPeriod >= course.startPeriod }
            PlacedCourse(
                course = course,
                lane = conflicts.indexOfFirst { it.id == course.id }.coerceAtLeast(0),
                laneCount = conflicts.size.coerceAtLeast(1),
                startIndex = startIndex,
                endIndex = endIndex,
            )
        }
    }
    return WeekProjection(days, placed)
}

internal fun clock(minutes: Int): String =
    (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

/** 字号量化到 0.5sp 一档，避免捏合时文本每帧重新排版造成抖动。 */
internal fun quantizeSp(size: Float): Float = (size * 2f).roundToInt() / 2f

/**
 * 课表配色。各项都允许「跟随主题」：值为 [GRID_COLOR_AUTO] 时回退到当前 Material 配色，
 * 因此新增设置不会改变默认观感，深色与浅色主题都自然成立。
 */
internal data class GridPalette(
    val header: Color,
    val cell: Color,
    val ink: Color,
    val line: Color,
    val lineStyle: String,
)

@Composable
internal fun rememberGridPalette(settings: AppSettings): GridPalette {
    val scheme = MaterialTheme.colorScheme
    val canvas = LocalKelaileCanvas.current
    return remember(
        settings.tableHeaderColor,
        settings.cellBackgroundColor,
        settings.cellTextColor,
        settings.gridLineColor,
        settings.gridLineStyle,
        scheme,
        canvas,
    ) {
        fun resolve(value: Long, fallback: Color) =
            if (value == GRID_COLOR_AUTO) fallback else Color(value)
        GridPalette(
            header = resolve(settings.tableHeaderColor, Color.Transparent),
            cell = resolve(
                settings.cellBackgroundColor,
                canvas.fill.copy(alpha = if (canvas.isDark) 0.22f else 0.30f),
            ),
            ink = resolve(settings.cellTextColor, scheme.onSurface),
            line = resolve(
                settings.gridLineColor,
                if (canvas.isDark) Color.White.copy(alpha = 0.13f) else Color(0xFF1B2230).copy(alpha = 0.11f),
            ),
            lineStyle = settings.gridLineStyle,
        )
    }
}

/**
 * 自定义课表背景图。
 *
 * 路径存在设置里，图片由用户从相册选择后复制到应用私有目录；解码失败时静默回退到纯色背景，
 * 不让一张坏图把课表整页拖黑。
 */
@Composable
internal fun rememberBackgroundPainter(path: String): State<androidx.compose.ui.graphics.painter.Painter?> {
    return produceState<androidx.compose.ui.graphics.painter.Painter?>(null, path) {
        value = if (path.isBlank()) null else withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val file = java.io.File(path)
                if (!file.isFile) return@runCatching null
                if (file.length() > MAX_BACKGROUND_BYTES) return@runCatching null
                val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return@runCatching null
                androidx.compose.ui.graphics.painter.BitmapPainter(bitmap.asImageBitmap())
            }.getOrNull()
        }
    }
}

private const val MAX_BACKGROUND_BYTES = 24L * 1024 * 1024

/** 格子间距：无样式时不留缝，网格线在视觉上消失；其余样式保留 1dp 缝隙。 */
private fun gridCellPadding(style: String): androidx.compose.ui.unit.Dp =
    if (style == GRID_LINE_NONE) 0.dp else 1.dp

/**
 * 表格容器的形状：选「无网格」时整个容器去掉圆角、描边与压面，
 * 表格直接铺在壁纸上；其余样式保持原来的大圆角玻璃卡。
 */
private fun gridContainerShape(style: String) =
    if (style == GRID_LINE_NONE) androidx.compose.ui.graphics.RectangleShape
    else KelaileShapes.rounded(KelaileShapes.card)

/**
 * 课表容器的压面浓度：设了背景图时压得很淡，让图透出来；
 * 没设图时保持原来的玻璃浓度。
 */
private fun gridVeil(settings: AppSettings, dark: Boolean, top: Boolean): Float {
    val hasImage = settings.timetableBackground.isNotBlank()
    return when {
        hasImage && dark -> if (top) 0.18f else 0.10f
        hasImage -> if (top) 0.26f else 0.16f
        dark -> if (top) 0.5f else 0.38f
        else -> if (top) 0.62f else 0.5f
    }
}

/**
 * 网格线图层，只负责「虚线」。
 *
 * 其余两种样式靠格子间距表达：缝隙露出课表背景即「透明线」，缝隙归零即「无样式」。
 */
@Composable
private fun DashedGridLines(
    columns: Int,
    rows: Int,
    cellW: Float,
    cellH: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (columns <= 0 || rows <= 0) return
    androidx.compose.foundation.Canvas(modifier) {
        val width = 1.dp.toPx()
        val effect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
        val totalY = cellH * rows
        for (c in 1 until columns) {
            val x = c * cellW
            drawLine(color, Offset(x, 0f), Offset(x, totalY), strokeWidth = width, pathEffect = effect)
        }
        val totalX = cellW * columns
        for (r in 1 until rows) {
            val y = r * cellH
            drawLine(color, Offset(0f, y), Offset(totalX, y), strokeWidth = width, pathEffect = effect)
        }
    }
}

private fun courseVisual(
    course: CourseSession,
    colors: Map<String, Long>,
    defaultColor: Long,
    dark: Boolean,
    courseCellColor: Long = com.xunyyds.kelaile.data.GRID_COLOR_AUTO,
): CourseStyle {
    // 优先用课程自己的颜色；没单独设色时，先看「有课表格自定义颜色」，
    // 再回退到默认课程色。这样设置里的新项能一次性统一所有未设色的课。
    val explicit = colors[course.id]
    val raw = when {
        explicit != null -> Color(explicit)
        courseCellColor != com.xunyyds.kelaile.data.GRID_COLOR_AUTO -> Color(courseCellColor)
        else -> Color(defaultColor)
    }
    val tint = courseCardTint(raw, dark)
    return CourseStyle(tint, courseCardInk(tint, dark))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Timetable(
    data: AppData,
    @Suppress("UNUSED_PARAMETER") scroll: ScrollState,
    viewWeek: Int,
    onViewWeek: (Int) -> Unit,
    onCourse: (CourseSession) -> Unit,
    onAdd: () -> Unit,
    /** 在指定星期与节次上开新课（空白格二次点击进入编辑时用它预填表单）。 */
    onAddAt: (weekday: Int, period: Int) -> Unit = { _, _ -> onAdd() },
    locatedCourseId: String? = null,
    onLocateHandled: () -> Unit = {},
    onImmersive: (Boolean) -> Unit = {},
    onBrowser: () -> Unit = {},
    onImportFile: () -> Unit = {},
    onExportFile: () -> Unit = {},
    onSearch: () -> Unit = {},
    onEditSchedule: () -> Unit = {},
    editMode: Boolean = false,
    onMoveCourse: (String, Int, Int) -> Unit = { _, _, _ -> },
    onMoveDay: (Int, Int) -> Unit = { _, _ -> },
    onMoveRow: (Int, Int) -> Unit = { _, _ -> },
    onEditMode: (Boolean) -> Unit = {},
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    onRevert: () -> Unit = {},
) {
    val s = data.settings
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snapMillis = kelaileMillis(240)
    val today = LocalDate.now()

    var boxW by remember { mutableIntStateOf(0) }
    var viewportH by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<PlacedCourse?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    var dragSpot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var columnFrom by remember { mutableIntStateOf(0) }
    var columnTo by remember { mutableIntStateOf(0) }
    var rowFrom by remember { mutableIntStateOf(0) }
    var rowTo by remember { mutableIntStateOf(0) }
    var colDrag by remember { mutableFloatStateOf(0f) }
    var rowDrag by remember { mutableFloatStateOf(0f) }

    var zoomMemory by rememberSaveable { mutableFloatStateOf(GRID_MIN_ZOOM) }
    val grid = remember { TimetableGridState(zoomMemory) }
    LaunchedEffect(grid) {
        snapshotFlow { grid.zoom }.distinctUntilChanged().collect { zoomMemory = it }
    }

    // 空白格两步点击：第一次点亮加号，第二次才进编辑。排课模式下同样走这一套。
    var tappedCell by remember { mutableStateOf<GridCell?>(null) }

    val gutterPx = with(density) { gutterWidth.toPx() }
    val headerPx = with(density) { dayHeaderHeight.toPx() }
    val week = viewWeek.coerceIn(1, s.weekCount)

    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    var slideFrom by remember { mutableIntStateOf(week) }

    val projection = remember(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods) {
        projectWeek(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods)
    }
    val canvasColors = LocalKelaileCanvas.current
    val palette = rememberGridPalette(s)
    val styles = remember(projection, data.colors, s.defaultColor, canvasColors.isDark, s.courseCellColor) {
        projection.placed.values.asSequence().flatten()
            .associate {
                it.course.id to courseVisual(it.course, data.colors, s.defaultColor, canvasColors.isDark, s.courseCellColor)
            }
    }
    // 课表背景图由页面壁纸层统一绘制（App.kt 传入），这里不再重复解码

    val dayCount = projection.days.size.coerceAtLeast(1)
    val periodCount = s.periods.size.coerceAtLeast(1)
    val availW = (boxW - gutterPx).coerceAtLeast(1f)
    val availH = (viewportH - headerPx).coerceAtLeast(1f)
    val noGrid = palette.lineStyle == GRID_LINE_NONE
    // 底部统一预留约两节课的高度，任何一种网格模式下最后一节课都能滚到底栏之上
    val bottomRows = GRID_BOTTOM_ROWS
    val layout = remember(dayCount, periodCount, availW, availH, gutterPx, headerPx, bottomRows) {
        GridLayout(dayCount, periodCount, availW, availH, gutterPx, headerPx, bottomRows)
    }
    val zoom = grid.zoom
    val cellW = layout.cellWidth(zoom)
    val cellH = layout.cellHeight(zoom)
    // 布局尺寸一律取整像素：格子、卡片、虚线共用同一套整数步长，
    // 否则浮点 cellW 逐列累积半像素，网格线会和格子边缘错位。
    val cellWpx = cellW.roundToInt().coerceAtLeast(1)
    val cellHpx = cellH.roundToInt().coerceAtLeast(1)

    val layoutNow by rememberUpdatedState(layout)
    val weekNow by rememberUpdatedState(week)
    val editNow by rememberUpdatedState(editMode)
    val projectionNow by rememberUpdatedState(projection)
    val settingsNow by rememberUpdatedState(s)
    val onWeekNow by rememberUpdatedState(onViewWeek)
    val onImmersiveNow by rememberUpdatedState(onImmersive)
    val onEditModeNow by rememberUpdatedState(onEditMode)

    val gestureJob = remember { JobSlot() }
    val velocityTracker = remember { VelocityTracker() }

    LaunchedEffect(layout) {
        grid.clampInto(layout)
    }

    LaunchedEffect(week) {
        val direction = if (week >= slideFrom) 1f else -1f
        slideFrom = week
        slide.snapTo(direction * .30f)
        slide.animateTo(0f, tween(durationMillis = snapMillis, easing = KelaileMotion.emphasizedEasing))
    }

    // 长按不再武装「进排课模式」：排课只在右上角「更多」菜单里进（见 TopBar 菜单）。
    // 这里仅保留长按指针跟踪的清理，避免上一轮的手势状态漏到下一轮。

    BackHandler(enabled = editMode) { onEditMode(false) }
    LaunchedEffect(editMode) { if (!editMode) { dragging = null; dragSpot = null; columnFrom = 0; rowFrom = 0 } }

    suspend fun animateZoomTo(target: Float, anchorX: Float, anchorY: Float) {
        val bounds = layoutNow
        val z0 = grid.zoom
        if (z0 <= 0f || abs(target - z0) < 1e-3f) {
            grid.zoom = target
            grid.clampInto(bounds)
            return
        }
        val x0 = grid.offsetX
        val y0 = grid.offsetY
        val distance = abs(target - z0)
        val spec = if (distance < 0.12f) {
            spring<Float>(dampingRatio = 0.9f, stiffness = 400f)
        } else {
            spring<Float>(dampingRatio = 0.82f, stiffness = 220f)
        }
        animate(0f, 1f, animationSpec = spec) { t, _ ->
            if (t >= 1f) {
                grid.zoom = target
                grid.offsetX = anchoredOffset(x0, anchorX, target / z0, bounds.availW, bounds.contentWidth(target))
                grid.offsetY = anchoredOffset(y0, anchorY, target / z0, bounds.availH, bounds.contentHeight(target))
                return@animate
            }
            val eased = t
            val z = z0 + (target - z0) * eased
            val ratio = z / z0
            grid.zoom = z
            grid.offsetX = anchoredOffset(x0, anchorX, ratio, bounds.availW, bounds.contentWidth(z))
            grid.offsetY = anchoredOffset(y0, anchorY, ratio, bounds.availH, bounds.contentHeight(z))
        }
        grid.zoom = target
        grid.clampInto(bounds)
    }

    suspend fun settleAxis(
        start: Float,
        velocity: Float,
        max: Float,
        set: (Float) -> Unit,
    ) {
        if (start < 0f || start > max) {
            animate(
                start, start.coerceIn(0f, max),
                animationSpec = spring(dampingRatio = .82f, stiffness = 900f),
            ) { v, _ -> set(v) }
            set(start.coerceIn(0f, max))
            return
        }
        decayInto(start, velocity, 0f, max, onChange = set)
    }

    fun settle(velocity: Offset, pinched: Boolean, lastCentroid: Offset) {
        val bounds = layoutNow
        gestureJob.launch(scope) {
            if (pinched) {
                val anchorX = (lastCentroid.x - bounds.gutterPx).let {
                    if (it.isFinite()) it.coerceIn(0f, bounds.availW) else bounds.availW / 2f
                }
                val anchorY = (lastCentroid.y - bounds.headerPx).let {
                    if (it.isFinite()) it.coerceIn(0f, bounds.availH) else bounds.availH / 2f
                }
                animateZoomTo(nearestZoomRung(grid.zoom), anchorX, anchorY)
                return@launch
            }
            val maxX = bounds.maxOffsetX(grid.zoom)
            val maxY = bounds.maxOffsetY(grid.zoom)
            coroutineScope {
                launch { settleAxis(grid.offsetX, -velocity.x, maxX) { grid.offsetX = it } }
                launch { settleAxis(grid.offsetY, -velocity.y, maxY) { grid.offsetY = it } }
            }
            grid.offsetX = clampOffset(grid.offsetX, bounds.availW, bounds.contentWidth(grid.zoom))
            grid.offsetY = clampOffset(grid.offsetY, bounds.availH, bounds.contentHeight(grid.zoom))
        }
    }

    fun editableZoneAt(x: Float, y: Float): Boolean {
        val bounds = layoutNow
        if (x < bounds.gutterPx + 2f || y < bounds.headerPx + 2f) return true
        val col = bounds.columnAt(x, grid.offsetX, grid.zoom)
        val row = bounds.rowAt(y, grid.offsetY, grid.zoom)
        val day = projectionNow.days.getOrNull(col) ?: return false
        return projectionNow.placed[day].orEmpty().any { row in it.startIndex..it.endIndex }
    }

    /**
     * 点中的那门课；点在空白格（或表格之外）返回 null。
     *
     * 与 [cellAt] 共用同一套坐标换算，保证「点中卡片」和「点中格子」不会互相矛盾。
     */
    fun courseAtPoint(x: Float, y: Float): CourseSession? {
        val bounds = layoutNow
        if (x < bounds.gutterPx || y < bounds.headerPx) return null
        val col = bounds.columnAt(x, grid.offsetX, grid.zoom)
        val row = bounds.rowAt(y, grid.offsetY, grid.zoom)
        val day = projectionNow.days.getOrNull(col) ?: return null
        return projectionNow.placed[day].orEmpty()
            .firstOrNull { row in it.startIndex..it.endIndex }?.course
    }

    /** 按下点是否落在一张有课卡片上。 */
    fun courseAt(x: Float, y: Float): Boolean = courseAtPoint(x, y) != null

    /**
     * 把屏幕坐标换算成「表格里的哪一格」。落在左侧节次栏、星期表头或
     * 底部预留空白里时返回 null —— 那些位置不是可编辑的格子。
     */
    fun cellAt(x: Float, y: Float): GridCell? {
        val bounds = layoutNow
        if (x < bounds.gutterPx || y < bounds.headerPx) return null
        val col = bounds.columnAt(x, grid.offsetX, grid.zoom)
        val row = bounds.rowAt(y, grid.offsetY, grid.zoom)
        if (row !in 0 until periodCount) return null
        val day = projectionNow.days.getOrNull(col) ?: return null
        val period = settingsNow.periods.getOrNull(row)?.period ?: return null
        return GridCell(day, row, period)
    }

    /**
     * 单击的统一入口。
     *
     * 规则只有一条：有课卡片 → 打开课程详情；空白格 → 第一次点亮加号、第二次开编辑。
     * 「第一次」与「第二次」以格子为准，换一格就从第一下重新开始。
     */
    fun handleTap(position: Offset) {
        val hit = courseAtPoint(position.x, position.y)
        if (hit != null) {
            tappedCell = null
            onCourse(hit)
            return
        }
        val cell = cellAt(position.x, position.y)
        if (cell == null) {
            tappedCell = null
            return
        }
        if (tappedCell == cell) {
            tappedCell = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddAt(cell.weekday, cell.period)
        } else {
            tappedCell = cell
        }
    }

    Column(Modifier.fillMaxSize()) {
        KelaileTopBar(
            title = "",
            collapseFraction = if (immersive) 1f else (grid.offsetY / (availH * .6f)).coerceIn(0f, 1f),
            modifier = Modifier.graphicsLayer { translationY = if (immersive) -size.height else 0f },
            titleContent = {
                if (editMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("排课编辑", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "拖课程改时间；拖表头整列移动",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().combinedClickable(
                            // 点按打开周次选择器；长按无响应，避免误触
                            onClick = { pickerOpen = true },
                            onLongClick = { },
                        ),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text("第 " + week + " 周", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            WeekDates.rangeLabel(s.start, week),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            actions = {
                if (editMode) {
                    GlassPillButton("撤销", onClick = onUndo, compact = true)
                    Spacer(Modifier.width(6.dp))
                    GlassPillButton("回退", onClick = onRevert, compact = true)
                    Spacer(Modifier.width(6.dp))
                    GlassPillButton("完成", onClick = { onEditMode(false) }, compact = true)
                } else {
                    if (week != s.currentWeek) {
                        GlassPillButton(
                            "回到本周",
                            onClick = { onViewWeek(s.currentWeek) },
                            compact = true,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { menu = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⋯", style = MaterialTheme.typography.titleMedium)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(
                                "新增课程" to onAdd,
                                "搜索课程" to onSearch,
                                "浏览器导入课表" to onBrowser,
                                "导入课表文件" to onImportFile,
                                "导出备份" to onExportFile,
                                "排课编辑模式" to onEditSchedule,
                            ).forEach { (label, action) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { menu = false; action() },
                                )
                            }
                        }
                    }
                }
            },
        )
        if (data.courses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                val canvas = LocalKelaileCanvas.current
                val shape = KelaileShapes.rounded(KelaileShapes.sheet)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(KelaileElevation.soft(KelaileElevation.card, shape))
                        .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
                        .padding(horizontal = 24.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KelaileSpacing.lg),
                ) {
                    Text("每一周，都从容一点", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "还没有课程，新增或导入你的课表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassPillButton("新增课程", onClick = onAdd)
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
            val gridShape = gridContainerShape(palette.lineStyle)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (noGrid) 0.dp else 10.dp)
                    .then(
                        // 无网格时不投影、不描边：表格与壁纸齐平，没有任何容器痕迹
                        if (noGrid) Modifier
                        else KelaileElevation.soft(4.dp, gridShape, 0.03f, 0.06f),
                    )
                    .clip(gridShape)
                    .background(
                        Brush.verticalGradient(
                            0f to canvasColors.fillElevated.copy(
                                alpha = if (noGrid) 0f else gridVeil(s, canvasColors.isDark, top = true),
                            ),
                            1f to canvasColors.fill.copy(
                                alpha = if (noGrid) 0f else gridVeil(s, canvasColors.isDark, top = false),
                            ),
                        ),
                    )
                    .then(
                        if (noGrid) Modifier
                        else Modifier.border(
                            1.dp,
                            Brush.verticalGradient(
                                0f to canvasColors.strokeTop,
                                1f to canvasColors.strokeBottom,
                            ),
                            gridShape,
                        ),
                    ),
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { boxW = it.width; viewportH = it.height }
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        // 系统长按时长：按下不移动超过这个时间就算长按
                        val longPressTimeout =
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            gestureJob.cancel()
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var pinched = false
                            var started = false
                            var consumedByChild = false
                            var travelled = 0f
                            var pulledDown = 0f
                            var weekPull = 0f
                            var verticalOnly = true
                            var lastCentroid = Offset.Unspecified
                            var lastRung = nearestZoomRung(grid.zoom)
                            val primaryId = down.id
                            var lastUptime = down.uptimeMillis
                            // 长按判定：按下后原地不动超过系统长按时长，就吞掉这一轮的点击，
                            // 既不开课程详情、也不进排课模式 —— 同一个手势只执行一个逻辑。
                            var longPressed = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 0) break
                                val bounds = layoutNow
                                val pan = event.calculatePan()
                                val zoomChange = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                lastUptime = event.changes.maxOf { it.uptimeMillis }

                                if (pressedCount > 1) {
                                    pinched = true
                                    if (zoomChange != 1f && zoomChange.isFinite()) {
                                        val z0 = grid.zoom
                                        val raw = z0 * zoomChange
                                        val next = rubberZoom(raw)
                                        if (next != z0) {
                                            val ratio = next / z0
                                            val ax = ((centroid.x - bounds.gutterPx)).coerceIn(0f, bounds.availW)
                                            val ay = ((centroid.y - bounds.headerPx)).coerceIn(0f, bounds.availH)
                                            grid.offsetX = anchoredOffset(
                                                grid.offsetX, ax, ratio, bounds.availW, bounds.contentWidth(next),
                                            )
                                            grid.offsetY = anchoredOffset(
                                                grid.offsetY, ay, ratio, bounds.availH, bounds.contentHeight(next),
                                            )
                                            grid.zoom = next

                                            val rung = nearestZoomRung(next)
                                            if (rung != lastRung) {
                                                lastRung = rung
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                    if (pan != Offset.Zero) {
                                        grid.offsetX = rubberOffset(
                                            grid.offsetX - pan.x, bounds.availW, bounds.contentWidth(grid.zoom),
                                        )
                                        grid.offsetY = rubberOffsetPinnedTop(
                                            grid.offsetY - pan.y, bounds.availH, bounds.contentHeight(grid.zoom),
                                        )
                                    }
                                    lastCentroid = centroid
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    continue
                                }

                                if (pinched) break

                                val tracked = event.changes.firstOrNull { it.id == primaryId } ?: event.changes.first()
                                val delta = tracked.position - down.position
                                if (!started) {
                                    travelled = delta.getDistance()

                                    if (travelled < touchSlop) {
                                        velocityTracker.addPosition(tracked.uptimeMillis, tracked.position)
                                        continue
                                    }
                                    started = true
                                    verticalOnly = abs(delta.y) >= abs(delta.x)
                                }
                                if (editNow && editableZoneAt(down.position.x, down.position.y)) {
                                    consumedByChild = true
                                    break
                                }
                                velocityTracker.addPosition(tracked.uptimeMillis, tracked.position)
                                pulledDown += -pan.y
                                if (pan != Offset.Zero) {
                                    val maxXnow = bounds.maxOffsetX(grid.zoom)

                                    if (maxXnow > 0f) {
                                        grid.offsetX = rubberOffset(
                                            grid.offsetX - pan.x, bounds.availW, bounds.contentWidth(grid.zoom),
                                        )
                                    } else if (!verticalOnly) {
                                        weekPull += pan.x
                                    }
                                    // 顶部钉死：滑到第一节课后不再产生越界位移，
                                    // 竖直方向的余量才能让给横向翻周
                                    grid.offsetY = rubberOffsetPinnedTop(
                                        grid.offsetY - pan.y, bounds.availH, bounds.contentHeight(grid.zoom),
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            // 双击不再缩放：空白格的「第一次点亮加号、第二次进编辑」本身就是两次单击，
                            // 再叠一层双击手势会把第二次点击吃掉，加号永远进不了编辑。
                            if (consumedByChild) return@awaitEachGesture
                            // 原地长按：只给一次触觉反馈，不派发任何点击
                            if (!started && !pinched &&
                                lastUptime - down.uptimeMillis >= longPressTimeout
                            ) {
                                longPressed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            val velocity = velocityTracker.calculateVelocity()
                            val bounds = layoutNow
                            val maxXnow = bounds.maxOffsetX(grid.zoom)
                            val atLeftEdge = grid.offsetX <= 0.5f
                            val atRightEdge = grid.offsetX >= maxXnow - 0.5f
                            val turnBy = when {
                                pinched || editNow -> 0
                                maxXnow > 0f && !(atLeftEdge || atRightEdge) -> 0
                                abs(weekPull) > bounds.availW * .22f -> if (weekPull > 0) -1 else 1
                                else -> 0
                            }
                            if (turnBy != 0) {
                                grid.offsetX = 0f
                                onWeekNow((weekNow + turnBy).coerceIn(1, settingsNow.weekCount))
                                return@awaitEachGesture
                            }
                            if (!pinched && settingsNow.autoHideBars && abs(pulledDown) > bounds.availH * .12f) {
                                val next = pulledDown > 0
                                if (next != immersive) { immersive = next; onImmersiveNow(next) }
                            }
                            if (started || pinched) {
                                settle(Offset(velocity.x, velocity.y), pinched, lastCentroid)
                            } else if (!longPressed) {
                                // 单击：只负责「点亮加号 → 再点进编辑」，不做别的
                                handleTap(down.position)
                            }
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = slide.value * size.width
                        alpha = (1f - abs(slide.value) * 2.2f).coerceIn(0f, 1f)
                    },
                    verticalAlignment = Alignment.Top,
                ) {

                    Column(Modifier.width(gutterWidth)) {
                        Spacer(Modifier.height(dayHeaderHeight))

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { availH.toDp() })
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .clipToBounds()
                                .graphicsLayer { translationY = -grid.offsetY.roundToInt().toFloat() },
                        ) {
                            val groups = remember(s.periods) { PeriodGroups.indexMap(s.periods) }
                            s.periods.forEachIndexed { rowIndex, pr ->
                                val group = groups[rowIndex]
                                val isBlockStart = group == null || group.startIndex == rowIndex
                                val isBlockEnd = group == null || group.endIndex == rowIndex
                                val marked = editMode && rowFrom != 0 && (rowTo == rowIndex || rowFrom - 1 == rowIndex)
                                Box(
                                    Modifier.height(with(density) { cellHpx.toDp() }).fillMaxWidth()
                                        .then(
                                            if (marked) Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                                                gridCellShape,
                                            ) else Modifier
                                        )
                                        .then(
                                            if (!editMode) Modifier else Modifier.pointerInput(rowIndex, cellHpx) {
                                                detectDragGestures(
                                                    onDragStart = { rowFrom = rowIndex + 1; rowTo = rowIndex; rowDrag = 0f },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        rowDrag += delta.y
                                                        rowTo = (rowIndex + (rowDrag / cellHpx).roundToInt())
                                                            .coerceIn(0, periodCount - 1)
                                                    },
                                                    onDragEnd = {
                                                        val from = rowFrom - 1
                                                        if (from != rowTo) onMoveRow(s.periods[from].period, s.periods[rowTo].period)
                                                        rowFrom = 0
                                                    },
                                                    onDragCancel = { rowFrom = 0 },
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    val gutterDp = with(density) { cellHpx.toDp().value }
                                    val periodSize = (gutterDp / 5.6f).coerceIn(9f, 12f).sp
                                    val clockSize = (gutterDp / 9.2f).coerceIn(7f, 9f).sp
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                        Text(
                                            pr.period.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontSize = periodSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = if (isBlockStart) 1f else .75f
                                            ),
                                        )
                                        if (pr.endMinutes > pr.startMinutes) {
                                            if (isBlockStart) {
                                                Text(
                                                    clock(pr.startMinutes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = clockSize,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            if (isBlockEnd) {
                                                Text(
                                                    clock(pr.endMinutes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = clockSize,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {

                        val cellDp = with(density) { cellWpx.toDp() }
                        val contentW = with(density) { (cellWpx * dayCount).toDp() }
                        Box(Modifier.fillMaxWidth().height(dayHeaderHeight).clipToBounds()) {
                            projection.days.forEachIndexed { dayIndex, day ->
                                val date = WeekDates.on(s.start, week, day)
                                val isToday = date == today
                                val marked = editMode && (columnTo == dayIndex || (columnFrom != 0 && columnFrom == dayIndex))
                                Box(
                                    Modifier
                                        .offset { IntOffset((dayIndex * cellWpx - grid.offsetX).roundToInt(), 0) }
                                        .requiredWidth(cellDp).height(dayHeaderHeight)
                                        .then(
                                            if (marked) Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                                                gridCellShape,
                                            ) else Modifier
                                        )
                                        .then(
                                            if (!editMode) Modifier else Modifier.pointerInput(dayIndex, cellWpx) {
                                                detectDragGestures(
                                                    onDragStart = { columnFrom = dayIndex + 1; columnTo = dayIndex; colDrag = 0f },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        colDrag += delta.x
                                                        columnTo = (dayIndex + (colDrag / cellWpx).roundToInt())
                                                            .coerceIn(0, dayCount - 1)
                                                    },
                                                    onDragEnd = {
                                                        val from = columnFrom - 1
                                                        if (from != columnTo) onMoveDay(projection.days[from], projection.days[columnTo])
                                                        columnFrom = 0
                                                    },
                                                    onDragCancel = { columnFrom = 0 },
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        Modifier
                                            .padding(horizontal = 2.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.small))
                                            .then(
                                                if (isToday) Modifier.background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                                                ) else Modifier,
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            dayNames[day - 1].removePrefix("星期"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else palette.ink,
                                            maxLines = 1,
                                        )
                                        Text(
                                            WeekDates.dayLabel(s.start, week, day),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize().clipToBounds()) {
                            if (palette.lineStyle == GRID_LINE_DASHED) {
                                DashedGridLines(
                                    columns = dayCount,
                                    rows = periodCount,
                                    cellW = cellWpx.toFloat(),
                                    cellH = cellHpx.toFloat(),
                                    color = palette.line,
                                    modifier = Modifier
                                        .offset { IntOffset((-grid.offsetX).roundToInt(), (-grid.offsetY).roundToInt()) }
                                        .requiredWidth(with(density) { (cellWpx * dayCount).toDp() })
                                        .requiredHeight(with(density) { (cellHpx * periodCount).toDp() }),
                                )
                            }

                            projection.days.forEachIndexed { dayIndex, day ->
                                Box(
                                    Modifier
                                        .offset {
                                            IntOffset(
                                                (dayIndex * cellWpx - grid.offsetX).roundToInt(),
                                                0,
                                            )
                                        }
                                        .requiredWidth(cellDp)
                                        .height(with(density) { availH.toDp() })
                                        .wrapContentHeight(Alignment.Top, unbounded = true)
                                        .clipToBounds()
                                        .graphicsLayer { translationY = -grid.offsetY.roundToInt().toFloat() },
                                ) {
                                        Column {
                                            repeat(periodCount) {
                                                Box(
                                                    Modifier
                                                        .height(with(density) { cellHpx.toDp() })
                                                        .fillMaxWidth()
                                                        .padding(gridCellPadding(palette.lineStyle))
                                                        .background(
                                                            palette.cell,
                                                            androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs),
                                                        ),
                                                )
                                            }
                                        }
                                        if (palette.lineStyle == GRID_LINE_DASHED) {
                                            DashedGridLines(
                                                columns = 0,
                                                rows = periodCount,
                                                cellW = cellWpx.toFloat(),
                                                cellH = cellHpx.toFloat(),
                                                color = palette.line,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        projection.placed[day].orEmpty().forEach { pc ->
                                            val course = pc.course
                                            val laneW = with(density) { (cellWpx / pc.laneCount).toDp() }
                                            val style = styles[course.id]
                                                ?: courseVisual(course, data.colors, s.defaultColor, canvasColors.isDark, s.courseCellColor)
                                            val located = locatedCourseId == course.id
                                            val locatePulse = remember { Animatable(0f) }
                                            LaunchedEffect(located) {
                                                // 只在命中时驱动动画：非命中卡片不写状态，避免多余重组
                                                if (located) {
                                                    locatePulse.animateTo(
                                                        1f,
                                                        tween(KelaileMotion.quick, easing = KelaileMotion.emphasizedEasing),
                                                    )
                                                    locatePulse.animateTo(
                                                        0.55f,
                                                        tween(KelaileMotion.standard, easing = KelaileMotion.standardEasing),
                                                    )
                                                }
                                            }
                                            val card = Modifier
                                                .offset(x = laneW * pc.lane, y = with(density) { (cellHpx * pc.startIndex).toDp() })
                                                .width(laneW)
                                                .height(with(density) { (cellHpx * (pc.endIndex - pc.startIndex + 1)).toDp() })
                                                // 与空白格用同一份内缩：卡片外缘正好落在格子外缘上，
                                                // 不会露出一圈比格子底色更深的缝
                                                .padding(gridCellPadding(palette.lineStyle))
                                                .then(
                                                    if (located) Modifier
                                                        .graphicsLayer {
                                                            val boost = 1f + 0.03f * locatePulse.value
                                                            scaleX = boost
                                                            scaleY = boost
                                                        }
                                                        .border(
                                                            (1.5f + 1.5f * locatePulse.value).dp,
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.5f + 0.5f * locatePulse.value,
                                                            ),
                                                            gridCellShape,
                                                        )
                                                    else Modifier
                                                )
                                                .then(
                                                    if (!editMode) Modifier else Modifier.pointerInput(course.id, cellWpx) {
                                                        detectDragGestures(
                                                            onDragStart = { local ->
                                                                dragging = pc
                                                                dragAt = Offset(
                                                                    gutterPx + dayIndex * cellWpx - grid.offsetX + local.x,
                                                                    headerPx + pc.startIndex * cellHpx - grid.offsetY + local.y,
                                                                )
                                                                dragSpot = day to pc.startIndex
                                                            },
                                                            onDrag = { change, delta ->
                                                                change.consume()
                                                                dragAt += delta
                                                                val bounds = layoutNow
                                                                val col = bounds.columnAt(dragAt.x, grid.offsetX, grid.zoom)
                                                                val row = bounds.rowAt(dragAt.y, grid.offsetY, grid.zoom)
                                                                val targetDay = projectionNow.days.getOrNull(col) ?: day
                                                                dragSpot = targetDay to row
                                                            },
                                                            onDragEnd = {
                                                                val spot = dragSpot
                                                                val period = spot?.let { s.periods.getOrNull(it.second)?.period }
                                                                if (spot != null && period != null &&
                                                                    (spot.first != course.weekday || period != course.startPeriod)
                                                                ) onMoveCourse(course.id, spot.first, period)
                                                                dragging = null; dragSpot = null
                                                            },
                                                            onDragCancel = { dragging = null; dragSpot = null },
                                                        )
                                                    }
                                                )

                                            val cardDp = with(density) { cellWpx.toDp().value }
                                            // 字号量化到 0.5sp：捏合时文本只在跨档才重排，不再逐帧抖动
                                            val cardSize = quantizeSp((cardDp / 7.2f).coerceIn(7f, 12f)).sp
                                            val metaSize = quantizeSp(cardSize.value * .82f).sp
                                            val textAlign = when (s.cellTextAlign) {
                                                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                                                "end" -> androidx.compose.ui.text.style.TextAlign.End
                                                else -> androidx.compose.ui.text.style.TextAlign.Start
                                            }
                                            val alignH = when (s.cellTextAlign) {
                                                "center" -> Alignment.CenterHorizontally
                                                "end" -> Alignment.End
                                                else -> Alignment.Start
                                            }

                                            val alignV = when (s.cellVerticalAlign) {
                                                CELL_VERTICAL_CENTER -> Alignment.CenterVertically
                                                CELL_VERTICAL_BOTTOM -> Alignment.Bottom
                                                else -> Alignment.Top
                                            }
                                            if (pc.startIndex >= 0 && pc.endIndex >= pc.startIndex) {
                                                val meta = buildString {
                                                    append(course.startPeriod).append("—").append(course.endPeriod).append("节")
                                                    if (course.location.isNotBlank()) {
                                                        append('\n')
                                                        if (s.showAtSign) append('@')
                                                        append(course.location)
                                                    }
                                                    if (s.showTeacher && course.teacher.isNotBlank()) {
                                                        append('\n').append(course.teacher)
                                                    }
                                                }
                                                GlassCourseCard(
                                                    onClick = { onCourse(course) },
                                                    tint = style.background,
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs),
                                                    modifier = card,
                                                    contentPadding = 5.dp,
                                                    contentAlignment = alignH,
                                                    contentVerticalArrangement = alignV,
                                                ) {
                                                    Text(
                                                        course.name,
                                                        color = style.ink,
                                                        fontSize = cardSize,
                                                        lineHeight = cardSize * 1.06f,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 3,
                                                        textAlign = textAlign,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        meta,
                                                        color = style.ink.copy(alpha = .85f),
                                                        fontSize = metaSize,
                                                        lineHeight = metaSize * 1.15f,
                                                        maxLines = 4,
                                                        textAlign = textAlign,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                dragging?.let { ghost ->
                    val ghostStyle = styles[ghost.course.id]
                        ?: courseVisual(ghost.course, data.colors, s.defaultColor, canvasColors.isDark, s.courseCellColor)
                    Box(
                        Modifier
                            .offset { IntOffset(dragAt.x.roundToInt(), dragAt.y.roundToInt()) }
                            .width(with(density) { cellWpx.toDp() })
                            .height(with(density) { (cellHpx * (ghost.endIndex - ghost.startIndex + 1)).toDp() })
                            .padding(gridCellPadding(palette.lineStyle))
                            .then(
                                Modifier
                                    .graphicsLayer { alpha = .92f }
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs))
                                    .background(ghostStyle.background)
                                    .border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = .55f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs),
                                    ),
                            ),
                    )
                }
                // 空白格第一次点击：在对应位置点亮一个加号，第二次点击才进编辑
                tappedCell?.let { cell ->
                    val cellIndex = projection.days.indexOf(cell.weekday)
                    if (cellIndex >= 0) {
                        val accent = MaterialTheme.colorScheme.primary
                        val cellShape = androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs)
                        Box(
                            Modifier
                                .offset { IntOffset(
                                    (gutterPx + cellIndex * cellWpx - grid.offsetX).roundToInt(),
                                    (headerPx + cell.rowIndex * cellHpx - grid.offsetY).roundToInt(),
                                ) }
                                .width(with(density) { cellWpx.toDp() })
                                .height(with(density) { cellHpx.toDp() })
                                .padding(gridCellPadding(palette.lineStyle))
                                .background(accent.copy(alpha = .14f), cellShape)
                                .border(1.5.dp, accent.copy(alpha = .72f), cellShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                                val half = size.minDimension / 2f
                                val pen = 2.dp.toPx()
                                drawLine(
                                    accent, Offset(center.x - half, center.y), Offset(center.x + half, center.y),
                                    strokeWidth = pen, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                )
                                drawLine(
                                    accent, Offset(center.x, center.y - half), Offset(center.x, center.y + half),
                                    strokeWidth = pen, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                )
                            }
                        }
                    }
                }
                dragSpot?.let { spot ->
                    val spotIndex = projection.days.indexOf(spot.first)
                    if (spotIndex >= 0) Box(
                        Modifier
                            .offset { IntOffset(
                                (gutterPx + spotIndex * cellWpx - grid.offsetX).roundToInt(),
                                (headerPx + spot.second * cellHpx - grid.offsetY).roundToInt(),
                            ) }
                            .width(with(density) { cellWpx.toDp() })
                            .height(with(density) { cellHpx.toDp() })
                            .padding(gridCellPadding(palette.lineStyle))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                                androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs),
                            )
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                androidx.compose.foundation.shape.RoundedCornerShape(KelaileShapes.xs),
                            ),
                    )
                }
            }
            }
            }
        }

    LaunchedEffect(locatedCourseId, week) {
        val id = locatedCourseId ?: return@LaunchedEffect
        var dayIndex = -1
        val found = withTimeoutOrNull(1500L) {
            var attempt = 0
            while (attempt < 25) {
                attempt += 1
                if (boxW > 0 && viewportH > 0) {
                    dayIndex = projectionNow.days.indexOfFirst { day ->
                        projectionNow.placed[day].orEmpty().any { it.course.id == id }
                    }
                    if (dayIndex >= 0) return@withTimeoutOrNull true
                }
                delay(60)
            }
            false
        }
        if (found != true) {
            onLocateHandled()
            return@LaunchedEffect
        }
        gestureJob.launch(scope) {
            try {
                val bounds = layoutNow
                if (grid.zoom < 1.6f) {
                    animateZoomTo(nearestZoomRung(1.8f), bounds.availW / 2f, bounds.availH / 2f)
                }
                val live = layoutNow
                val placed = projectionNow.placed[projectionNow.days[dayIndex]].orEmpty().first { it.course.id == id }
                val cellWnow = live.cellWidth(grid.zoom)
                val cellHnow = live.cellHeight(grid.zoom)
                val targetX = (dayIndex + .5f) * cellWnow - live.availW / 2f
                val targetY = (placed.startIndex + (placed.endIndex - placed.startIndex + 1) / 2f) * cellHnow -
                    live.availH / 2f
                animate(
                    grid.offsetX, clampOffset(targetX, live.availW, live.contentWidth(grid.zoom)),
                    animationSpec = tween(snapMillis, easing = KelaileMotion.emphasizedEasing),
                ) { v, _ -> grid.offsetX = v }
                animate(
                    grid.offsetY, clampOffset(targetY, live.availH, live.contentHeight(grid.zoom)),
                    animationSpec = tween(snapMillis, easing = KelaileMotion.emphasizedEasing),
                ) { v, _ -> grid.offsetY = v }
                delay(1600)
            } finally {
                onLocateHandled()
            }
        }
    }
    WeekPickerSheet(
        open = pickerOpen,
        settings = s,
        viewWeek = week,
        onPick = { onViewWeek(it); pickerOpen = false },
        onLocate = { onViewWeek(s.currentWeek); pickerOpen = false },
        onDismiss = { pickerOpen = false },
    )
}

@Composable
private fun WeekPickerSheet(
    open: Boolean,
    settings: AppSettings,
    viewWeek: Int,
    onPick: (Int) -> Unit,
    onLocate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewWeek - 2).coerceAtLeast(0),
    )
    val canvas = LocalKelaileCanvas.current
    GlassSheet(visible = open, onDismiss = onDismiss, title = "选择周次") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.gradeLabel.ifBlank { "本学期" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "当前第 " + settings.currentWeek + " 周 · 共 " + settings.weekCount + " 周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassPillButton("回到当前周", onClick = onLocate, compact = true)
            }
            LazyRow(
                state = state,
                contentPadding = PaddingValues(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((1..settings.weekCount).toList()) { w ->
                    val selected = w == viewWeek
                    Column(
                        Modifier
                            .clip(KelaileShapes.rounded(KelaileShapes.medium))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else canvas.fill,
                            )
                            .clickable { onPick(w) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "第 $w 周",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else if (w == settings.currentWeek) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            WeekDates.rangeLabel(settings.start, w),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
