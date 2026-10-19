package com.xu.wake.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.xu.wake.ui.glass.liquidGlass
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow

                                                                                              


private class StaggerCursor { var next = 0 }

private val LocalStaggerCursor = staticCompositionLocalOf<StaggerCursor?> { null }



private const val StaggerRiseDp = 10f

@Composable
private fun Modifier.staggerEnter(delayMillis: Int): Modifier {
    val motion = xwakeMotionEnabled()
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) appear.animateTo(1f, tween(delayMillis, easing = XwakeMotion.emphasizedEasing))
        else appear.snapTo(1f)
    }
    val rise = with(LocalDensity.current) { StaggerRiseDp.dp.toPx() }
    return this.graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * rise
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = XwakeShapes.rounded(XwakeShapes.card)
    val stagger = xwakeMotionEnabled()
    val cursor = remember(stagger) { if (stagger) StaggerCursor() else null }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(XwakeSpacing.sm),
    ) {
        if (title.isNotBlank()) {
            Text(
                title,
                modifier = Modifier
                    .padding(horizontal = XwakeSpacing.lg)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(XwakeElevation.soft(XwakeElevation.card, shape)),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {

            CompositionLocalProvider(LocalStaggerCursor provides cursor) {
                Column(content = content)
            }
        }
    }
}

                                                                                      
@Composable
fun SettingRow(title: String, subtitle: String = "", onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cursor = LocalStaggerCursor.current
    val slot = remember(cursor) { cursor?.next?.also { cursor.next = it + 1 } ?: 0 }

    val delay = xwakeStaggerDelay(slot)
    Row(
        modifier = Modifier.fillMaxWidth()
            .staggerEnter(delay)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = if (subtitle.isBlank()) 58.dp else 76.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(XwakeSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(XwakeSpacing.lg))
        val chevron = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(Modifier.size(8.dp, 14.dp)) {
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.15f)
                    lineTo(size.width * 0.8f, size.height * 0.5f)
                    lineTo(size.width * 0.2f, size.height * 0.85f)
                },
                chevron,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

                                                                                                   
@Composable
fun PressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: androidx.compose.ui.graphics.Shape = XwakeShapes.rounded(XwakeShapes.card),
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    contentPadding: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = xwakeMotionEnabled()
    val scale by animateFloatAsState(
        if (pressed && motion) 0.985f else 1f,
        animationSpec = if (motion) {
            tween(XwakeMotion.quick, easing = XwakeMotion.emphasizedEasing)
        } else snap(),
        label = "Card press",
    )
    val baseColor = color
    val tone by animateColorAsState(
        if (pressed && motion) lerp(baseColor, MaterialTheme.colorScheme.onSurface, 0.05f) else baseColor,
        animationSpec = if (motion) {
            tween(XwakeMotion.fast, easing = XwakeMotion.standardEasing)
        } else snap(),
        label = "Card tone",
    )
    val haptic = LocalHapticFeedback.current
    val contentColor = if (color == MaterialTheme.colorScheme.primary) {
        MaterialTheme.colorScheme.onPrimary
    } else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(tone)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = minHeight),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

   
                                                                       
                                                                                   
                                                              
   
@Composable
fun XwakeDock(
    style: String,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("课表", "设置")
    val selected = index.coerceIn(labels.indices)
    val solid = style == "default"
    val floating = !solid
    val liquid = style == "liquid"
    val colors = MaterialTheme.colorScheme
    val outerRadius = if (floating) XwakeShapes.dock else XwakeShapes.tiny
    val dockInset = 6.dp
    val shape = if (solid) androidx.compose.ui.graphics.RectangleShape else XwakeShapes.rounded(outerRadius)

    val indicatorShape = XwakeShapes.rounded(XwakeShapes.nested(outerRadius, dockInset))
    val motion = xwakeMotionEnabled()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(selected.toFloat()) }
    var dragStretch by remember { mutableStateOf(0f) }
    var measuredWidth by remember { mutableStateOf(0) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    
    val settledPosition by animateFloatAsState(
        if (dragging) dragPosition else selected.toFloat(),
        animationSpec = if (motion && !dragging) spring(dampingRatio = 0.85f, stiffness = 420f) else snap(),
        label = "Dock indicator position",
    )
    val position = (if (dragging) dragPosition else settledPosition).coerceIn(0f, 1f)
    val haptic = LocalHapticFeedback.current
    val base = if (liquid) Modifier
        .liquidGlass(
            outerRadius.value,
            colors.surface.copy(alpha = 0.26f),
            colors.surfaceContainerHigh,
        )
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), shape)
    else Modifier.background(if (solid) colors.surfaceContainerLow else colors.surfaceContainer)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier.widthIn(max = if (floating) 300.dp else androidx.compose.ui.unit.Dp.Infinity)
                .fillMaxWidth()
                .then(
                    if (floating) {
                        XwakeElevation.soft(XwakeElevation.dock, shape, 0.05f, 0.10f)
                    } else Modifier,
                )
                .clip(shape).then(base).padding(if (floating) dockInset else 0.dp)
                .onSizeChanged { measuredWidth = it.width }
                .pointerInput(rtl) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            val slotPx = measuredWidth / 2f
                            if (slotPx > 0f) {
                                val physical = (start.x / slotPx - 0.5f).coerceIn(0f, 1f)
                                dragPosition = if (rtl) 1f - physical else physical
                                dragging = true
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val slotPx = measuredWidth / 2f
                            if (slotPx > 0f && dragging) {
                                val logicalDelta = delta / slotPx * if (rtl) -1f else 1f
                                dragPosition = (dragPosition + logicalDelta).coerceIn(0f, 1f)
                                dragStretch = abs(logicalDelta).coerceIn(0f, 0.08f)
                            }
                        },
                        onDragEnd = {
                            val destination = dragPosition.roundToInt().coerceIn(0, 1)
                            dragging = false
                            dragStretch = 0f
                            
                            if (destination != currentSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(destination)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragStretch = 0f
                        },
                    )
                }
                .selectableGroup(),
        ) {
            val slot = maxWidth / labels.size
            
            if (floating) Box(Modifier.matchParentSize()) {
                val indicatorMaterial = if (liquid) Modifier.liquidGlass(
                    XwakeShapes.nested(outerRadius, dockInset).value,
                    colors.primaryContainer.copy(alpha = 0.40f),
                    colors.primaryContainer,
                ) else Modifier.background(colors.primaryContainer)
                Box(
                    Modifier.offset(x = slot * position).width(slot).fillMaxSize()
                        .padding(XwakeSpacing.hairline)
                        .graphicsLayer {
                            val stretch = if (motion && dragging) {
                                dragStretch * (1f - abs(2f * position - 1f))
                            } else 0f
                            scaleX = 1f + stretch
                            scaleY = 1f - stretch * 0.35f
                        }
                        .clip(indicatorShape).then(indicatorMaterial)
                        .then(
                            if (liquid) Modifier.border(
                                1.dp, colors.primary.copy(alpha = 0.22f), indicatorShape,
                            ) else Modifier,
                        ),
                )
            }


            if (solid) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp)
                    .background(colors.outlineVariant.copy(alpha = .6f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { item, title ->
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (pressed && motion) 0.94f else 1f,
                        animationSpec = if (motion) {
                            tween(XwakeMotion.fast, easing = XwakeMotion.emphasizedEasing)
                        } else snap(),
                        label = "Dock tab press",
                    )
                    val ink = when {
                        item != selected -> colors.onSurfaceVariant
                        solid -> colors.primary
                        else -> colors.onPrimaryContainer
                    }
                    Column(
                        Modifier.weight(1f).clip(XwakeShapes.rounded(XwakeShapes.card))
                            .selectable(
                                selected = item == selected,
                                interactionSource = source,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (item != selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(item)
                                    }
                                },
                            )
                            .heightIn(min = 60.dp)
                            .padding(horizontal = XwakeSpacing.xs, vertical = XwakeSpacing.sm)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(XwakeSpacing.xs),
                    ) {
                        DockGlyph(if (item == 0) 2 else 3, ink)
                        Text(
                            title,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = ink,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun DockGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val pen = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
        when (index) {
            0 -> {
                drawCircle(color, 8f * u, Offset(12f * u, 13f * u), style = pen)
                drawLine(color, Offset(12f * u, 8f * u), Offset(12f * u, 13f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(12f * u, 13f * u), Offset(16f * u, 15f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(4f * u, 3f * u), Offset(7f * u, 3f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(17f * u, 3f * u), Offset(20f * u, 3f * u), pen.width, StrokeCap.Round)
            }
            1 -> {
                drawRoundRect(color, Offset(3f * u, 4f * u), Size(18f * u, 13f * u), CornerRadius(2f * u), style = pen)
                drawLine(color, Offset(12f * u, 17f * u), Offset(12f * u, 21f * u), pen.width, StrokeCap.Round)
                drawLine(color, Offset(8f * u, 21f * u), Offset(16f * u, 21f * u), pen.width, StrokeCap.Round)
            }
            2 -> {
                drawRoundRect(color, Offset(5f * u, 3f * u), Size(14f * u, 18f * u), CornerRadius(2f * u), style = pen)
                for (y in listOf(8f, 12f, 16f)) drawLine(
                    color, Offset(9f * u, y * u), Offset(15f * u, y * u), pen.width, StrokeCap.Round,
                )
            }
            else -> {
                for ((y, x) in listOf(6f to 9f, 12f to 16f, 18f to 10f)) {
                    drawLine(color, Offset(3f * u, y * u), Offset(21f * u, y * u), pen.width, StrokeCap.Round)
                    drawCircle(color, 2.3f * u, Offset(x * u, y * u))
                }
            }
        }
    }
}




                     
                                             
                                                                           
                                                                             
                                                                          
                                                                             
                                                      
fun highlightMatches(
    text: String,
    terms: List<String>,
    highlight: SpanStyle,
): AnnotatedString {
    if (text.isEmpty() || terms.isEmpty()) return AnnotatedString(text)
    val ranges = mutableListOf<IntRange>()
    terms.forEach { term ->
        if (term.isEmpty()) return@forEach
        var from = 0
        while (from <= text.length - term.length) {
            val at = text.indexOf(term, from, ignoreCase = true)
            if (at < 0) break
            ranges.add(at until at + term.length)
            from = at + term.length
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    val merged = mutableListOf<IntRange>()
    ranges.sortedBy { it.first }.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else merged.add(range)
    }
    return buildAnnotatedString {
        append(text)
        merged.forEach { addStyle(highlight, it.first, it.last + 1) }
    }
}

                     
                                                      
                                                                         
                                                                                 
fun searchTerms(raw: String): List<String> =
    raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

                     
                                                                          
                                                                                     
                                                                                     
                                                                                     
                     
fun searchScore(entry: SearchText, terms: List<String>): Int {
    if (terms.isEmpty()) return 0
    var score = 0
    for (term in terms) {
        val title = entry.title.indexOf(term, ignoreCase = true)
        val path = entry.path.indexOf(term, ignoreCase = true)
        val keywords = entry.keywords.indexOf(term, ignoreCase = true)
        score += when {
            title == 0 -> 100
            title > 0 -> 60
            keywords >= 0 -> 24
            path >= 0 -> 12
            else -> return -1
        }
    }
    return score
}

                      
data class SearchText(
    val title: String,
    val path: String = "",
    val keywords: String = "",
)

                     
 
                                    
                                                                           
                                                                         
                                                                             
                                                         
                                                          
@Composable
fun XwakeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索设置",
    onClear: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    shape: Shape = RoundedCornerShape(28.dp),
    contentDescriptionText: String = "搜索框",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)
            .semantics { contentDescription = contentDescriptionText },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                XwakeGlyph(XwakeGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.semantics { contentDescription = "清除搜索" },
                    ) { XwakeGlyph(XwakeGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            singleLine = true,
            shape = shape,
            textStyle = LocalTextStyle.current,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                capitalization = KeyboardCapitalization.None,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

                      
                                                                       
                                                                                      
                                                                 
                                                                                    
                                                                                
                                                          
@Composable
fun <T> XwakeSearchResults(
    query: String,
    items: List<T>,
    textOf: (T) -> SearchText,
    onPick: (T) -> Unit,
    emptyTitle: String = "没有找到相关设置",
    emptyHint: String = "试试“主题”“周次”或“动画”",
    clearLabel: String = "清除搜索",
    onClearSearch: () -> Unit = {},
    resultTitle: (Int) -> String = { "搜索结果 · $it" },
) {
    if (items.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(emptyTitle, style = MaterialTheme.typography.titleMedium)
            Text(emptyHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClearSearch) { Text(clearLabel) }
        }
        return
    }
    val terms = remember(query) { searchTerms(query) }
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Section(resultTitle(items.size)) {
        items.forEach { item ->
            val text = textOf(item)
            val title = remember(text.title, terms, highlight) {
                highlightMatches(text.title, terms, highlight)
            }
            val path = remember(text.path, terms, highlight) {
                highlightMatches(text.path, terms, highlight)
            }
            SearchResultRow(title, path) { onPick(item) }
        }
    }
}

                      
                                                                         
                                                                              
                                                                    
                                                                               
@Composable
fun SearchResultRow(
    title: AnnotatedString,
    subtitle: AnnotatedString = AnnotatedString(""),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = if (subtitle.text.isBlank()) 58.dp else 76.dp)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.text.isNotBlank()) Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

                      
                                                                          
                                                                                  
                                                                          
                                                                        
                                                                         
@Composable
fun XwakeRecentList(
    items: List<String>,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    clearLabel: String? = null,
    onClearAll: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        items.forEach { entry ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClickLabel = "使用这条搜索记录") { onUse(entry) }
                        .padding(start = 18.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XwakeGlyph(XwakeGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(entry, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(52.dp).clickable(
                        role = Role.Button,
                        onClickLabel = "删除该条搜索历史",
                        onClick = { onRemove(entry) },
                    ).semantics { contentDescription = "删除 $entry" },
                    contentAlignment = Alignment.Center,
                ) { XwakeGlyph(XwakeGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (clearLabel != null && items.isNotEmpty()) {
            TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(clearLabel)
            }
        }
    }
}

                      
enum class XwakeGlyphKind { Search, Close, Back }

                      
                                                                                    
                                                                                    
                                                                                              
                                                                                  
@Composable
fun XwakeGlyph(
    kind: XwakeGlyphKind,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val pen = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            XwakeGlyphKind.Search -> {
                drawCircle(tint, 6.4f * u, Offset(10.4f * u, 10.4f * u), style = pen)
                drawLine(tint, Offset(15.2f * u, 15.2f * u), Offset(20.2f * u, 20.2f * u), pen.width, StrokeCap.Round)
            }
            XwakeGlyphKind.Close -> {
                drawLine(tint, Offset(6.5f * u, 6.5f * u), Offset(17.5f * u, 17.5f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(17.5f * u, 6.5f * u), Offset(6.5f * u, 17.5f * u), pen.width, StrokeCap.Round)
            }
            XwakeGlyphKind.Back -> {
                drawLine(tint, Offset(14.5f * u, 4.5f * u), Offset(7f * u, 12f * u), pen.width, StrokeCap.Round)
                drawLine(tint, Offset(7f * u, 12f * u), Offset(14.5f * u, 19.5f * u), pen.width, StrokeCap.Round)
            }
        }
    }
}
