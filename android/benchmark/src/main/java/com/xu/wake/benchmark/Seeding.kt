package com.xu.wake.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

object Seeding {

    private const val EDIT_TEXT_CLASS = "android.widget.EditText"
    private const val TIMETABLE_ACTION = "＋ 新增"
    private const val TIMETABLE_EMPTY_ACTION = "新增课程"

    fun ensureSeeded(device: UiDevice) {
        device.launchApp()
        if (device.timetableIsEmpty()) {
            repeat(BenchmarkTarget.SEED_COURSE_COUNT) { index -> addCourse(device, index) }
        }
        check(device.wait(Until.hasObject(By.text(BenchmarkTarget.TAB_SETTINGS)), 8_000)) {
            "底部导航栏缺少「${BenchmarkTarget.TAB_SETTINGS}」入口，无法切换到设置页"
        }
    }

    fun isSeeded(device: UiDevice): Boolean {
        device.launchApp()
        return !device.timetableIsEmpty()
    }

    private fun addCourse(device: UiDevice, index: Int) {
        if (device.timetableIsEmpty()) device.clickText(TIMETABLE_EMPTY_ACTION)
        else device.clickText(TIMETABLE_ACTION)

        val nameField = device.findObjects(By.clazz(EDIT_TEXT_CLASS)).firstOrNull()
            ?: error("课程编辑器里找不到可输入的文本框，应用界面可能已改变")
        nameField.text = "基准课程 ${index + 1}"
        device.waitForIdle()

        device.clickText("星期" + "一二三四五六日"[index % 7])
        device.clickText("保存")

        check(device.wait(Until.hasObject(By.text(TIMETABLE_ACTION)), 10_000)) {
            "新增课程没有在 10 秒内保存成功，请检查设备存储状态"
        }
        device.waitForIdle()
    }
}
