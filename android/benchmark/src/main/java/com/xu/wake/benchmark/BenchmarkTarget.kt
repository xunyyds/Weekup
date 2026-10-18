package com.xu.wake.benchmark

import android.os.ParcelFileDescriptor
import androidx.benchmark.macro.CompilationMode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Facts about the app under test plus the small UI-driving helpers every benchmark needs.
 *
 * Everything the benchmarks touch is expressed as text or content description that already
 * exists in the product UI — the benchmark module must never require a hook in production code.
 * When a selector changes in the app, update it here and every benchmark follows.
 */
object BenchmarkTarget {
    const val PACKAGE = "com.xu.wake"
    const val ACTIVITY = "com.xu.wake.MainActivity"

    /** Measured iterations per benchmark. Five is the library default and enough to spot a regression. */
    const val ITERATIONS = 5

    /**
     * Full AOT compilation keeps runs comparable across time on the same device.
     * Switch to `CompilationMode.None()` to measure the profile-guided, real-user state instead.
     */
    val COMPILATION: CompilationMode = CompilationMode.Full()

    /** Bottom dock labels, in order. */
    const val TAB_TIMETABLE = "课表"
    const val TAB_SETTINGS = "设置"

    /** contentDescription of the search pill on the settings page. */
    const val SEARCH_PILL = "搜索课程、设置与动作"
    /** contentDescription of the text field inside the full-screen search. */
    const val SEARCH_FIELD = "搜索框"

    /** Query driven into the search field; long enough to re-render the result list several times. */
    const val SEARCH_QUERY = "课程设置"

    const val EMPTY_TIMETABLE = "还没有课程，新增或导入你的课表"
    const val ADD_COURSE = "新增课程"

    /** How many synthetic courses the seeding step creates (one per weekday, two weeks of grid). */
    const val SEED_COURSE_COUNT = 12
}

/** Runs a shell command through the instrumentation's UiAutomation (shell uid, no root needed). */
fun shell(command: String): String {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
}

/** Launches the app through `am start -W` and waits until its window is present. */
fun UiDevice.launchApp() {
    shell("am start -W -n ${BenchmarkTarget.PACKAGE}/${BenchmarkTarget.ACTIVITY}")
    check(wait(Until.hasObject(By.pkg(BenchmarkTarget.PACKAGE).depth(0)), 10_000)) {
        "${BenchmarkTarget.PACKAGE} 未能在 10 秒内显示窗口，请确认已安装该包名的应用"
    }
    waitForIdle()
}

/** Waits for a node with the exact text and returns it, failing with the selector for easy triage. */
fun UiDevice.awaitText(text: String, timeout: Long = 8_000): UiObject2 =
    wait(Until.findObject(By.text(text)), timeout)
        ?: error("界面上找不到文本「$text」（超时 ${timeout}ms），应用界面可能已改变")

/** Waits for a node with the exact content description and returns it. */
fun UiDevice.awaitDesc(desc: String, timeout: Long = 8_000): UiObject2 =
    wait(Until.findObject(By.desc(desc)), timeout)
        ?: error("界面上找不到 contentDescription「$desc」（超时 ${timeout}ms），应用界面可能已改变")

/** Clicks a node found by exact text. */
fun UiDevice.clickText(text: String, timeout: Long = 8_000) {
    awaitText(text, timeout).click()
    waitForIdle()
}

/** Clicks a node found by exact content description. */
fun UiDevice.clickDesc(desc: String, timeout: Long = 8_000) {
    awaitDesc(desc, timeout).click()
    waitForIdle()
}

/** True when the timetable currently shows its "no courses" placeholder. */
fun UiDevice.timetableIsEmpty(): Boolean =
    hasObject(By.text(BenchmarkTarget.EMPTY_TIMETABLE))
