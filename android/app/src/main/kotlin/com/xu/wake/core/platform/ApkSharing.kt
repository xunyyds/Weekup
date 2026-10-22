package com.xu.wake.core.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

                                                                                               
object ApkSharing {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sharing = AtomicBoolean(false)

                                                                                          
    fun shareInstalledApk(context: Context) {
        if (!sharing.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { copyInstalledApk(app) }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("课来了 APK", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "分享课来了安装包").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(app, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                sharing.set(false)
            }
        }
    }

    private fun copyInstalledApk(app: Context): Uri {
        val info = app.applicationInfo
        require(info.splitSourceDirs.isNullOrEmpty()) {
            "当前为拆分安装包，请使用完整独立 APK 版本分享"
        }
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "app"
        val directory = File(app.cacheDir, "shared-apk").canonicalFile
        check(directory.parentFile == app.cacheDir.canonicalFile) { "无效的 APK 分享目录" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建 APK 分享目录" }
        val destination = File(directory, "kelaile-${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}-arm64-v8a.apk").canonicalFile
        check(destination.parentFile == directory) { "无效的 APK 分享路径" }
        
        val temporary = File.createTempFile("apk-", ".tmp", directory)
        try {
            File(info.sourceDir).inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.renameTo(destination)) { "无法保存 APK 分享副本" }
            return FileProvider.getUriForFile(app, "${app.packageName}.apkshare", destination)
        } finally {
            temporary.delete()
        }
    }
}
