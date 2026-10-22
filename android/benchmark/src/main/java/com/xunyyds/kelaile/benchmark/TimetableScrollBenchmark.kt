package com.xunyyds.kelaile.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.xunyyds.kelaile.benchmark.Interaction.flingScrollable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TimetableScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollTimetable() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        requireTimetableVisible()

        repeat(3) {
            device.flingScrollable(Direction.DOWN)
            device.flingScrollable(Direction.UP)
        }
    }

    @Test
    fun swipeWeeks() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        requireTimetableVisible()

        repeat(3) {
            device.swipeWeek(next = true)
            device.swipeWeek(next = false)
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.requireTimetableVisible() {
        check(device.wait(Until.hasObject(androidx.test.uiautomator.By.text(BenchmarkTarget.TAB_TIMETABLE)), 8_000)) {
            "启动后 8 秒内没有看到「${BenchmarkTarget.TAB_TIMETABLE}」页，请先运行 SeedTimetable 播种数据"
        }
        device.waitForIdle()
    }

    private fun androidx.test.uiautomator.UiDevice.swipeWeek(next: Boolean) {
        val y = displayHeight / 2
        val from = if (next) displayWidth * 3 / 4 else displayWidth / 4
        val to = if (next) displayWidth / 4 else displayWidth * 3 / 4
        swipe(from, y, to, y, 20)
        waitForIdle()
    }
}
