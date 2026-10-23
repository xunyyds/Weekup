package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferMeterTest {

    @Test
    fun formatBytes_usesBinaryUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("12.3 MB", formatBytes((12.3 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun progressLabel_fallsBackWhenTotalUnknown() {
        assertEquals("12.0 MB / 未知大小", progressLabel(12L * 1024 * 1024, 0))
        assertEquals("12.0 MB / 45.0 MB", progressLabel(12L * 1024 * 1024, 45L * 1024 * 1024))
    }

    @Test
    fun progressPercent_handlesUnknownAndClamps() {
        assertNull(progressPercent(10, 0))
        assertEquals(0, progressPercent(0, 100))
        assertEquals(50, progressPercent(50, 100))
        assertEquals(100, progressPercent(120, 100))
        assertNull(progressPercent(10, -1))
    }

    @Test
    fun formatSpeed_showsPlaceholderBeforeFirstSample() {
        assertEquals("计算中…", formatSpeed(null))
        assertEquals("0 B/s", formatSpeed(0.0))
        assertEquals("1.0 MB/s", formatSpeed(1024.0 * 1024))
    }

    @Test
    fun meter_requiresTwoSamples() {
        val meter = TransferMeter()
        meter.record(1024, 1_000)
        assertNull(meter.bytesPerSecond(1_000))
    }

    @Test
    fun meter_reportsPositiveRateAfterWindow() {
        val meter = TransferMeter()
        var now = 1_000L
        var bytes = 0L
        repeat(12) {
            now += 200
            bytes += 200 * 1024
            meter.record(bytes, now)
        }
        val rate = meter.bytesPerSecond(now)
        assertTrue("rate should be positive but was $rate", rate != null && rate > 0)
        assertEquals(1024.0 * 1024, rate!!, 200_000.0)
    }

    @Test
    fun meter_smoothsSpikeInsteadOfJumping() {
        val meter = TransferMeter()
        var now = 0L
        var bytes = 0L
        repeat(10) {
            now += 200
            bytes += 100 * 1024
            meter.record(bytes, now)
        }
        val steady = meter.bytesPerSecond(now)!!
        bytes += 8 * 1024 * 1024
        now += 200
        meter.record(bytes, now)
        val spiked = meter.bytesPerSecond(now)!!
        assertTrue("spike should not multiply reported rate", spiked < steady * 4)
    }

    @Test
    fun meter_goesStaleWhenNoNewSamples() {
        val meter = TransferMeter()
        var now = 0L
        var bytes = 0L
        repeat(6) {
            now += 200
            bytes += 100 * 1024
            meter.record(bytes, now)
        }
        assertEquals(0.0, meter.bytesPerSecond(now + 5_000)!!, 0.0001)
    }

    @Test
    fun meter_resetClearsState() {
        val meter = TransferMeter()
        meter.record(1_000, 1_000)
        meter.record(2_000, 2_000)
        meter.reset()
        assertNull(meter.bytesPerSecond(2_000))
    }

    @Test
    fun meter_ignoresBackwardsProgress() {
        val meter = TransferMeter()
        meter.record(5_000, 1_000)
        meter.record(1_000, 1_200)
        val rate = meter.bytesPerSecond(1_200)
        assertTrue(rate == null || rate >= 0.0)
    }
}
