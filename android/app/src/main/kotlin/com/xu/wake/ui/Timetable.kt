package com.xu.wake.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xu.wake.data.*
import com.xu.wake.ui.glass.GlassHost
import com.xu.wake.ui.glass.glassSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

private val gridCellShape = XwakeShapes.rounded(XwakeShapes.small)

private val gutterWidth = 54.dp

private const val ROW_ASPECT = 0.92f

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 2.6f

private data class CourseStyle(val background: Color, val ink: Color)

internal data class PlacedCourse(
    val course: CourseSession,
    val lane: Int,
    val laneCount: Int,
    val startIndex: Int,
    val endIndex: Int,
)

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

private fun courseStyle(course: CourseSession, colors: Map<String, Long>, defaultColor: Long, surface: Color): CourseStyle {
    val background = Color(colors[course.id] ?: defaultColor).compositeOver(surface)
    return CourseStyle(background, if (background.luminance() > .4f) Color(0xff17231d) else Color.White)
}

internal fun snapZoom(zoom: Float, dayCount: Int): Float {
    if (dayCount <= 0) return MIN_ZOOM
    val visible = (dayCount / zoom).roundToInt().coerceIn(1, dayCount)
    return (dayCount.toFloat() / visible).coerceIn(MIN_ZOOM, MAX_ZOOM)
}

internal fun snapOffset(offset: Float, cell: Float, viewport: Float, content: Float): Float {
    val max = (content - viewport).coerceAtLeast(0f)
    if (max <= 0f) return 0f
    return (offset / cell).roundToInt().toFloat().times(cell).coerceIn(0f, max)
}

private val dayHeaderHeight = 38.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Timetable(
    data: AppData,
    scroll: ScrollState,
    viewWeek: Int,
    onViewWeek: (Int) -> Unit,
    onCourse: (CourseSession) -> Unit,
    onAdd: () -> Unit,
    locatedCourseId: String? = null,
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
) {
    val s = data.settings
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snapMillis = xwakeMillis(240)
    val today = LocalDate.now()

    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
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

    val gutterPx = with(density) { gutterWidth.toPx() }
    val headerPx = with(density) { dayHeaderHeight.toPx() }
    val week = viewWeek.coerceIn(1, s.weekCount)

    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    var slideFrom by remember { mutableIntStateOf(week) }

    val projection = remember(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods) {
        projectWeek(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods)
    }
    val surface = MaterialTheme.colorScheme.surface
    val styles = remember(projection, surface, data.colors, s.defaultColor) {
        projection.placed.values.asSequence().flatten()
            .associate { it.course.id to courseStyle(it.course, data.colors, s.defaultColor, surface) }
    }

    val dayCount = projection.days.size.coerceAtLeast(1)
    val periodCount = s.periods.size.coerceAtLeast(1)
    val availW = (boxW - gutterPx).coerceAtLeast(1f)
    val availH = (viewportH - headerPx).coerceAtLeast(1f)
    val cellW = availW / dayCount * zoom
    val cellH = cellW * ROW_ASPECT
    val maxX = (cellW * dayCount - availW).coerceAtLeast(0f)
    val maxY = (cellH * periodCount - availH).coerceAtLeast(0f)

    fun applySnap() {
        val targetZoom = snapZoom(zoom, dayCount)
        val cw = availW / dayCount * targetZoom
        val ch = cw * ROW_ASPECT
        val tx = snapOffset(offsetX, cw, availW, cw * dayCount)
        val ty = snapOffset(offsetY, ch, availH, ch * periodCount)
        val z0 = zoom
        val x0 = offsetX.coerceIn(0f, (cellW * dayCount - availW).coerceAtLeast(0f))
        val y0 = offsetY.coerceIn(0f, (cellH * periodCount - availH).coerceAtLeast(0f))
        scope.launch {
            androidx.compose.animation.core.animate(
                0f, 1f,
                animationSpec = tween(durationMillis = snapMillis, easing = XwakeMotion.emphasizedEasing),
            ) { t, _ ->
                zoom = z0 + (targetZoom - z0) * t
                offsetX = x0 + (tx - x0) * t
                offsetY = y0 + (ty - y0) * t
            }
            zoom = targetZoom; offsetX = tx; offsetY = ty
        }
    }

    LaunchedEffect(week) {
        val direction = if (week >= slideFrom) 1f else -1f
        slideFrom = week
        slide.snapTo(direction * .30f)
        slide.animateTo(0f, tween(durationMillis = snapMillis, easing = XwakeMotion.emphasizedEasing))
    }

    BackHandler(enabled = editMode) { onEditMode(false) }
    LaunchedEffect(editMode) { if (!editMode) { dragging = null; dragSpot = null; columnFrom = 0; rowFrom = 0 } }

    fun spotAt(x: Float, y: Float): Pair<Int, Int> {
        val col = ((x - gutterPx + offsetX) / cellW).toInt().coerceIn(0, dayCount - 1)
        val row = ((y - headerPx + offsetY) / cellH).toInt().coerceIn(0, periodCount - 1)
        return projection.days[col] to row
    }

    val dragThreshold = with(density) { 52.dp.toPx() }

    Column(Modifier.fillMaxSize()) {
        XwakeTopBar(
            title = "",
            collapseFraction = if (immersive) 1f else (offsetY / (availH * .6f)).coerceIn(0f, 1f),
            modifier = Modifier.graphicsLayer { translationY = if (immersive) -size.height else 0f },
            titleContent = {
                if (editMode) {
                    Column {
                        Text("排课编辑", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            "拖课程改时间；拖星期表头整列移动，拖左侧节次整行移动",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {

                    Column(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { pickerOpen = true },
                            onLongClick = { pickerOpen = true },
                        ),
                    ) {
                        Text("第 " + week + " 周", style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
                    TextButton(onClick = { onEditMode(false) }) { Text("完成") }
                } else {

                    if (week != s.currentWeek) {
                        AssistChip(
                            onClick = { onViewWeek(s.currentWeek) },
                            label = { Text("回到第 " + s.currentWeek + " 周") },
                        )
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleMedium)
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(XwakeSpacing.lg)) {
                    Text("每一周，都从容一点", style = MaterialTheme.typography.headlineSmall)
                    Text("还没有课程，新增或导入你的课表")
                    Button(onClick = onAdd) { Text("新增课程") }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { boxW = it.width; viewportH = it.height }

                    .pointerInput(editMode) {
                        detectTapGestures(onLongPress = { if (!editMode) onEditMode(true) })
                    }
                    .pointerInput(dayCount, periodCount, availW, availH, week) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var pinching = false
                            var edgePull = 0f
                            var pulledDown = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (event.changes.count { it.pressed } > 1) pinching = true

                                if (pinching && zoomChange != 1f) {
                                    val anchorX = (event.calculateCentroid().x - gutterPx).coerceAtLeast(0f)
                                    val atContent = (anchorX + offsetX) / cellW
                                    val next = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    val cw = availW / dayCount * next
                                    zoom = next
                                    offsetX = (atContent * cw - anchorX)
                                        .coerceIn(0f, (cw * dayCount - availW).coerceAtLeast(0f))
                                } else if (panChange != Offset.Zero && dragging == null) {
                                    val nx = offsetX - panChange.x
                                    val ny = offsetY - panChange.y
                                    if (maxX <= 0f) edgePull += -panChange.x else {
                                        offsetX = nx.coerceIn(0f, maxX)
                                        edgePull = if (nx < 0f) nx else if (nx > maxX) nx - maxX else 0f
                                    }
                                    offsetY = ny.coerceIn(0f, maxY)
                                    pulledDown += -panChange.y
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            if (!editMode && abs(edgePull) > dragThreshold) {
                                offsetX = 0f
                                onViewWeek((week + if (edgePull < 0) 1 else -1).coerceIn(1, s.weekCount))
                            } else if (s.autoHideBars) {
                                val next = pulledDown > dragThreshold
                                if (next != immersive) { immersive = next; onImmersive(next) }
                            }
                            applySnap()
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = slide.value * size.width
                        alpha = (1f - abs(slide.value) * 2.2f).coerceIn(0f, 1f)
                    },
                ) {

                    Column(Modifier.width(gutterWidth)) {
                        Spacer(Modifier.height(dayHeaderHeight))

                        Column(Modifier.graphicsLayer { translationY = -offsetY }) {
                            s.periods.forEachIndexed { rowIndex, pr ->
                                val marked = editMode && rowFrom != 0 && (rowTo == rowIndex || rowFrom - 1 == rowIndex)
                                Box(
                                    Modifier.height(with(density) { cellH.toDp() }).fillMaxWidth()
                                        .then(
                                            if (marked) Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                                                gridCellShape,
                                            ) else Modifier
                                        )
                                        .then(
                                            if (!editMode) Modifier else Modifier.pointerInput(rowIndex, cellH) {
                                                detectDragGestures(
                                                    onDragStart = { rowFrom = rowIndex + 1; rowTo = rowIndex; rowDrag = 0f },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        rowDrag += delta.y
                                                        rowTo = (rowIndex + (rowDrag / cellH).roundToInt())
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
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            pr.period.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (pr.endMinutes > pr.startMinutes) {
                                            Text(
                                                clock(pr.startMinutes),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )

                                            val nextStart = s.periods.getOrNull(s.periods.indexOf(pr) + 1)?.startMinutes
                                            if (pr.endMinutes != nextStart) {
                                                Text(
                                                    clock(pr.endMinutes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
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

                        Row(Modifier.graphicsLayer { translationX = -offsetX }) {
                            projection.days.forEachIndexed { dayIndex, day ->
                                val date = WeekDates.on(s.start, week, day)
                                val isToday = date == today
                                val marked = editMode && (columnTo == dayIndex || (columnFrom != 0 && columnFrom == dayIndex))
                                Box(
                                    Modifier.width(with(density) { cellW.toDp() }).height(dayHeaderHeight)
                                        .then(
                                            if (marked) Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                                                gridCellShape,
                                            ) else Modifier
                                        )
                                        .then(
                                            if (!editMode) Modifier else Modifier.pointerInput(dayIndex, cellW) {
                                                detectDragGestures(
                                                    onDragStart = { columnFrom = dayIndex + 1; columnTo = dayIndex; colDrag = 0f },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        colDrag += delta.x
                                                        columnTo = (dayIndex + (colDrag / cellW).roundToInt())
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
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            dayNames[day - 1].removePrefix("星期"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
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
                            Row(Modifier.graphicsLayer { translationX = -offsetX }) {
                                projection.days.forEachIndexed { dayIndex, day ->
                                    Box(
                                        Modifier
                                            .width(with(density) { cellW.toDp() })
                                            .fillMaxHeight()
                                            .graphicsLayer { translationY = -offsetY },
                                    ) {
                                        Column {
                                            repeat(periodCount) {
                                                Box(
                                                    Modifier
                                                        .height(with(density) { cellH.toDp() })
                                                        .fillMaxWidth()
                                                        .padding(1.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceContainerLow, gridCellShape),
                                                )
                                            }
                                        }
                                        projection.placed[day].orEmpty().forEach { pc ->
                                            val course = pc.course
                                            val laneW = with(density) { (cellW / pc.laneCount).toDp() }
                                            val style = styles[course.id]
                                                ?: courseStyle(course, data.colors, s.defaultColor, surface)
                                            val located = locatedCourseId == course.id
                                            val card = Modifier
                                                .offset(x = laneW * pc.lane, y = with(density) { (cellH * pc.startIndex).toDp() })
                                                .width(laneW)
                                                .height(with(density) { (cellH * (pc.endIndex - pc.startIndex + 1)).toDp() })
                                                .padding(1.dp)
                                                .then(
                                                    if (located) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, gridCellShape)
                                                    else Modifier
                                                )
                                                .then(
                                                    if (!editMode) Modifier else Modifier.pointerInput(course.id, cellW) {
                                                        detectDragGestures(
                                                            onDragStart = { local ->
                                                                dragging = pc
                                                                dragAt = Offset(
                                                                    gutterPx + dayIndex * cellW - offsetX + local.x,
                                                                    headerPx + pc.startIndex * cellH - offsetY + local.y,
                                                                )
                                                                dragSpot = day to pc.startIndex
                                                            },
                                                            onDrag = { change, delta ->
                                                                change.consume()
                                                                dragAt += delta
                                                                dragSpot = spotAt(dragAt.x, dragAt.y)
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

                                            val cardDp = with(density) { cellW.toDp().value }
                                            val cardSize = (cardDp / 4.2f).coerceIn(8f, 15f).sp
                                            if (pc.startIndex >= 0 && pc.endIndex >= pc.startIndex) {
                                                PressCard({ onCourse(course) }, card, style.background) {
                                                    Text(
                                                        course.name,
                                                        color = style.ink,
                                                        fontSize = cardSize,
                                                        lineHeight = cardSize * 1.15f,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 3,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        course.location + (if (course.location.isBlank()) "" else "\n") +
                                                            course.startPeriod + "—" + course.endPeriod + "节",
                                                        color = style.ink.copy(alpha = .85f),
                                                        fontSize = cardSize * .86f,
                                                        lineHeight = cardSize,
                                                        maxLines = 3,
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
                        ?: courseStyle(ghost.course, data.colors, s.defaultColor, surface)
                    Box(
                        Modifier
                            .offset { IntOffset(dragAt.x.roundToInt(), dragAt.y.roundToInt()) }
                            .width(with(density) { cellW.toDp() })
                            .height(with(density) { (cellH * (ghost.endIndex - ghost.startIndex + 1)).toDp() })
                            .padding(1.dp)
                            .background(ghostStyle.background.copy(alpha = .92f), gridCellShape),
                    )
                }
                dragSpot?.let { spot ->
                    val spotIndex = projection.days.indexOf(spot.first)
                    if (spotIndex >= 0) Box(
                        Modifier
                            .offset { IntOffset(
                                (gutterPx + spotIndex * cellW - offsetX).roundToInt(),
                                (headerPx + spot.second * cellH - offsetY).roundToInt(),
                            ) }
                            .width(with(density) { cellW.toDp() })
                            .height(with(density) { cellH.toDp() })
                            .border(2.dp, MaterialTheme.colorScheme.primary, gridCellShape),
                    )
                }
            }
        }
    }
    if (pickerOpen) {
        WeekPickerSheet(
            settings = s,
            viewWeek = week,
            onPick = { onViewWeek(it); pickerOpen = false },
            onLocate = { onViewWeek(s.currentWeek); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerSheet(
    settings: AppSettings,
    viewWeek: Int,
    onPick: (Int) -> Unit,
    onLocate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewWeek - 2).coerceAtLeast(0),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("选择周次", style = MaterialTheme.typography.titleMedium)
                Text(
                    "当前第 " + settings.currentWeek + " 周 · 共 " + settings.weekCount + " 周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLocate) { Text("回到当前周") }
        }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..settings.weekCount).toList()) { w ->
                FilterChip(
                    selected = w == viewWeek,
                    onClick = { onPick(w) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "第 " + w + " 周",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (w == settings.currentWeek) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(WeekDates.rangeLabel(settings.start, w), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
