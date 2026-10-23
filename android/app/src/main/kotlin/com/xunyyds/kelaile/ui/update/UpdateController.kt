package com.xunyyds.kelaile.ui.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.xunyyds.kelaile.core.update.ApkInstaller
import com.xunyyds.kelaile.core.update.ApkVerifier
import com.xunyyds.kelaile.core.update.UpdateClient
import com.xunyyds.kelaile.data.ReleaseAsset
import com.xunyyds.kelaile.data.ReleaseInfo
import com.xunyyds.kelaile.data.UpdateSource
import com.xunyyds.kelaile.data.VersionRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 一次更新检查的可见结果，供关于页展示「最新版本」信息。 */
data class UpdateSnapshot(
    val checking: Boolean = false,
    val latest: ReleaseInfo? = null,
    val error: String? = null,
)

/**
 * 更新流程的状态持有者。
 *
 * 手动检测、启动静默检查、下载、校验、安装都收敛在这里，界面只读状态、发意图，
 * 保证两条入口的行为完全一致（差别仅在是否展示 loading 与是否弹「已是最新」）。
 */
@Stable
class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentVersion: () -> String,
    private val skippedVersion: () -> String,
    private val onSkip: (String) -> Unit,
) {
    var dialogInfo by mutableStateOf<ReleaseInfo?>(null)
        private set
    var dialogAsset by mutableStateOf<ReleaseAsset?>(null)
        private set
    var dialogForced by mutableStateOf(false)
        private set
    var stage by mutableStateOf(UpdateStage.Available)
        private set
    var progress by mutableStateOf(UpdateProgress())
        private set
    var errorText by mutableStateOf<String?>(null)
        private set
    var stageLabel by mutableStateOf("")
        private set

    /** 关于页「检测更新」下方的红字，启动静默检查失败时也写这里。 */
    var inlineError by mutableStateOf<String?>(null)
        private set

    var snapshot by mutableStateOf(UpdateSnapshot())
        private set

    /** 「已是最新」这类一次性提示，由界面弹 AlertDialog 后清空。 */
    var notice by mutableStateOf<String?>(null)
        private set

    private var downloadJob: Job? = null
    private var downloadedApk: File? = null
    private val busy = AtomicBoolean(false)

    fun clearInlineError() {
        inlineError = null
    }

    fun dismissNotice() {
        notice = null
    }

    fun dismissDialog() {
        if (stage == UpdateStage.Downloading) {
            cancelDownload()
            return
        }
        dialogInfo = null
        dialogAsset = null
        stage = UpdateStage.Available
        errorText = null
        progress = UpdateProgress()
        stageLabel = ""
    }

    /** 启动时的静默检查：不展示 loading，失败只写红字，成功时按规则决定是否弹窗。 */
    fun checkSilently() {
        scope.launch {
            snapshot = snapshot.copy(checking = true)
            try {
                val info = UpdateClient.fetchLatest()
                snapshot = UpdateSnapshot(checking = false, latest = info, error = null)
                inlineError = null
                presentIfNeeded(info, manual = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = readable(e)
                snapshot = UpdateSnapshot(checking = false, latest = snapshot.latest, error = reason)
                inlineError = "更新检查失败请手动重试，原因：$reason"
            }
        }
    }

    /** 手动检测：总是给出结果，最新/失败都会弹窗，失败同时写红字。 */
    fun checkManually() {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            snapshot = snapshot.copy(checking = true)
            try {
                val info = UpdateClient.fetchLatest()
                snapshot = UpdateSnapshot(checking = false, latest = info, error = null)
                inlineError = null
                val decision = VersionRules.decide(info.version, currentVersion(), skippedVersion(), manual = true)
                if (decision.show) {
                    open(info, decision.forced)
                } else {
                    notice = "已是最新版本（当前 ${currentVersion()}）"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = readable(e)
                snapshot = UpdateSnapshot(checking = false, latest = snapshot.latest, error = reason)
                inlineError = "更新检查失败请手动重试，原因：$reason"
                notice = reason
            } finally {
                busy.set(false)
            }
        }
    }

    private fun presentIfNeeded(info: ReleaseInfo, manual: Boolean) {
        val decision = VersionRules.decide(info.version, currentVersion(), skippedVersion(), manual)
        if (decision.show) open(info, decision.forced)
    }

    private fun open(info: ReleaseInfo, forced: Boolean) {
        dialogInfo = info
        dialogAsset = UpdateSource.pickAsset(info.assets)
        dialogForced = forced
        stage = if (dialogAsset == null) UpdateStage.Failed else UpdateStage.Available
        if (dialogAsset == null) errorText = "该版本没有可用的安装包（未找到 .apk 资产）"
        progress = UpdateProgress()
        stageLabel = ""
    }

    fun skipCurrentVersion() {
        val info = dialogInfo ?: return
        onSkip(info.version)
        dismissDialog()
    }

    fun retry() {
        val info = dialogInfo ?: return
        val asset = dialogAsset
        if (asset == null) {
            errorText = "该版本没有可用的安装包（未找到 .apk 资产）"
            stage = UpdateStage.Failed
            return
        }
        startDownload(info, asset)
    }

    fun startDownload() {
        val info = dialogInfo ?: return
        val asset = dialogAsset ?: return
        startDownload(info, asset)
    }

    private fun startDownload(info: ReleaseInfo, asset: ReleaseAsset) {
        if (!busy.compareAndSet(false, true)) return
        downloadJob?.cancel()
        errorText = null
        progress = UpdateProgress()
        stage = UpdateStage.Downloading
        stageLabel = ""
        val directory = ApkInstaller.updateDirectory(context)
        val target = File(directory, "kelaile-${info.version}.apk")
        downloadJob = scope.launch {
            try {
                ApkInstaller.clearDownloads(context)
                val result = UpdateClient.download(
                    info = info,
                    asset = asset,
                    target = target,
                    listener = { downloaded, total, speed ->
                        progress = UpdateProgress(downloaded, total, speed, progress.attemptLabel)
                    },
                    onAttempt = { label -> progress = progress.copy(attemptLabel = label) },
                )
                progress = progress.copy(attemptLabel = "已连接 ${result.source.hostLabel()}")
                val verified = ApkVerifier.verify(context, result.file, context.packageName)
                downloadedApk = result.file
                val size = result.file.length().takeIf { it > 0L } ?: progress.total
                progress = UpdateProgress(size, size, 0.0, "")
                stage = UpdateStage.Ready
                stageLabel = "已下载 ${formatSize(size)}（${verified.versionName}），安装将关闭应用，是否继续？"
            } catch (e: CancellationException) {
                progress = UpdateProgress()
                throw e
            } catch (e: Exception) {
                errorText = readable(e)
                stage = UpdateStage.Failed
            } finally {
                busy.set(false)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadedApk?.delete()
        downloadedApk = null
        busy.set(false)
        progress = UpdateProgress()
        errorText = null
        stage = UpdateStage.Available
        stageLabel = ""
    }

    /** 安装：先确认已获授权，再交给系统安装器。调用方负责在确认弹窗里问「将关闭应用」。 */
    fun install(): Boolean {
        val file = downloadedApk ?: run {
            errorText = "安装包不存在，请重新下载"
            stage = UpdateStage.Failed
            return false
        }
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.requestInstallPermission(context)
            stageLabel = "请先允许本应用安装未知应用，授权后返回这里再点安装。"
            return false
        }
        return runCatching {
            ApkInstaller.install(context, file)
            true
        }.getOrElse { e ->
            errorText = "无法调起安装器：${e.message ?: "请检查系统设置"}"
            stage = UpdateStage.Failed
            false
        }
    }

    fun openReleasePage(open: (String) -> Unit) = open(UpdateSource.RELEASE_PAGE)

    private fun readable(e: Exception): String = when (e) {
        is IllegalArgumentException -> e.message ?: "文件校验失败"
        else -> e.message ?: "网络请求失败"
    }

    private fun formatSize(bytes: Long): String = com.xunyyds.kelaile.data.formatBytes(bytes)

    private fun String.hostLabel(): String = runCatching {
        java.net.URI(this).host?.takeIf { it.isNotBlank() } ?: "下载源"
    }.getOrElse { "下载源" }
}

@Composable
fun rememberUpdateController(
    currentVersion: String,
    skippedVersion: String,
    onSkip: (String) -> Unit,
): UpdateController {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val versionState by rememberUpdatedState(currentVersion)
    val skipState by rememberUpdatedState(skippedVersion)
    val skipAction by rememberUpdatedState(onSkip)
    return remember {
        UpdateController(
            context = context,
            scope = scope,
            currentVersion = { versionState },
            skippedVersion = { skipState },
            onSkip = { skipAction(it) },
        )
    }
}
