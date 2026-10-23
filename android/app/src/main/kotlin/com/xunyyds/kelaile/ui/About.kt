package com.xunyyds.kelaile.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xunyyds.kelaile.core.platform.ApkSharing
import com.xunyyds.kelaile.core.platform.AppLinks
import com.xunyyds.kelaile.core.platform.AppMetadata
import com.xunyyds.kelaile.core.platform.AppPlatform
import kotlinx.coroutines.launch

class ApkShareProvider : FileProvider()

fun shareInstalledApk(context: Context) = ApkSharing.shareInstalledApk(context)

@Composable
fun AboutContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var licenses by remember { mutableStateOf<String?>(null) }
    var licenseLoading by remember { mutableStateOf(false) }
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
            SettingRow("版本", info?.let { "${it.versionName} / ${it.versionCode}" } ?: if (metadataFailed) "无法读取版本" else "读取中…") {}
            SettingRow("系统", info?.deviceDescription ?: "—") {}
        }
        Section("开源项目") {
            SettingRow("GitHub", AppLinks.REPOSITORY) { AppPlatform.openLink(context, AppLinks.REPOSITORY) }
            SettingRow("开源许可", if (licenseLoading) "读取中…" else "本地第三方声明与 Apache 2.0 许可") {
                if (!licenseLoading) {
                    licenseLoading = true
                    scope.launch {
                        try {
                            licenses = AppPlatform.readThirdPartyLicense(context)
                        } finally {
                            licenseLoading = false
                        }
                    }
                }
            }
            SettingRow("AndroidLiquidGlass", "Kyant0 · Apache License 2.0") { AppPlatform.openLink(context, AppLinks.ANDROID_LIQUID_GLASS) }
        }
        Section("") { SettingRow("分享应用", "通过系统分享当前已安装 APK") { ApkSharing.shareInstalledApk(context) } }
        Text("课程与设置仅存于本机。教务登录由系统 WebView 处理，不收集或上传登录凭据。", Modifier.padding(KelaileSpacing.md), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
