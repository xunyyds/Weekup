package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRulesTest {

    @Test
    fun segments_parsesTagsWithPrefixes() {
        assertEquals(listOf(2, 5, 3), VersionRules.segments("v2.5.3"))
        assertEquals(listOf(2, 5, 3), VersionRules.segments("2.5.3"))
        assertEquals(listOf(2, 6, 0), VersionRules.segments("release-2.6.0"))
        assertEquals(listOf(2, 6, 0), VersionRules.segments("V2.6.0-beta.1"))
        assertEquals(listOf(3), VersionRules.segments("v3"))
        assertTrue(VersionRules.segments("nightly").isEmpty())
        assertTrue(VersionRules.segments(null).isEmpty())
    }

    @Test
    fun normalize_stripsPrefixAndTrailingSuffix() {
        assertEquals("2.5.3", VersionRules.normalize("v2.5.3"))
        assertEquals("2.6.0", VersionRules.normalize("V2.6.0"))
        assertEquals("2.6.0", VersionRules.normalize("2.6.0-rc1"))
    }

    @Test
    fun compare_handlesVectorsAndPadding() {
        assertTrue(VersionRules.isNewer("2.6.0", "2.5.3"))
        assertTrue(VersionRules.isNewer("v2.6.0", "2.5.3"))
        assertFalse(VersionRules.isNewer("2.5.3", "2.5.3"))
        assertFalse(VersionRules.isNewer("2.5.2", "2.5.3"))
        assertEquals(0, VersionRules.compare("2.5", "2.5.0"))
        assertEquals(0, VersionRules.compare("unknown", "2.5.3"))
        assertEquals(0, VersionRules.compare(null, "2.5.3"))
    }

    @Test
    fun releaseGap_countsMinorStepsWithinSameMajor() {
        assertEquals(3, VersionRules.releaseGap("2.5.3", "2.8.0"))
        assertEquals(1, VersionRules.releaseGap("2.5.3", "2.6.0"))
        assertEquals(0, VersionRules.releaseGap("2.5.3", "2.5.4"))
        assertEquals(0, VersionRules.releaseGap("2.6.0", "2.5.3"))
    }

    @Test
    fun releaseGap_crossMajorAlwaysForces() {
        assertTrue(VersionRules.releaseGap("2.5.3", "3.0.0") >= VersionRules.FORCE_AFTER)
        assertTrue(VersionRules.releaseGap("2.0.0", "4.0.0") >= VersionRules.FORCE_AFTER)
    }

    @Test
    fun decide_noUpdateNeverShowsDialog() {
        val decision = VersionRules.decide("2.5.3", "2.5.3", null, manual = false)
        assertFalse(decision.show)
        assertFalse(decision.forced)
    }

    @Test
    fun decide_failedCheckShowsNothing() {
        val decision = VersionRules.decide(null, "2.5.3", null, manual = false)
        assertFalse(decision.show)
    }

    @Test
    fun decide_normalUpdateShowsNonForced() {
        val decision = VersionRules.decide("2.6.0", "2.5.3", null, manual = false)
        assertTrue(decision.show)
        assertFalse(decision.forced)
    }

    @Test
    fun decide_skippedVersionSuppressesOnlyThatVersion() {
        val skipped = VersionRules.decide("2.6.0", "2.5.3", "2.6.0", manual = false)
        assertFalse(skipped.show)

        val newer = VersionRules.decide("2.7.0", "2.5.3", "2.6.0", manual = false)
        assertTrue(newer.show)
        assertFalse(newer.forced)
    }

    @Test
    fun decide_manualIgnoresSkip() {
        val decision = VersionRules.decide("2.6.0", "2.5.3", "2.6.0", manual = true)
        assertTrue(decision.show)
        assertFalse(decision.forced)
    }

    @Test
    fun decide_forcesWhenLatestIsThreeReleasesAhead() {
        val decision = VersionRules.decide("2.8.0", "2.5.3", null, manual = false)
        assertTrue(decision.show)
        assertTrue(decision.forced)
    }

    @Test
    fun decide_skippedVersionStillForcedWhenThreeMissed() {
        val decision = VersionRules.decide("2.8.0", "2.5.3", "2.5.3", manual = false)
        assertTrue(decision.show)
        assertTrue(decision.forced)
    }

    @Test
    fun decide_skipForgivesOnlyUntilThreshold() {
        assertFalse(VersionRules.decide("2.7.0", "2.5.3", "2.7.0", manual = false).show)
        val forced = VersionRules.decide("2.8.0", "2.5.3", "2.5.3", manual = false)
        assertTrue(forced.forced)
    }

    @Test
    fun decide_forcedEvenWhenUserSkippedThatExactVersion() {
        val decision = VersionRules.decide("3.0.0", "2.5.3", "3.0.0", manual = false)
        assertTrue(decision.show)
        assertTrue(decision.forced)
    }
}
