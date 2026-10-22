package com.xunyyds.kelaile.benchmark

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

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

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
