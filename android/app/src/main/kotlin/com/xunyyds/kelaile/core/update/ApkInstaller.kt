package com.xunyyds.kelaile.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 安装下载好的更新包。
 *
 * 走已有的 apkshare FileProvider（authorities `${applicationId}.apkshare`），
 * 新增的下载目录加入 `res/xml/share_paths.xml`，避免 FileProvider 拿不到 URI 直接崩溃。
 */
object ApkInstaller {

    /** 下载目录，必须与 `share_paths.xml` 中的 `update_apk` 路径一致。 */
    fun updateDirectory(context: Context): File = File(context.applicationContext.filesDir, "update")

    /** 本应用是否已被允许「安装未知应用」。系统拒绝查询时按未授权处理，不抛出。 */
    fun canInstall(context: Context): Boolean {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching { app.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    /** 打开「安装未知应用」授权页；用户返回后需要重新检查 [canInstall]。 */
    fun requestInstallPermission(context: Context) {
        val app = context.applicationContext
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }

    /** 调起系统安装器。调用方需先确认 [canInstall]。 */
    fun install(context: Context, apk: File) {
        require(apk.isFile) { "安装包不存在，请重新下载" }
        require(apk.length() > 0L) { "安装包为空，请重新下载" }
        val app = context.applicationContext
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.apkshare", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            app.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            throw IllegalStateException("系统没有可用的安装器，请到文件管理器手动安装下载好的 APK", e)
        } catch (e: SecurityException) {
            throw IllegalStateException("系统拒绝了安装请求，请允许本应用安装未知应用后重试", e)
        }
    }

    /** 清理旧的下载残留，避免占用存储。 */
    fun clearDownloads(context: Context) {
        val directory = updateDirectory(context)
        runCatching {
            directory.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        }
    }
}
