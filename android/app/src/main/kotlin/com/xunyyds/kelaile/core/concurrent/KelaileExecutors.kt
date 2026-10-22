package com.xunyyds.kelaile.core.concurrent

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object KelaileExecutors {

    private val cores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val io: ExecutorService = build(
        name = "kelaile-io",
        size = (cores * 2 + 2).coerceIn(4, 12),
        queueCapacity = 128,
    )

    val compute: ExecutorService = build(
        name = "kelaile-compute",
        size = cores.coerceIn(2, 6),
        queueCapacity = 32,
    )

    private fun build(name: String, size: Int, queueCapacity: Int): ExecutorService {
        val pool = ThreadPoolExecutor(
            size,
            size,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(queueCapacity),
            DaemonFactory(name),

            ThreadPoolExecutor.CallerRunsPolicy(),
        )

        pool.allowCoreThreadTimeOut(true)
        return pool
    }

    private class DaemonFactory(private val prefix: String) : ThreadFactory {
        private val next = AtomicInteger(1)
        override fun newThread(runnable: Runnable): Thread =
            Thread(runnable, prefix + "-" + next.getAndIncrement()).apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            }
    }
}
