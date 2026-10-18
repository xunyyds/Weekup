package com.xu.wake.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 紧凑顶栏。
 *
 * 与系统状态栏联动：自身高度里含状态栏内边距，标题不会被时钟与信号图标压住。
 * 随课表滚动在 [expandedHeight] 与 [collapsedHeight] 之间连续收缩，副标题在收起时淡出。
 *
 * 展开高度也刻意压得比 Material 的 LargeTopAppBar（152dp）小得多——课表才是主体。
 *
 * @param collapseFraction 0 = 完全展开，1 = 完全收起，由调用方按滚动量给出。
 */
@Composable
fun XwakeTopBar(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedHeight: Dp = 58.dp,
    collapsedHeight: Dp = 44.dp,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val f = collapseFraction.coerceIn(0f, 1f)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val body = expandedHeight + (collapsedHeight - expandedHeight) * f
    Row(
        modifier
            .fillMaxWidth()
            .height(statusBar + body)
            .padding(top = statusBar, start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (titleContent != null) {
            Column(Modifier.weight(1f)) { titleContent() }
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(1f - f),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * 系统状态栏与导航栏的显隐开关。
 *
 * 沉浸浏览课表时把系统栏一并收起，退出沉浸或离开课表页时恢复。
 * 收起时保留 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE，边缘内滑仍可临时唤出。
 */
@Composable
fun rememberSystemBars(): (Boolean) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { hidden ->
            val activity = context.xwakeActivity()
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
                else controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private fun Context.xwakeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.xwakeActivity() else null
    else -> null
}
