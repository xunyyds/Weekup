package com.xunyyds.kelaile.core.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * 安装包校验。
 *
 * 下载可能被镜像劫持、被运营商缓存替换、或因中断而截断，因此安装前必须确认文件本身是一个
 * 合法的 APK，且包名与本应用一致，避免把别的应用装上去。
 */
object ApkVerifier {

    data class Info(val packageName: String, val versionName: String)

    /** 校验失败时抛出带可读原因的异常。 */
    fun verify(context: Context, file: File, expectedPackage: String): Info {
        require(file.isFile) { "安装包不存在，请重新下载" }
        require(file.length() > MIN_BYTES) { "安装包过小（${file.length()} 字节），可能下载不完整，请重试" }

        val packageManager = context.applicationContext.packageManager
        val archive = try {
            packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } catch (e: Exception) {
            throw IllegalArgumentException("无法读取安装包：${e.message ?: "文件已损坏"}", e)
        } ?: throw IllegalArgumentException("安装包已损坏或不是有效的 APK，请重新下载")

        val name = archive.packageName
            ?: throw IllegalArgumentException("安装包缺少包名，可能已损坏")
        require(name == expectedPackage) { "安装包包名不匹配（$name），已拒绝安装" }

        val version = archive.versionName.orEmpty()
        require(version.isNotBlank()) { "安装包缺少版本信息，可能已损坏" }
        return Info(name, version)
    }

    private const val MIN_BYTES = 1024L
}
