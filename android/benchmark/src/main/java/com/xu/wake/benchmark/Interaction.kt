package com.xu.wake.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object Interaction {

    fun UiDevice.flingScrollable(direction: Direction = Direction.DOWN) {
        val scrollable = wait(Until.findObject(By.scrollable(true)), 5_000)
        if (scrollable != null) flingNode(scrollable, direction) else swipeFallback(direction)
        waitForIdle()
    }

    fun UiDevice.flingScrollableIn(selectorText: String, direction: Direction = Direction.DOWN) {
        val scrollable = findObjects(By.scrollable(true))
            .firstOrNull { it.text?.contains(selectorText) == true }
            ?: findObjects(By.scrollable(true)).firstOrNull()
        if (scrollable != null) flingNode(scrollable, direction) else swipeFallback(direction)
        waitForIdle()
    }

    private fun UiDevice.flingNode(node: UiObject2, direction: Direction) {
        node.setGestureMargin(displayHeight / 5)
        node.fling(direction)
    }

    private fun UiDevice.swipeFallback(direction: Direction) {
        val centerX = displayWidth / 2
        val from = if (direction == Direction.DOWN) displayHeight / 4 else displayHeight * 3 / 4
        val to = if (direction == Direction.DOWN) displayHeight * 3 / 4 else displayHeight / 4
        swipe(centerX, from, centerX, to, 12)
    }

    fun UiDevice.typeInto(desc: String, text: String) {
        val field = awaitDesc(desc)
        field.click()
        waitForIdle()
        val builder = StringBuilder()
        for (char in text) {
            builder.append(char)
            field.text = builder.toString()
            waitForIdle()
        }
    }
}
