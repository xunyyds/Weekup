package com.xunyyds.kelaile.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.xunyyds.kelaile.benchmark.Interaction.flingScrollable
import com.xunyyds.kelaile.benchmark.Interaction.typeInto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SearchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openSearch() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        openSearchScreen()
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun typeQuery() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        openSearchScreen()
        device.typeInto(BenchmarkTarget.SEARCH_FIELD, BenchmarkTarget.SEARCH_QUERY)
    }

    @Test
    fun scrollResults() = benchmarkRule.measureRepeated(
        packageName = BenchmarkTarget.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkTarget.ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = BenchmarkTarget.COMPILATION,
        setupBlock = { pressHome() },
    ) {
        openSearchScreen()
        device.typeInto(BenchmarkTarget.SEARCH_FIELD, "课")
        repeat(3) {
            device.flingScrollable(Direction.DOWN)
            device.flingScrollable(Direction.UP)
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openSearchScreen() {
        startActivityAndWait()
        device.clickText(BenchmarkTarget.TAB_SETTINGS)
        device.clickDesc(BenchmarkTarget.SEARCH_PILL)
        check(device.wait(Until.hasObject(By.desc(BenchmarkTarget.SEARCH_FIELD)), 8_000)) {
            "全屏搜索没有在 8 秒内出现，请检查搜索入口的 contentDescription 是否变化"
        }
        device.waitForIdle()
    }
}
