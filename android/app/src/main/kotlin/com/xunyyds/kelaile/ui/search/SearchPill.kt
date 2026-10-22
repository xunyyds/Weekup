package com.xunyyds.kelaile.ui.search

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
import com.xunyyds.kelaile.ui.KelaileGlyph
import com.xunyyds.kelaile.ui.KelaileGlyphKind
import com.xunyyds.kelaile.ui.KelaileShapes
import com.xunyyds.kelaile.ui.kelaileMotionEnabled

val SearchPillHeight = 56.dp

internal val SearchPillRadius = KelaileShapes.sheet

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.KelaileSearchPill(
    collapsed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = kelaileMotionEnabled()
    val shape = RoundedCornerShape(SearchPillRadius)

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
            KelaileGlyph(KelaileGlyphKind.Search, MaterialTheme.colorScheme.onSurfaceVariant)
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
