package com.xu.wake.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold and warm start of the real app process.
 *
 * This is the metric a user feels every morning when they open the timetable, and it is also the
 * one place where the splash → first-frame hand-off shows up. It needs a physical device: on an
 * emulator the numbers are meaningless, which is why nothing here is asserted against a threshold.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Process is killed before every iteration: measures class loading, splash and first frame. */
    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    /** Process stays alive, the activity is recreated: isolates activity + Compose composition cost. */
    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    /**
     * Cold start with the timetable already populated, so the measured frame also pays for the
     * course projection and the grid it draws. Requires the seeded state; see [Seeding].
     */
    @Test
    fun coldStartupWithCourses() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.pkg(BenchmarkTarget.PACKAGE).depth(0)), 5_000)) {
            "启动后 5 秒内没有找到应用窗口"
        }
    }
}
