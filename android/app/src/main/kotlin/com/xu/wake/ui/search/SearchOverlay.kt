package com.xu.wake.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xu.wake.core.prefs.SearchHistory
import com.xu.wake.data.AppData
import com.xu.wake.ui.XwakeGlyph
import com.xu.wake.ui.XwakeGlyphKind
import com.xu.wake.ui.XwakeMotion
import com.xu.wake.ui.XwakeShapes
import com.xu.wake.ui.highlightMatches
import com.xu.wake.ui.palette.PaletteEntry
import com.xu.wake.ui.palette.PaletteKind
import com.xu.wake.ui.palette.paletteActions
import com.xu.wake.ui.palette.paletteKindOrder
import com.xu.wake.ui.xwakeMillis
import com.xu.wake.ui.xwakeMotionEnabled

/** Shared-element key linking the settings-page search pill to the full-screen search bar. */
const val SearchFieldKey = "xwake-search-field"

/**
 * Full-screen search over courses, settings and actions.
 *
 * The search bar is a shared element with the settings-page pill, so opening reads as that pill
 * growing to fill the screen instead of a second surface appearing on top of the page.
 *
 * Only [visible] decides whether this subtree is on screen; everything under the bar keys off its
 * own state, so a keystroke recomposes the field and the list and nothing else.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.XwakeSearchOverlay(
    visible: Boolean,
    data: AppData,
    onDismiss: () -> Unit,
    onCourse: (String) -> Unit,
    onSetting: (route: String, title: String) -> Unit,
    onAction: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = xwakeMotionEnabled()
    val focusRequester = remember { FocusRequester() }
    // The query is held here but never read here: only SearchPane observes it, so a keystroke
    // invalidates that pane and leaves this root — the shared element and its animation — alone.
    val query = rememberSaveable { mutableStateOf("") }

    val index = remember(data.courses) { SearchIndex.forData(data) }
    val history = remember(context) { SearchHistory(context) }
    var historyItems by remember { mutableStateOf(history.items()) }

    fun rememberSearch(raw: String) {
        if (raw.isBlank()) return
        history.add(raw)
        historyItems = history.items()
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

    // The focus request must wait for the first frame of the entry transition: before that the bar
    // is not attached yet, the request is dropped, and the keyboard never opens.
    LaunchedEffect(visible) {
        if (visible) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else {
            query.value = ""
            keyboard?.hide()
            historyItems = history.items()
        }
    }
    BackHandler(enabled = visible) {
        if (query.value.isNotEmpty()) query.value = "" else close()
    }

    // The bar keeps its pill radius while shared-element bounds grow; straightening it inside the
    // same window is what stops the corners popping at either end of the transition.
    val radius = remember { Animatable(SearchPillRadius.value) }
    val scrimAlpha = remember { Animatable(0f) }
    val cornerMillis = xwakeMillis(XwakeMotion.standard)
    val scrimMillis = xwakeMillis(XwakeMotion.quick)
    LaunchedEffect(visible) {
        if (!motion) {
            radius.snapTo(0f)
            scrimAlpha.snapTo(if (visible) 1f else 0f)
            return@LaunchedEffect
        }
        if (visible) {
            radius.animateTo(0f, tween(cornerMillis, easing = XwakeMotion.emphasizedEasing))
            scrimAlpha.animateTo(1f, tween(scrimMillis))
        } else {
            scrimAlpha.snapTo(0f)
            radius.snapTo(SearchPillRadius.value)
        }
    }
    val barShape = RoundedCornerShape(radius.value.dp)
    val sharedBar = if (motion) {
        Modifier.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(SearchFieldKey),
            visible = visible,
        )
    } else {
        Modifier
    }

    val enter = if (motion) fadeIn(tween(xwakeMillis(XwakeMotion.quick))) else EnterTransition.None
    val exit = if (motion) fadeOut(tween(xwakeMillis(XwakeMotion.fast))) else ExitTransition.None

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * scrimAlpha.value))
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
                shape = barShape,
                barModifier = sharedBar,
                focusRequester = focusRequester,
                history = history,
                historyItems = historyItems,
                onHistoryChange = { historyItems = it },
                onPick = { pick(it) },
                onClose = { close() },
            )
        }
    }
}

/**
 * Everything that depends on what the user typed. Kept in its own scope so the query, the result
 * set and the list are the only things a keystroke can recompose.
 */
@Composable
private fun SearchPane(
    query: MutableState<String>,
    index: SearchIndex,
    shape: RoundedCornerShape,
    barModifier: Modifier,
    focusRequester: FocusRequester,
    history: SearchHistory,
    historyItems: List<String>,
    onHistoryChange: (List<String>) -> Unit,
    onPick: (PaletteEntry) -> Unit,
    onClose: () -> Unit,
) {
    // Refinement hint: the previous result set is an accelerator, not UI state, so it lives in a
    // plain slot — writing it during composition must not invalidate anything.
    val refineHint = remember(index) { Ref<SearchResult?>(null) }
    val result = remember(query.value, index) {
        index.search(query.value, refineHint.value).also { refineHint.value = it }
    }
    val accent = MaterialTheme.colorScheme.primary
    val highlight = remember(accent) { SpanStyle(color = accent, fontWeight = FontWeight.Bold) }
    Column(Modifier.fillMaxSize().imePadding().semantics { paneTitle = "搜索" }) {
        Surface(
            modifier = barModifier.fillMaxWidth().statusBarsPadding(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            SearchField(
                query = query.value,
                onQueryChange = { query.value = it },
                onClear = { query.value = "" },
                onSubmit = { result.entries.firstOrNull()?.let(onPick) },
                onClose = onClose,
                focusRequester = focusRequester,
                shape = shape,
            )
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            ResultList(
                query = query.value,
                result = result,
                terms = result.terms,
                highlight = highlight,
                historyItems = historyItems,
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
    onClose: () -> Unit,
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
            placeholder = { Text("搜索课程、设置或动作") },
            leadingIcon = {
                XwakeGlyph(XwakeGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.semantics { contentDescription = "清空搜索内容" },
                    ) { XwakeGlyph(XwakeGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
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
        IconButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = "关闭搜索" },
        ) { XwakeGlyph(XwakeGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun ResultList(
    query: String,
    result: SearchResult,
    terms: List<String>,
    highlight: SpanStyle,
    historyItems: List<String>,
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (terms.isEmpty()) {
            item(key = "recent-header") { GroupHeader("最近搜索", 0, showCount = false) }
            if (historyItems.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        "还没有搜索记录，试试“周次”或教师名",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else {
                items(historyItems, key = { "history:" + it }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onUse = { onUseHistory(entry) },
                        onRemove = { onRemoveHistory(entry) },
                    )
                }
                item(key = "history-clear") {
                    TextButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                        Text("清空搜索历史")
                    }
                }
            }
            item(key = "actions-header") { GroupHeader("常用动作", 0, showCount = false) }
            items(paletteActions, key = { "quick:" + it.id }) { entry ->
                ResultRow(entry, terms, highlight) { onPick(entry) }
            }
        } else if (entries.isEmpty()) {
            item(key = "empty") { EmptyState(query, onClear, onClose) }
        } else {
            paletteKindOrder.forEach { kind ->
                val group = grouped[kind].orEmpty()
                if (group.isNotEmpty()) {
                    item(key = "header:" + kind.name) {
                        GroupHeader(kind.label, group.size, showCount = true)
                    }
                    items(group, key = { "e:" + it.id }) { entry ->
                        ResultRow(entry, terms, highlight) { onPick(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int, showCount: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showCount) {
            Text(
                " · " + count,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ResultRow(entry: PaletteEntry, terms: List<String>, highlight: SpanStyle, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val title = remember(entry.title, terms, highlight) { highlightMatches(entry.title, terms, highlight) }
    val subtitle = remember(entry.subtitle, terms, highlight) {
        highlightMatches(entry.subtitle, terms, highlight)
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XwakeShapes.card))
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = if (subtitle.text.isBlank()) 52.dp else 68.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(entry.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (subtitle.text.isNotBlank()) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
private fun HistoryRow(entry: String, onUse: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClickLabel = "使用这条搜索记录", onClick = onUse)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XwakeGlyph(XwakeGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(entry, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Box(
            Modifier.size(48.dp).clickable(
                role = Role.Button,
                onClickLabel = "删除该条搜索历史",
                onClick = onRemove,
            ).semantics { contentDescription = "删除 " + entry },
            contentAlignment = Alignment.Center,
        ) { XwakeGlyph(XwakeGlyphKind.Close, MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun EmptyState(query: String, onClear: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("没有找到「")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append(query.trim())
                }
                append("」相关内容")
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = "没有找到 " + query.trim() + " 相关内容" },
        )
        Text(
            "换个教师名、教室号，或试试“周次”“导出”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear) { Text("清除搜索") }
            TextButton(onClick = onClose) { Text("关闭搜索") }
        }
    }
}

/** Plain mutable slot: a refinement hint must never invalidate composition when written. */
private class Ref<T>(var value: T)
