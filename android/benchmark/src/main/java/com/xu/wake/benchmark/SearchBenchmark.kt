package com.xu.wake.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.xu.wake.benchmark.Interaction.flingScrollable
import com.xu.wake.benchmark.Interaction.typeInto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the full-screen search: open, type, browse results.
 *
 * Search is the interaction with the most work behind a single keystroke — the palette is scored,
 * the result list re-renders and the shared element morphs — so it gets its own benchmark. The
 * three tests split the cost by phase; comparing them tells you whether a regression sits in the
 * open transition, the query path or the result list itself.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SearchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Only the pill → full-screen morph, with no query typed. */
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

    /** One character at a time through the real query path. */
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

    /** Scrolling the result list after a query that matches many entries. */
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

    /**
     * Navigates settings → search and waits for the field.
     *
     * The search entry point lives on the settings page, so every iteration pays for one dock tap
     * first; that cost is identical across iterations and therefore part of the measured baseline.
     */
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
