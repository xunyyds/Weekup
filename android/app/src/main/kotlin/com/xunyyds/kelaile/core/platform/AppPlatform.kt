package com.xunyyds.kelaile.core.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

                                                                                      
data class AppMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val deviceDescription: String,
)

                                                                                                 
object AppPlatform {
    suspend fun readMetadata(context: Context): AppMetadata = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        AppMetadata(
            packageName = app.packageName,
            versionName = info.versionName ?: "未知",
            versionCode = versionCode,
            deviceDescription = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}",
        )
    }

    suspend fun readApplicationIcon(context: Context, sizePx: Int = 240): Bitmap = withContext(Dispatchers.IO) {
        require(sizePx > 0) { "Icon size must be positive" }
        val app = context.applicationContext
        val drawable = app.packageManager.getApplicationIcon(app.packageName)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
    }

    suspend fun readThirdPartyLicense(context: Context): String = withContext(Dispatchers.IO) {
        runCatching {
            context.applicationContext.assets.open("licenses/AndroidLiquidGlass-Apache-2.0.txt")
                .bufferedReader().use { it.readText() }
        }.getOrDefault("无法读取许可，请访问上游项目。")
    }

                                                                
    fun openLink(context: Context, url: String) {
        runCatching {
            val uri = Uri.parse(url)
            require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank())
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Toast.makeText(context.applicationContext, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
        }
    }
}
