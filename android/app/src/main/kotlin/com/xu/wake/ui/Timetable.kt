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

/** Period cell shape. Hoisted to file scope: it used to be rebuilt for every cell of every day. */
private val gridCellShape = XwakeShapes.rounded(XwakeShapes.small)

/** 左侧节次栏宽度。 */
private val gutterWidth = 54.dp

/** 单元格宽高比的倒数：行高 = 列宽 * 该系数，缩放时保持比例。 */
private const val ROW_ASPECT = 0.92f

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3.4f

/** A course already resolved against the theme surface. Depends on composition-local colour, so it
 *  is cached per composition rather than folded into the theme-independent [WeekProjection]. */
private data class CourseStyle(val background: Color, val ink: Color)

/** One course, resolved against a single week's grid: lane, lane count, and period row span. */
internal data class PlacedCourse(
    val course: CourseSession,
    val lane: Int,
    val laneCount: Int,
    val startIndex: Int,
    val endIndex: Int,
)

/** Immutable projection of one week: the rotated day order plus per-weekday placed courses. */
internal data class WeekProjection(
    val days: List<Int>,
    val placed: Map<Int, List<PlacedCourse>>,
)

/**
 * Pure week -> day -> course projection for the timetable grid.
 *
 * Every parameter here is a declared dependency: the composable caches the result with exactly
 * these values as [remember] keys, so a change to any of them must invalidate the cache, and the
 * signature is the list that proves nothing else is read.
 *
 * Lane semantics are kept verbatim from the inline implementation this replaced: a course's lane is
 * its index among the *other* weekday courses whose period span overlaps it, and every course in
 * that overlap group shares the same width divisor. No compaction, no reordering.
 */
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

/** 分钟数 -> 「08:20」。 */
internal fun clock(minutes: Int): String =
    (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

private fun courseStyle(course: CourseSession, colors: Map<String, Long>, defaultColor: Long, surface: Color): CourseStyle {
    val background = Color(colors[course.id] ?: defaultColor).compositeOver(surface)
    return CourseStyle(background, if (background.luminance() > .4f) Color(0xff17231d) else Color.White)
}

/**
 * 把缩放吸附到「屏幕上正好放下整数列」的档位。
 *
 * 每个天数对应一个档位：放下 n 列时 zoom = 总天数 / n。因此手势结束后看到的永远是完整格子，
 * 不会停在半列上。
 */
internal fun snapZoom(zoom: Float, dayCount: Int): Float {
    if (dayCount <= 0) return MIN_ZOOM
    val visible = (dayCount / zoom).roundToInt().coerceIn(1, dayCount)
    return (dayCount.toFloat() / visible).coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** 吸附偏移到整格边界，并夹在内容范围内。 */
internal fun snapOffset(offset: Float, cell: Float, viewport: Float, content: Float): Float {
    val max = (content - viewport).coerceAtLeast(0f)
    if (max <= 0f) return 0f
    return (offset / cell).roundToInt().toFloat().times(cell).coerceIn(0f, max)
}

/** 顶栏下方星期表头的高度。 */
private val dayHeaderHeight = 44.dp

/**
 * 课表页。
 *
 * 视图周与配置周是两件事：[viewWeek] 只是本次浏览到哪一周，翻周不会写回设置；
 * 设置里的「当前周」是固定的锚点，定位按钮就是回到它。
 *
 * 手势集中在一处：单指拖动滚表，双指捏合缩放，拖到左右边界后继续拖即切换周。
 * 手势结束一律吸附——缩放吸到「屏幕上放下整数列」的档位，偏移吸到整格边界。
 *
 * 所有日期由 [WeekDates] 从开学日期推算，表头日期、周区间标签共用同一套换算。
 *
 * @param onImmersive 沉浸状态变化：用户向下滚动时为 true，调用方据此隐藏底栏与系统栏。
 */
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

    val gutterPx = with(density) { gutterWidth.toPx() }
    val headerPx = with(density) { dayHeaderHeight.toPx() }
    val week = viewWeek.coerceIn(1, s.weekCount)

    /* 切周过渡：新一周从行进方向滑入并淡入。幅度取屏宽的一小段而非整屏，配合吸附一起看更稳。 */
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

    /** 手势结束后的吸附：先定缩放档位，再把偏移吸到整格。 */
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

    /* 编辑模式下返回键先退出编辑，而不是直接离开课表页。 */
    BackHandler(enabled = editMode) { onEditMode(false) }

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
                    /* 点按或长按周次都打开选周面板；长按是主路径，点按让它可被发现。 */
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
                    /* 只有翻离当前周时才出现，避免平时占位。 */
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
                    /* 空白处长按进入排课编辑；有课的地方由卡片自己处理长按。 */
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
                                } else if (panChange != Offset.Zero) {
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
                            /* 拖过左右边界足够远 -> 切周并回到整列；否则吸附回整格。 */
                            /* 编辑模式下不定周：拖动是排课，不是翻周。 */
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
                    /* 节次栏固定在左侧，只随纵向滚动。 */
                    Column(Modifier.width(gutterWidth)) {
                        Spacer(Modifier.height(dayHeaderHeight))
                        /* 偏移只作用于标签本身：连同上面的占位一起偏移会让它与固定表头错位。 */
                        Column(Modifier.graphicsLayer { translationY = -offsetY }) {
                            s.periods.forEach { pr ->
                                Box(
                                    Modifier.height(with(density) { cellH.toDp() }).fillMaxWidth(),
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
                                            /* 下一节紧接着开始时，这个结束时间与它的开始时间相同，
                                               重复显示只会让左栏变吵。 */
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
                        /* 星期与日期：随横向滚动，不随纵向滚动。 */
                        Row(Modifier.graphicsLayer { translationX = -offsetX }) {
                            projection.days.forEach { day ->
                                val date = WeekDates.on(s.start, week, day)
                                val isToday = date == today
                                Box(
                                    Modifier.width(with(density) { cellW.toDp() }).height(dayHeaderHeight),
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
                                projection.days.forEach { day ->
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
                                            /* 字号随列宽走：7 天自适应后每列只有几十 dp，
                                               固定字号会把中文逐字换行。 */
                                            /* 中文字宽约等于字号本身，按「一列约放下 4 个字」反推，
                                               否则固定字号会把课名逐字换行。 */
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

/** 横向一排的选周面板；当前周与正在浏览的周各自有标记。 */
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
