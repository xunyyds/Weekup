package com.xu.wake.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import com.xu.wake.ui.glass.GlassHost
import com.xu.wake.ui.glass.glassSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

/** Period cell shape. Hoisted to file scope: it used to be rebuilt for every cell of every day. */
private val gridCellShape = XwakeShapes.rounded(XwakeShapes.medium)

/** Width of one day column; shared by the column and the lane maths so they cannot drift apart. */
private val dayColumnWidth = 108.dp

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

private fun courseStyle(course: CourseSession, colors: Map<String, Long>, defaultColor: Long, surface: Color): CourseStyle {
    val background = Color(colors[course.id] ?: defaultColor).compositeOver(surface)
    return CourseStyle(background, if (background.luminance() > .4f) Color(0xff17231d) else Color.White)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Timetable(data: AppData, scroll: ScrollState, onWeek: (Int)->Unit, onCourse:(CourseSession)->Unit, onAdd:()->Unit, locatedCourseId: String? = null) {
    val s = data.settings
    var drag by remember { mutableFloatStateOf(0f) }
    val headerBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(headerBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0,0,0,0),
        topBar = { LargeTopAppBar(title={Text("课表")}, actions={TextButton(onClick=onAdd){Text("＋ 新增")}}, scrollBehavior=headerBehavior,windowInsets=WindowInsets(0,0,0,0)) },
    ) { headerPadding ->
    Column(Modifier.fillMaxSize().padding(headerPadding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = s.currentWeek > 1, onClick = { onWeek(s.currentWeek-1) }) { Text("‹ 上一周") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(s.semester, style = MaterialTheme.typography.labelLarge); AnimatedContent(s.currentWeek, label = "Week number") { Text("第 $it 周", style = MaterialTheme.typography.titleLarge) }; if(s.school.isNotBlank()) Text(s.school, style = MaterialTheme.typography.labelSmall) }
            TextButton(enabled = s.currentWeek < s.weekCount, onClick = { onWeek(s.currentWeek+1) }) { Text("下一周 ›") }
        }
        if(data.courses.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(XwakeSpacing.lg)) { Text("每一周，都从容一点", style = MaterialTheme.typography.headlineSmall); Text("还没有课程，新增或导入你的课表"); Button(onClick = onAdd) { Text("新增课程") } }
        } else Box(Modifier.fillMaxSize().pointerInput(s.currentWeek, s.weekCount) { detectHorizontalDragGestures(onDragStart = { drag=0f }, onDragEnd = { if(kotlin.math.abs(drag)>100) onWeek((s.currentWeek + if(drag<0) 1 else -1).coerceIn(1,s.weekCount)) }, onHorizontalDrag = { _, amount -> drag += amount }) }) {
            MotionContent("week%02d".format(s.currentWeek)) { key ->
                val week = key.removePrefix("week").toIntOrNull() ?: s.currentWeek
                // The projection depends on these six inputs and nothing else; see [projectWeek].
                val projection = remember(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods) {
                    projectWeek(data.courses, week, s.firstDay, s.lastDay, s.weekStart, s.periods)
                }
                val surface = MaterialTheme.colorScheme.surface
                val styles = remember(projection, surface, data.colors, s.defaultColor) {
                    projection.placed.values.asSequence().flatten()
                        .associate { it.course.id to courseStyle(it.course, data.colors, s.defaultColor, surface) }
                }
                val rowHeight = (100*s.density).dp
                val gridColor = MaterialTheme.colorScheme.surfaceContainerLow
                Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(scroll).padding(XwakeSpacing.sm)) {
                    Column(Modifier.width(40.dp)) { Spacer(Modifier.height(48.dp)); s.periods.forEach { p -> Box(Modifier.height(rowHeight), contentAlignment = Alignment.TopCenter) { Text("${p.period}", modifier=Modifier.padding(top=12.dp)) } } }
                    projection.days.forEach { day -> Column(Modifier.width(dayColumnWidth)) {
                        Text(dayNames[day-1], Modifier.height(48.dp).padding(10.dp), color = if(day == LocalDate.now().dayOfWeek.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Box(Modifier.height(rowHeight * s.periods.size).fillMaxWidth()) {
                            Column { s.periods.forEach { _ -> Box(Modifier.height(rowHeight).fillMaxWidth().padding(3.dp).background(gridColor, gridCellShape)) } }
                            projection.placed[day].orEmpty().forEach { pc ->
                                val course = pc.course
                                val laneWidth = dayColumnWidth / pc.laneCount
                                val style = styles[course.id] ?: courseStyle(course, data.colors, s.defaultColor, surface)
                                val located = locatedCourseId == course.id
                                val cardModifier = Modifier.offset(x=laneWidth*pc.lane,y=rowHeight*pc.startIndex).width(laneWidth).height(rowHeight*(pc.endIndex-pc.startIndex+1)).padding(3.dp)
                                    .then(
                                        /* A search result lands here; the ring says "this is the one" without
                                           stealing the tap, so the card still opens its details. */
                                        if (located) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, XwakeShapes.rounded(XwakeShapes.medium))
                                        else Modifier
                                    )
                                if(pc.startIndex >= 0 && pc.endIndex >= pc.startIndex) PressCard({ onCourse(course) }, cardModifier, style.background) {
                                    Text(course.name, color=style.ink, fontWeight=FontWeight.Bold, maxLines=3, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("${course.location}\n${course.startPeriod}—${course.endPeriod}节", color=style.ink, style=MaterialTheme.typography.labelSmall, maxLines=3, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } }
                }
            }
        }
    }
    }
}
