package com.xunyyds.kelaile.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xunyyds.kelaile.ui.glass.KelaileBackdrop
import com.xunyyds.kelaile.ui.glass.contentSource
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen
import com.xunyyds.kelaile.ui.glass.glassSource

/**
 * 顶栏：悬浮玻璃胶囊。
 *
 * 标题、副标题与右侧动作都收进同一枚胶囊里，胶囊本身只有 1dp 高光描边，
 * 靠背景渐变透出玻璃感；[collapseFraction] 让高度随滚动轻微收缩。
 */
@Composable
fun KelaileTopBar(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedHeight: Dp = 54.dp,
    collapsedHeight: Dp = 44.dp,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val f = collapseFraction.coerceIn(0f, 1f)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.capsule)
    val body = expandedHeight + (collapsedHeight - expandedHeight) * f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBar)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(KelaileElevation.soft(6.dp, shape, 0.03f, 0.07f))
            .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
            .glassSheen(shape, Color.White)
            .height(body)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (titleContent != null) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { titleContent() }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
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
 * 页面底色：纵向渐变 + 散景光斑。
 *
 * 壁纸这一层挂 [glassSource]（只录背景），内容层整体再挂 [contentSource]
 * （录壁纸 + 正文，不含悬浮件），前者供卡片级透镜、后者供液态底栏折射。
 * 内容作为兄弟节点画在上面，不会被录进壁纸采样层，避免自引用。
 */
@Composable
fun KelaileScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().glassSource()) {
            KelaileBackdrop(Modifier.matchParentSize())
        }
        Box(Modifier.matchParentSize().contentSource()) {
            content()
        }
    }
}

@Composable
fun rememberSystemBars(): (Boolean) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { hidden ->
            val activity = context.kelaileActivity()
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
                else controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private fun Context.kelaileActivity(): Activity? = kelaileHostActivity()
