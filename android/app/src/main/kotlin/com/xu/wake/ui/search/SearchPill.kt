package com.xu.wake.ui.search

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xu.wake.ui.XwakeGlyph
import com.xu.wake.ui.XwakeGlyphKind
import com.xu.wake.ui.XwakeShapes
import com.xu.wake.ui.xwakeMotionEnabled

/** Height of the collapsed pill; the shared element interpolates from here to the full screen. */
val SearchPillHeight = 56.dp

/** Corner radius of the collapsed pill; the expanded bar straightens out to 0dp. */
internal val SearchPillRadius = XwakeShapes.sheet

/**
 * The one search entry point in the app: a read-only pill at the top of the settings page.
 *
 * Tapping it does not focus anything — it opens the full-screen search, morphing this pill into
 * that screen's search bar via the shared element key [SearchFieldKey].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.XwakeSearchPill(
    collapsed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = xwakeMotionEnabled()
    val shape = RoundedCornerShape(SearchPillRadius)
    // Exactly one end of the shared element may be visible at a time: the pill while collapsed, the
    // expanded bar inside the overlay while open. Both report the same key, which is what makes the
    // element travel between them instead of appearing in two places at once.
    val shared = if (motion) {
        Modifier.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(SearchFieldKey),
            visible = collapsed,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(shared)
            .height(SearchPillHeight)
            .shadow(6.dp, shape, clip = false)
            .semantics { contentDescription = "搜索课程、设置与动作" },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().height(SearchPillHeight)
                .clickable(role = Role.Button, onClickLabel = "打开搜索", onClick = onOpen)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XwakeGlyph(XwakeGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Text(
                    "搜索课程、设置与动作",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
