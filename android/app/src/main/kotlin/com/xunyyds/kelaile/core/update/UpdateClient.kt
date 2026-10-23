package com.xunyyds.kelaile.core.update

import com.xunyyds.kelaile.data.ReleaseAsset
import com.xunyyds.kelaile.data.ReleaseInfo
import com.xunyyds.kelaile.data.UpdateSource
import com.xunyyds.kelaile.data.TransferMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * GitHub Release 查询与安装包下载。
 *
 * 只用平台 `HttpURLConnection`，与项目既有网络方式一致，不引入新依赖。
 * 所有失败都转换成可读中文原因，供弹窗与关于页红字直接展示。
 */
object UpdateClient {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 20_000
    private const val USER_AGENT = "kelaile-updater"

    /** 查询最新正式版本。失败时抛出带可读原因的异常。 */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val text = try {
            val connection = (URL(UpdateSource.API_LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    val hint = when {
                        status == 403 || status == 429 -> "GitHub 接口限流，请稍后再试"
                        status == 404 -> "仓库或版本不存在"
                        status in 500..599 -> "GitHub 服务暂时不可用"
                        else -> "HTTP $status"
                    }
                    throw IOException("更新接口请求失败（$hint）")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (e: UnknownHostException) {
            throw IOException("无法连接更新服务器，请检查网络（${e.message ?: "域名解析失败"}）", e)
        } catch (e: SocketTimeoutException) {
            throw IOException("连接更新服务器超时，请稍后重试", e)
        } catch (e: SSLException) {
            throw IOException("安全连接失败，请检查系统时间或网络代理", e)
        }

        try {
            ReleaseInfo.parse(text)
        } catch (e: Exception) {
            throw IOException("无法解析版本信息：${e.message ?: "格式异常"}", e)
        }
    }

    data class Result(val file: File, val source: String)

    /** 下载进度回调。 */
    fun interface Listener {
        fun onProgress(downloaded: Long, total: Long, bytesPerSecond: Double?)
    }

    /**
     * 依次尝试直连与镜像，直到某个地址完整下载。
     *
     * 任一地址失败都会记录原因；全部失败时抛出汇总了每个地址与原因的异常，便于用户判断是网络还是镜像问题。
     */
    suspend fun download(
        info: ReleaseInfo,
        asset: ReleaseAsset,
        target: File,
        listener: Listener,
        onAttempt: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val candidates = UpdateSource.downloadCandidates(info, asset)
        val failures = mutableListOf<String>()
        for ((index, url) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            onAttempt(if (index == 0) "正在连接 GitHub…" else "正在尝试镜像 ${index}/${candidates.size - 1}…")
            try {
                val file = fetchOne(url, target, asset.sizeBytes, listener)
                return@withContext Result(file, url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val label = if (index == 0) "直连" else "镜像 ${index}"
                failures += "$label：${e.message ?: "未知错误"}"
                target.delete()
            }
        }
        throw IOException("全部下载地址均失败（${failures.joinToString("；")}）")
    }

    private fun fetchOne(url: String, target: File, expectedSize: Long, listener: Listener): File {
        val meter = TransferMeter()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
            instanceFollowRedirects = true
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException(describeStatus(status))
            }
            val declared = connection.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("无法创建下载目录") }

            var written = 0L
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        meter.record(written, System.currentTimeMillis())
                        listener.onProgress(written, declared.coerceAtLeast(0L), meter.bytesPerSecond(System.currentTimeMillis()))
                    }
                }
            }
            if (written <= 0L) throw IOException("下载内容为空")
            if (declared > 0L && written != declared) {
                throw IOException("文件不完整（已下载 $written 字节，应为 $declared 字节）")
            }
            return target
        } catch (e: UnknownHostException) {
            throw IOException("无法解析域名", e)
        } catch (e: SocketTimeoutException) {
            throw IOException("连接超时", e)
        } catch (e: SSLException) {
            throw IOException("安全连接失败", e)
        } finally {
            connection.disconnect()
        }
    }

    /** 把 HTTP 状态码翻译成用户能看懂的原因。 */
    fun describeStatus(status: Int): String = when {
        status == 403 || status == 429 -> "HTTP $status（访问受限或限流）"
        status == 404 -> "HTTP $status（安装包不存在）"
        status in 500..599 -> "HTTP $status（服务器错误）"
        else -> "HTTP $status"
    }
}
