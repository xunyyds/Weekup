package com.xu.wake.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
