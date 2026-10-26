package com.xunyyds.kelaile.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xunyyds.kelaile.core.platform.ApkSharing
import com.xunyyds.kelaile.core.platform.AppLinks
import com.xunyyds.kelaile.core.platform.AppMetadata
import com.xunyyds.kelaile.core.platform.AppPlatform
import com.xunyyds.kelaile.ui.glass.glassPanel
import com.xunyyds.kelaile.ui.glass.glassSheen
import com.xunyyds.kelaile.ui.update.UpdateController

class ApkShareProvider : FileProvider()

fun shareInstalledApk(context: Context) = ApkSharing.shareInstalledApk(context)

/**
 * 关于页：分组链接卡片布局。
 *
 * 头部一张大卡放图标、名称、版本与两个快捷动作；下方按「项目 / 许可」
 * 两个分组列出链接条目，每条是「图标徽章 + 标题 + 副标题」，复用设置页
 * 的 [SettingRow]，保证与全应用同一套材质与间距。
 */
@Composable
fun AboutContent(update: UpdateController) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf<String?>(null) }
    var licenseTitle by remember { mutableStateOf("开源许可") }
    var info by remember(context) { mutableStateOf<AppMetadata?>(null) }
    var logo by remember(context) { mutableStateOf<ImageBitmap?>(null) }
    var metadataFailed by remember(context) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(context) {
        runCatching { AppPlatform.readMetadata(context) }
            .onSuccess { info = it }
            .onFailure { metadataFailed = true }
        runCatching { AppPlatform.readApplicationIcon(context) }
            .onSuccess { logo = it.asImageBitmap() }
    }
    KelaileDialog(
        visible = licenses != null,
        onDismiss = { licenses = null },
        title = licenseTitle,
        confirmLabel = "关闭",
        content = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    licenses.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KelaileSpacing.xl)) {
        HeroHeader(
            logo = logo,
            version = info?.let { "${it.versionName} · ${it.versionCode}" }
                ?: if (metadataFailed) "无法读取版本" else "读取中…",
            checking = update.snapshot.checking,
            onCheckUpdate = { if (!update.snapshot.checking) update.checkManually() },
            onShare = { ApkSharing.shareInstalledApk(context) },
        )
        Section("项目") {
            AboutLinkTile(KelaileGlyphKind.Code, "源代码仓库", "在 GitHub 浏览与下载源码") {
                AppPlatform.openLink(context, AppLinks.REPOSITORY)
            }
            AboutLinkTile(KelaileGlyphKind.Bug, "问题反馈", "遇到问题欢迎提交 Issue") {
                AppPlatform.openLink(context, AppLinks.ISSUES)
            }
            AboutLinkTile(KelaileGlyphKind.Doc, "更新日志", "查看各版本的改动记录") {
                AppPlatform.openLink(context, AppLinks.RELEASES)
            }
        }
        Section("许可") {
            AboutLinkTile(KelaileGlyphKind.Balance, "开源许可", "本项目以 GPL-3.0 发布") {
                AppPlatform.openLink(context, AppLinks.LICENSE)
            }
            AboutLinkTile(KelaileGlyphKind.Heart, "致谢", "感谢这些项目提供的灵感") {
                licenseTitle = "致谢"
                licenses = creditText
            }
            AboutLinkTile(KelaileGlyphKind.Book, "第三方许可证", "依赖组件的许可全文") {
                licenseTitle = "第三方许可证"
                licenses = "$creditText\n\n${AppPlatform.readLicenseSync(context)}"
            }
        }
        SettingsFooterPhrase("课来了是一个开源的课程表应用，感谢每一位用户与贡献者。")
    }
}

private const val creditText =
    "AndroidLiquidGlass · Copyright 2025 Kyant\n已对背景采样及镜头算法作 Android-only 最小适配。"

/**
 * 关于页头：应用图标 + 名称 + 版本号，下方「检查更新 / 分享应用」快捷动作。
 */
@Composable
private fun HeroHeader(
    logo: ImageBitmap?,
    version: String,
    checking: Boolean,
    onCheckUpdate: () -> Unit,
    onShare: () -> Unit,
) {
    val canvas = LocalKelaileCanvas.current
    val shape = RoundedCornerShape(KelaileShapes.sheet)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(KelaileElevation.soft(KelaileElevation.card, shape))
            .glassPanel(shape, canvas.fill, canvas.strokeTop, canvas.strokeBottom)
            .glassSheen(shape, Color.White)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(KelaileSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (logo != null) {
                    Image(
                        logo,
                        "课来了图标",
                        Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    val tint = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(38.dp)) {
                        val u = size.minDimension / 24f
                        val pen = Stroke(width = 1.7f * u, cap = StrokeCap.Round)
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(3f * u, 4f * u),
                            size = androidx.compose.ui.geometry.Size(18f * u, 16f * u),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * u),
                            style = pen,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "课来了",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "每一周，都从容一点",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassPillButton(
                if (checking) "正在检查…" else "检查更新",
                onClick = { if (!checking) onCheckUpdate() },
                compact = true,
                tint = MaterialTheme.colorScheme.primary,
            )
            GlassPillButton("分享应用", onClick = onShare, compact = true)
        }
    }
}

/**
 * 链接瓦片：图标徽章 + 标题 + 副标题，右侧区分外链（ ↗ ）与站内（ → ）。
 * 外链图标用细箭头直接画，避免引入额外矢量资源。
 */
@Composable
private fun AboutLinkTile(
    glyph: KelaileGlyphKind,
    title: String,
    subtitle: String = "",
    internal: Boolean = false,
    onClick: () -> Unit,
) {
    SettingRow(title = title, subtitle = subtitle, glyph = glyph, onClick = onClick)
}
