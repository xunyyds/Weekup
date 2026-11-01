package com.xunyyds.kelaile.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.core.prefs.SearchHistory
import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.ui.KelaileElevation
import com.xunyyds.kelaile.ui.KelaileGlyph
import com.xunyyds.kelaile.ui.KelaileGlyphKind
import com.xunyyds.kelaile.ui.KelaileMotion
import com.xunyyds.kelaile.ui.KelaileShapes
import com.xunyyds.kelaile.ui.KelaileSpacing
import com.xunyyds.kelaile.ui.Section
import com.xunyyds.kelaile.ui.SectionRow
import com.xunyyds.kelaile.ui.LocalKelaileCanvas
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen
import com.xunyyds.kelaile.ui.highlightMatches
import com.xunyyds.kelaile.ui.palette.PaletteEntry
import com.xunyyds.kelaile.ui.palette.PaletteKind
import com.xunyyds.kelaile.ui.palette.paletteActions
import com.xunyyds.kelaile.ui.palette.paletteKindOrder
import com.xunyyds.kelaile.ui.kelaileMillis
import com.xunyyds.kelaile.ui.kelaileMotionEnabled

const val SearchFieldKey = "kelaile-search-field"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.KelaileSearchOverlay(
    visible: Boolean,
    data: AppData,
    onDismiss: () -> Unit,
    onCourse: (String) -> Unit,
    onSetting: (route: String, title: String) -> Unit,
    onAction: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = kelaileMotionEnabled()
    val focusRequester = remember { FocusRequester() }
    val canvas = LocalKelaileCanvas.current

    val query = rememberSaveable { mutableStateOf("") }

    val index = remember(data.courses) { SearchIndex.forData(data) }
    val history = remember(context) { SearchHistory(context) }
    var historyItems by remember { mutableStateOf(history.items()) }
    var historyTimes by remember { mutableStateOf(history.times()) }

    val suggestions = remember(data, historyItems, historyTimes, visible) {
        SearchSuggestions.suggest(data, historyItems, historyTimes, System.currentTimeMillis())
    }

    fun rememberSearch(raw: String) {
        if (raw.isBlank()) return
        history.add(raw)
        historyItems = history.items()
        historyTimes = history.times()
    }

    fun close() {
        keyboard?.hide()
        onDismiss()
    }

    fun pick(entry: PaletteEntry) {
        rememberSearch(query.value)
        keyboard?.hide()
        when (entry.kind) {
            PaletteKind.Course -> onCourse(entry.payload)
            PaletteKind.Setting -> onSetting(entry.payload, entry.title)
            PaletteKind.Action -> onAction(entry.payload)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else {
            query.value = ""
            keyboard?.hide()
            historyItems = history.items()
            historyTimes = history.times()
        }
    }
    BackHandler(enabled = visible) {
        if (query.value.isNotEmpty()) query.value = "" else close()
    }

    val radius = remember { Animatable(SearchPillRadius.value) }
    val scrimAlpha = remember { Animatable(0f) }
    val cornerMillis = kelaileMillis(KelaileMotion.standard)
    val scrimMillis = kelaileMillis(KelaileMotion.quick)
    val reducedMillis = kelaileMillis(KelaileMotion.reduced)
    LaunchedEffect(visible) {
        if (!motion) {
            // 减少动画：只做极短淡入淡出，圆角直接到位
            radius.snapTo(if (visible) 0f else SearchPillRadius.value)
            scrimAlpha.animateTo(if (visible) 1f else 0f, tween(reducedMillis))
            return@LaunchedEffect
        }
        if (visible) {
            radius.animateTo(0f, tween(cornerMillis, easing = KelaileMotion.emphasizedEasing))
            scrimAlpha.animateTo(1f, tween(scrimMillis, easing = KelaileMotion.decelerateEasing))
        } else {
            // 退场时圆角与遮罩一起收回，不要一关就跳到最终值
            scrimAlpha.animateTo(0f, tween(scrimMillis, easing = KelaileMotion.accelerateEasing))
            radius.animateTo(SearchPillRadius.value, tween(cornerMillis, easing = KelaileMotion.accelerateEasing))
        }
    }
    val sharedBar = if (motion) {
        Modifier.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(SearchFieldKey),
            visible = visible,
        )
    } else {
        Modifier
    }

    // 减少动画时保留一次很短的淡入淡出，避免面板硬切出现
    val enterMillis = kelaileMillis(if (motion) KelaileMotion.quick else KelaileMotion.reduced)
    val exitMillis = kelaileMillis(if (motion) KelaileMotion.fast else KelaileMotion.reduced)
    val enter = fadeIn(tween(enterMillis, easing = KelaileMotion.decelerateEasing))
    val exit = fadeOut(tween(exitMillis, easing = KelaileMotion.accelerateEasing))

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .background(canvas.scrim.copy(alpha = canvas.scrim.alpha * scrimAlpha.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { close() },
                    )
                    .semantics { contentDescription = "关闭搜索" },
            )
            SearchPane(
                query = query,
                index = index,
                radius = radius,
                barModifier = sharedBar,
                focusRequester = focusRequester,
                history = history,
                historyItems = historyItems,
                suggestions = suggestions,
                onHistoryChange = { historyItems = it; historyTimes = history.times() },
                onPick = { pick(it) },
                onClose = { close() },
            )
        }
    }
}

@Composable
private fun SearchPane(
    query: MutableState<String>,
    index: SearchIndex,
    radius: Animatable<Float, AnimationVector1D>,
    barModifier: Modifier,
    focusRequester: FocusRequester,
    history: SearchHistory,
    historyItems: List<String>,
    suggestions: List<Suggestion>,
    onHistoryChange: (List<String>) -> Unit,
    onPick: (PaletteEntry) -> Unit,
    onClose: () -> Unit,
) {

    val refineHint = remember(index) { Ref<SearchResult?>(null) }
    val result = remember(query.value, index) {
        index.search(query.value, refineHint.value).also { refineHint.value = it }
    }
    val accent = MaterialTheme.colorScheme.primary
    val highlight = remember(accent) { SpanStyle(color = accent, fontWeight = FontWeight.Bold) }
    val canvas = LocalKelaileCanvas.current
    val fieldShape = RoundedCornerShape(radius.value.dp)
    // 整页一体玻璃面板：顶部大圆角，与底部弹层同一套圆角体系
    val paneShape = RoundedCornerShape(
        topStart = KelaileShapes.sheet,
        topEnd = KelaileShapes.sheet,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    Column(Modifier.fillMaxSize().imePadding().semantics { paneTitle = "搜索" }) {
        // 整页一体玻璃面板：顶部大圆角贴住屏顶，头卡与结果区同材质，不再左右留缝
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    KelaileElevation.soft(10.dp, paneShape, 0.04f, 0.10f),
                )
                .glassPanel(paneShape, canvas.sheetFill, canvas.strokeTop, canvas.strokeBottom)
                .glassSheen(paneShape, Color.White)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "搜索",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = "关闭搜索" },
                ) { KelaileGlyph(KelaileGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Box(
                barModifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(fieldShape)
                    .background(canvas.fill.copy(alpha = 0.55f)),
            ) {
                SearchField(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    onClear = { query.value = "" },
                    onSubmit = { result.entries.firstOrNull()?.let(onPick) },
                    focusRequester = focusRequester,
                    shape = fieldShape,
                )
            }
            ResultList(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                query = query.value,
                result = result,
                terms = result.terms,
                highlight = highlight,
                historyItems = historyItems,
                suggestions = suggestions,
                onUseHistory = { query.value = it },
                onRemoveHistory = {
                    history.remove(it)
                    onHistoryChange(history.items())
                },
                onClearHistory = {
                    history.clear()
                    onHistoryChange(history.items())
                },
                onPick = onPick,
                onClear = { query.value = "" },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    shape: RoundedCornerShape,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    ) {
                        onSubmit()
                        true
                    } else false
                }
                .semantics { contentDescription = "搜索框" },
            placeholder = { Text("课程、老师、教室或设置") },
            leadingIcon = {
                KelaileGlyph(KelaileGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.semantics { contentDescription = "清空搜索内容" },
                    ) { KelaileGlyph(KelaileGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            singleLine = true,
            shape = shape,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
                capitalization = KeyboardCapitalization.None,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun ResultList(
    modifier: Modifier = Modifier,
    query: String,
    result: SearchResult,
    terms: List<String>,
    highlight: SpanStyle,
    historyItems: List<String>,
    suggestions: List<Suggestion>,
    onUseHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPick: (PaletteEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val entries = result.entries
    val grouped = remember(entries) { entries.groupBy { it.kind } }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(KelaileSpacing.md),
    ) {
        if (terms.isEmpty()) {
            if (suggestions.isNotEmpty()) {
                item(key = "suggest-group") {
                    Section("建议搜索") {
                        suggestions.forEach { suggestion ->
                            SectionRow {
                                SearchLineRow(
                                    entry = suggestion.entry,
                                    title = suggestion.entry.title,
                                    subtitle = suggestion.reason,
                                    terms = emptyList(),
                                    highlight = highlight,
                                ) { onPick(suggestion.entry) }
                            }
                        }
                    }
                }
            }
            item(key = "recent-group") {
                Section("最近搜索") {
                    if (historyItems.isEmpty()) {
                        SectionRow {
                            Text(
                                "还没有搜索记录，试试「周次」或教师名",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    } else {
                        historyItems.forEach { entry ->
                            SectionRow {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "使用这条搜索记录",
                                                onClick = { onUseHistory(entry) },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        KelaileGlyph(
                                            KelaileGlyphKind.Search,
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(entry, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    }
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "删除该条搜索历史",
                                                onClick = { onRemoveHistory(entry) },
                                            )
                                            .semantics { contentDescription = "删除 " + entry },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        KelaileGlyph(
                                            KelaileGlyphKind.Close,
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        SectionRow {
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("清空搜索历史") }
                        }
                    }
                }
            }
        } else if (entries.isEmpty()) {
            item(key = "empty") {
                Section("没有找到") {
                    SectionRow { EmptyState(query, onClear, onClose) }
                }
            }
        } else {
            paletteKindOrder.forEach { kind ->
                val group = grouped[kind].orEmpty()
                if (group.isNotEmpty()) {
                    item(key = "group:" + kind.name) {
                        Section("${kind.label} · ${group.size}") {
                            group.forEach { entry ->
                                SectionRow {
                                    SearchLineRow(
                                        entry = entry,
                                        title = entry.title,
                                        subtitle = entry.subtitle,
                                        terms = terms,
                                        highlight = highlight,
                                    ) { onPick(entry) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分组卡内的一行结果：类型徽标 + 主副标题，整行可点。
 */
@Composable
private fun SearchLineRow(
    entry: PaletteEntry,
    title: String,
    subtitle: String,
    terms: List<String>,
    highlight: SpanStyle,
    onClick: () -> Unit,
) {
    val canvas = LocalKelaileCanvas.current
    val highlighted = remember(title, terms, highlight) {
        if (terms.isEmpty()) AnnotatedString(title) else highlightMatches(title, terms, highlight)
    }
    val subHighlighted = remember(subtitle, terms, highlight) {
        if (subtitle.isBlank() || terms.isEmpty()) {
            AnnotatedString(subtitle)
        } else {
            highlightMatches(subtitle, terms, highlight)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (subHighlighted.text.isBlank()) 54.dp else 68.dp)
            .clip(RoundedCornerShape(KelaileShapes.card))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(entry.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                highlighted,
                style = MaterialTheme.typography.bodyLarge,
                color = if (canvas.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (subHighlighted.text.isNotBlank()) {
                Text(
                    subHighlighted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KindBadge(kind: PaletteKind) {
    val tint = when (kind) {
        PaletteKind.Course -> MaterialTheme.colorScheme.primary
        PaletteKind.Setting, PaletteKind.Action -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(34.dp).background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.label.take(1), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun EmptyState(query: String, onClear: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("「")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append(query.trim())
                }
                append("」没有匹配")
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = "没有找到 " + query.trim() + " 相关内容" },
        )
        Text(
            "换个教师名、教室号，或试试「周次」「导出」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear) { Text("清除搜索") }
            TextButton(onClick = onClose) { Text("关闭搜索") }
        }
    }
}

private class Ref<T>(var value: T)
