package com.xunyyds.kelaile.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xunyyds.kelaile.data.ReleaseAsset
import com.xunyyds.kelaile.data.ReleaseInfo
import com.xunyyds.kelaile.data.formatBytes
import com.xunyyds.kelaile.data.formatSpeed
import com.xunyyds.kelaile.data.progressLabel
import com.xunyyds.kelaile.data.progressPercent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 更新弹窗的阶段。 */
enum class UpdateStage {
    /** 已发现新版本，等待用户选择。 */
    Available,

    /** 下载中。 */
    Downloading,

    /** 下载完成并通过校验，可以安装。 */
    Ready,

    /** 失败，可重试。 */
    Failed,
}

/** 下载进度状态，集中由一个对象驱动，减少重组抖动。 */
data class UpdateProgress(
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val bytesPerSecond: Double? = null,
    val attemptLabel: String = "",
) {
    val percent: Int? get() = progressPercent(downloaded, total)
    val indeterminate: Boolean get() = total <= 0L
}

/**
 * 更新弹窗。
 *
 * 只负责展示与收集用户意图；网络、校验、安装都在调用方完成，便于在设置页与启动检查复用同一套流程。
 */
@Composable
fun UpdateDialog(
    info: ReleaseInfo,
    asset: ReleaseAsset?,
    currentVersion: String,
    forced: Boolean,
    stage: UpdateStage,
    progress: UpdateProgress,
    error: String?,
    stageLabel: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onSkipVersion: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val downloading = stage == UpdateStage.Downloading
    var confirmCancel by remember { mutableStateOf(false) }
    val dismissRequest = {
        if (downloading) confirmCancel = true else onDismiss()
    }

    if (confirmCancel) {
        com.xunyyds.kelaile.ui.GlassDialogScaffold(
            onDismiss = { confirmCancel = false },
            title = {
                Text(
                    "取消下载？",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    "已下载的部分会被删除，下次需要重新开始下载。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                com.xunyyds.kelaile.ui.GlassPillButton(
                    "取消下载",
                    onClick = { confirmCancel = false; onCancelDownload() },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("继续下载") }
            },
        )
    }

    com.xunyyds.kelaile.ui.GlassDialogScaffold(
            onDismiss = { if (!forced) dismissRequest() },
            dismissOnBackPress = !forced,
            title = {
                Text(
                    text = when (stage) {
                        UpdateStage.Failed -> "更新失败"
                        else -> "发现新版本"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    VersionSummary(info = info, asset = asset, currentVersion = currentVersion)
                    if (forced) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "此版本必须更新后才能继续使用。",
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("更新日志", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (info.body.isBlank()) {
                        Text(
                            "本次更新没有提供说明。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(
                            markdown = info.body,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        )
                    }
                    if (stage != UpdateStage.Available) {
                        Spacer(Modifier.height(14.dp))
                        ProgressSection(
                            stage = stage,
                            progress = progress,
                            stageLabel = stageLabel,
                            error = error,
                        )
                    }
                }
            },
            confirmButton = {
                when (stage) {
                    UpdateStage.Available -> com.xunyyds.kelaile.ui.GlassPillButton("立即更新", onClick = onUpdate)
                    UpdateStage.Downloading -> com.xunyyds.kelaile.ui.GlassPillButton("取消下载", onClick = { confirmCancel = true })
                    UpdateStage.Ready -> com.xunyyds.kelaile.ui.GlassPillButton("安装", onClick = onInstall)
                    UpdateStage.Failed -> com.xunyyds.kelaile.ui.GlassPillButton("重试", onClick = onRetry)
                }
            },
            dismissButton = {
                val skip: (() -> Unit)? = when (stage) {
                    UpdateStage.Available -> if (forced) null else onSkipVersion
                    UpdateStage.Downloading -> if (forced) null else onDismiss
                    UpdateStage.Ready -> if (forced) null else onDismiss
                    UpdateStage.Failed -> null
                }
                if (stage == UpdateStage.Failed) {
                    Row {
                        TextButton(onClick = onOpenReleasePage) { Text("手动下载") }
                        if (!forced) TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                } else if (skip != null) {
                    TextButton(onClick = skip) { Text(stage.dismissLabel) }
                }
            },
    )
}

private val UpdateStage.dismissLabel: String
    get() = when (this) {
        UpdateStage.Available -> "此版本不再提示"
        UpdateStage.Downloading -> "后台下载"
        UpdateStage.Ready -> "稍后安装"
        UpdateStage.Failed -> "关闭"
    }

@Composable
private fun VersionSummary(info: ReleaseInfo, asset: ReleaseAsset?, currentVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            info.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        InfoLine("发布时间", friendlyTime(info.publishedAt))
        InfoLine("版本", "$currentVersion → ${info.version}")
        InfoLine("安装包大小", asset?.sizeBytes?.takeIf { it > 0L }?.let(::formatBytes) ?: "未知大小")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 进度区：百分比、已下载/总大小、实时网速三行信息。
 * 进度与网速来自同一个状态对象，避免在进度条内部反复采样。
 */
@Composable
private fun ProgressSection(
    stage: UpdateStage,
    progress: UpdateProgress,
    stageLabel: String,
    error: String?,
) {
    val percent = progress.percent
    when (stage) {
        UpdateStage.Downloading -> {
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    percent?.let { "$it%" } ?: "下载中",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    progressLabel(progress.downloaded, progress.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatSpeed(progress.bytesPerSecond),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (progress.attemptLabel.isNotBlank()) {
                    Text(
                        progress.attemptLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        UpdateStage.Ready -> {
            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("下载完成", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    formatBytes(progress.total.takeIf { it > 0L } ?: progress.downloaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stageLabel.ifBlank { "安装将关闭应用，是否继续？" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        UpdateStage.Failed -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        "下载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        error ?: "未知错误，请重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (progress.downloaded > 0L) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "已下载 ${formatBytes(progress.downloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        UpdateStage.Available -> Unit
    }
}

/** 把 ISO 时间转成本地可读日期；解析失败时退回原始日期部分。 */
internal fun friendlyTime(raw: String): String {
    if (raw.isBlank()) return "未知"
    return runCatching {
        val instant = java.time.Instant.parse(raw)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        zoned.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))
    }.recoverCatching {
        val local = java.time.LocalDateTime.parse(raw)
        local.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))
    }.getOrElse { raw.take(10) }
}
