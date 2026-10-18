package com.xu.wake.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Gesture primitives shared by the frame-timing benchmarks.
 *
 * Compose reports its scroll containers through the accessibility tree, so the benchmarks fling the
 * node the app actually declared scrollable instead of swiping at guessed coordinates. A screen
 * swipe stays as the fallback for surfaces that only become scrollable after the first layout pass.
 */
object Interaction {

    /**
     * Flings the first scrollable node on screen.
     *
     * The gesture margin is a fifth of the screen height, which keeps the fling velocity comparable
     * between devices with very different densities — without it a large screen flings much faster
     * and the measured frames are no longer comparable.
     */
    fun UiDevice.flingScrollable(direction: Direction = Direction.DOWN) {
        val scrollable = wait(Until.findObject(By.scrollable(true)), 5_000)
        if (scrollable != null) flingNode(scrollable, direction) else swipeFallback(direction)
        waitForIdle()
    }

    /** Flings the node matching [selector], or the first scrollable node when it is not found. */
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

    /**
     * Types [text] into the node with content description [desc], one character at a time.
     *
     * Typing character by character is the point: each set-text is one accessibility action, so the
     * benchmark measures the query re-index and the result list re-render per keystroke rather than
     * a single jump to the final string. Latin input through `input text` would skip the Chinese
     * IME path the real UI uses, so the set-text action is the honest proxy here.
     */
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
