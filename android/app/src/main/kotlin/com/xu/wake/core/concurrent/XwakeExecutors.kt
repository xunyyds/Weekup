package com.xu.wake.core.concurrent

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's shared background pools.
 *
 * Two pools, because the two workloads have opposite shapes:
 *
 * - [io] runs blocking work (web replay, file copies). Threads spend most of their life parked in
 *   a socket read, so a handful of extra threads costs almost nothing and keeps independent
 *   requests from queueing behind each other. This is what the WebView replay needs: it used to
 *   run on a single thread, so every sub-resource of a page waited for the previous one.
 * - [compute] runs CPU-bound work (schedule parsing). It stays at the core count: more threads
 *   than cores cannot finish more work, it only adds contention and cache thrash.
 *
 * Both pools bound their queue and fall back to running the task on the caller's thread rather
 * than dropping it, so a burst degrades into backpressure instead of silent loss.
 */
object XwakeExecutors {

    private val cores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /** Blocking I/O. Sized above the core count because these threads are mostly waiting. */
    val io: ExecutorService = build(
        name = "xwake-io",
        size = (cores * 2 + 2).coerceIn(4, 12),
        queueCapacity = 128,
    )

    /** CPU-bound parsing. Never more threads than cores. */
    val compute: ExecutorService = build(
        name = "xwake-compute",
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
            /* Never drop work: the submitting thread runs it, which throttles the producer. */
            ThreadPoolExecutor.CallerRunsPolicy(),
        )
        /* Let idle threads die so a background burst does not pin memory forever. */
        pool.allowCoreThreadTimeOut(true)
        return pool
    }

    /** Daemon threads with a readable name and background priority, so logcat stays diagnosable. */
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
