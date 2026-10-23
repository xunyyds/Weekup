package com.xunyyds.kelaile.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xunyyds.kelaile.core.platform.ApkSharing
import com.xunyyds.kelaile.core.platform.AppLinks
import com.xunyyds.kelaile.core.platform.AppMetadata
import com.xunyyds.kelaile.core.platform.AppPlatform
import com.xunyyds.kelaile.ui.update.UpdateController

class ApkShareProvider : FileProvider()

fun shareInstalledApk(context: Context) = ApkSharing.shareInstalledApk(context)

@Composable
fun AboutContent(update: UpdateController) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf<String?>(null) }
    var info by remember(context) { mutableStateOf<AppMetadata?>(null) }
    var logo by remember(context) { mutableStateOf<ImageBitmap?>(null) }
    var metadataFailed by remember(context) { mutableStateOf(false) }
    LaunchedEffect(context) {
        runCatching { AppPlatform.readMetadata(context) }
            .onSuccess { info = it }
            .onFailure { metadataFailed = true }
        runCatching { AppPlatform.readApplicationIcon(context) }
            .onSuccess { logo = it.asImageBitmap() }
    }
    licenses?.let { license ->
        AlertDialog(
            onDismissRequest = { licenses = null },
            title = { Text("开源许可") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text("AndroidLiquidGlass · Copyright 2025 Kyant\n已对背景采样及镜头算法作 Android-only 最小适配。\n\n$license", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licenses = null }) { Text("关闭") } },
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xl)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xl)) {
            val image = logo
            if (image != null) {
                Image(image, "课来了图标", Modifier.size(136.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer))
            } else {
                Box(Modifier.size(136.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer))
            }
            Text("课来了", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("每一周，都从容一点", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("") {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp).heightIn(min = 58.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("关于课来了", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { ApkSharing.shareInstalledApk(context) }) { Text("分享应用") }
            }
            SettingRow("版本", info?.let { "${it.versionName} / ${it.versionCode}" } ?: if (metadataFailed) "无法读取版本" else "读取中…") {}
            SettingRow("系统", info?.deviceDescription ?: "—") {}
        }
        Section("更新") {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (!update.snapshot.checking) update.checkManually()
                }.heightIn(min = 76.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xs)) {
                    Text("检测更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            update.snapshot.checking -> "正在检查…"
                            update.snapshot.error != null -> "检查失败，可手动重试"
                            update.snapshot.latest != null -> "已连接到 GitHub Release"
                            else -> "从 GitHub Release 获取最新正式版本"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(KelaileSpacing.lg))
                if (update.snapshot.checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("检查", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            val inline = update.inlineError
            if (!inline.isNullOrBlank()) {
                Text(
                    inline,
                    Modifier.padding(horizontal = 18.dp).padding(bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SettingRow(
                "最新正式版本",
                update.snapshot.latest?.let { release ->
                    "${release.version} · ${friendlyReleaseDate(release.publishedAt)}"
                } ?: "尚未检测",
            ) {
                update.snapshot.latest?.let { release ->
                    AppPlatform.openLink(context, AppLinks.REPOSITORY + "/releases/tag/" + release.tag)
                }
            }
        }
        Section("开源项目") {
            Row(
                Modifier.fillMaxWidth().clickable { AppPlatform.openLink(context, AppLinks.REPOSITORY) }
                    .heightIn(min = 58.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GitHubMark(MaterialTheme.colorScheme.onSurface, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("GitHub · xunyyds/kelaile", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("在浏览器打开", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingRow("开源许可", "在 GitHub 上查看仓库许可文件") {
                AppPlatform.openLink(context, AppLinks.LICENSE)
            }
            SettingRow("AndroidLiquidGlass", "Kyant0 · Apache License 2.0") { AppPlatform.openLink(context, AppLinks.ANDROID_LIQUID_GLASS) }
        }
        Text(
            "课程与设置仅存于本机。教务登录由系统 WebView 处理，不收集或上传登录凭据。",
            Modifier.padding(KelaileSpacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun friendlyReleaseDate(raw: String): String =
    if (raw.isBlank()) "发布时间未知" else raw.take(10)

/** GitHub 图标：用 Canvas 画轮廓，避免为单个图标引入依赖。 */
@Composable
private fun GitHubMark(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "GitHub" }) {
        val s = size.minDimension
        val width = s * .09f
        drawCircle(tint.copy(alpha = .12f), radius = s * .5f, center = Offset(s / 2f, s / 2f))
        val head = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(s * .26f, s * .22f), s * .40f))
        }
        drawPath(head, tint, style = Stroke(width = width, cap = StrokeCap.Round))
        val tail = Path().apply {
            moveTo(s * .36f, s * .63f)
            quadraticBezierTo(s * .33f, s * .86f, s * .5f, s * .86f)
            quadraticBezierTo(s * .67f, s * .86f, s * .64f, s * .63f)
        }
        drawPath(tail, tint, style = Stroke(width = width, cap = StrokeCap.Round))
        drawLine(tint, Offset(s * .16f, s * .42f), Offset(s * .3f, s * .52f), strokeWidth = width, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * .84f, s * .42f), Offset(s * .7f, s * .52f), strokeWidth = width, cap = StrokeCap.Round)
    }
}
