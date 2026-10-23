package com.xunyyds.kelaile.data

import java.util.Locale

/**
 * 滑动窗口网速与进度格式化。
 *
 * 采样点按时间保存，网速取窗口内首尾的字节差除以真实经过时间，避免用相邻两次回调的瞬时差
 * 导致数字乱跳；窗口内样本不足时返回 null，让界面继续显示上一次的平滑值而不是 0。
 */
class TransferMeter(private val windowMillis: Long = 1_200L) {

    private data class Sample(val at: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()

    private var lastBytes = 0L
    private var lastAt = 0L
    private var smoothed = 0.0

    /** 记录一次进度回调；[at] 为单调时钟毫秒。 */
    fun record(bytes: Long, at: Long) {
        if (at <= 0L) return
        val safe = bytes.coerceAtLeast(0L)
        val previous = lastAt
        lastBytes = safe
        lastAt = at
        if (previous == 0L) {
            samples.addLast(Sample(at, safe))
            return
        }
        samples.addLast(Sample(at, safe))
        while (samples.size > 2 && at - samples.first().at > windowMillis) samples.removeFirst()

        val first = samples.first()
        val elapsed = at - first.at
        if (elapsed < MIN_ELAPSED) return
        val delta = safe - first.bytes
        if (delta < 0L) return
        val rate = delta * 1000.0 / elapsed
        smoothed = if (smoothed <= 0.0) rate else {
            val eased = smoothed + (rate - smoothed) * SMOOTHING
            eased.coerceIn(smoothed / MAX_STEP, smoothed * MAX_STEP)
        }
    }

    /** 当前网速（字节每秒）；样本不足时返回 null，调用方沿用上次显示值。 */
    fun bytesPerSecond(now: Long): Double? {
        if (smoothed <= 0.0) return null
        if (lastAt > 0L && now - lastAt > STALE_MILLIS) return 0.0
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val elapsed = last.at - first.at
        if (elapsed < MIN_ELAPSED) return null
        return smoothed
    }

    fun reset() {
        samples.clear()
        lastBytes = 0L
        lastAt = 0L
        smoothed = 0.0
    }

    private companion object {
        const val MIN_ELAPSED = 120L
        const val STALE_MILLIS = 1_500L

        /** 新样本权重，越小越平滑。 */
        const val SMOOTHING = 0.35

        /** 单次更新允许的相对变化上限，避免窗口内突发大块数据把读数放大到不合理倍数。 */
        const val MAX_STEP = 1.8
    }
}

/** 网速展示：`1.2 MB/s`；未就绪时给出占位而不是 0。 */
fun formatSpeed(bytesPerSecond: Double?): String {
    if (bytesPerSecond == null) return "计算中…"
    if (bytesPerSecond <= 0.0) return "0 B/s"
    return formatBytes(bytesPerSecond.toLong()) + "/s"
}

/** 文件大小展示：`12.3 MB`。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
}

/** 进度百分比：0..100 的整数，总大小未知时返回 null。 */
fun progressPercent(downloaded: Long, total: Long): Int? {
    if (total <= 0L) return null
    return ((downloaded.coerceAtLeast(0L) * 100L) / total).coerceIn(0L, 100L).toInt()
}

/** 进度文案：`12.3 MB / 45.6 MB`，总大小未知时显示「未知大小」。 */
fun progressLabel(downloaded: Long, total: Long): String {
    val done = formatBytes(downloaded.coerceAtLeast(0L))
    return if (total <= 0L) "$done / 未知大小" else "$done / ${formatBytes(total)}"
}
