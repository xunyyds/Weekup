package com.xunyyds.kelaile.benchmark

import android.os.ParcelFileDescriptor
import androidx.benchmark.macro.CompilationMode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object BenchmarkTarget {
    const val PACKAGE = "com.xunyyds.kelaile"
    const val ACTIVITY = "com.xunyyds.kelaile.MainActivity"

    const val ITERATIONS = 5

    val COMPILATION: CompilationMode = CompilationMode.Full()

    const val TAB_TIMETABLE = "课表"
    const val TAB_SETTINGS = "设置"

    const val SEARCH_PILL = "搜索课程、设置与动作"

    const val SEARCH_FIELD = "搜索框"

    const val SEARCH_QUERY = "课程设置"

    const val EMPTY_TIMETABLE = "还没有课程，新增或导入你的课表"
    const val ADD_COURSE = "新增课程"

    const val SEED_COURSE_COUNT = 12
}

fun shell(command: String): String {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
}

fun UiDevice.launchApp() {
    shell("am start -W -n ${BenchmarkTarget.PACKAGE}/${BenchmarkTarget.ACTIVITY}")
    check(wait(Until.hasObject(By.pkg(BenchmarkTarget.PACKAGE).depth(0)), 10_000)) {
        "${BenchmarkTarget.PACKAGE} 未能在 10 秒内显示窗口，请确认已安装该包名的应用"
    }
    waitForIdle()
}

fun UiDevice.awaitText(text: String, timeout: Long = 8_000): UiObject2 =
    wait(Until.findObject(By.text(text)), timeout)
        ?: error("界面上找不到文本「$text」（超时 ${timeout}ms），应用界面可能已改变")

fun UiDevice.awaitDesc(desc: String, timeout: Long = 8_000): UiObject2 =
    wait(Until.findObject(By.desc(desc)), timeout)
        ?: error("界面上找不到 contentDescription「$desc」（超时 ${timeout}ms），应用界面可能已改变")

fun UiDevice.clickText(text: String, timeout: Long = 8_000) {
    awaitText(text, timeout).click()
    waitForIdle()
}

fun UiDevice.clickDesc(desc: String, timeout: Long = 8_000) {
    awaitDesc(desc, timeout).click()
    waitForIdle()
}

fun UiDevice.timetableIsEmpty(): Boolean =
    hasObject(By.text(BenchmarkTarget.EMPTY_TIMETABLE))
