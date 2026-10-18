package com.xu.wake.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prepares the device, then stops. Deliberately not a benchmark: it is the one-time setup step the
 * scrolling and search benchmarks depend on, kept as its own test so it can be re-run on a wiped
 * device without re-measuring anything.
 *
 * Run it with:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.xu.wake.benchmark.SeedTimetable
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SeedTimetable {

    @Test
    fun seed() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Seeding.ensureSeeded(device)
        assertTrue(
            "播种后课表仍然为空，请检查 UI 选择器是否与当前版本的应用界面一致",
            Seeding.isSeeded(device),
        )
    }
}
